package ru.codebox.app;

import android.content.Context;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.commands.BuildBundleCommand;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Creates an unsigned Android App Bundle from a prepared base module.
 *
 * Requires:
 * com.android.tools.build:bundletool:1.18.2
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

        try {
            if (context == null) {
                throw new IOException("Context отсутствует");
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

            File output = new File(
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

            Path modulePath = moduleZip.toPath();
            Path outputPath = output.toPath();

            // Minimal BundleConfig. Bundletool supplies defaults
            // for configuration fields not explicitly set.
            BundleConfig config =
                    BundleConfig.newBuilder()
                            .build();

            log.append("\n--- Bundletool build-bundle ---\n");

            BuildBundleCommand command =
                    BuildBundleCommand.builder()
                            .setModulesPaths(
                                    Collections.singletonList(
                                            modulePath
                                    )
                            )
                            .setOutputPath(outputPath)
                            .setBundleConfig(config)
                            .build();

            command.execute();

            if (!Files.isRegularFile(outputPath) ||
                    Files.size(outputPath) == 0) {
                throw new IOException(
                        "Bundletool не создал AAB"
                );
            }

            log.append("Bundletool: завершён\n");

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

            log.append("Проверка структуры: OK\n");

            log.append("Результат: UNSIGNED_AAB_CREATED\n");

            log.append("AAB: ")
                    .append(output.getAbsolutePath())
                    .append('\n');

            log.append("Размер: ")
                    .append(output.length())
                    .append(" байт\n");

            log.append(
                    "AAB ещё не подписан. " +
                    "Для публикации потребуется JAR-подпись.\n"
            );

            return new Result(
                    true,
                    output,
                    log.toString()
            );

        } catch (Throwable error) {
            log.append("\nРезультат: FAILED\n");

            log.append("Ошибка: ")
                    .append(error.getClass().getName())
                    .append(": ")
                    .append(error.getMessage())
                    .append('\n');

            return new Result(
                    false,
                    null,
                    log.toString()
            );
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
                    "В AAB отсутствует обязательный файл: " +
                    name
            );
        }
    }
}
