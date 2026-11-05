package ru.codebox.app;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Preparation and diagnostics for an offline Java compilation stage.
 * Does not execute ECJ or claim to produce class files.
 */
public final class CodeBoxJavaCompiler {

    public static final class Result {
        public final boolean compiled;
        public final String report;
        public final int sourceCount;

        private Result(boolean compiled, String report, int sourceCount) {
            this.compiled = compiled;
            this.report = report;
            this.sourceCount = sourceCount;
        }
    }

    private static final int MAX_SOURCES = 5000;
    private static final int MAX_DEPTH = 40;

    private CodeBoxJavaCompiler() { }

    /**
     * Read-only preparation check. The project must live immediately below
     * the app-private projects directory.
     */
    public static Result inspect(Context context, File projectDirectory) throws IOException {
        if (context == null || projectDirectory == null) {
            throw new IllegalArgumentException("Не указан Context или каталог проекта");
        }

        File projectsRoot = new File(context.getFilesDir(), "projects").getCanonicalFile();
        File project = projectDirectory.getCanonicalFile();
        if (!projectsRoot.equals(project.getParentFile()) || !project.isDirectory()) {
            throw new IOException("Каталог проекта отсутствует или расположен вне projects");
        }

        File toolRoot = new File(context.getFilesDir(), "build-tools").getCanonicalFile();
        File ecj = new File(toolRoot, "ecj.jar").getCanonicalFile();
        File androidJar = new File(toolRoot, "android.jar").getCanonicalFile();
        if (!toolRoot.equals(ecj.getParentFile()) ||
                !toolRoot.equals(androidJar.getParentFile())) {
            throw new IOException("Недопустимое расположение инструментов");
        }

        List<String> sources = new ArrayList<>();
        File sourceRoot = new File(project, "app/src/main/java").getCanonicalFile();
        if (sourceRoot.isDirectory() && isWithin(project, sourceRoot)) {
            collectSources(sourceRoot, sourceRoot, sources, 0);
        }
        Collections.sort(sources);

        StringBuilder report = new StringBuilder();
        report.append("CodeBox 5 — подготовка компиляции Java\n");
        report.append("Проект: ").append(project.getName()).append('\n');
        report.append("Каталог исходников: app/src/main/java\n");
        report.append("Найдено Java-файлов: ").append(sources.size()).append('\n');
        for (String name : sources) {
            report.append("  - ").append(name).append('\n');
        }
        report.append("ECJ: ").append(fileStatus(ecj)).append('\n');
        report.append("Android API: ").append(fileStatus(androidJar)).append('\n');
        if (sources.isEmpty()) {
            report.append("ПРЕДУПРЕЖДЕНИЕ: Java-исходники не найдены.\n");
        }
        report.append("Компиляция НЕ выполнялась. Совместимость ECJ с Android не проверена.\n");
        report.append("Для настоящей компиляции необходим адаптированный запуск ECJ, ");
        report.append("classpath Android API и запись диагностических сообщений.\n");
        report.append("Результат: NOT_COMPILED\n");
        return new Result(false, report.toString(), sources.size());
    }

    private static void collectSources(File root, File directory,
                                       List<String> output, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("Превышена глубина каталогов Java-исходников");
        }
        File[] children = directory.listFiles();
        if (children == null) {
            throw new IOException("Не удалось прочитать каталог: " + directory.getName());
        }
        for (File child : children) {
            File canonical = child.getCanonicalFile();
            if (!isWithin(root, canonical)) {
                throw new IOException("Обнаружен путь вне каталога исходников");
            }
            if (canonical.isDirectory()) {
                collectSources(root, canonical, output, depth + 1);
            } else if (canonical.isFile() && canonical.getName().endsWith(".java")) {
                if (output.size() >= MAX_SOURCES) {
                    throw new IOException("Слишком много Java-исходников (лимит 5000)");
                }
                String relative = root.toURI().relativize(canonical.toURI()).getPath();
                output.add(relative);
            }
        }
    }

    private static boolean isWithin(File root, File candidate) throws IOException {
        String rootPath = root.getCanonicalPath();
        String candidatePath = candidate.getCanonicalPath();
        return candidatePath.equals(rootPath) ||
                candidatePath.startsWith(rootPath + File.separator);
    }

    private static String fileStatus(File file) {
        if (!file.isFile()) return "отсутствует";
        if (file.length() == 0) return "пустой файл";
        return "найден (" + file.length() + " байт; запуск не проверен)";
    }
}
