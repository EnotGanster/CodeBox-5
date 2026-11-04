package ru.codebox.app;

import java.io.File;
import java.io.IOException;

public final class ProjectFileManager {

    private final File projectRoot;

    public ProjectFileManager(File projectRoot)
            throws IOException {

        if (projectRoot == null
                || !projectRoot.isDirectory()) {
            throw new IOException("Проект не найден");
        }

        this.projectRoot = projectRoot.getCanonicalFile();
    }

    public File createFolder(File parent, String name)
            throws IOException {

        validateName(name);

        File directory = resolveChild(parent, name);

        if (directory.exists()) {
            throw new IOException(
                    "Файл или папка уже существует"
            );
        }

        FileUtils.createDirectory(directory);

        return directory;
    }

    public File createFile(
            File parent,
            String name,
            String content
    ) throws IOException {

        validateName(name);

        File file = resolveChild(parent, name);

        if (file.exists()) {
            throw new IOException(
                    "Файл или папка уже существует"
            );
        }

        FileUtils.writeText(
                file,
                content == null ? "" : content
        );

        return file;
    }

    public File createJavaFile(
            File parent,
            String className,
            String packageName
    ) throws IOException {

        if (className == null
                || !className.matches(
                        "[A-Za-z_$][A-Za-z0-9_$]*"
                )) {
            throw new IOException(
                    "Неверное имя Java-класса"
            );
        }

        if (packageName == null
                || !packageName.matches(
                        "[A-Za-z_$][A-Za-z0-9_$]*"
                        + "(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
                )) {
            throw new IOException(
                    "Неверное имя Java-пакета"
            );
        }

        String content =
                "package " + packageName + ";\n\n"
                + "public class " + className + " {\n"
                + "\n"
                + "}\n";

        return createFile(
                parent,
                className + ".java",
                content
        );
    }

    private File resolveChild(File parent, String name)
            throws IOException {

        if (parent == null) {
            throw new IOException("Папка не указана");
        }

        File canonicalParent = parent.getCanonicalFile();

        if (!canonicalParent.isDirectory()) {
            throw new IOException(
                    "Родительская папка не существует"
            );
        }

        if (!isInsideProject(canonicalParent)) {
            throw new IOException(
                    "Папка находится вне проекта"
            );
        }

        File child = new File(
                canonicalParent, name
        ).getCanonicalFile();

        if (!isInsideProject(child)
                || child.equals(projectRoot)) {
            throw new IOException(
                    "Недопустимый путь"
            );
        }

        return child;
    }

    private boolean isInsideProject(File file)
            throws IOException {

        String rootPath =
                projectRoot.getCanonicalPath();

        String filePath =
                file.getCanonicalPath();

        return filePath.equals(rootPath)
                || filePath.startsWith(
                        rootPath + File.separator
                );
    }

    private void validateName(String name)
            throws IOException {

        if (name == null
                || name.isEmpty()
                || name.length() > 100
                || name.equals(".")
                || name.equals("..")
                || name.contains("/")
                || name.contains("\\")
                || name.indexOf('\0') >= 0
                || name.matches(".*[\\p{Cntrl}].*")) {

            throw new IOException(
                    "Недопустимое имя файла или папки"
            );
        }

        if (name.endsWith(" ")
                || name.endsWith(".")) {

            throw new IOException(
                    "Имя не должно заканчиваться "
                            + "пробелом или точкой"
            );
        }
    }
}
