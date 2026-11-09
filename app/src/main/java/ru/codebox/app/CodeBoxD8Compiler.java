package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * CodeBox 5 - Local D8 Compiler.
 *
 * Converts ECJ class files and local JAR/AAR bytecode into classes.dex.
 * Reads dependencies from app/libs. Maven resolution is handled separately.
 * Must be called from a background thread.
 */
public final class CodeBoxD8Compiler {

    private static final int MAX_DEPTH = 40;
    private static final int MAX_CLASSES = 100000;
    private static final int MAX_LIBRARIES = 500;
    private static final long MAX_ARCHIVE_ENTRY = 100L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_TOTAL = 500L * 1024L * 1024L;

    private CodeBoxD8Compiler() {
    }

    public static final class Result {
        public final boolean compiled;
        public final File dexDirectory;
        public final File dexFile;
        public final String report;

        private Result(boolean compiled, File dexDirectory,
                       File dexFile, String report) {
            this.compiled = compiled;
            this.dexDirectory = dexDirectory;
            this.dexFile = dexFile;
            this.report = report;
        }
    }

    public static Result compile(Context context, File classesDirectory,
                                 String projectName) {
        StringBuilder report = new StringBuilder();
        report.append("CodeBox 5 - D8 Compiler\n");
        report.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n');

        File output = null;
        File dexFile = null;

        try {
            if (context == null) throw new IOException("Context отсутствует");
            if (projectName == null ||
                    !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
                throw new IOException("Недопустимое имя проекта");
            }
            if (classesDirectory == null || !classesDirectory.isDirectory()) {
                throw new IOException("Каталог CLASS отсутствует");
            }

            File cache = context.getCacheDir().getCanonicalFile();
            File classesRoot = classesDirectory.getCanonicalFile();
            if (!inside(cache, classesRoot)) {
                throw new IOException("Каталог CLASS вне cache");
            }

            File expectedClasses = new File(
                    cache, "ecj-classes-" + projectName).getCanonicalFile();
            if (!expectedClasses.equals(classesRoot)) {
                throw new IOException("Неожиданный каталог CLASS");
            }

            output = new File(cache, "d8-output-" + projectName).getCanonicalFile();
            if (!cache.equals(output.getParentFile())) {
                throw new IOException("Недопустимый каталог D8");
            }

            report.append("CLASS: ").append(classesRoot.getAbsolutePath()).append('\n');
            report.append("DEX output: ").append(output.getAbsolutePath()).append('\n');

            List<File> classFiles = new ArrayList<>();
            collectClasses(classesRoot, classesRoot, classFiles, 0);
            report.append("Найдено CLASS файлов: ").append(classFiles.size()).append('\n');
            if (classFiles.isEmpty()) throw new IOException("Нет файлов .class для D8");

            File projectRoot = new File(
                    new File(context.getFilesDir(), "projects"), projectName)
                    .getCanonicalFile();
            File appDirectory = new File(projectRoot, "app").getCanonicalFile();
            if (!inside(projectRoot, appDirectory) || !appDirectory.isDirectory()) {
                throw new IOException("Каталог app проекта не найден");
            }

            File libsDirectory = new File(appDirectory, "libs").getCanonicalFile();
            List<File> programJars = new ArrayList<>();
            List<File> aarFiles = new ArrayList<>();
            if (libsDirectory.exists()) {
                if (!inside(appDirectory, libsDirectory) || !libsDirectory.isDirectory()) {
                    throw new IOException("Недопустимый каталог app/libs");
                }
                collectLibraries(libsDirectory, libsDirectory, programJars, aarFiles, 0);
            }

            File aarExtractRoot = new File(
                    cache, "d8-aar-libs-" + projectName).getCanonicalFile();
            if (aarExtractRoot.exists()) deleteTree(aarExtractRoot, aarExtractRoot, 0);
            if (!aarExtractRoot.mkdirs()) {
                throw new IOException("Не удалось создать каталог извлечения AAR");
            }

            long extractedBytes = 0;
            int aarIndex = 0;
            for (File aar : aarFiles) {
                extractedBytes += extractAarJars(
                        aar, aarExtractRoot, aarIndex++, extractedBytes);
            }

            File[] extracted = aarExtractRoot.listFiles();
            if (extracted == null) throw new IOException("Не удалось прочитать извлечённые AAR");
            for (File file : extracted) {
                if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
                        && file.length() > 0) {
                    programJars.add(file.getCanonicalFile());
                }
            }

            if (programJars.size() > MAX_LIBRARIES) {
                throw new IOException("Слишком много JAR/AAR-библиотек (лимит "
                        + MAX_LIBRARIES + ")");
            }
            for (File jar : programJars) {
                if (!jar.isFile() || jar.length() == 0) {
                    throw new IOException("Пустая или недоступная библиотека: " + jar.getName());
                }
            }

            report.append("Каталог библиотек: ").append(libsDirectory.getAbsolutePath()).append('\n');
            report.append("Локальных JAR/AAR: ")
                    .append(programJars.size()).append(" JAR для D8, ")
                    .append(aarFiles.size()).append(" AAR обнаружено\n");
            for (File jar : programJars) {
                report.append("  PROGRAM: ").append(jar.getName())
                        .append(" (").append(jar.length()).append(" байт)\n");
            }

            if (output.exists()) deleteTree(output, output, 0);
            if (!output.mkdirs()) throw new IOException("Не удалось создать каталог D8");

            File androidJar = new File(
                    context.getFilesDir(), "build-tools/android.jar").getCanonicalFile();
            if (!androidJar.isFile() || androidJar.length() == 0) {
                throw new IOException("android.jar отсутствует");
            }

            report.append("Android library: ").append(androidJar.getAbsolutePath()).append('\n');
            report.append("Min API: 26\nOutput mode: DexIndexed\n");
            report.append("\n--- Запуск D8 ---\n");

            D8Command.Builder builder = D8Command.builder();
            List<java.nio.file.Path> programPaths = new ArrayList<>();
            for (File classFile : classFiles) programPaths.add(classFile.toPath());
            for (File jar : programJars) programPaths.add(jar.toPath());

            builder.addProgramFiles(programPaths.toArray(new java.nio.file.Path[0]));
            builder.addLibraryFiles(androidJar.toPath());
            builder.setMinApiLevel(26);
            builder.setOutput(output.toPath(), OutputMode.DexIndexed);
            D8.run(builder.build());

            dexFile = new File(output, "classes.dex").getCanonicalFile();
            if (!output.equals(dexFile.getParentFile())) {
                throw new IOException("Недопустимый путь classes.dex");
            }
            if (!dexFile.isFile() || dexFile.length() == 0) {
                throw new IOException("D8 не создал classes.dex");
            }

            report.append("Результат: DEX_CREATED\n");
            report.append("classes.dex: ").append(dexFile.getAbsolutePath()).append('\n');
            report.append("Размер: ").append(dexFile.length()).append(" байт\n");
            report.append("Внешние библиотеки переданы D8 как program inputs.\n");

            return new Result(true, output, dexFile, report.toString());

        } catch (Throwable error) {
            report.append("\nРезультат: FAILED\n");
            report.append("Ошибка: ").append(error.getClass().getName()).append('\n');
            if (error.getMessage() != null) {
                report.append("Сообщение: ").append(error.getMessage()).append('\n');
            }
            Throwable cause = error.getCause();
            if (cause != null) {
                report.append("Причина: ").append(cause.getClass().getName())
                        .append(": ").append(cause.getMessage()).append('\n');
            }
            return new Result(false, output, dexFile, report.toString());
        }
    }

    private static void collectLibraries(File root, File directory,
                                         List<File> jars, List<File> aars,
                                         int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Превышена глубина app/libs");
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Не удалось прочитать app/libs");
        for (File child : children) {
            File canonical = child.getCanonicalFile();
            if (!inside(root, canonical)) throw new IOException("Библиотека вне app/libs");
            if (canonical.isDirectory()) {
                collectLibraries(root, canonical, jars, aars, depth + 1);
            } else if (canonical.isFile()) {
                String name = canonical.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jar")) jars.add(canonical);
                else if (name.endsWith(".aar")) aars.add(canonical);
                if (jars.size() + aars.size() > MAX_LIBRARIES) {
                    throw new IOException("Превышен лимит локальных библиотек");
                }
            }
        }
    }

    private static long extractAarJars(File aar, File destination,
                                       int aarIndex, long totalBefore)
            throws IOException {
        long extracted = 0;
        int jarIndex = 0;
        byte[] buffer = new byte[32768];

        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(aar)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                boolean wanted = "classes.jar".equals(name)
                        || (name.startsWith("libs/") && name.toLowerCase(Locale.ROOT).endsWith(".jar"));
                if (!entry.isDirectory() && wanted) {
                    File target = new File(destination,
                            "aar" + aarIndex + "_jar" + (jarIndex++) + ".jar")
                            .getCanonicalFile();
                    if (!inside(destination, target)) {
                        throw new IOException("Недопустимый путь JAR внутри AAR");
                    }
                    long written = 0;
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            new FileOutputStream(target))) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            written += count;
                            if (written > MAX_ARCHIVE_ENTRY
                                    || totalBefore + extracted + written > MAX_EXTRACTED_TOTAL) {
                                throw new IOException("AAR превышает лимит распаковки");
                            }
                            out.write(buffer, 0, count);
                        }
                    }
                    if (written == 0) {
                        target.delete();
                    } else {
                        extracted += written;
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IOException("Не удалось прочитать AAR " + aar.getName()
                    + ": " + e.getMessage(), e);
        }
        return extracted;
    }

    private static void collectClasses(File root, File directory,
                                       List<File> files, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Превышена глубина CLASS");
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Не удалось прочитать CLASS каталог");
        for (File child : children) {
            File canonical = child.getCanonicalFile();
            if (!inside(root, canonical)) throw new IOException("CLASS файл вне каталога");
            if (canonical.isDirectory()) {
                collectClasses(root, canonical, files, depth + 1);
            } else if (canonical.isFile() && canonical.getName().endsWith(".class")) {
                files.add(canonical);
                if (files.size() > MAX_CLASSES) throw new IOException("Слишком много CLASS файлов");
            }
        }
    }

    private static boolean inside(File root, File child) throws IOException {
        String base = root.getCanonicalPath();
        String path = child.getCanonicalPath();
        return path.equals(base) || path.startsWith(base + File.separator);
    }

    private static void deleteTree(File root, File target, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Превышена глубина удаления");
        File canonical = target.getCanonicalFile();
        if (!canonical.equals(root) && !inside(root, canonical)) {
            throw new IOException("Удаление вне разрешённого каталога");
        }
        if (canonical.isDirectory()) {
            File[] children = canonical.listFiles();
            if (children == null) throw new IOException("Не удалось прочитать каталог удаления");
            for (File child : children) deleteTree(root, child, depth + 1);
        }
        if (!canonical.delete()) throw new IOException("Не удалось удалить: "
                + canonical.getAbsolutePath());
    }
}
