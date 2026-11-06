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
 * Prepares a base module ZIP from AAPT2 --proto-format output and D8 classes.dex.
 * This is NOT a complete .aab. BundleConfig, bundletool and signing are separate steps.
 * Invoke on a background thread.
 */
public final class CodeBoxAabBuilder {
    private static final int BUFFER = 16384;
    private static final int MAX_ENTRIES = 100000;
    private static final long MAX_BYTES = 512L * 1024 * 1024;

    private CodeBoxAabBuilder() { }

    public static final class Result {
        public final boolean prepared;
        public final File moduleZip;
        public final String report;

        private Result(boolean prepared, File moduleZip, String report) {
            this.prepared = prepared;
            this.moduleZip = moduleZip;
            this.report = report;
        }
    }

    public static Result buildBaseModule(Context context, File protoResourceApk,
                                         File dexFile, String projectName) {
        StringBuilder log = new StringBuilder("CodeBox 5 - AAB Base Module Builder\n");
        File temp = null;
        try {
            if (context == null) throw new IOException("Context отсутствует");
            if (projectName == null ||
                    !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
                throw new IOException("Недопустимое имя проекта");
            }
            if (protoResourceApk == null || !protoResourceApk.isFile()
                    || protoResourceApk.length() == 0) {
                throw new IOException("Промежуточный protobuf APK отсутствует");
            }
            if (dexFile == null || !dexFile.isFile() || dexFile.length() == 0) {
                throw new IOException("classes.dex отсутствует");
            }
            if (protoResourceApk.length() > MAX_BYTES || dexFile.length() > MAX_BYTES) {
                throw new IOException("Входные файлы слишком велики");
            }

            File dir = new File(context.getCacheDir(), "aab-base-" + projectName);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Не удалось создать рабочий каталог AAB");
            }
            File output = new File(dir, "base-module.zip");
            temp = new File(dir, "base-module.zip.tmp");
            if (temp.exists() && !temp.delete()) {
                throw new IOException("Не удалось удалить временный ZIP");
            }

            Set<String> names = new HashSet<>();
            boolean manifestFound = false;
            boolean resourcesFound = false;
            int countEntries = 0;
            long total = 0;
            byte[] buffer = new byte[BUFFER];

            try (ZipInputStream input = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(protoResourceApk)));
                 FileOutputStream fileOut = new FileOutputStream(temp);
                 ZipOutputStream outputZip = new ZipOutputStream(
                         new BufferedOutputStream(fileOut))) {
                ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    String original = entry.getName();
                    validateName(original);
                    if (entry.isDirectory()) {
                        input.closeEntry();
                        continue;
                    }
                    String mapped;
                    if ("AndroidManifest.xml".equals(original)) {
                        mapped = "manifest/AndroidManifest.xml";
                        manifestFound = true;
                    } else if ("resources.pb".equals(original)) {
                        mapped = "resources.pb";
                        resourcesFound = true;
                    } else if (original.startsWith("res/") ||
                            original.startsWith("assets/") ||
                            original.startsWith("lib/")) {
                        mapped = original;
                    } else if (original.startsWith("META-INF/") ||
                            original.equals("resources.arsc") ||
                            original.equals("classes.dex") ||
                            original.matches("classes[0-9]+\\.dex")) {
                        throw new IOException("Недопустимая запись protobuf APK: " + original);
                    } else {
                        // Other AAPT2 root files (if any) belong under root/ in a module.
                        mapped = "root/" + original;
                    }
                    validateName(mapped);
                    if (!names.add(mapped)) {
                        throw new IOException("Повтор записи модуля: " + mapped);
                    }
                    if (++countEntries > MAX_ENTRIES) {
                        throw new IOException("Слишком много записей в модуле");
                    }
                    ZipEntry dest = new ZipEntry(mapped);
                    outputZip.putNextEntry(dest);
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_BYTES) {
                            throw new IOException("Превышен лимит распакованных данных");
                        }
                        outputZip.write(buffer, 0, n);
                    }
                    outputZip.closeEntry();
                    input.closeEntry();
                }
                if (!manifestFound || !resourcesFound) {
                    throw new IOException("Нет protobuf манифеста или resources.pb");
                }
                if (!names.add("dex/classes.dex")) {
                    throw new IOException("Повтор dex/classes.dex");
                }
                outputZip.putNextEntry(new ZipEntry("dex/classes.dex"));
                try (InputStream dexIn = new BufferedInputStream(new FileInputStream(dexFile))) {
                    int n;
                    while ((n = dexIn.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_BYTES) {
                            throw new IOException("Превышен лимит размера модуля");
                        }
                        outputZip.write(buffer, 0, n);
                    }
                }
                outputZip.closeEntry();
                outputZip.finish();
            }

            try (ZipFile zip = new ZipFile(temp)) {
                requireEntry(zip, "manifest/AndroidManifest.xml");
                requireEntry(zip, "resources.pb");
                ZipEntry dex = zip.getEntry("dex/classes.dex");
                if (dex == null || dex.getSize() != dexFile.length()) {
                    throw new IOException("DEX отсутствует или его размер не совпадает");
                }
            }
            if (output.exists() && !output.delete()) {
                throw new IOException("Не удалось заменить прежний ZIP модуля");
            }
            if (!temp.renameTo(output)) {
                throw new IOException("Не удалось сохранить ZIP модуля");
            }
            log.append("BASE_MODULE_PREPARED: OK\n")
                    .append("Файл: ").append(output.getAbsolutePath()).append('\n')
                    .append("Размер: ").append(output.length()).append(" байт\n")
                    .append("Ресурсных записей: ").append(countEntries).append('\n')
                    .append("Это ZIP модуля base, НЕ готовый AAB.\n")
                    .append("Требуются bundletool, BundleConfig и подпись AAB.\n");
            return new Result(true, output, log.toString());
        } catch (Exception error) {
            if (temp != null && temp.exists()) temp.delete();
            log.append("BASE_MODULE_PREPARED: FAILED\nОшибка: ")
                    .append(error.getClass().getSimpleName()).append(": ")
                    .append(error.getMessage()).append('\n');
            return new Result(false, null, log.toString());
        }
    }

    private static void requireEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory() || entry.getSize() <= 0) {
            throw new IOException("Отсутствует обязательная запись: " + name);
        }
    }

    private static void validateName(String name) throws IOException {
        if (name == null || name.isEmpty() || name.startsWith("/") ||
                name.startsWith("\\") || name.contains("\\") ||
                name.indexOf('\0') >= 0 || name.equals("..") ||
                name.startsWith("../") || name.contains("/../") ||
                name.endsWith("/..") || name.contains("//") ||
                name.matches("^[A-Za-z]:.*")) {
            throw new IOException("Недопустимое имя ZIP-записи: " + name);
        }
    }
}
