package ru.codebox.app;

import android.content.Context;

import com.android.tools.build.bundletool.commands.BuildApksCommand;

import java.io.File;
import java.io.IOException;

/**
 * CodeBox 5 - APKS Builder.
 *
 * Creates a universal APK set from an AAB.
 *
 * APK signing and installation are handled
 * separately.
 */
public final class CodeBoxApksBuilder {

    private CodeBoxApksBuilder() {
    }

    public static final class Result {

        public final boolean built;
        public final File apksFile;
        public final String report;

        private Result(
                boolean built,
                File apksFile,
                String report
        ) {
            this.built = built;
            this.apksFile = apksFile;
            this.report = report;
        }
    }

    public static Result build(
            Context context,
            File aabFile,
            String projectName
    ) {

        StringBuilder log = new StringBuilder();

        log.append("CodeBox 5 - APKS Builder\n");
        log.append("========================\n");

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
                        "Исходный AAB не найден"
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

            File outputDir = new File(
                    context.getCacheDir(),
                    "apks-output-" + projectName
            );

            if (!outputDir.isDirectory()
                    && !outputDir.mkdirs()) {

                throw new IOException(
                        "Не удалось создать каталог APKS"
                );
            }

            File output = new File(
                    outputDir,
                    projectName + ".apks"
            );

            if (output.exists()
                    && !output.delete()) {

                throw new IOException(
                        "Не удалось удалить старый APKS"
                );
            }

            log.append("Исходный AAB: ")
                    .append(aabFile.getAbsolutePath())
                    .append('\n');

            log.append("Выходной APKS: ")
                    .append(output.getAbsolutePath())
                    .append('\n');

            log.append("Режим: UNIVERSAL\n");

            /*
             * The signing configuration must be
             * supplied before executing build-apks.
             *
             * We intentionally stop here rather
             * than generate an APK signed with
             * an unrelated or unknown key.
             */

            log.append(
                    "\nBuildApksCommand: доступен\n"
            );

            log.append(
                    "Подготовка каталога: OK\n"
            );

            log.append(
                    "Подпись APK: ещё не подключена\n"
            );

            log.append(
                    "Результат: NOT_BUILT\n"
            );

            return new Result(
                    false,
                    null,
                    log.toString()
            );

        } catch (Throwable error) {

            log.append("\nОшибка: ")
                    .append(error.getClass().getName())
                    .append(": ")
                    .append(error.getMessage())
                    .append('\n');

            log.append("Результат: FAILED\n");

            return new Result(
                    false,
                    null,
                    log.toString()
            );
        }
    }
}
