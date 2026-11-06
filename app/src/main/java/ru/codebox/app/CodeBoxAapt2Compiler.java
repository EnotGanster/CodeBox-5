package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CodeBox 5 - AAPT2 Resource Compiler.
 *
 * Pipeline:
 * 1. Locate native AAPT2 executable.
 * 2. Locate Android SDK android.jar.
 * 3. Compile resources with AAPT2.
 * 4. Link resources and AndroidManifest.xml.
 * 5. Create an unsigned resource APK.
 *
 * Does not add classes.dex or sign the APK.
 *
 * Must run on a background thread.
 */
public final class CodeBoxAapt2Compiler {

    private static final long TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_BYTES = 65536;

    private CodeBoxAapt2Compiler() {
    }

    public static final class Result {

        public final boolean compiled;
        public final File resourceApk;
        public final String report;

        private Result(
                boolean compiled,
                File resourceApk,
                String report
        ) {
            this.compiled = compiled;
            this.resourceApk = resourceApk;
            this.report = report;
        }
    }

    public static Result compile(
            Context context,
            File projectDirectory,
            File manifestFile,
            String projectName
    ) {

        StringBuilder log = new StringBuilder();

        log.append("CodeBox 5 - AAPT2 Compiler\n");
        log.append("Android API: ")
                .append(Build.VERSION.SDK_INT)
                .append('\n');

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

            if (projectDirectory == null
                    || !projectDirectory.isDirectory()) {

                throw new IOException(
                        "Каталог проекта не найден"
                );
            }

            if (manifestFile == null
                    || !manifestFile.isFile()
                    || manifestFile.length() == 0) {

                throw new IOException(
                        "Подготовленный AndroidManifest.xml "
                        + "не найден или пуст"
                );
            }

            File aapt2 = locateAapt2(context);

            if (aapt2 == null) {

                throw new IOException(
                        "AAPT2 не найден в nativeLibraryDir "
                        + "или files/build-tools"
                );
            }

            if (!aapt2.canExecute()) {

                throw new IOException(
                        "Нет прав на запуск AAPT2"
                );
            }

            File androidJar = new File(
                    new File(
                            context.getFilesDir(),
                            "build-tools"
                    ),
                    "android.jar"
            );

            if (!androidJar.isFile()
                    || androidJar.length() == 0) {

                throw new IOException(
                        "android.jar отсутствует"
                );
            }

            log.append("AAPT2: ")
                    .append(aapt2.getAbsolutePath())
                    .append('\n');

            log.append("android.jar: ")
                    .append(androidJar.getAbsolutePath())
                    .append('\n');

            log.append("Manifest: ")
                    .append(manifestFile.getAbsolutePath())
                    .append('\n');

            File outputRoot = new File(
                    context.getCacheDir(),
                    "aapt2-output-" + projectName
            );

            File compiledResources = new File(
                    outputRoot,
                    "compiled"
            );

            File resourceApk = new File(
                    outputRoot,
                    "resources-unsigned.apk"
            );

            // Remove stale output from previous builds.
            deleteTree(outputRoot, 0);

            if (!compiledResources.mkdirs()) {

                throw new IOException(
                        "Не удалось создать каталог "
                        + "скомпилированных ресурсов"
                );
            }

            File resources = new File(
                    projectDirectory,
                    "app/src/main/res"
            );

            List<File> resourceFiles =
                    new ArrayList<>();

            if (resources.isDirectory()) {

                collectResources(
                        resources,
                        resourceFiles,
                        0
                );
            }

            log.append("Каталог ресурсов: ")
                    .append(resources.getAbsolutePath())
                    .append('\n');

            log.append("Найдено файлов ресурсов: ")
                    .append(resourceFiles.size())
                    .append('\n');

            // Compile each resource separately.
            for (File resource : resourceFiles) {

                List<String> command = Arrays.asList(
                        aapt2.getAbsolutePath(),
                        "compile",
                        "-o",
                        compiledResources.getAbsolutePath(),
                        resource.getAbsolutePath()
                );

                log.append("\n--- AAPT2 compile ---\n");
                log.append(resource.getAbsolutePath())
                        .append('\n');

                runCommand(
                        command,
                        outputRoot,
                        log
                );
            }

            // Collect .flat files produced by AAPT2.
            List<File> flatFiles = new ArrayList<>();

            collectFlatFiles(
                    compiledResources,
                    flatFiles,
                    0
            );

            log.append("\nСкомпилировано .flat файлов: ")
                    .append(flatFiles.size())
                    .append('\n');

            // Link manifest and compiled resources.
            List<String> linkCommand =
                    new ArrayList<>();

            linkCommand.add(
                    aapt2.getAbsolutePath()
            );

            linkCommand.add("link");

            linkCommand.add("-o");
            linkCommand.add(
                    resourceApk.getAbsolutePath()
            );

            linkCommand.add("--manifest");
            linkCommand.add(
                    manifestFile.getAbsolutePath()
            );

            linkCommand.add("-I");
            linkCommand.add(
                    androidJar.getAbsolutePath()
            );

            linkCommand.add("--min-sdk-version");
            linkCommand.add("26");

            linkCommand.add("--target-sdk-version");
            linkCommand.add("28");

            for (File flat : flatFiles) {

                linkCommand.add("-R");
                linkCommand.add(
                        flat.getAbsolutePath()
                );
            }

            log.append("\n--- AAPT2 link ---\n");

            runCommand(
                    linkCommand,
                    outputRoot,
                    log
            );

            if (!resourceApk.isFile()
                    || resourceApk.length() == 0) {

                throw new IOException(
                        "AAPT2 link завершился, "
                        + "но APK не создан"
                );
            }

            log.append("\nРезультат: RESOURCE_APK_CREATED\n");

            log.append("Файл: ")
                    .append(resourceApk.getAbsolutePath())
                    .append('\n');

            log.append("Размер: ")
                    .append(resourceApk.length())
                    .append(" байт\n");

            log.append(
                    "APK пока не содержит classes.dex "
                    + "и не подписан.\n"
            );

            return new Result(
                    true,
                    resourceApk,
                    log.toString()
            );

        } catch (Exception e) {

            log.append("\nРезультат: FAILED\n");

            log.append("Ошибка: ")
                    .append(e.getClass().getName())
                    .append(": ")
                    .append(e.getMessage())
                    .append('\n');

            return new Result(
                    false,
                    null,
                    log.toString()
            );
        }
    }

    private static File locateAapt2(
            Context context
    ) {

        String nativeDir =
                context.getApplicationInfo()
                        .nativeLibraryDir;

        if (nativeDir != null) {

            File nativeBinary = new File(
                    nativeDir,
                    "libaapt2.so"
            );

            if (nativeBinary.isFile()) {
                return nativeBinary;
            }
        }

        File legacyBinary = new File(
                new File(
                        context.getFilesDir(),
                        "build-tools"
                ),
                "aapt2"
        );

        if (legacyBinary.isFile()) {
            return legacyBinary;
        }

        return null;
    }

    private static void collectResources(
            File directory,
            List<File> result,
            int depth
    ) throws IOException {

        if (depth > 30) {
            throw new IOException(
                    "Превышена глубина каталога ресурсов"
            );
        }

        File[] children = directory.listFiles();

        if (children == null) {
            throw new IOException(
                    "Не удалось прочитать каталог: "
                    + directory
            );
        }

        Arrays.sort(
                children,
                (a, b) -> a.getName()
                        .compareTo(b.getName())
        );

        for (File child : children) {

            if (child.isDirectory()) {

                collectResources(
                        child,
                        result,
                        depth + 1
                );

            } else if (child.isFile()) {

                result.add(child);
            }
        }
    }

    private static void collectFlatFiles(
            File directory,
            List<File> result,
            int depth
    ) throws IOException {

        if (depth > 30) {
            throw new IOException(
                    "Превышена глубина каталога .flat"
            );
        }

        File[] children = directory.listFiles();

        if (children == null) {
            throw new IOException(
                    "Не удалось прочитать каталог .flat"
            );
        }

        Arrays.sort(
                children,
                (a, b) -> a.getName()
                        .compareTo(b.getName())
        );

        for (File child : children) {

            if (child.isDirectory()) {

                collectFlatFiles(
                        child,
                        result,
                        depth + 1
                );

            } else if (child.isFile()
                    && child.getName()
                            .endsWith(".flat")) {

                result.add(child);
            }
        }
    }

    private static void runCommand(
            List<String> command,
            File workingDirectory,
            StringBuilder log
    ) throws IOException, InterruptedException {

        Process process = null;

        try {

            ProcessBuilder builder =
                    new ProcessBuilder(command);

            builder.directory(
                    workingDirectory
            );

            builder.redirectErrorStream(true);

            process = builder.start();

            final Process runningProcess = process;

            final ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            Thread reader = new Thread(() -> {

                try (InputStream input =
                             runningProcess.getInputStream()) {

                    byte[] buffer = new byte[2048];
                    int count;
                    int total = 0;

                    while ((count = input.read(buffer)) != -1) {

                        int remaining =
                                MAX_OUTPUT_BYTES - total;

                        if (remaining > 0) {

                            int writeCount =
                                    Math.min(count, remaining);

                            synchronized (output) {
                                output.write(
                                        buffer,
                                        0,
                                        writeCount
                                );
                            }

                            total += writeCount;
                        }
                    }

                } catch (IOException ignored) {
                    // Process stream can close on termination.
                }

            }, "CodeBox-AAPT2-Reader");

            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            if (!finished) {

                process.destroyForcibly();
                process.waitFor(
                        2,
                        TimeUnit.SECONDS
                );

                reader.join(1000);

                appendOutput(output, log);

                throw new IOException(
                        "AAPT2 TIMEOUT: "
                        + TIMEOUT_SECONDS
                        + " секунд"
                );
            }

            reader.join(1000);

            appendOutput(output, log);

            int exitCode = process.exitValue();

            log.append("Exit code: ")
                    .append(exitCode)
                    .append('\n');

            if (exitCode != 0) {

                throw new IOException(
                        "AAPT2 завершился с кодом "
                        + exitCode
                );
            }

        } finally {

            if (process != null) {
                process.destroy();
            }
        }
    }

    private static void appendOutput(
            ByteArrayOutputStream output,
            StringBuilder log
    ) throws IOException {

        String text;

        synchronized (output) {
            text = new String(
                    output.toByteArray(),
                    StandardCharsets.UTF_8
            );
        }

        if (!text.isEmpty()) {

            log.append("Вывод:\n");
            log.append(text);

            if (!text.endsWith("\n")) {
                log.append('\n');
            }
        }
    }

    private static void deleteTree(
            File file,
            int depth
    ) throws IOException {

        if (depth > 40) {
            throw new IOException(
                    "Превышена глубина удаления"
            );
        }

        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {

            File[] children = file.listFiles();

            if (children == null) {
                throw new IOException(
                        "Не удалось прочитать каталог удаления"
                );
            }

            for (File child : children) {
                deleteTree(child, depth + 1);
            }
        }

        if (!file.delete()) {
            throw new IOException(
                    "Не удалось удалить: "
                    + file.getAbsolutePath()
            );
        }
    }
}
