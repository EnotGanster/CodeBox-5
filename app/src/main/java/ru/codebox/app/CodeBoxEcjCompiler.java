package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CodeBox 5 — ECJ Java Compiler.
 *
 * Experimental Android bootclasspath configuration.
 *
 * Compiles Java sources to .class files.
 * Does not create APK or AAB.
 *
 * Must be called from a background thread.
 */
public final class CodeBoxEcjCompiler {

    private static final int MAX_SOURCES = 5000;
    private static final int MAX_DEPTH = 40;

    public static final class Result {

        public final boolean compiled;
        public final int sourceCount;
        public final String report;
        public final File classesDirectory;

        private Result(
                boolean compiled,
                int sourceCount,
                String report,
                File classesDirectory
        ) {
            this.compiled = compiled;
            this.sourceCount = sourceCount;
            this.report = report;
            this.classesDirectory = classesDirectory;
        }
    }

    private CodeBoxEcjCompiler() {
    }

    public static Result compile(
            Context context,
            File projectDirectory
    ) throws IOException {

        if (context == null || projectDirectory == null) {
            throw new IllegalArgumentException(
                    "Не указан Context или каталог проекта"
            );
        }

        File projectsRoot = new File(
                context.getFilesDir(),
                "projects"
        ).getCanonicalFile();

        File project = projectDirectory.getCanonicalFile();

        if (!projectsRoot.equals(project.getParentFile())
                || !project.isDirectory()) {

            throw new IOException(
                    "Проект должен находиться непосредственно "
                            + "в каталоге projects"
            );
        }

        File sourceRoot = new File(
                project,
                "app/src/main/java"
        ).getCanonicalFile();

        if (!inside(project, sourceRoot)
                || !sourceRoot.isDirectory()) {

            throw new IOException(
                    "Каталог app/src/main/java "
                            + "не найден или недопустим"
            );
        }

        List<File> sources = new ArrayList<>();

        collect(
                sourceRoot,
                sourceRoot,
                sources,
                0
        );

        Collections.sort(
                sources,
                (a, b) -> a.getPath().compareTo(b.getPath())
        );

        File tools = new File(
                context.getFilesDir(),
                "build-tools"
        ).getCanonicalFile();

        File androidJar = new File(
                tools,
                "android.jar"
        ).getCanonicalFile();

        File cacheRoot =
                context.getCacheDir().getCanonicalFile();

        File output = new File(
                cacheRoot,
                "ecj-classes-" + project.getName()
        ).getCanonicalFile();

        if (!inside(cacheRoot, output)
                || output.equals(cacheRoot)) {

            throw new IOException(
                    "Недопустимый каталог результата"
            );
        }

        StringBuilder log = new StringBuilder();

        log.append("CodeBox 5 — ECJ Compiler\n");
        log.append("Режим: Android Bootclasspath Test\n");

        log.append("Проект: ")
                .append(project.getName())
                .append('\n');

        log.append("Android API устройства: ")
                .append(Build.VERSION.SDK_INT)
                .append('\n');

        log.append("Java runtime: ")
                .append(
                        System.getProperty(
                                "java.version",
                                "unknown"
                        )
                )
                .append('\n');

        log.append("Исходников: ")
                .append(sources.size())
                .append('\n');

        for (File source : sources) {

            log.append("  ")
                    .append(
                            sourceRoot.toURI()
                                    .relativize(
                                            source.toURI()
                                    )
                                    .getPath()
                    )
                    .append('\n');
        }

        log.append("\n--- Проверка Java API ---\n");

        String[] requiredClasses = {
                "javax.lang.model.SourceVersion",
                "javax.lang.model.element.Element",
                "javax.lang.model.type.TypeMirror",
                "javax.annotation.processing.Processor",
                "javax.tools.JavaFileObject",
                "javax.tools.Diagnostic",
                "org.eclipse.jdt.core.compiler.batch.BatchCompiler"
        };

        ClassLoader loader =
                CodeBoxEcjCompiler.class.getClassLoader();

        for (String className : requiredClasses) {

            try {

                Class.forName(
                        className,
                        false,
                        loader
                );

                log.append("OK: ")
                        .append(className)
                        .append('\n');

            } catch (ClassNotFoundException e) {

                log.append("MISSING: ")
                        .append(className)
                        .append('\n');

            } catch (LinkageError | RuntimeException e) {

                log.append("ERROR: ")
                        .append(className)
                        .append(" — ")
                        .append(e)
                        .append('\n');
            }
        }

        log.append("\n--- Android API ---\n");

        if (!tools.equals(androidJar.getParentFile())
                || !androidJar.isFile()
                || androidJar.length() == 0) {

            log.append(
                    "ERROR: Не найден непустой "
                            + "build-tools/android.jar\n"
            );

            log.append("Результат: FAILED\n");

            return new Result(
                    false,
                    sources.size(),
                    log.toString(),
                    output
            );
        }

        log.append("android.jar: ")
                .append(androidJar.getAbsolutePath())
                .append('\n');

        log.append("Размер: ")
                .append(androidJar.length())
                .append(" байт\n");

        if (sources.isEmpty()) {

            log.append(
                    "ERROR: Нет Java-файлов для компиляции\n"
            );

            log.append("Результат: FAILED\n");

            return new Result(
                    false,
                    0,
                    log.toString(),
                    output
            );
        }

        // Remove previous compilation output.
        // Old class files must not be mistaken
        // for newly compiled classes.

        deleteTree(output);

        if (!output.mkdirs()) {

            throw new IOException(
                    "Не удалось создать каталог class-файлов"
            );
        }

        List<String> arguments = new ArrayList<>();

        arguments.add("-encoding");
        arguments.add("UTF-8");

        arguments.add("-source");
        arguments.add("8");

        arguments.add("-target");
        arguments.add("8");

        // Disable annotation processing.
        arguments.add("-proc:none");

        // Experimental Android bootclasspath.
        // This may prevent ECJ from trying to
        // discover desktop JVM platform libraries.

        arguments.add("-bootclasspath");
        arguments.add(androidJar.getAbsolutePath());

        // Android classes for project compilation.

        arguments.add("-classpath");
        arguments.add(androidJar.getAbsolutePath());

        // Output directory for .class files.

        arguments.add("-d");
        arguments.add(output.getAbsolutePath());

        // Java source files.

        for (File source : sources) {

            arguments.add(
                    source.getAbsolutePath()
            );
        }

        log.append("\n--- Параметры ECJ ---\n");

        log.append("Source: Java 8\n");
        log.append("Target: Java 8\n");
        log.append("Annotation processing: disabled\n");

        log.append("Bootclasspath: ")
                .append(androidJar.getAbsolutePath())
                .append('\n');

        log.append("Classpath: ")
                .append(androidJar.getAbsolutePath())
                .append('\n');

        log.append("Output: ")
                .append(output.getAbsolutePath())
                .append('\n');

        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        boolean success = false;

        log.append("\n--- Запуск ECJ ---\n");

        try {

            Class<?> compiler = Class.forName(
                    "org.eclipse.jdt.core.compiler.batch.BatchCompiler",
                    true,
                    loader
            );

            Method compileMethod = null;

            for (Method candidate : compiler.getMethods()) {

                Class<?>[] parameters =
                        candidate.getParameterTypes();

                if (candidate.getName().equals("compile")
                        && parameters.length == 4
                        && parameters[0] == String[].class
                        && parameters[1] == PrintWriter.class
                        && parameters[2] == PrintWriter.class) {

                    compileMethod = candidate;
                    break;
                }
            }

            if (compileMethod == null) {

                log.append(
                        "ERROR: Метод "
                                + "BatchCompiler.compile "
                                + "не найден\n"
                );

            } else {

                Object value = compileMethod.invoke(
                        null,
                        arguments.toArray(new String[0]),
                        new PrintWriter(stdout, true),
                        new PrintWriter(stderr, true),
                        null
                );

                success = Boolean.TRUE.equals(value);
            }

        } catch (InvocationTargetException e) {

            log.append(
                    "ERROR: ECJ выбросил исключение\n"
            );

            Throwable cause = e.getCause() != null
                    ? e.getCause()
                    : e;

            appendThrowable(log, cause);

        } catch (ClassNotFoundException e) {

            log.append(
                    "ERROR: Класс ECJ не найден\n"
            );

            appendThrowable(log, e);

        } catch (ReflectiveOperationException e) {

            log.append(
                    "ERROR: Ошибка вызова ECJ\n"
            );

            appendThrowable(log, e);

        } catch (LinkageError e) {

            log.append(
                    "ERROR: Ошибка совместимости ECJ\n"
            );

            appendThrowable(log, e);

        } catch (RuntimeException e) {

            log.append(
                    "ERROR: Ошибка выполнения ECJ\n"
            );

            appendThrowable(log, e);
        }

        log.append("\n--- ECJ stdout ---\n");
        log.append(stdout.toString());
        log.append('\n');

        log.append("\n--- ECJ stderr ---\n");
        log.append(stderr.toString());
        log.append('\n');

        log.append("\nРезультат: ")
                .append(
                        success
                                ? "COMPILED"
                                : "FAILED"
                )
                .append('\n');

        log.append("Каталог классов: ")
                .append(output.getAbsolutePath())
                .append('\n');

        return new Result(
                success,
                sources.size(),
                log.toString(),
                output
        );
    }

