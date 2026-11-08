package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import com.android.tools.build.bundletool.commands.BuildApksCommand;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.androidtools.Aapt2Command;

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
 * Stage 4:
 * AAB -> APKS -> universal.apk ->
 * Android Keystore APK signing -> verification.
 *
 * The original signed AAB is never modified.
 *
 * Failure here must not fail the existing AAB build.
 */
public final class CodeBoxApksBuilder {

    private static final long MAX_AAB_BYTES =
            512L * 1024L * 1024L;

    private static final long MAX_APKS_BYTES =
            1024L * 1024L * 1024L;

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
        log.append("Stage: 4 - SIGNED UNIVERSAL APK\n");

        File apksFile = null;
        File extractedApk = null;

        try {

            validateInputs(
                    context,
                    aabFile,
                    projectName
            );

            log.append("\n--- Input ---\n");
            log.append("Project: ")
                    .append(projectName)
                    .append('\n');

            log.append("AAB: ")
                    .append(aabFile.getAbsolutePath())
                    .append('\n');

            log.append("AAB size: ")
                    .append(aabFile.length())
                    .append('\n');

            log.append("Android API: ")
                    .append(Build.VERSION.SDK_INT)
                    .append('\n');

            File aapt2 = findAapt2(context);

            if (aapt2 == null) {
                throw new IOException(
                        "Исполняемый AAPT2 не найден"
                );
            }

            log.append("\n--- AAPT2 ---\n");
            log.append("Path: ")
                    .append(aapt2.getAbsolutePath())
                    .append('\n');

            Aapt2Command aapt2Command =
                    Aapt2Command.createFromExecutablePath(
                            aapt2.toPath()
                    );

            log.append("AAPT2_COMMAND: CREATED\n");

            File outputRoot = new File(
                    context.getCacheDir(),
                    "apks-output-" + projectName
            );

            if (!outputRoot.isDirectory()
                    && !outputRoot.mkdirs()) {
                throw new IOException(
                        "Не удалось создать каталог APKS"
                );
            }

            File workDirectory = new File(
                    outputRoot,
                    "run-" + System.nanoTime()
            );

            if (!workDirectory.mkdirs()) {
                throw new IOException(
                        "Не удалось создать рабочий каталог"
                );
            }

            apksFile = new File(
                    workDirectory,
                    projectName + ".apks"
            );

            extractedApk = new File(
                    workDirectory,
                    "universal.apk"
            );

            log.append("\n--- Build APKS ---\n");
            log.append("Mode: UNIVERSAL\n");

            BuildApksCommand command =
                    BuildApksCommand.builder()
                            .setBundlePath(
                                    aabFile.toPath()
                            )
                            .setOutputFile(
                                    apksFile.toPath()
                            )
                            .setApkBuildMode(
                                    ApkBuildMode.UNIVERSAL
                            )
                            .setAapt2Command(
                                    aapt2Command
                            )
                            .setOverwriteOutput(true)
                            .build();

            log.append("COMMAND_CREATED: OK\n");

            command.execute();

            log.append("COMMAND_EXECUTED: OK\n");

            if (!apksFile.isFile()
                    || apksFile.length() == 0) {
                throw new IOException(
                        "Bundletool не создал APKS"
                );
            }

            if (apksFile.length() > MAX_APKS_BYTES) {
                throw new IOException(
                        "APKS превышает лимит размера"
                );
            }

            log.append("APKS_CREATED: OK\n");
            log.append("APKS_SIZE: ")
                    .append(apksFile.length())
                    .append('\n');

            extractUniversalApk(
                    apksFile,
                    extractedApk,
                    log
            );

            log.append("\n--- APK Signing ---\n");

            log.append(
                    "Signing engine: CodeBoxApkSigner\n"
            );

            log.append(
                    "Signing key: persistent Android Keystore\n"
            );

            CodeBoxApkSigner.Result signed =
                    CodeBoxApkSigner.sign(
                            context,
                            extractedApk,
                            projectName
                    );

            if (signed.report != null) {
                log.append(signed.report).append('\n');
            }

            if (!signed.signed
                    || signed.signedApk == null
                    || !signed.signedApk.isFile()
                    || signed.signedApk.length() == 0) {

                throw new IOException(
                        "Не удалось подписать или проверить "
                                + "universal.apk"
                );
            }

            log.append("\n--- Final APK ---\n");

            log.append("SIGNED_UNIVERSAL_APK: OK\n");
            log.append("APK_SIGNATURE_VERIFIED: OK\n");

            log.append("APK: ")
                    .append(
                            signed.signedApk.getAbsolutePath()
                    )
                    .append('\n');

            log.append("APK_SIZE: ")
                    .append(
                            signed.signedApk.length()
                    )
                    .append('\n');

            log.append(
                    "APK_SIGNATURE_SCHEME: V2\n"
            );

            log.append(
                    "APK_INSTALLATION: NOT_TESTED\n"
            );

            log.append(
                    "APKS_BUILT: true\n"
            );

            log.append(
                    "Status: SUCCEEDED\n"
            );

            return new Result(
                    true,
                    apksFile,
                    signed.signedApk,
                    log.toString()
            );

        } catch (Throwable error) {

            log.append("\n--- Failure ---\n");

            appendError(log, error);

            log.append(
                    "\nSIGNED_UNIVERSAL_APK: FAILED\n"
            );

            log.append(
                    "APKS_BUILT: false\n"
            );

            log.append(
                    "Status: FAILED\n"
            );

            return new Result(
                    false,
                    apksFile != null && apksFile.isFile()
                            ? apksFile : null,
                    null,
                    log.toString()
            );
        }
    }

    private static void validateInputs(
            Context context,
            File aabFile,
            String projectName
    ) throws IOException {

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
                    "AAB превышает ограничение размера"
            );
        }
    }

    private static File findAapt2(
            Context context
    ) {

        File nativeAapt2 = new File(
                context.getApplicationInfo()
                        .nativeLibraryDir,
                "libaapt2.so"
        );

        if (nativeAapt2.isFile()
                && nativeAapt2.canExecute()) {
            return nativeAapt2;
        }

        File externalAapt2 = new File(
                context.getFilesDir(),
                "build-tools/aapt2"
        );

        if (externalAapt2.isFile()
                && externalAapt2.canExecute()) {
            return externalAapt2;
        }

        return null;
    }

    private static void extractUniversalApk(
            File apksFile,
            File destination,
            StringBuilder log
    ) throws IOException {

        log.append(
                "\n--- Extract universal APK ---\n"
        );

        try (ZipFile zip = new ZipFile(apksFile)) {

            ZipEntry selected = null;

            Enumeration<? extends ZipEntry> entries =
                    zip.entries();

            while (entries.hasMoreElements()) {

                ZipEntry entry =
                        entries.nextElement();

                String name = entry.getName();

                log.append("ENTRY: ")
                        .append(name)
                        .append('\n');

                if (!entry.isDirectory()
                        && (
                        name.equals("universal.apk")
                                || name.equals(
                                "splits/universal.apk"
                        )
                )) {
                    selected = entry;
                }
            }

            if (selected == null) {
                throw new IOException(
                        "universal.apk не найден внутри APKS"
                );
            }

            if (selected.getSize() > MAX_APK_BYTES) {
                throw new IOException(
                        "universal.apk слишком большой"
                );
            }

            long total = 0;

            try (
                    InputStream input =
                            zip.getInputStream(selected);

                    FileOutputStream output =
                            new FileOutputStream(destination)
            ) {

                byte[] buffer = new byte[32768];

                int count;

                while ((count = input.read(buffer)) != -1) {

                    total += count;

                    if (total > MAX_APK_BYTES) {
                        throw new IOException(
                                "Превышен лимит APK"
                        );
                    }

                    output.write(
                            buffer,
                            0,
                            count
                    );
                }

                output.flush();
                output.getFD().sync();
            }

            if (total == 0) {
                throw new IOException(
                        "Извлечённый APK пуст"
                );
            }

            if (selected.getSize() >= 0
                    && total != selected.getSize()) {
                throw new IOException(
                        "Размер извлечённого APK не совпадает"
                );
            }

            log.append("EXTRACTED_BYTES: ")
                    .append(total)
                    .append('\n');

            log.append(
                    "UNIVERSAL_APK_EXTRACTED: OK\n"
            );
        }
    }

    private static void appendError(
            StringBuilder log,
            Throwable error
    ) {

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

        log.append("\n--- Stack trace ---\n");
        log.append(writer.toString());
    }
}
