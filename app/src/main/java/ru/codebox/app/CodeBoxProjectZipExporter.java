package ru.codebox.app;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Экспорт всего Android-проекта в ZIP.
 *
 * В архив записывается содержимое корня проекта:
 * app/, build.gradle, settings.gradle и другие элементы.
 * Лишняя внешняя папка с именем проекта НЕ добавляется.
 *
 * Этот класс не изменяет файлы проекта и не требует
 * разрешений на доступ к общему хранилищу Android.
 *
 * Для сохранения в "Загрузки" ProjectActivity должна
 * получить Uri через ACTION_CREATE_DOCUMENT и передать
 * ContentResolver.openOutputStream(uri) в exportProject().
 */
public final class CodeBoxProjectZipExporter {

    private static final int BUFFER_SIZE = 32768;
    private static final int MAX_DEPTH = 100;
    private static final int MAX_ENTRIES = 50000;
    private static final long MAX_TOTAL_BYTES =
            2L * 1024 * 1024 * 1024;
    private static final long MAX_SINGLE_FILE_BYTES =
            512L * 1024 * 1024;

    private CodeBoxProjectZipExporter() {
    }

    public static final class Result {
        public final int files;
        public final int directories;
        public final long uncompressedBytes;

        private Result(int files, int directories,
                       long uncompressedBytes) {
            this.files = files;
            this.directories = directories;
            this.uncompressedBytes = uncompressedBytes;
        }
    }

    private static final class Counter {
        int files;
        int directories;
        int entries;
        long totalBytes;
    }

    /**
     * Вызывать из фонового потока.
     *
     * @param projectRoot каталог проекта, например files/projects/CodeBoxTest
     * @param destination уже открытый поток целевого ZIP
     * @return число файлов/папок и общий исходный размер
     * @throws IOException если проект невозможно полностью прочитать,
     *                     есть символические ссылки или превышен лимит
     */
    public static Result exportProject(
            File projectRoot, OutputStream destination)
            throws IOException {

        if (projectRoot == null || destination == null) {
            throw new IOException("Не указаны проект или поток ZIP");
        }

        // Проверяем исходную ссылку до канонизации.
        if (Files.isSymbolicLink(projectRoot.toPath())) {
            throw new IOException(
                    "Корень проекта не может быть символической ссылкой");
        }

        File root = projectRoot.getCanonicalFile();
        if (!root.isDirectory() || !root.canRead()) {
            throw new IOException("Каталог проекта недоступен");
        }

        Counter counter = new Counter();
        String rootPath = root.getCanonicalPath();
        byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipOutputStream zip =
                     new ZipOutputStream(destination)) {
            writeDirectory(root, root, rootPath, zip,
                    counter, buffer, 0);
            zip.finish();
        }

        if (counter.entries == 0) {
            throw new IOException("Проект не содержит файлов");
        }

        return new Result(
                counter.files,
                counter.directories,
                counter.totalBytes);
    }

    private static void writeDirectory(
            File root, File directory, String rootPath,
            ZipOutputStream zip, Counter counter,
            byte[] buffer, int depth) throws IOException {

        if (depth > MAX_DEPTH) {
            throw new IOException(
                    "Слишком большая глубина вложенности папок");
        }

        checkSafe(rootPath, directory);

        File[] children = directory.listFiles();
        if (children == null) {
            throw new IOException(
                    "Не удалось прочитать: " + directory.getPath());
        }

        // Воспроизводимый, предсказуемый порядок файлов.
        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });

        for (File child : children) {
            checkSafe(rootPath, child);
            counter.entries++;

            if (counter.entries > MAX_ENTRIES) {
                throw new IOException(
                        "В проекте слишком много элементов");
            }

            String relative = root.toPath()
                    .relativize(child.toPath())
                    .toString()
                    .replace(File.separatorChar, '/');

            if (relative.length() == 0
                    || relative.startsWith("/")
                    || relative.equals("..")
                    || relative.startsWith("../")
                    || relative.contains("/../")) {
                throw new IOException(
                        "Недопустимый путь в проекте: " + relative);
            }

            if (child.isDirectory()) {
                ZipEntry entry = new ZipEntry(relative + "/");
                entry.setTime(child.lastModified());
                zip.putNextEntry(entry);
                zip.closeEntry();

                counter.directories++;
                writeDirectory(root, child, rootPath,
                        zip, counter, buffer, depth + 1);
            } else if (child.isFile()) {
                long declaredSize = child.length();

                if (declaredSize > MAX_SINGLE_FILE_BYTES) {
                    throw new IOException(
                            "Файл больше 512 МБ: " + relative);
                }

                if (declaredSize > MAX_TOTAL_BYTES
                        - counter.totalBytes) {
                    throw new IOException(
                            "Проект больше 2 ГБ");
                }

                ZipEntry entry = new ZipEntry(relative);
                entry.setTime(child.lastModified());
                zip.putNextEntry(entry);

                long fileBytes = 0;
                try (BufferedInputStream input =
                             new BufferedInputStream(
                                     new FileInputStream(child),
                                     BUFFER_SIZE)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        fileBytes += count;

                        if (fileBytes > MAX_SINGLE_FILE_BYTES
                                || count > MAX_TOTAL_BYTES
                                - counter.totalBytes) {
                            throw new IOException(
                                    "Превышен размер экспорта: "
                                            + relative);
                        }

                        zip.write(buffer, 0, count);
                        counter.totalBytes += count;
                    }
                }

                zip.closeEntry();
                counter.files++;
            } else {
                throw new IOException(
                        "Неподдерживаемый тип файла: " + relative);
            }
        }
    }

    private static void checkSafe(
            String rootPath, File file) throws IOException {

        if (Files.isSymbolicLink(file.toPath())) {
            throw new IOException(
                    "Символическая ссылка в проекте: "
                            + file.getName());
        }

        String canonical = file.getCanonicalPath();
        if (!canonical.equals(rootPath)
                && !canonical.startsWith(
                        rootPath + File.separator)) {
            throw new IOException(
                    "Файл выходит за пределы проекта: "
                            + file.getName());
        }
    }
}
