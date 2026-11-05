package ru.codebox.app;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs a standalone Android SDK android.jar.
 *
 * Does not download files or execute binaries.
 * Keeps the previous android.jar if installation fails.
 */
public final class CodeBoxAndroidJarInstaller {

    private static final long MAX_JAR_SIZE =
            150L * 1024L * 1024L;

    private static final int BUFFER_SIZE = 32768;

    public static final class Result {
        public final File installedFile;
        public final String sha256;
        public final long size;
        public final String report;

        private Result(
                File installedFile,
                String sha256,
                long size,
                String report
        ) {
            this.installedFile = installedFile;
            this.sha256 = sha256;
            this.size = size;
            this.report = report;
        }
    }

    private CodeBoxAndroidJarInstaller() {
    }

    /**
     * Installs android.jar from a user-selected stream.
     *
     * Must be called on a background thread.
     */
    public static synchronized Result install(
            Context context,
            InputStream input
    ) throws IOException {

        if (context == null || input == null) {
            throw new IllegalArgumentException(
                    "Не указан Context или поток android.jar"
            );
        }

        File tools = new File(
                context.getFilesDir(),
                "build-tools"
        ).getCanonicalFile();

        if (!tools.isDirectory() && !tools.mkdirs()) {
            throw new IOException(
                    "Не удалось создать каталог build-tools"
            );
        }

        File target = new File(
                tools,
                "android.jar"
        ).getCanonicalFile();

        File temporary = new File(
                tools,
                "android.jar.tmp"
        ).getCanonicalFile();

        File backup = new File(
                tools,
                "android.jar.backup"
        ).getCanonicalFile();

        if (!tools.equals(target.getParentFile())
                || !tools.equals(temporary.getParentFile())
                || !tools.equals(backup.getParentFile())) {
            throw new IOException(
                    "Недопустимый путь установки"
            );
        }

        if (temporary.exists() && !temporary.delete()) {
            throw new IOException(
                    "Не удалось удалить временный файл"
            );
        }

        String checksum;
        long total = 0;

        try {
            MessageDigest digest = createSha256();

            try (InputStream source =
                         new BufferedInputStream(input);
                 FileOutputStream output =
                         new FileOutputStream(temporary)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int count;

                while ((count = source.read(buffer)) != -1) {
                    if (count == 0) {
                        continue;
                    }

                    total += count;

                    if (total > MAX_JAR_SIZE) {
                        throw new IOException(
                                "android.jar превышает лимит 150 МБ"
                        );
                    }

                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                }

                output.getFD().sync();
            }

            if (total == 0) {
                throw new IOException(
                        "Выбран пустой android.jar"
                );
            }

            checksum = toHex(digest.digest());

            validateJar(temporary);

            if (backup.exists() && !backup.delete()) {
                throw new IOException(
                        "Не удалось очистить резервную копию"
                );
            }

            boolean hadPrevious = target.exists();

            if (hadPrevious && !target.renameTo(backup)) {
                throw new IOException(
                        "Не удалось сохранить предыдущий android.jar"
                );
            }

            if (!temporary.renameTo(target)) {
                if (hadPrevious && !backup.renameTo(target)) {
                    throw new IOException(
                            "Не удалось установить android.jar "
                            + "и восстановить предыдущую версию"
                    );
                }

                throw new IOException(
                        "Не удалось установить android.jar"
                );
            }

            StringBuilder report = new StringBuilder();

            report.append(
                    "CodeBox 5 — установка Android API\n"
            );

            report.append(
                    "Статус: INSTALLED\n"
            );

            report.append(
                    "Файл: "
            ).append(target.getAbsolutePath()).append('\n');

            report.append(
                    "Размер: "
            ).append(total).append(" байт\n");

            report.append(
                    "SHA-256: "
            ).append(checksum).append('\n');

            report.append(
                    "Проверка: ZIP/JAR структура и классы Android API\n"
            );

            report.append(
                    "ВНИМАНИЕ: подлинность и версия Android SDK "
                    + "не подтверждены.\n"
            );

            report.append(
                    "Совместимость с ECJ ещё не проверена.\n"
            );

            if (backup.exists() && !backup.delete()) {
                report.append(
                        "Предупреждение: резервная копия не удалена.\n"
                );
            }

            return new Result(
                    target,
                    checksum,
                    total,
                    report.toString()
            );

        } finally {
            if (temporary.exists()) {
                temporary.delete();
            }
        }
    }

    private static void validateJar(File jar)
            throws IOException {

        try (ZipFile zip = new ZipFile(jar)) {

            boolean hasActivity = false;
            boolean hasContext = false;
            boolean hasView = false;
            boolean hasManifest = false;

            Enumeration<? extends ZipEntry> entries =
                    zip.entries();

            int entryCount = 0;

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                entryCount++;

                if (entryCount > 100000) {
                    throw new IOException(
                            "Слишком много записей в android.jar"
                    );
                }

                String name = entry.getName();

                if ("android/app/Activity.class".equals(name)) {
                    hasActivity = true;
                }

                if ("android/content/Context.class".equals(name)) {
                    hasContext = true;
                }

                if ("android/view/View.class".equals(name)) {
                    hasView = true;
                }

                if ("META-INF/MANIFEST.MF".equals(name)) {
                    hasManifest = true;
                }
            }

            if (!hasActivity || !hasContext || !hasView) {
                throw new IOException(
                        "Это не подходящий android.jar: "
                        + "отсутствуют основные классы Android API"
                );
            }

            if (!hasManifest) {
                throw new IOException(
                        "Отсутствует META-INF/MANIFEST.MF"
                );
            }
        }
    }

    private static MessageDigest createSha256()
            throws IOException {

        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(
                    "SHA-256 недоступен",
                    e
            );
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder =
                new StringBuilder(bytes.length * 2);

        for (byte value : bytes) {
            builder.append(
                    String.format(
                            Locale.US,
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return builder.toString();
    }
}
