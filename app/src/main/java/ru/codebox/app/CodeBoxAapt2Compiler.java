package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * CodeBox 5 local AAPT2 resource compiler.
 * APK mode produces a binary resource APK; bundle mode produces a protobuf APK
 * for later conversion into a base module. Run on a background thread.
 */
public final class CodeBoxAapt2Compiler {
    private static final long TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_BYTES = 131072;
    private static final int MAX_RESOURCE_FILES = 10000;
    private static final int MAX_DEPTH = 30;

    private CodeBoxAapt2Compiler() { }

    public static final class Result {
        public final boolean compiled;
        public final File resourceApk;
        public final String report;

        private Result(boolean compiled, File resourceApk, String report) {
            this.compiled = compiled;
            this.resourceApk = resourceApk;
            this.report = report;
        }
    }

    /** Existing APK entry point: unchanged signature and binary resource format. */
    public static Result compile(Context context, File projectDirectory,
                                 File manifestFile, String projectName) {
        return compileInternal(context, projectDirectory, manifestFile, projectName, false);
    }

    /**
     * Produces a protobuf-format resource APK for a future AAB base module.
     * The returned resourceApk is NOT an AAB and must not be renamed to .aab.
     */
    public static Result compileForBundle(Context context, File projectDirectory,
                                          File manifestFile, String projectName) {
        return compileInternal(context, projectDirectory, manifestFile, projectName, true);
    }

