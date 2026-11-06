package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CodeBox 5 - Local D8 Compiler.
 *
 * Converts ECJ .class files into classes.dex.
 *
 * Experimental Android Runtime compatibility.
 * Does not create, package or sign APK files.
 */
public final class CodeBoxD8Compiler {

    private static final int MAX_DEPTH = 40;
    private static final int MAX_CLASSES = 100000;

    private CodeBoxD8Compiler() {
    }

    public static final class Result {

        public final boolean compiled;
        public final File dexDirectory;
        public final File dexFile;
        public final String report;

        private Result(
                boolean compiled,
                File dexDirectory,
                File dexFile,
                String report
        ) {
            this.compiled = compiled;
            this.dexDirectory = dexDirectory;
            this.dexFile = dexFile;
            this.report = report;
        }
    }

    public static Result compile(
            Context context,
            File classesDirectory,
            String projectName
    ) {
        StringBuilder report = new StringBuilder();

        report.append("CodeBox 5 - D8 Compiler\n");
        report.append("Android API: ")
                .append(Build.VERSION.SDK_INT)
                .append('\n');

        File output = null;
        File dexFile = null;

        try {
            if (context == null) {
                throw new IOException(
                        "Context отсутствует"
                );
            }

            if (projectName == null
                    || !projectName.matches(
                            "[A-Za-z][A-Za-z0-9_]{0,49}"
                    )) {
                throw new IOException(
                        "Недопустимое имя проекта"
                );
            }

            if (classesDirectory == null
                    || !classesDirectory.isDirectory()) {
                throw new IOException(
                        "Каталог CLASS отсутствует"
                );
            }

            File cache = context.getCacheDir()
                    .getCanonicalFile();

            File classesRoot = classesDirectory
                    .getCanonicalFile();

            if (!classesRoot.getPath().startsWith(
                    cache.getPath() + File.separator
            )) {
                throw new IOException(
                        "Каталог CLASS вне cache"
                );
            }

            File expectedClasses = new File(
                    cache,
                    "ecj-classes-" + projectName
            ).getCanonicalFile();

            if (!expectedClasses.equals(classesRoot)) {
                throw new IOException(
                        "Неожиданный каталог CLASS"
                );
            }

            output = new File(
                    cache,
                    "d8-output-" + projectName
            ).getCanonicalFile();

            if (!cache.equals(output.getParentFile())) {
                throw new IOException(
                        "Недопустимый каталог D8"
                );
            }

            report.append("CLASS: ")
                    .append(classesRoot.getAbsolutePath())
                    .append('\n');

            report.append("DEX output: ")
                    .append(output.getAbsolutePath())
                    .append('\n');

            List<File> classFiles = new ArrayList<>();

            collectClasses(
                    classesRoot,
                    classesRoot,
                    classFiles,
                    0
            );

            report.append("Найдено CLASS файлов: ")
                    .append(classFiles.size())
                    .append('\n');

            if (classFiles.isEmpty()) {
                throw new IOException(
                        "Нет файлов .class для D8"
                );
            }

            if (output.exists()) {
                deleteTree(output, output, 0);
            }

            if (!output.mkdirs()) {
                throw new IOException(
                        "Не удалось создать каталог D8"
                );
            }

            File androidJar = new File(
                    context.getFilesDir(),
                    "build-tools/android.jar"
            ).getCanonicalFile();

            if (!androidJar.isFile()
                    || androidJar.length() == 0) {
                throw new IOException(
                        "android.jar отсутствует"
                );
            }

            report.append("Android library: ")
                    .append(androidJar.getAbsolutePath())
                    .append('\n');

            report.append("Min API: 26\n");
            report.append("Output mode: DexIndexed\n");

            report.append("\n--- Запуск D8 ---\n");

            D8Command.Builder builder =
                    D8Command.builder();

            builder.addProgramFiles(
                    classFiles.toArray(new File[0])
                            .length == 0
                            ? new java.nio.file.Path[0]
                            : toPaths(classFiles)
            );

            builder.addLibraryFiles(
                    androidJar.toPath()
            );

            builder.setMinApiLevel(26);

            builder.setOutput(
                    output.toPath(),
                    OutputMode.DexIndexed
            );

            D8.run(builder.build());

            dexFile = new File(
                    output,
                    "classes.dex"
            ).getCanonicalFile();

            if (!output.equals(dexFile.getParentFile())) {
                throw new IOException(
                        "Недопустимый путь classes.dex"
                );
            }

            if (!dexFile.isFile()
                    || dexFile.length() == 0) {
                throw new IOException(
                        "D8 не создал classes.dex"
                );
            }

            report.append("Результат: DEX_CREATED\n");
            report.append("classes.dex: ")
                    .append(dexFile.getAbsolutePath())
                    .append('\n');

            report.append("Размер: ")
                    .append(dexFile.length())
                    .append(" байт\n");

            return new Result(
                    true,
                    output,
                    dexFile,
                    report.toString()
            );

        } catch (Throwable error) {

            report.append("\nРезультат: FAILED\n");
            report.append("Ошибка: ")
                    .append(error.getClass().getName())
                    .append('\n');

            if (error.getMessage() != null) {
                report.append("Сообщение: ")
                        .append(error.getMessage())
                        .append('\n');
            }

            Throwable cause = error.getCause();

            if (cause != null) {
                report.append("Причина: ")
                        .append(cause.getClass().getName())
                        .append(": ")
                        .append(cause.getMessage())
                        .append('\n');
            }

            return new Result(
                    false,
                    output,
                    dexFile,
                    report.toString()
            );
        }
    }

