package ru.codebox.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class FileUtils {

    private FileUtils() {
        // Служебный класс
    }

    public static void createDirectory(File directory)
            throws IOException {

        if (directory.isDirectory()) {
            return;
        }

        if (directory.exists()) {
            throw new IOException(
                    "Путь занят файлом: " + directory.getName()
            );
        }

        if (!directory.mkdirs()) {
            throw new IOException(
                    "Не удалось создать папку: "
                            + directory.getName()
            );
        }
    }

    public static void writeText(File file, String content)
            throws IOException {

        if (file.exists()) {
            throw new IOException(
                    "Файл уже существует: " + file.getName()
            );
        }

        File parent = file.getParentFile();

        if (parent != null) {
            createDirectory(parent);
        }

        try (FileOutputStream output =
                     new FileOutputStream(file, false)) {

            output.write(
                    content.getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    public static String readText(File file)
            throws IOException {

        if (!file.isFile()) {
            throw new IOException(
                    "Файл не найден: " + file.getName()
            );
        }

        try (FileInputStream input =
                     new FileInputStream(file)) {

            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream result =
                    new java.io.ByteArrayOutputStream();

            int count;

            while ((count = input.read(buffer)) != -1) {
                result.write(buffer, 0, count);
            }

            return new String(
                    result.toByteArray(),
                    StandardCharsets.UTF_8
            );
        }
    }

    public static boolean exists(File file) {
        return file.exists();
    }
}
