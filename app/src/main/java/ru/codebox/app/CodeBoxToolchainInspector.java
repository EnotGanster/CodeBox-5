package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only inventory of local Android build tools.
 * Does not execute binaries, download files, or claim APK/AAB support.
 */
public final class CodeBoxToolchainInspector {

    private CodeBoxToolchainInspector() { }

    public static final class Result {
        public final String report;
        public final boolean readyForApk;
        public final boolean readyForAab;

        private Result(String report, boolean readyForApk, boolean readyForAab) {
            this.report = report;
            this.readyForApk = readyForApk;
            this.readyForAab = readyForAab;
        }
    }

    public static Result inspect(Context context) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context отсутствует");
        }
        File files = context.getFilesDir().getCanonicalFile();
        File tools = new File(files, "build-tools").getCanonicalFile();
        if (!files.equals(tools.getParentFile())) {
            throw new IOException("Недопустимый каталог инструментов");
        }

        StringBuilder report = new StringBuilder();
        report.append("CodeBox 5 — проверка локальных инструментов\n");
        report.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n');
        report.append("Поддерживаемые ABI: ");
        if (Build.SUPPORTED_ABIS != null) {
            for (int i = 0; i < Build.SUPPORTED_ABIS.length; i++) {
                if (i > 0) report.append(", ");
                report.append(Build.SUPPORTED_ABIS[i]);
            }
        }
        report.append("\nКаталог инструментов: ").append(tools.getAbsolutePath()).append('\n');
        report.append("Каталог существует: ").append(tools.isDirectory() ? "да" : "нет").append("\n\n");

        report.append("Проверка ожидаемых компонентов (только наличие файлов):\n");
        check(report, tools, "aapt2", "aapt2");
        check(report, tools, "android.jar", "android.jar");
        check(report, tools, "d8.jar", "d8.jar");
        check(report, tools, "ecj.jar", "ecj.jar");
        check(report, tools, "apksigner.jar", "apksigner.jar");
        check(report, tools, "bundletool.jar", "bundletool.jar");
        report.append("\nВНИМАНИЕ: наличие файла не подтверждает, что он запускается на Android.\n");
        report.append("Компиляция Java, AAPT2, D8, упаковка и подпись пока не интегрированы.\n");
        report.append("Готовность APK: НЕТ\nГотовность AAB: НЕТ\n");
        return new Result(report.toString(), false, false);
    }

    private static void check(StringBuilder report, File directory, String label, String filename)
            throws IOException {
        File file = new File(directory, filename).getCanonicalFile();
        if (!directory.equals(file.getParentFile())) {
            throw new IOException("Недопустимое имя инструмента: " + filename);
        }
        report.append(" - ").append(label).append(": ");
        if (!file.isFile()) {
            report.append("отсутствует");
        } else if (file.length() == 0) {
            report.append("пустой файл");
        } else {
            report.append("найден (").append(file.length()).append(" байт; совместимость не проверена)");
        }
        report.append('\n');
    }
}