    private static void appendThrowable(
            StringBuilder log,
            Throwable throwable
    ) {

        if (throwable == null) {
            return;
        }

        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);

        throwable.printStackTrace(writer);
        writer.flush();

        log.append(buffer.toString());
        log.append('\n');
    }

    private static void collect(
            File root,
            File directory,
            List<File> output,
            int depth
    ) throws IOException {

        if (depth > MAX_DEPTH) {

            throw new IOException(
                    "Превышена глубина каталогов"
            );
        }

        File[] children = directory.listFiles();

        if (children == null) {

            throw new IOException(
                    "Не удалось прочитать каталог: "
                            + directory
            );
        }

        for (File child : children) {

            File canonical =
                    child.getCanonicalFile();

            if (!inside(root, canonical)) {

                throw new IOException(
                        "Путь вне каталога Java"
                );
            }

            if (canonical.isDirectory()) {

                collect(
                        root,
                        canonical,
                        output,
                        depth + 1
                );

            } else if (canonical.isFile()
                    && canonical.getName().endsWith(".java")) {

                if (output.size() >= MAX_SOURCES) {

                    throw new IOException(
                            "Превышен лимит "
                                    + MAX_SOURCES
                                    + " Java-файлов"
                    );
                }

                output.add(canonical);
            }
        }
    }

    private static boolean inside(
            File root,
            File child
    ) throws IOException {

        String base =
                root.getCanonicalPath();

        String path =
                child.getCanonicalPath();

        return path.equals(base)
                || path.startsWith(
                        base + File.separator
                );
    }

    private static void deleteTree(
            File file
    ) throws IOException {

        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {

            File[] children =
                    file.listFiles();

            if (children == null) {

                throw new IOException(
                        "Не удалось прочитать "
                                + file
                );
            }

            for (File child : children) {

                deleteTree(child);
            }
        }

        if (!file.delete()) {

            throw new IOException(
                    "Не удалось удалить "
                            + file
            );
        }
    }
}
