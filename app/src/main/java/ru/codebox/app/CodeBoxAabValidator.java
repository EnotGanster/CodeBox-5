package ru.codebox.app;

import android.content.Context;

import com.android.tools.build.bundletool.model.AppBundle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * CodeBox 5 - AAB Validator.
 *
 * Checks Android App Bundle structure
 * and compatibility with bundletool.
 *
 * Run on a background thread.
 */
public final class CodeBoxAabValidator {

    private static final long MAX_AAB_BYTES =
            512L * 1024L * 1024L;

    private static final int BUFFER_SIZE = 8192;

    private CodeBoxAabValidator() {
    }

    public static final class Result {

        public final boolean valid;
        public final File aabFile;
        public final String report;

        private Result(
                boolean valid,
                File aabFile,
                String report
        ) {
            this.valid = valid;
            this.aabFile = aabFile;
            this.report = report;
        }
    }

    /**
     * Validate an existing AAB.
     */
    public static Result validate(
            Context context,
            File aabFile
    ) {

        StringBuilder log = new StringBuilder();

        log.append("CodeBox 5 - AAB Validator\n");
        log.append("=========================\n");

        try {

            if (context == null) {

                throw new IOException(
                        "Context отсутствует"
                );
            }

            if (aabFile == null
                    || !aabFile.isFile()
                    || aabFile.length() == 0) {

                throw new IOException(
                        "AAB не найден"
                );
            }

            if (aabFile.length() > MAX_AAB_BYTES) {

                throw new IOException(
                        "AAB превышает допустимый размер"
                );
            }

            log.append("\n--- Исходный AAB ---\n");

            log.append("Файл: ")
                    .append(aabFile.getAbsolutePath())
                    .append('\n');

            log.append("Размер: ")
                    .append(aabFile.length())
                    .append(" байт\n");

            log.append("\n--- ZIP validation ---\n");

            int count = 0;

            Set<String> names = new HashSet<>();

            byte[] buffer = new byte[BUFFER_SIZE];

            try (ZipFile zip = new ZipFile(aabFile)) {

                Enumeration<? extends ZipEntry> entries =
                        zip.entries();

                while (entries.hasMoreElements()) {

                    ZipEntry entry = entries.nextElement();

                    String name = entry.getName();

                    if (name == null
                            || name.isEmpty()
                            || name.startsWith("/")
                            || name.contains("\\")
                            || name.indexOf('\0') >= 0) {

                        throw new IOException(
                                "Недопустимое имя записи: "
                                        + name
                        );
                    }

                    String[] parts = name.split("/");

                    for (String part : parts) {

                        if ("..".equals(part)) {

                            throw new IOException(
                                    "Небезопасный путь: "
                                            + name
                            );
                        }
                    }

                    if (!names.add(name)) {

                        throw new IOException(
                                "Повторяющаяся запись: "
                                        + name
                        );
                    }

                    if (entry.isDirectory()) {
                        continue;
                    }

                    try (InputStream input =
                                 zip.getInputStream(entry)) {

                        while (input.read(buffer) != -1) {
                            // Read the entire ZIP entry.
                            // ZIP CRC is checked by the
                            // underlying implementation.
                        }
                    }

                    count++;
                }

                requireEntry(
                        zip,
                        "BundleConfig.pb"
                );

                requireEntry(
                        zip,
                        "base/manifest/AndroidManifest.xml"
                );

                requireEntry(
                        zip,
                        "base/resources.pb"
                );

                requireEntry(
                        zip,
                        "base/dex/classes.dex"
                );
            }

            log.append("ZIP: OK\n");

            log.append("Файлов: ")
                    .append(count)
                    .append('\n');

            log.append("BundleConfig.pb: OK\n");
            log.append("base manifest: OK\n");
            log.append("resources.pb: OK\n");
            log.append("classes.dex: OK\n");

            log.append("\n--- Bundletool validation ---\n");

            /*
             * bundletool 1.18.2 expects ZipFile,
             * not java.nio.file.Path.
             *
             * Keep the ZIP open while bundletool
             * reads and parses the App Bundle.
             */
            try (ZipFile bundleZip = new ZipFile(aabFile)) {

                AppBundle bundle =
                        AppBundle.buildFromZip(
                                bundleZip
                        );

                if (bundle == null) {

                    throw new IOException(
                            "Bundletool не прочитал AAB"
                    );
                }

                log.append(
                        "AppBundle.buildFromZip: OK\n"
                );
            }

            log.append("\n--- Результат ---\n");

            log.append("Статус: VALID\n");

            log.append(
                    "Структура AAB проверена успешно\n"
            );

            return new Result(
                    true,
                    aabFile,
                    log.toString()
            );

        } catch (Throwable error) {

            log.append("\n--- Ошибка ---\n");

            log.append(
                    error.getClass().getName()
            );

            log.append(": ")
                    .append(error.getMessage())
                    .append('\n');

            StringWriter writer =
                    new StringWriter();

            PrintWriter printer =
                    new PrintWriter(writer);

            error.printStackTrace(printer);
            printer.flush();

            log.append(writer.toString());

            log.append("\n--- Результат ---\n");

            log.append("Статус: INVALID\n");

            return new Result(
                    false,
                    null,
                    log.toString()
            );
        }
    }

    /**
     * Require a non-empty ZIP entry.
     */
    private static void requireEntry(
            ZipFile zip,
            String name
    ) throws IOException {

        ZipEntry entry = zip.getEntry(name);

        if (entry == null
                || entry.isDirectory()
                || entry.getSize() <= 0) {

            throw new IOException(
                    "Отсутствует обязательный файл: "
                            + name
            );
        }
    }
}
