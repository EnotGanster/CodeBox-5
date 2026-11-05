package ru.codebox.app;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Проверка структуры проекта перед будущей сборкой APK. */
public final class CodeBoxProjectBuildValidator {

    private CodeBoxProjectBuildValidator() { }

    public static String validate(File projectDirectory) throws IOException {
        File root = projectDirectory.getCanonicalFile();
        if (!root.isDirectory()) {
            throw new IOException("Папка проекта не найдена");
        }

        List<String> results = new ArrayList<>();
        int errors = 0;
        int warnings = 0;

        File app = new File(root, "app");
        File manifest = new File(app, "src/main/AndroidManifest.xml");
        File javaDir = new File(app, "src/main/java");
        File resDir = new File(app, "src/main/res");
        File appGradle = new File(app, "build.gradle");
        File appGradleKts = new File(app, "build.gradle.kts");
        File settingsGradle = new File(root, "settings.gradle");
        File settingsGradleKts = new File(root, "settings.gradle.kts");

        if (manifest.isFile()) {
            results.add("✓ AndroidManifest.xml найден");
        } else {
            results.add("✗ Нет app/src/main/AndroidManifest.xml");
            errors++;
        }

        if (javaDir.isDirectory() && containsJava(javaDir, 0)) {
            results.add("✓ Исходные Java-файлы найдены");
        } else {
            results.add("✗ Не найдены Java-файлы в app/src/main/java");
            errors++;
        }

        if (appGradle.isFile() || appGradleKts.isFile()) {
            results.add("✓ Конфигурация модуля app найдена");
        } else {
            results.add("✗ Нет app/build.gradle или app/build.gradle.kts");
            errors++;
        }

        if (settingsGradle.isFile() || settingsGradleKts.isFile()) {
            results.add("✓ Настройки Gradle найдены");
        } else {
            results.add("✗ Нет settings.gradle или settings.gradle.kts");
            errors++;
        }

        if (resDir.isDirectory()) {
            results.add("✓ Папка ресурсов res найдена");
        } else {
            results.add("! Папка app/src/main/res отсутствует");
            warnings++;
        }

        StringBuilder report = new StringBuilder();
        report.append("Проверка проекта: ").append(root.getName()).append("\n\n");
        for (String result : results) {
            report.append(result).append('\n');
        }
        report.append("\nОшибок: ").append(errors)
                .append(" • Предупреждений: ").append(warnings);
        if (errors == 0) {
            report.append("\n\nСтруктура готова к следующему этапу разработки сборщика.");
        } else {
            report.append("\n\nИсправь ошибки структуры перед сборкой APK.");
        }
        report.append("\n\nЭто проверка файлов, а не компиляция APK.");
        return report.toString();
    }

    private static boolean containsJava(File directory, int depth) {
        if (depth > 20) return false;
        File[] files = directory.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (java.nio.file.Files.isSymbolicLink(file.toPath())) continue;
            if (file.isFile() && file.getName().endsWith(".java")) return true;
            if (file.isDirectory() && containsJava(file, depth + 1)) return true;
        }
        return false;
    }
}
