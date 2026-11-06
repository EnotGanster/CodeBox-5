package ru.codebox.app;

import android.content.Context;
import android.os.Build;

public final class CodeBoxD8Inspector {

    private CodeBoxD8Inspector() {
    }

    public static String inspect(Context context) {
        StringBuilder report = new StringBuilder();

        report.append("CodeBox 5 - D8 Inspector\n");
        report.append("Android API: ")
                .append(Build.VERSION.SDK_INT)
                .append("\n\n");

        if (context == null) {
            report.append("ERROR: Context отсутствует\n");
            return report.toString();
        }

        checkClass(report, "com.android.tools.r8.D8");
        checkClass(report, "com.android.tools.r8.D8Command");
        checkClass(report, "com.android.tools.r8.OutputMode");
        checkClass(report, "com.android.tools.r8.CompilationFailedException");

        report.append("\nПроверка завершена.\n");
        report.append("Это проверка загрузки классов, ");
        report.append("а не запуск компиляции D8.\n");

        return report.toString();
    }

    private static void checkClass(
            StringBuilder report,
            String className) {

        report.append(className).append(": ");

        try {
            Class.forName(
                    className,
                    false,
                    CodeBoxD8Inspector.class.getClassLoader()
            );

            report.append("OK\n");

        } catch (Throwable error) {
            report.append("FAILED\n");
            report.append("Причина: ")
                    .append(error.getClass().getName())
                    .append("\n");

            if (error.getMessage() != null) {
                report.append("Сообщение: ")
                        .append(error.getMessage())
                        .append("\n");
            }
        }
    }
}
