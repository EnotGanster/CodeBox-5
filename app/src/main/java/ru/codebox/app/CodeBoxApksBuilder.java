package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import com.android.tools.build.bundletool.commands.BuildApksCommand;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.model.utils.Aapt2Command;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * CodeBox 5 - APKS Builder.
 *
 * Generates a universal APK set from an AAB
 * using bundletool.
 *
 * The extracted APK is an intermediate artifact.
 * It must be signed with the persistent CodeBox
 * APK signing key before being distributed.
 */
public final class CodeBoxApksBuilder {

    private static final long MAX_AAB_BYTES =
            512L * 1024L * 1024L;

    private static final long MAX_APK_BYTES =
            512L * 1024L * 1024L;

    private CodeBoxApksBuilder() {
    }

    public static final class Result {

        public final boolean built;
        public final File apksFile;
        public final File universalApk;
        public final String report;

        private Result(
                boolean built,
                File apksFile,
                File universalApk,
                String report
        ) {
            this.built = built;
            this.apksFile = apksFile;
            this.universalApk = universalApk;
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

        File output = null;
        File extractedApk = null;

        try {

            if (context == null) {
                throw new IOException(
                        "Context отсутствует"
                );
            }

            if (Build.VERSION.SDK_INT < 26) {
                throw new IOException(
                        "Требуется Android API 26+"
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

            if (aabFile == null
                    || !aabFile.isFile()
                    || aabFile.length() == 0) {

                throw new IOException(
                        "Исходный AAB отсутствует"
                );
            }

            if (aabFile.length() > MAX_AAB_BYTES) {
                throw new IOException(
                        "Исходный AAB слишком большой"
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

            output = new File(
                    outputDir,
                    projectName + ".apks"
            );

            extractedApk = new File(
                    outputDir,
                    projectName + "-universal-intermediate.apk"
            );

            if (output.exists()
                    && !output.delete()) {

                throw new IOException(
                        "Не удалось удалить прежний APKS"
                );
            }

            if (extractedApk.exists()
                    && !extractedApk.delete()) {

                throw new IOException(
                        "Не удалось удалить прежний APK"
                );
            }

            log.append("\n--- Input ---\n");
            log.append("AAB: ")
                    .append(aabFile.getAbsolutePath())
                    .append('\n');

            log.append("AAB size: ")
                    .append(aabFile.length())
                    .append(" bytes\n");

            log.append("\n--- AAPT2 ---\n");

            File nativeAapt2 = new File(
                    context.getApplicationInfo()
                            .nativeLibraryDir,
                    "libaapt2.so"
            );

            File externalAapt2 = new File(
                    context.getFilesDir(),
                    "build-tools/aapt2"
            );

            File aapt2;

            if (nativeAapt2.isFile()
                    && nativeAapt2.canExecute()) {

                aapt2 = nativeAapt2;

            } else if (externalAapt2.isFile()
                    && externalAapt2.canExecute()) {

                aapt2 = externalAapt2;

            } else {

                throw new IOException(
                        "Исполняемый AAPT2 не найден"
                );
            }

            log.append("AAPT2: ")
                    .append(aapt2.getAbsolutePath())
                    .append('\n');

            log.append("\n--- Bundletool build-apks ---\n");

            log.append("Mode: UNIVERSAL\n");
            log.append("Output: ")
                    .append(output.getAbsolutePath())
                    .append('\n');

            /*
             * Without an explicit signing configuration,
             * bundletool may use its debug signing key.
             *
             * Therefore, the generated APK is NOT
             * considered the final signed application.
             */

            BuildApksCommand command =
                    BuildApksCommand.builder()
                            .setBundlePath(
                                    aabFile.toPath()
                            )
                            .setOutputFile(
                                    output.toPath()
                            )
                            .setApkBuildMode(
                                    ApkBuildMode.UNIVERSAL
                            )
                            .setAapt2Command(
                                    Aapt2Command
                                            .createFromExecutablePath(
                                                    aapt2.toPath()
                                            )
                            )
                            .setOverwriteOutput(true)
                            .build();

            log.append(
                    "BuildApksCommand: создан\n"
            );

            command.execute();

            log.append(
                    "BuildApksCommand: выполнен\n"
            );

            if (!output.isFile()
                    || output.length() == 0) {

                throw new IOException(
                        "Bundletool не создал APKS"
                );
            }

            log.append("APKS_CREATED: OK\n");

            log.append("APKS size: ")
                    .append(output.length())
                    .append(" bytes\n");

            log.append("\n--- APKS validation ---\n");

            try (ZipFile zip = new ZipFile(output)) {

                ZipEntry universal =
                        zip.getEntry("universal.apk");

                if (universal == null
                        || universal.isDirectory()) {

                    throw new IOException(
                            "В APKS отсутствует universal.apk"
                    );
                }

                log.append(
                        "universal.apk: FOUND\n"
                );

                Enumeration<? extends ZipEntry> entries =
                        zip.entries();

                int count = 0;

                while (entries.hasMoreElements()) {

                    ZipEntry entry = entries.nextElement();

                    log.append("Entry: ")
                            .append(entry.getName())
                            .append('\n');

                    count++;
                }

                log.append("ZIP entries: ")
                        .append(count)
                        .append('\n');

                try (
                        InputStream input =
                                zip.getInputStream(universal);

                        FileOutputStream fileOutput =
                                new FileOutputStream(extractedApk)
                ) {

                    byte[] buffer = new byte[8192];

                    int read;
                    long total = 0;

                    while ((read = input.read(buffer)) != -1) {

                        total += read;

                        if (total > MAX_APK_BYTES) {
                            throw new IOException(
                                    "Universal APK слишком большой"
                            );
                        }

                        fileOutput.write(
                                buffer,
                                0,
                                read
                        );
                    }

                    fileOutput.flush();
                    fileOutput.getFD().sync();
                }
            }

            if (!extractedApk.isFile()
                    || extractedApk.length() == 0) {

                throw new IOException(
                        "Не удалось извлечь universal.apk"
                );
            }

            try (ZipFile apkZip =
                         new ZipFile(extractedApk)) {

                ZipEntry manifest =
                        apkZip.getEntry(
                                "AndroidManifest.xml"
                        );

                ZipEntry dex =
                        apkZip.getEntry(
                                "classes.dex"
                        );

                if (manifest == null
                        || manifest.isDirectory()) {

                    throw new IOException(
                            "В universal.apk отсутствует "
                                    + "AndroidManifest.xml"
                    );
                }

                if (dex == null
                        || dex.isDirectory()) {

                    throw new IOException(
                            "В universal.apk отсутствует "
                                    + "classes.dex"
                    );
                }
            }

            log.append(
                    "UNIVERSAL_APK_EXTRACTED: OK\n"
            );

            log.append("APK: ")
                    .append(extractedApk.getAbsolutePath())
                    .append('\n');

            log.append("APK size: ")
                    .append(extractedApk.length())
                    .append(" bytes\n");

            log.append(
                    "\n--- Result ---\n"
            );

            log.append(
                    "Status: APKS_CREATED\n"
            );

            log.append(
                    "Внимание: universal.apk является "
                            + "промежуточным результатом.\n"
            );

            log.append(
                    "Перед установкой необходимо "
                            + "подписать его постоянным "
                            + "ключом CodeBox и проверить "
                            + "результат через ApkVerifier.\n"
            );

            return new Result(
                    true,
                    output,
                    extractedApk,
                    log.toString()
            );

        } catch (Throwable error) {

            log.append(
                    "\n--- Error ---\n"
            );

            log.append(
                    error.getClass().getName()
            ).append(": ")
                    .append(error.getMessage())
                    .append('\n');

            StringWriter writer =
                    new StringWriter();

            PrintWriter printer =
                    new PrintWriter(writer);

            error.printStackTrace(printer);
            printer.flush();

            log.append(writer.toString());

            if (output != null && output.exists()) {
                output.delete();
            }

            if (extractedApk != null
                    && extractedApk.exists()) {

                extractedApk.delete();
            }

            log.append(
                    "\nStatus: FAILED\n"
            );

            return new Result(
                    false,
                    null,
                    null,
                    log.toString()
            );
        }
    }
}