    private static java.nio.file.Path[] toPaths(
            List<File> files
    ) {
        java.nio.file.Path[] paths =
                new java.nio.file.Path[files.size()];

        for (int i = 0; i < files.size(); i++) {
            paths[i] = files.get(i).toPath();
        }

        return paths;
    }

    private static void collectClasses(
            File root,
            File directory,
            List<File> files,
            int depth
    ) throws IOException {

        if (depth > MAX_DEPTH) {
            throw new IOException(
                    "Превышена глубина CLASS"
            );
        }

        File[] children = directory.listFiles();

        if (children == null) {
            throw new IOException(
                    "Не удалось прочитать CLASS каталог"
            );
        }

        for (File child : children) {
            File canonical = child.getCanonicalFile();

            if (!canonical.getPath().startsWith(
                    root.getPath() + File.separator
            )) {
                throw new IOException(
                        "CLASS файл вне каталога"
                );
            }

            if (canonical.isDirectory()) {
                collectClasses(
                        root,
                        canonical,
                        files,
                        depth + 1
                );

            } else if (canonical.isFile()
                    && canonical.getName()
                            .endsWith(".class")) {

                files.add(canonical);

                if (files.size() > MAX_CLASSES) {
                    throw new IOException(
                            "Слишком много CLASS файлов"
                    );
                }
            }
        }
    }

    private static void deleteTree(
            File root,
            File target,
            int depth
    ) throws IOException {

        if (depth > MAX_DEPTH) {
            throw new IOException(
                    "Превышена глубина удаления"
            );
        }

        File canonical = target.getCanonicalFile();

        if (!canonical.equals(root)
                && !canonical.getPath().startsWith(
                        root.getPath() + File.separator
                )) {
            throw new IOException(
                    "Удаление вне каталога D8"
            );
        }

        if (canonical.isDirectory()) {
            File[] children = canonical.listFiles();

            if (children == null) {
                throw new IOException(
                        "Не удалось прочитать каталог D8"
                );
            }

            for (File child : children) {
                deleteTree(
                        root,
                        child,
                        depth + 1
                );
            }
        }

        if (!canonical.delete()) {
            throw new IOException(
                    "Не удалось удалить: "
                    + canonical.getAbsolutePath()
            );
        }
    }
}
