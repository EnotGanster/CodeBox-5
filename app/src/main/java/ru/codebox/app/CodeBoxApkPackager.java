package ru.codebox.app;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * CodeBox 5: merges an AAPT2 resource APK with D8 classes.dex.
 * The result is unsigned and cannot yet be installed.
 * Run from a background thread.
 */
public final class CodeBoxApkPackager {

    private static final int BUFFER_SIZE = 16384;
    private static final int MAX_ENTRIES = 100000;
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_NATIVE_ENTRIES = 128;
    private static final long MAX_NATIVE_LIBRARY_BYTES = 96L * 1024 * 1024;
    private static final long MAX_NATIVE_TOTAL_BYTES = 192L * 1024 * 1024;

    private static final class NativeLibrary {
        final File file;
        final String archiveName;

        NativeLibrary(File file, String archiveName) {
            this.file = file;
            this.archiveName = archiveName;
        }
    }

    private CodeBoxApkPackager() { }

    public static final class Result {
        public final boolean packaged;
        public final File apkFile;
        public final String report;

        private Result(boolean packaged, File apkFile, String report) {
            this.packaged = packaged;
            this.apkFile = apkFile;
            this.report = report;
        }
    }

    public static Result packageApk(
            Context context,
            File resourceApk,
            File dexFile,
            String projectName
    ) {
        StringBuilder log = new StringBuilder("CodeBox 5 - APK Packager\n");
        File temp = null;
        try {
            if (context == null) throw new IOException("Context отсутствует");
            if (projectName == null || !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
                throw new IOException("Недопустимое имя проекта");
            }
            if (resourceApk == null || !resourceApk.isFile() || resourceApk.length() == 0) {
                throw new IOException("Промежуточный APK AAPT2 отсутствует или пуст");
            }
            if (dexFile == null || !dexFile.isFile() || dexFile.length() == 0) {
                throw new IOException("classes.dex отсутствует или пуст");
            }
            if (resourceApk.length() > MAX_UNCOMPRESSED_BYTES || dexFile.length() > MAX_UNCOMPRESSED_BYTES) {
                throw new IOException("Слишком большой входной файл");
            }

            File outputDir = new File(context.getCacheDir(), "apk-package-" + projectName);
            if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
                throw new IOException("Не удалось создать каталог APK");
            }
            File output = new File(outputDir, projectName + "-unsigned.apk");
            temp = new File(outputDir, projectName + "-unsigned.apk.tmp");
            if (temp.exists() && !temp.delete()) throw new IOException("Не удалось удалить временный APK");

            // Optional native libraries are packaged only when the project supplies them.
            // This does NOT change the built-in AAPT2 used by CodeBox 5 itself.
            List<NativeLibrary> nativeLibraries = collectNativeLibraries(context, projectName);
            log.append("Native libraries: ").append(nativeLibraries.size()).append('\n');
            if (!nativeLibraries.isEmpty()) {
                log.append("Native packaging: DEFLATED; for an extracted nativeLibraryDir, ")
                        .append("set android:extractNativeLibs=\"true\" in the target app manifest.\n");
            }
            log.append("Resource APK: ").append(resourceApk.getAbsolutePath()).append('\n');
            log.append("DEX: ").append(dexFile.getAbsolutePath()).append('\n');
            log.append("Output: ").append(output.getAbsolutePath()).append('\n');

            Set<String> names = new HashSet<>();
            boolean manifestFound = false;
            int copied = 0;
            long totalBytes = 0;
            byte[] buffer = new byte[BUFFER_SIZE];

            try (ZipInputStream input = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(resourceApk)));
                 FileOutputStream fileOut = new FileOutputStream(temp);
                 ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(fileOut))) {

                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name == null || name.isEmpty() || name.startsWith("/") ||
                            name.startsWith("\\") || name.contains("\\") ||
                            name.equals("..") || name.startsWith("../") ||
                            name.contains("/../") || name.endsWith("/..") ||
                            name.contains("\u0000")) {
                        throw new IOException("Недопустимое имя ZIP-записи: " + name);
                    }
                    if (!names.add(name)) throw new IOException("Повтор ZIP-записи: " + name);
                    if (names.size() > MAX_ENTRIES) throw new IOException("Слишком много записей APK");
                    if (name.equals("classes.dex") || name.matches("classes[0-9]+\\.dex")) {
                        throw new IOException("В ресурсном APK уже есть DEX: " + name);
                    }
                    if (name.equals("AndroidManifest.xml") && !entry.isDirectory()) {
                        manifestFound = true;
                    }
                    ZipEntry newEntry = new ZipEntry(name);
                    if (entry.getTime() >= 0) newEntry.setTime(entry.getTime());
                    zipOut.putNextEntry(newEntry);
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        totalBytes += count;
                        if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw new IOException("Превышен лимит распакованных данных APK");
                        }
                        zipOut.write(buffer, 0, count);
                    }
                    zipOut.closeEntry();
                    input.closeEntry();
                    copied++;
                }
                if (!manifestFound) throw new IOException("В ресурсном APK нет AndroidManifest.xml");

                ZipEntry dexEntry = new ZipEntry("classes.dex");
                zipOut.putNextEntry(dexEntry);
                try (InputStream dexIn = new BufferedInputStream(new FileInputStream(dexFile))) {
                    int count;
                    while ((count = dexIn.read(buffer)) != -1) {
                        totalBytes += count;
                        if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw new IOException("Превышен лимит размера APK");
                        }
                        zipOut.write(buffer, 0, count);
                    }
                }
                zipOut.closeEntry();

                // Stage 20.1: package optional app/src/main/jniLibs/<abi>/lib*.so.
                // Only direct descendants of the allowlisted ABI folder are supported.
                for (NativeLibrary nativeLib : nativeLibraries) {
                    String name = nativeLib.archiveName;
                    if (!names.add(name)) {
                        throw new IOException("Повтор ZIP-записи для нативной библиотеки: " + name);
                    }
                    if (names.size() > MAX_ENTRIES) {
                        throw new IOException("Слишком много записей в APK");
                    }
                    ZipEntry nativeEntry = new ZipEntry(name);
                    zipOut.putNextEntry(nativeEntry);
                    try (InputStream libIn = new BufferedInputStream(new FileInputStream(nativeLib.file))) {
                        int count;
                        while ((count = libIn.read(buffer)) != -1) {
                            totalBytes += count;
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw new IOException("Превышен лимит распакованных данных APK");
                            }
                            zipOut.write(buffer, 0, count);
                        }
                    }
                    zipOut.closeEntry();
                }
                zipOut.finish();
            }

            try (ZipFile check = new ZipFile(temp)) {
                if (check.getEntry("AndroidManifest.xml") == null ||
                        check.getEntry("classes.dex") == null) {
                    throw new IOException("Проверка APK: отсутствует Manifest или DEX");
                }
                if (check.getEntry("classes.dex").getSize() != dexFile.length()) {
                    throw new IOException("Проверка APK: размер classes.dex не совпадает");
                }
                for (NativeLibrary nativeLib : nativeLibraries) {
                    ZipEntry entry = check.getEntry(nativeLib.archiveName);
                    if (entry == null || entry.getSize() != nativeLib.file.length()) {
                        throw new IOException("Нативная библиотека не записана полностью: "
                                + nativeLib.archiveName);
                    }
                }
            }

            if (output.exists() && !output.delete()) {
                throw new IOException("Не удалось заменить старый APK");
            }
            if (!temp.renameTo(output)) throw new IOException("Не удалось переименовать временный APK");

            log.append("Скопировано записей из AAPT2: ").append(copied).append('\n');
            log.append("classes.dex: ").append(dexFile.length()).append(" байт\n");
            for (NativeLibrary nativeLib : nativeLibraries) {
                log.append("JNI: ").append(nativeLib.archiveName).append(" (")
                        .append(nativeLib.file.length()).append(" байт)\n");
            }
            log.append("Результат: UNSIGNED_APK_CREATED\n");
            log.append("APK: ").append(output.getAbsolutePath()).append('\n');
            log.append("Размер: ").append(output.length()).append(" байт\n");
            log.append("APK не подписан. Установка пока невозможна.\n");
            return new Result(true, output, log.toString());

        } catch (Exception error) {
            if (temp != null && temp.exists()) temp.delete();
            log.append("Результат: FAILED\nОшибка: ")
                    .append(error.getClass().getSimpleName())
                    .append(": ").append(error.getMessage()).append('\n');
            return new Result(false, null, log.toString());
        }
    }

    /**
     * Safely locate only the known JNI library layout in a user project.
     * This method never changes any project files or native executables.
     */
    private static List<NativeLibrary> collectNativeLibraries(
            Context context, String projectName) throws IOException {
        List<NativeLibrary> result = new ArrayList<>();
        File projects = new File(context.getFilesDir(), "projects").getCanonicalFile();
        File project = new File(projects, projectName).getCanonicalFile();
        if (!projects.equals(project.getParentFile())) {
            throw new IOException("Небезопасный путь проекта для JNI");
        }
        File root = new File(project, "app/src/main/jniLibs");
        if (!root.exists()) return result;
        if (Files.isSymbolicLink(root.toPath()) || !root.isDirectory()) {
            throw new IOException("jniLibs не является безопасной папкой");
        }
        File rootCanonical = root.getCanonicalFile();
        // Reject symbolic links anywhere in app/src/main/jniLibs, not just
        // at the final directory. Otherwise an ancestor symlink could
        // redirect JNI collection outside the selected project.
        if (!rootCanonical.equals(root.getAbsoluteFile())) {
            throw new IOException("Символическая ссылка в пути jniLibs запрещена");
        }
        File[] abiFolders = root.listFiles();
        if (abiFolders == null) throw new IOException("Нельзя прочитать jniLibs");
        long totalNativeBytes = 0;
        for (File abiDir : abiFolders) {
            if (Files.isSymbolicLink(abiDir.toPath())) {
                throw new IOException("Символическая ссылка в jniLibs запрещена");
            }
            String abi = abiDir.getName();
            if (!abiDir.isDirectory() || !isAllowedAbi(abi)) {
                throw new IOException("Недопустимая ABI-папка jniLibs: " + abi);
            }
            File canonicalAbi = abiDir.getCanonicalFile();
            if (!rootCanonical.equals(canonicalAbi.getParentFile())) {
                throw new IOException("Папка ABI выходит за пределы jniLibs");
            }
            File[] libraries = abiDir.listFiles();
            if (libraries == null) throw new IOException("Нельзя прочитать папку ABI: " + abi);
            for (File library : libraries) {
                if (Files.isSymbolicLink(library.toPath()) || !library.isFile()) {
                    throw new IOException("В jniLibs ожидается обычный файл .so: " + library.getName());
                }
                String name = library.getName();
                if (!name.matches("lib[A-Za-z0-9_.+\\-]+\\.so")) {
                    throw new IOException("Неверное имя нативной библиотеки: " + name);
                }
                if (!canonicalAbi.equals(library.getCanonicalFile().getParentFile())) {
                    throw new IOException("Библиотека выходит за пределы ABI-папки");
                }
                long size = library.length();
                if (size < 20 || size > MAX_NATIVE_LIBRARY_BYTES) {
                    throw new IOException("Недопустимый размер нативной библиотеки: " + name);
                }
                totalNativeBytes += size;
                if (totalNativeBytes > MAX_NATIVE_TOTAL_BYTES || result.size() >= MAX_NATIVE_ENTRIES) {
                    throw new IOException("Превышен лимит нативных библиотек");
                }
                validateElf(library, abi);
                result.add(new NativeLibrary(library, "lib/" + abi + "/" + name));
            }
        }
        return result;
    }

    private static boolean isAllowedAbi(String abi) {
        return "arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)
                || "armeabi".equals(abi) || "x86".equals(abi)
                || "x86_64".equals(abi);
    }

    /** ELF signature, little-endian machine and ABI check; not runtime validation. */
    private static void validateElf(File file, String abi) throws IOException {
        byte[] header = new byte[20];
        try (InputStream stream = new FileInputStream(file)) {
            int read = 0;
            while (read < header.length) {
                int size = stream.read(header, read, header.length - read);
                if (size < 0) break;
                read += size;
            }
            if (read < header.length) throw new IOException("ELF заголовок неполный");
        }
        if ((header[0] & 0xFF) != 0x7f || header[1] != 'E' ||
                header[2] != 'L' || header[3] != 'F' ||
                header[5] != 1) {
            throw new IOException("Не ELF или неверный порядок байт: " + file.getName());
        }
        int bits = header[4] & 0xFF;
        int machine = (header[18] & 0xFF) | ((header[19] & 0xFF) << 8);
        boolean compatible;
        if ("arm64-v8a".equals(abi)) compatible = bits == 2 && machine == 183;
        else if ("x86_64".equals(abi)) compatible = bits == 2 && machine == 62;
        else if ("x86".equals(abi)) compatible = bits == 1 && machine == 3;
        else compatible = bits == 1 && machine == 40;
        if (!compatible) {
            throw new IOException("ELF не соответствует ABI " + abi + ": " + file.getName());
        }
    }
    }