    private static Result compileInternal(Context context, File projectDirectory,
                                          File manifestFile, String projectName,
                                          boolean bundleMode) {
        StringBuilder log = new StringBuilder();
        log.append("CodeBox 5 - AAPT2 Resource Compiler\n");
        log.append("Mode: ").append(bundleMode ? "AAB PROTO" : "APK BINARY").append('\n');
        log.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n');

        try {
            if (context == null) throw new IOException("Context отсутствует");
            if (projectName == null ||
                    !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
                throw new IOException("Недопустимое имя проекта");
            }
            if (projectDirectory == null || !projectDirectory.isDirectory()) {
                throw new IOException("Каталог проекта не найден");
            }
            if (manifestFile == null || !manifestFile.isFile() ||
                    manifestFile.length() == 0) {
                throw new IOException("Подготовленный AndroidManifest.xml отсутствует");
            }

            File project = projectDirectory.getCanonicalFile();
            File manifest = manifestFile.getCanonicalFile();
            File resources = new File(project, "app/src/main/res");
            File aapt2 = locateAapt2(context);
            if (aapt2 == null || !aapt2.isFile() || !aapt2.canExecute()) {
                throw new IOException("Исполняемый AAPT2 не найден");
            }
            File androidJar = new File(new File(context.getFilesDir(),
                    "build-tools"), "android.jar");
            if (!androidJar.isFile() || androidJar.length() == 0) {
                throw new IOException("android.jar отсутствует");
            }

            // Separate caches prevent APK and AAB builds from deleting each other's files.
            String cacheName = (bundleMode ? "aapt2-bundle-output-" : "aapt2-output-")
                    + projectName;
            File outputRoot = new File(context.getCacheDir(), cacheName).getCanonicalFile();
            File compiledResources = new File(outputRoot, "compiled");
            File resourceApk = new File(outputRoot,
                    bundleMode ? "resources-proto.apk" : "resources-unsigned.apk");
            File cacheRoot = context.getCacheDir().getCanonicalFile();
            if (!cacheRoot.equals(outputRoot.getParentFile())) {
                throw new IOException("Недопустимый путь каталога сборки");
            }
            deleteTree(outputRoot, 0);
            if (!compiledResources.mkdirs()) {
                throw new IOException("Не удалось создать каталог .flat");
            }

            log.append("AAPT2: ").append(aapt2.getAbsolutePath()).append('\n');
            log.append("android.jar: ").append(androidJar.getAbsolutePath()).append('\n');
            log.append("Manifest: ").append(manifest.getAbsolutePath()).append('\n');
            log.append("Каталог ресурсов: ").append(resources.getAbsolutePath()).append('\n');

            List<File> resourceFiles = new ArrayList<>();
            if (resources.exists()) {
                if (!resources.isDirectory()) {
                    throw new IOException("res существует, но не является каталогом");
                }
                File canonicalRes = resources.getCanonicalFile();
                collectResources(canonicalRes, canonicalRes, resourceFiles, 0);
            }
            log.append("Найдено файлов ресурсов: ")
                    .append(resourceFiles.size()).append('\n');

            if (!resourceFiles.isEmpty()) {
                List<String> compileCommand = Arrays.asList(
                        aapt2.getAbsolutePath(), "compile", "--dir",
                        resources.getAbsolutePath(), "-o",
                        compiledResources.getAbsolutePath());
                log.append("\n--- AAPT2 compile --dir ---\n");
                runCommand(compileCommand, outputRoot, log);
            }

            List<File> flatFiles = new ArrayList<>();
            collectFlatFiles(compiledResources, compiledResources, flatFiles, 0);
            log.append("Скомпилировано .flat файлов: ")
                    .append(flatFiles.size()).append('\n');
            if (!resourceFiles.isEmpty() && flatFiles.isEmpty()) {
                throw new IOException("AAPT2 не создал .flat для непустого res");
            }

            List<String> linkCommand = new ArrayList<>();
            linkCommand.add(aapt2.getAbsolutePath());
            linkCommand.add("link");
            if (bundleMode) linkCommand.add("--proto-format");
            linkCommand.add("-o");
            linkCommand.add(resourceApk.getAbsolutePath());
            linkCommand.add("--manifest");
            linkCommand.add(manifest.getAbsolutePath());
            linkCommand.add("-I");
            linkCommand.add(androidJar.getAbsolutePath());
            linkCommand.add("--min-sdk-version");
            linkCommand.add("26");
            linkCommand.add("--target-sdk-version");
            linkCommand.add("28");
            for (File flat : flatFiles) {
                linkCommand.add("-R");
                linkCommand.add(flat.getAbsolutePath());
            }
            log.append(bundleMode ? "\n--- AAPT2 link --proto-format ---\n"
                    : "\n--- AAPT2 link ---\n");
            runCommand(linkCommand, outputRoot, log);
            if (!resourceApk.isFile() || resourceApk.length() == 0) {
                throw new IOException("AAPT2 link не создал ресурсный пакет");
            }

            if (bundleMode) {
                // An AAPT2 protobuf APK is an intermediate artifact, not a bundle.
                try (ZipFile zip = new ZipFile(resourceApk)) {
                    ZipEntry manifestEntry = zip.getEntry("AndroidManifest.xml");
                    ZipEntry resourcesEntry = zip.getEntry("resources.pb");
                    if (manifestEntry == null || manifestEntry.isDirectory()
                            || manifestEntry.getSize() == 0) {
                        throw new IOException("PROTO пакет не содержит AndroidManifest.xml");
                    }
                    if (resourcesEntry == null || resourcesEntry.isDirectory()
                            || resourcesEntry.getSize() == 0) {
                        throw new IOException("PROTO пакет не содержит resources.pb");
                    }
                    log.append("PROTO_MANIFEST: OK\n");
                    log.append("RESOURCES_PB: OK\n");
                }
            }

            log.append(bundleMode ? "\nРезультат: PROTO_RESOURCE_APK_CREATED\n"
                    : "\nРезультат: RESOURCE_APK_CREATED\n")
                    .append("Файл: ").append(resourceApk.getAbsolutePath()).append('\n')
                    .append("Размер: ").append(resourceApk.length()).append(" байт\n");
            if (bundleMode) {
                log.append("Это промежуточный protobuf APK, не готовый AAB.\n");
            } else {
                log.append("APK пока не содержит classes.dex и не подписан.\n");
            }
            return new Result(true, resourceApk, log.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.append("\nРезультат: FAILED\nОшибка: выполнение AAPT2 прервано\n");
            return new Result(false, null, log.toString());
        } catch (Exception e) {
            log.append("\nРезультат: FAILED\nОшибка: ")
                    .append(e.getClass().getName()).append(": ")
                    .append(e.getMessage()).append('\n');
            return new Result(false, null, log.toString());
        }
    }

    private static File locateAapt2(Context context) {
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        if (nativeDir != null) {
            File nativeBinary = new File(nativeDir, "libaapt2.so");
            if (nativeBinary.isFile()) return nativeBinary;
        }
        File legacy = new File(new File(context.getFilesDir(), "build-tools"), "aapt2");
        return legacy.isFile() ? legacy : null;
    }

    private static void collectResources(File root, File directory,
                                         List<File> result, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Слишком глубокий каталог ресурсов");
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Не удалось прочитать " + directory);
        Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
        for (File child : children) {
            File canonical = child.getCanonicalFile();
            if (!canonical.toPath().startsWith(root.toPath())) {
                throw new IOException("Ресурс выходит за пределы res: " + child);
            }
            if (canonical.isDirectory()) {
                collectResources(root, canonical, result, depth + 1);
            } else if (canonical.isFile()) {
                result.add(canonical);
                if (result.size() > MAX_RESOURCE_FILES) {
                    throw new IOException("Слишком много файлов ресурсов");
                }
            }
        }
    }

    private static void collectFlatFiles(File root, File directory,
                                         List<File> result, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Слишком глубокий каталог .flat");
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Не удалось прочитать каталог .flat");
        Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
        for (File child : children) {
            File canonical = child.getCanonicalFile();
            if (!canonical.toPath().startsWith(root.toPath())) {
                throw new IOException("Файл .flat выходит за пределы каталога");
            }
            if (canonical.isDirectory()) {
                collectFlatFiles(root, canonical, result, depth + 1);
            } else if (canonical.isFile() && canonical.getName().endsWith(".flat")) {
                result.add(canonical);
                if (result.size() > MAX_RESOURCE_FILES) {
                    throw new IOException("Слишком много .flat файлов");
                }
            }
        }
    }

    private static void runCommand(List<String> command, File workingDirectory,
                                   StringBuilder log) throws IOException, InterruptedException {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory);
            builder.redirectErrorStream(true);
            process = builder.start();
            final Process running = process;
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream input = running.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    int total = 0;
                    while ((count = input.read(buffer)) != -1) {
                        int remaining = MAX_OUTPUT_BYTES - total;
                        if (remaining > 0) {
                            int written = Math.min(count, remaining);
                            synchronized (output) { output.write(buffer, 0, written); }
                            total += written;
                        }
                    }
                } catch (IOException ignored) {
                    // Stream can close when AAPT2 exits.
                }
            }, "CodeBox-AAPT2-Reader");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                reader.join(1000);
                appendOutput(output, log);
                throw new IOException("AAPT2 TIMEOUT: " + TIMEOUT_SECONDS + " секунд");
            }
            reader.join(1000);
            appendOutput(output, log);
            int exit = process.exitValue();
            log.append("Exit code: ").append(exit).append('\n');
            if (exit != 0) throw new IOException("AAPT2 завершился с кодом " + exit);
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static void appendOutput(ByteArrayOutputStream output, StringBuilder log) {
        String value;
        synchronized (output) {
            value = new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
        if (!value.isEmpty()) {
            log.append("Вывод:\n").append(value);
            if (!value.endsWith("\n")) log.append('\n');
        }
    }

    private static void deleteTree(File file, int depth) throws IOException {
        if (depth > 40) throw new IOException("Превышена глубина удаления");
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Не удалось прочитать каталог удаления");
            for (File child : children) deleteTree(child, depth + 1);
        }
        if (!file.delete()) throw new IOException("Не удалось удалить: " + file);
    }
}
