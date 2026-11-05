package ru.codebox.app;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** In-process ECJ compiler. APK/AAB packaging is not performed here. */
public final class CodeBoxEcjCompiler {
    private static final int MAX_SOURCES = 5000;
    private static final int MAX_DEPTH = 40;

    public static final class Result {
        public final boolean compiled;
        public final int sourceCount;
        public final String report;
        public final File classesDirectory;

        private Result(boolean compiled, int sourceCount, String report, File classesDirectory) {
            this.compiled = compiled;
            this.sourceCount = sourceCount;
            this.report = report;
            this.classesDirectory = classesDirectory;
        }
    }

    private CodeBoxEcjCompiler() { }

    /**
     * Runs ECJ on the calling thread; invoke from a background thread.
     * Requires an Android API android.jar in the private build-tools directory.
     * The ECJ library must already be bundled in the CodeBox APK.
     */
    public static Result compile(Context context, File projectDirectory) throws IOException {
        if (context == null || projectDirectory == null) {
            throw new IllegalArgumentException("Не указан Context или каталог проекта");
        }

        File root = new File(context.getFilesDir(), "projects").getCanonicalFile();
        File project = projectDirectory.getCanonicalFile();
        if (!root.equals(project.getParentFile()) || !project.isDirectory()) {
            throw new IOException("Проект должен находиться непосредственно в каталоге projects");
        }

        File sourceRoot = new File(project, "app/src/main/java").getCanonicalFile();
        if (!inside(project, sourceRoot) || !sourceRoot.isDirectory()) {
            throw new IOException("Каталог app/src/main/java не найден или недопустим");
        }

        List<File> sources = new ArrayList<>();
        collect(sourceRoot, sourceRoot, sources, 0);
        Collections.sort(sources, (a, b) -> a.getPath().compareTo(b.getPath()));

        File tools = new File(context.getFilesDir(), "build-tools").getCanonicalFile();
        File androidJar = new File(tools, "android.jar").getCanonicalFile();
        File output = new File(context.getCacheDir(), "ecj-classes-" + project.getName()).getCanonicalFile();
        if (!inside(context.getCacheDir().getCanonicalFile(), output)) {
            throw new IOException("Недопустимый каталог результата");
        }

        StringBuilder log = new StringBuilder("CodeBox 5 — ECJ\n");
        log.append("Проект: ").append(project.getName()).append('\n');
        log.append("Исходников: ").append(sources.size()).append('\n');
        for (File file : sources) {
            log.append("  ").append(sourceRoot.toURI().relativize(file.toURI()).getPath()).append('\n');
        }

        if (sources.isEmpty()) {
            log.append("ERROR: Нет Java-файлов для компиляции.\n");
            return new Result(false, 0, log.toString(), output);
        }
        if (!tools.equals(androidJar.getParentFile()) || !androidJar.isFile() || androidJar.length() == 0) {
            log.append("ERROR: Не найден непустой build-tools/android.jar.\n");
            log.append("ECJ включён в APK, но Android API нужен отдельно.\n");
            return new Result(false, sources.size(), log.toString(), output);
        }

        // Do not allow stale class files to be mistaken for fresh output.
        deleteTree(output);
        if (!output.mkdirs()) {
            throw new IOException("Не удалось создать каталог для class-файлов");
        }

        List<String> args = new ArrayList<>();
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-source");
        args.add("8");
        args.add("-target");
        args.add("8");
        args.add("-proc:none");
        args.add("-classpath");
        args.add(androidJar.getAbsolutePath());
        args.add("-d");
        args.add(output.getAbsolutePath());
        for (File file : sources) args.add(file.getAbsolutePath());

        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        boolean success = false;
        try {
            Class<?> compiler = Class.forName("org.eclipse.jdt.core.compiler.batch.BatchCompiler");
            Method method = null;
            for (Method candidate : compiler.getMethods()) {
                Class<?>[] parameters = candidate.getParameterTypes();
                if (candidate.getName().equals("compile") && parameters.length == 4
                        && parameters[0] == String[].class
                        && parameters[1] == PrintWriter.class
                        && parameters[2] == PrintWriter.class) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) {
                log.append("ERROR: Метод ECJ BatchCompiler.compile не найден.\n");
            } else {
                Object value = method.invoke(null, args.toArray(new String[0]),
                        new PrintWriter(stdout, true), new PrintWriter(stderr, true), null);
                success = Boolean.TRUE.equals(value);
            }
        } catch (ClassNotFoundException e) {
            log.append("ERROR: ECJ не найден в APK. Проверьте зависимость Gradle.\n");
        } catch (InvocationTargetException e) {
            log.append("ERROR: ECJ завершился исключением: ")
                    .append(e.getCause() == null ? e : e.getCause()).append('\n');
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            log.append("ERROR: Невозможно запустить ECJ: ").append(e).append('\n');
        }
        log.append("ECJ stdout:\n").append(stdout).append('\n');
        log.append("ECJ stderr:\n").append(stderr).append('\n');
        log.append("Результат: ").append(success ? "COMPILED" : "FAILED").append('\n');
        log.append("Каталог классов: ").append(output.getAbsolutePath()).append('\n');
        return new Result(success, sources.size(), log.toString(), output);
    }

    private static void collect(File root, File dir, List<File> out, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Превышена глубина каталогов");
        File[] children = dir.listFiles();
        if (children == null) throw new IOException("Не удалось прочитать " + dir);
        for (File child : children) {
            File canonical = child.getCanonicalFile();
            if (!inside(root, canonical)) throw new IOException("Путь вне каталога Java");
            if (canonical.isDirectory()) collect(root, canonical, out, depth + 1);
            else if (canonical.isFile() && canonical.getName().endsWith(".java")) {
                if (out.size() >= MAX_SOURCES) throw new IOException("Превышен лимит 5000 Java-файлов");
                out.add(canonical);
            }
        }
    }

    private static boolean inside(File root, File child) throws IOException {
        String base = root.getCanonicalPath();
        String path = child.getCanonicalPath();
        return path.equals(base) || path.startsWith(base + File.separator);
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Не удалось прочитать " + file);
            for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IOException("Не удалось очистить " + file);
    }
}
