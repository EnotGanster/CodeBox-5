package ru.codebox.app;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs a pre-verified, flat ZIP of build tools into app-private storage.
 * Does not download or execute tools and does not establish runtime compatibility.
 * Call on a worker thread, never on the Android UI thread.
 */
public final class CodeBoxToolchainInstaller {

    private static final long MAX_ARCHIVE_BYTES = 150L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 300L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 150L * 1024 * 1024;
    private static final String[] REQUIRED = {
            "aapt2", "android.jar", "d8.jar", "ecj.jar",
            "apksigner.jar", "bundletool.jar"
    };

    private CodeBoxToolchainInstaller() { }

    public static final class Result {
        public final File directory;
        public final String archiveSha256;
        public final String report;

        private Result(File directory, String archiveSha256, String report) {
            this.directory = directory;
            this.archiveSha256 = archiveSha256;
            this.report = report;
        }
    }

    /**
     * expectedSha256 must come from a trusted source independent of the ZIP.
     * The caller owns and closes the input stream.
     */
    public static synchronized Result install(Context context, InputStream input,
                                               String expectedSha256) throws IOException {
        if (context == null || input == null) {
            throw new IllegalArgumentException("Context или архив отсутствует");
        }
        if (expectedSha256 == null ||
                !expectedSha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Требуется доверенный SHA-256 архива");
        }

        File root = context.getFilesDir().getCanonicalFile();
        File target = child(root, "build-tools");
        File staging = child(root, "build-tools-staging");
        File backup = child(root, "build-tools-backup");
        File archive = child(root, "build-tools-upload.tmp");

        if (staging.exists() || backup.exists() || archive.exists()) {
            throw new IOException("Обнаружены остатки предыдущей установки. " +
                    "Не удаляйте их автоматически: сначала проверьте состояние инструментов.");
        }

        String actualHash;
        try {
            actualHash = copyAndHash(input, archive);
            if (!actualHash.equalsIgnoreCase(expectedSha256)) {
                throw new IOException("SHA-256 архива не совпадает с доверенным значением");
            }
            if (!staging.mkdir()) {
                throw new IOException("Не удалось создать временную папку инструментов");
            }
            extract(archive, staging);
            if (target.exists()) {
                if (!target.isDirectory() || !target.renameTo(backup)) {
                    throw new IOException("Не удалось сохранить предыдущие инструменты");
                }
            }
            if (!staging.renameTo(target)) {
                if (backup.exists() && !backup.renameTo(target)) {
                    throw new IOException("Установка не удалась; резервная копия: " +
                            backup.getAbsolutePath());
                }
                throw new IOException("Не удалось активировать новые инструменты");
            }
            // Failure to delete a backup does not invalidate an activated kit.
            if (backup.exists()) deleteTree(backup);
            return new Result(target, actualHash,
                    "Комплект распакован. SHA-256: " + actualHash + "\n" +
                    "Каталог: " + target.getAbsolutePath() + "\n" +
                    "Запуск инструментов и возможность сборки APK/AAB НЕ проверены.");
        } finally {
            if (archive.exists()) archive.delete();
            // Never delete staging after a failed activation with a retained backup.
            if (staging.exists() && !backup.exists()) deleteTree(staging);
        }
    }

    private static String copyAndHash(InputStream input, File destination)
            throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 недоступен", e);
        }
        long total = 0;
        byte[] buffer = new byte[16384];
        try (FileOutputStream output = new FileOutputStream(destination)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                total += count;
                if (total > MAX_ARCHIVE_BYTES) {
                    throw new IOException("ZIP превышает лимит 150 МиБ");
                }
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return hex.toString();
    }

    private static void extract(File archive, File staging) throws IOException {
        Set<String> allowed = new HashSet<>(Arrays.asList(REQUIRED));
        Set<String> found = new HashSet<>();
        long expanded = 0;
        byte[] buffer = new byte[16384];
        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !allowed.contains(name) ||
                        !found.add(name)) {
                    throw new IOException("Лишний, повторный или запрещённый элемент ZIP: " + name);
                }
                File destination = child(staging, name);
                long size = 0;
                try (BufferedOutputStream output = new BufferedOutputStream(
                        new FileOutputStream(destination))) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        if (count == 0) continue;
                        size += count;
                        expanded += count;
                        if (size > MAX_FILE_BYTES || expanded > MAX_EXPANDED_BYTES) {
                            throw new IOException("Превышен лимит распаковки ZIP");
                        }
                        output.write(buffer, 0, count);
                    }
                }
                if (size == 0) throw new IOException("Пустой инструмент: " + name);
                zip.closeEntry();
            }
        }
        if (found.size() != allowed.size()) {
            allowed.removeAll(found);
            throw new IOException("В ZIP отсутствуют: " + allowed);
        }
    }

    private static File child(File parent, String name) throws IOException {
        File result = new File(parent, name).getCanonicalFile();
        if (!parent.equals(result.getParentFile())) {
            throw new IOException("Недопустимый путь: " + name);
        }
        return result;
    }

    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        file.delete();
    }
}
