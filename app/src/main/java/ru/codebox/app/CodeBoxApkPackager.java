package ru.codebox.app;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
            }

            if (output.exists() && !output.delete()) {
                throw new IOException("Не удалось заменить старый APK");
            }
            if (!temp.renameTo(output)) throw new IOException("Не удалось переименовать временный APK");

            log.append("Скопировано записей из AAPT2: ").append(copied).append('\n');
            log.append("classes.dex: ").append(dexFile.length()).append(" байт\n");
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
}
