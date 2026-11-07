package ru.codebox.app;

import android.content.Context;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.commands.BuildBundleCommand;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * CodeBox 5 - Bundletool Compiler.
 *
 * Creates an unsigned Android App Bundle from
 * a prepared base module ZIP.
 *
 * Includes detailed diagnostics for Guava and
 * bundletool runtime compatibility.
 *
 * Run on a background thread.
 */
public final class CodeBoxBundletoolCompiler {

    private static final long MAX_MODULE_BYTES =
            512L * 1024 * 1024;

    private CodeBoxBundletoolCompiler() {
    }

    public static final class Result {

        public final boolean built;
        public final File aabFile;
        public final String report;

        private Result(
                boolean built,
                File aabFile,
                String report
        ) {
            this.built = built;
            this.aabFile = aabFile;
            this.report = report;
        }
    }

    public static Result build(
            Context context,
            File moduleZip,
            String projectName
    ) {

        StringBuilder log = new StringBuilder(
                "CodeBox 5 - Bundletool Compiler\n"
        );

        File output = null;

        try {

            if (context == null) {
                throw new IOException(
                        "Context отсутствует"
                );
            }

            if (projectName == null ||
                    !projectName.matches(
                            "[A-Za-z][A-Za-z0-9_]{0,49}"
                    )) {
                throw new IOException(
                        "Недопустимое имя проекта"
                );
            }

            if (moduleZip == null ||
                    !moduleZip.isFile() ||
                    moduleZip.length() == 0) {
                throw new IOException(
                        "ZIP модуля base отсутствует"
                );
            }

            if (moduleZip.length() > MAX_MODULE_BYTES) {
                throw new IOException(
                        "ZIP модуля слишком большой"
                );
            }

            File outputDir = new File(
                    context.getCacheDir(),
                    "aab-bundle-" + projectName
            );

            if (!outputDir.isDirectory() &&
                    !outputDir.mkdirs()) {
                throw new IOException(
                        "Не удалось создать каталог AAB"
                );
            }

            output = new File(
                    outputDir,
                    projectName + "-unsigned.aab"
            );

            if (output.exists() && !output.delete()) {
                throw new IOException(
                        "Не удалось удалить прежний AAB"
                );
            }

            log.append("Модуль: ")
                    .append(moduleZip.getAbsolutePath())
                    .append('\n');

            log.append("Размер модуля: ")
                    .append(moduleZip.length())
                    .append(" байт\n");

            log.append("Выходной AAB: ")
                    .append(output.getAbsolutePath())
                    .append('\n');

            log.append("\n--- Runtime diagnostics ---\n");

            appendClassInfo(
                    log,
                    "Bundletool",
                    BuildBundleCommand.class
            );

            appendClassInfo(
                    log,
                    "Guava ImmutableList",
                    ImmutableList.class
            );

            appendClassInfo(
                    log,
                    "Guava Predicate",
                    com.google.common.base.Predicate.class
            );

            appendClassInfo(
                    log,
                    "Java Predicate",
                    java.util.function.Predicate.class
            );

            boolean predicateCompatible =
                    java.util.function.Predicate.class
                            .isAssignableFrom(
                                    com.google.common.base.Predicate.class
                            );

            log.append("Guava Predicate implements Java Predicate: ")
                    .append(predicateCompatible)
                    .append('\n');

            appendGuavaPredicateDiagnostics(log);

            Path modulePath = moduleZip.toPath();
            Path outputPath = output.toPath();

            BundleConfig config =
                    BundleConfig.newBuilder()
                            .build();

            log.append(
                    "\n--- Bundletool build-bundle ---\n"
            );

            BuildBundleCommand command =
                    BuildBundleCommand.builder()
                            .setModulesPaths(
                                    ImmutableList.of(modulePath)
                            )
                            .setOutputPath(outputPath)
                            .setBundleConfig(config)
                            .build();

            log.append("BuildBundleCommand: создан\n");

            command.execute();

            log.append("BuildBundleCommand: выполнен\n");

            if (!Files.isRegularFile(outputPath) ||
                    Files.size(outputPath) == 0) {
                throw new IOException(
                        "Bundletool не создал AAB"
                );
            }

            log.append(
                    "Bundletool: завершён успешно\n"
            );

            try (ZipFile zip = new ZipFile(output)) {

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

            log.append(
                    "Проверка структуры AAB: OK\n"
            );

            log.append(
                    "Результат: UNSIGNED_AAB_CREATED\n"
            );

            log.append("AAB: ")
                    .append(output.getAbsolutePath())
                    .append('\n');

            log.append("Размер: ")
                    .append(output.length())
                    .append(" байт\n");

            log.append(
                    "AAB ещё не подписан.\n"
            );

            log.append(
                    "Для публикации потребуется JAR-подпись.\n"
            );

            return new Result(
                    true,
                    output,
                    log.toString()
            );

        } catch (Throwable error) {

            if (output != null && output.exists()) {
                output.delete();
            }

            log.append(
                    "\nРезультат: FAILED\n"
            );

            log.append("Ошибка: ")
                    .append(error.getClass().getName())
                    .append(": ")
                    .append(error.getMessage())
                    .append('\n');

            log.append("\n--- Full stack trace ---\n");

            StringWriter writer = new StringWriter();
            PrintWriter printer = new PrintWriter(writer);

            error.printStackTrace(printer);
            printer.flush();

            log.append(writer.toString());

            Throwable cause = error.getCause();

            if (cause != null) {
                log.append("\nRoot cause: ")
                        .append(cause.getClass().getName())
                        .append(": ")
                        .append(cause.getMessage())
                        .append('\n');
            }

            return new Result(
                    false,
                    null,
                    log.toString()
            );
        }
    }

    private static void appendClassInfo(
            StringBuilder log,
            String label,
            Class<?> type
    ) {

        log.append(label)
                .append(": ")
                .append(type.getName())
                .append('\n');

        try {

            ClassLoader loader = type.getClassLoader();

            log.append("  ClassLoader: ")
                    .append(loader == null
                            ? "BOOT"
                            : loader.getClass().getName())
                    .append('\n');

            Package pkg = type.getPackage();

            if (pkg != null) {

                log.append("  Version: ")
                        .append(pkg.getImplementationVersion())
                        .append('\n');
            }

        } catch (Throwable error) {

            log.append("  Diagnostic error: ")
                    .append(error.getClass().getName())
                    .append('\n');
        }
    }

    private static void appendGuavaPredicateDiagnostics(
            StringBuilder log
    ) {

        log.append("\n--- Guava Predicate diagnostics ---\n");

        try {

            Class<?> predicateClass =
                    Class.forName(
                            "com.google.common.base.Predicates$NotPredicate"
                    );

            log.append("NotPredicate: FOUND\n");

            appendClassInfo(
                    log,
                    "NotPredicate",
                    predicateClass
            );

            boolean compatible =
                    java.util.function.Predicate.class
                            .isAssignableFrom(predicateClass);

            log.append(
                    "NotPredicate implements Java Predicate: "
            ).append(compatible).append('\n');

            Method[] methods =
                    predicateClass.getDeclaredMethods();

            for (Method method : methods) {

                if ("apply".equals(method.getName())
                        || "test".equals(method.getName())) {

                    log.append("Method: ")
                            .append(method.toString())
                            .append('\n');
                }
            }

        } catch (Throwable error) {

            log.append("Predicate diagnostics error: ")
                    .append(error.getClass().getName())
                    .append(": ")
                    .append(error.getMessage())
                    .append('\n');
        }
    }

    private static void requireEntry(
            ZipFile zip,
            String name
    ) throws IOException {

        ZipEntry entry = zip.getEntry(name);

        if (entry == null ||
                entry.isDirectory() ||
                entry.getSize() <= 0) {

            throw new IOException(
                    "В AAB отсутствует обязательный файл: "
                            + name
            );
        }
    }
}
