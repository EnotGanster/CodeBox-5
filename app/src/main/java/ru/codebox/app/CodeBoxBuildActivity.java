package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** UI for offline build diagnostics and verified ZIP installation. */
public final class CodeBoxBuildActivity extends Activity {
    private static final int PICK_TOOLCHAIN_ZIP = 7124;
    private CodeBoxBuildManager manager;
    private String projectName;
    private TextView statusView;
    private TextView reportView;
    private String installationReport;
    private boolean installing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        projectName = getIntent().getStringExtra("project_name");
        if (projectName == null || !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
            fatal("Не передано корректное имя проекта");
            return;
        }
        try {
            manager = new CodeBoxBuildManager(this);
        } catch (IOException e) {
            fatal("Невозможно создать журнал: " + e);
            return;
        }
        buildInterface();
        refreshReport();
    }

    private void fatal(String message) {
        new AlertDialog.Builder(this).setTitle("Ошибка")
                .setMessage(message).setCancelable(false)
                .setPositiveButton("Закрыть", (d, w) -> finish()).show();
    }

    private void buildInterface() {
        int background = Color.rgb(17, 20, 35);
        int panel = Color.rgb(29, 34, 53);
        int white = Color.WHITE;
        int muted = Color.rgb(185, 193, 215);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(12));
        root.setBackgroundColor(background);
        setContentView(root);

        root.addView(label("Сборка проекта", 23, white, true));
        TextView subtitle = label(projectName + "  •  Локально, без интернета", 13, muted, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(5);
        subtitleParams.bottomMargin = dp(13);
        root.addView(subtitle, subtitleParams);

        statusView = label("Ожидание", 13, muted, true);
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusView.setBackground(shape(panel, 12));
        root.addView(statusView);

        LinearLayout builds = new LinearLayout(this);
        LinearLayout.LayoutParams buildsParams = new LinearLayout.LayoutParams(-1, -2);
        buildsParams.topMargin = dp(10);
        root.addView(builds, buildsParams);
        addWeightedButton(builds, "Собрать APK", Color.rgb(107, 73, 218),
                () -> requestBuild(CodeBoxBuildManager.OutputFormat.APK), true);
        addWeightedButton(builds, "Собрать AAB", Color.rgb(37, 111, 192),
                () -> requestBuild(CodeBoxBuildManager.OutputFormat.AAB), false);

        TextView install = actionButton("Установить инструменты из ZIP", Color.rgb(47, 121, 113));
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(-1, dp(44));
        installParams.topMargin = dp(10);
        root.addView(install, installParams);
        install.setOnClickListener(v -> requestInstall());

        TextView heading = label("Подробный журнал", 17, white, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(15);
        headingParams.bottomMargin = dp(8);
        root.addView(heading, headingParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(shape(panel, 12));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        reportView = label("Журнал появится после проверки.", 12, muted, false);
        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(reportView);

        LinearLayout tools = new LinearLayout(this);
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, -2);
        toolsParams.topMargin = dp(10);
        root.addView(tools, toolsParams);
        addWeightedButton(tools, "Сохранить TXT", Color.rgb(65, 76, 102), this::saveLog, true);
        addWeightedButton(tools, "Поделиться", Color.rgb(65, 76, 102), this::shareLog, false);
    }

    private void addWeightedButton(LinearLayout row, String name, int color,
                                   Runnable action, boolean first) {
        TextView button = actionButton(name, color);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(45), 1);
        if (first) p.rightMargin = dp(5);
        else p.leftMargin = dp(5);
        row.addView(button, p);
        button.setOnClickListener(v -> {
            if (installing) {
                toast("Дождитесь завершения установки");
                return;
            }
            action.run();
        });
    }

    private void requestBuild(CodeBoxBuildManager.OutputFormat format) {
        new AlertDialog.Builder(this)
                .setTitle("Локальная сборка " + format.name())
                .setMessage("Компиляторы ещё не подключены к конвейеру сборки. "
                        + "Будет записана диагностика со статусом FAILED; APK/AAB не создаётся.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Записать диагностику", (d, w) -> runDiagnostic(format))
                .show();
    }

    private void runDiagnostic(CodeBoxBuildManager.OutputFormat format) {
        installationReport = null;
        try {
            manager.start(projectName, format);
            manager.setStage("Проверка проекта");
            File root = new File(getFilesDir(), "projects").getCanonicalFile();
            File project = new File(root, projectName).getCanonicalFile();
            if (!root.equals(project.getParentFile()) || !project.isDirectory()) {
                manager.fail("Папка проекта не найдена");
            } else {
                manager.info("Папка проекта найдена: " + project.getAbsolutePath());
                manager.info("Формат результата: " + format.name());
                manager.setStage("Проверка инструментов");
                CodeBoxToolchainInspector.Result result =
                        CodeBoxToolchainInspector.inspect(this);
                manager.info(result.report);
                manager.info("Готовность APK: " + (result.readyForApk ? "да" : "нет"));
                manager.info("Готовность AAB: " + (result.readyForAab ? "да" : "нет"));
                manager.fail("Реальная компиляция и упаковка " + format.name()
                        + " ещё не реализованы. Выходной файл не создан.");
            }
        } catch (Exception e) {
            if (manager.getState() == CodeBoxBuildManager.State.RUNNING) {
                manager.fail("Ошибка диагностики: " + e);
            }
            toast("Ошибка: " + e.getMessage());
        }
        refreshReport();
    }

    private void requestInstall() {
        if (installing) return;
        new AlertDialog.Builder(this)
                .setTitle("Установка инструментов")
                .setMessage("Нужен заранее проверенный ZIP с шестью файлами в корне: "
                        + "aapt2, android.jar, d8.jar, ecj.jar, apksigner.jar, bundletool.jar. "
                        + "Также потребуется доверенный SHA-256, полученный отдельно от архива. "
                        + "Наличие файлов ещё не означает совместимость с Android. "
                        + "Не выбирайте архив из непроверенного источника.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Выбрать ZIP", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{"application/zip", "application/octet-stream"});
                    try {
                        startActivityForResult(intent, PICK_TOOLCHAIN_ZIP);
                    } catch (Exception e) {
                        toast("Не удалось открыть выбор файла: " + e.getMessage());
                    }
                }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_TOOLCHAIN_ZIP || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        EditText hash = new EditText(this);
        hash.setSingleLine(true);
        hash.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hash.setHint("64 символа SHA-256");
        hash.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Доверенный SHA-256")
                .setMessage("Вставьте контрольную сумму из независимого доверенного источника. "
                        + "Не копируйте её из того же непроверенного архива.")
                .setView(hash)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Установить", (d, w) -> {
                    String expected = hash.getText().toString().trim();
                    if (!expected.matches("(?i)[0-9a-f]{64}")) {
                        toast("SHA-256 должен содержать ровно 64 шестнадцатеричных символа");
                        return;
                    }
                    installZip(uri, expected);
                }).show();
    }

    private void installZip(Uri uri, String expected) {
        if (installing) return;
        installing = true;
        statusView.setText("Статус: установка инструментов...");
        reportView.setText("Проверка SHA-256 и распаковка ZIP. Подождите...");
        new Thread(() -> {
            String result;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) throw new IOException("Не удалось открыть ZIP");
                CodeBoxToolchainInstaller.Result installed =
                        CodeBoxToolchainInstaller.install(getApplicationContext(), stream, expected);
                CodeBoxToolchainInspector.Result inventory =
                        CodeBoxToolchainInspector.inspect(getApplicationContext());
                result = "CodeBox 5 — установка инструментов\nСтатус: файлы распакованы\n"
                        + installed.report + "\n\n" + inventory.report;
            } catch (Exception e) {
                result = "CodeBox 5 — установка инструментов\nСтатус: FAILED\n"
                        + "Ошибка: " + e.toString() + "\n"
                        + "Прежний комплект мог сохраниться; проверьте каталог инструментов.";
            }
            final String report = result;
            runOnUiThread(() -> {
                installing = false;
                installationReport = report;
                refreshReport();
            });
        }, "CodeBoxToolchainInstall").start();
    }

    private void refreshReport() {
        if (manager == null || statusView == null || reportView == null) return;
        if (installationReport != null) {
            statusView.setText(installationReport.contains("Статус: FAILED")
                    ? "Установка: ошибка" : "Установка: файлы распакованы (не проверены запуском)");
            reportView.setText(installationReport);
            return;
        }
        CodeBoxBuildManager.State state = manager.getState();
        statusView.setText("Статус: " + state.name() + "  |  Ошибок: "
                + manager.getErrorCount() + "  |  Предупреждений: "
                + manager.getWarningCount());
        reportView.setText(state == CodeBoxBuildManager.State.IDLE
                ? "Выберите APK или AAB для диагностики. Для установки инструментов нужен "
                  + "доверенный ZIP и отдельный SHA-256."
                : manager.getReport());
    }

    private String currentReport() {
        if (installationReport != null) return installationReport;
        if (manager == null || manager.getState() == CodeBoxBuildManager.State.IDLE) return null;
        return manager.getReport();
    }

    private void saveLog() {
        String report = currentReport();
        if (report == null) {
            toast("Сначала запустите диагностику или установку");
            return;
        }
        try {
            File file;
            if (installationReport == null) {
                file = manager.saveReport();
            } else {
                File dir = new File(getFilesDir(), "build-logs");
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("Не удалось создать каталог журналов");
                }
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                        .format(new Date());
                file = new File(dir, "toolchain_" + stamp + ".txt");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(report.getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
            }
            new AlertDialog.Builder(this).setTitle("Журнал сохранён")
                    .setMessage("Внутренний файл приложения:\n" + file.getAbsolutePath()
                            + "\n\nЧтобы передать текст журнала, нажмите «Поделиться».")
                    .setPositiveButton("OK", null).show();
        } catch (IOException e) {
            fatalLog(e);
        }
    }

    private void shareLog() {
        String report = currentReport();
        if (report == null) {
            toast("Сначала запустите диагностику или установку");
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "CodeBox 5 — журнал " + projectName);
        send.putExtra(Intent.EXTRA_TEXT, report);
        try {
            startActivity(Intent.createChooser(send, "Отправить журнал"));
        } catch (Exception e) {
            toast("Не удалось открыть меню отправки: " + e.getMessage());
        }
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView actionButton(String value, int color) {
        TextView view = label(value, 13, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setBackground(shape(color, 11));
        return view;
    }

    private GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void fatalLog(Exception e) {
        new AlertDialog.Builder(this).setTitle("Ошибка сохранения")
                .setMessage(e.toString()).setPositiveButton("OK", null).show();
    }
}
