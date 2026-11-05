package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/**
 * Build diagnostics screen. APK/AAB compilers are NOT connected yet.
 * A requested build is explicitly marked FAILED, never as successful.
 */
public final class CodeBoxBuildActivity extends Activity {

    private CodeBoxBuildManager manager;
    private String projectName;
    private TextView statusView;
    private TextView reportView;
    private TextView apkButton;
    private TextView aabButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectName = getIntent().getStringExtra("project_name");
        if (projectName == null || !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
            new AlertDialog.Builder(this)
                    .setTitle("Ошибка проекта")
                    .setMessage("Не передано корректное имя проекта.")
                    .setPositiveButton("Закрыть", (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }
        try {
            manager = new CodeBoxBuildManager(this);
        } catch (IOException exception) {
            new AlertDialog.Builder(this)
                    .setTitle("Ошибка журнала")
                    .setMessage(exception.toString())
                    .setPositiveButton("Закрыть", (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }
        buildInterface();
        refreshReport();
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

        TextView title = label("Сборка проекта", 23, white, true);
        root.addView(title);
        TextView subtitle = label(projectName + "  •  Локально, без интернета", 13, muted, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(5);
        subtitleParams.bottomMargin = dp(17);
        root.addView(subtitle, subtitleParams);

        statusView = label("Ожидание", 14, muted, true);
        statusView.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusView.setBackground(shape(panel, 12));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, -2);
        buttonsParams.topMargin = dp(12);
        root.addView(buttons, buttonsParams);

        apkButton = actionButton("Собрать APK", Color.rgb(107, 73, 218));
        aabButton = actionButton("Собрать AAB", Color.rgb(37, 111, 192));
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, dp(48), 1f);
        first.rightMargin = dp(6);
        buttons.addView(apkButton, first);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(48), 1f);
        second.leftMargin = dp(6);
        buttons.addView(aabButton, second);
        apkButton.setOnClickListener(v -> requestBuild(CodeBoxBuildManager.OutputFormat.APK));
        aabButton.setOnClickListener(v -> requestBuild(CodeBoxBuildManager.OutputFormat.AAB));

        TextView heading = label("Подробный журнал", 17, white, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(19);
        headingParams.bottomMargin = dp(9);
        root.addView(heading, headingParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(shape(panel, 12));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(scroll, scrollParams);
        reportView = label("Журнал появится после запуска проверки.", 12, muted, false);
        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(reportView);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, -2);
        toolsParams.topMargin = dp(12);
        root.addView(tools, toolsParams);

        TextView save = actionButton("Сохранить TXT", Color.rgb(65, 76, 102));
        TextView share = actionButton("Поделиться", Color.rgb(65, 76, 102));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        saveParams.rightMargin = dp(6);
        tools.addView(save, saveParams);
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        shareParams.leftMargin = dp(6);
        tools.addView(share, shareParams);
        save.setOnClickListener(v -> saveLog());
        share.setOnClickListener(v -> shareLog());
    }

    private void requestBuild(CodeBoxBuildManager.OutputFormat format) {
        if (manager == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Локальная сборка " + format.name())
                .setMessage("Компиляторы Java, AAPT2, D8 и упаковщик пока не подключены. "
                        + "Будет создан диагностический журнал с честным статусом FAILED. "
                        + "APK/AAB сейчас не создаётся.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Записать диагностику", (dialog, which) -> runDiagnostic(format))
                .show();
    }

    private void runDiagnostic(CodeBoxBuildManager.OutputFormat format) {
        try {
            manager.start(projectName, format);
            manager.setStage("Проверка окружения");
            File directory = new File(new File(getFilesDir(), "projects"), projectName);
            if (!directory.isDirectory()) {
                manager.fail("Папка проекта не найдена: " + directory.getAbsolutePath());
            } else {
                manager.info("Папка проекта найдена: " + directory.getAbsolutePath());
                manager.info("Формат результата: " + format.name());
                manager.fail("Локальный компилятор и упаковщик " + format.name()
                        + " пока не подключены. Сборка не выполнялась.");
            }
        } catch (Exception exception) {
            if (manager.getState() == CodeBoxBuildManager.State.RUNNING) {
                manager.fail("Ошибка подготовки: " + exception.toString());
            }
            Toast.makeText(this, exception.toString(), Toast.LENGTH_LONG).show();
        }
        refreshReport();
    }

    private void refreshReport() {
        if (manager == null || statusView == null || reportView == null) return;
        CodeBoxBuildManager.State state = manager.getState();
        statusView.setText("Статус: " + state.name() + "  |  Ошибок: "
                + manager.getErrorCount() + "  |  Предупреждений: "
                + manager.getWarningCount());
        reportView.setText(state == CodeBoxBuildManager.State.IDLE
                ? "Выберите APK или AAB. Пока доступна только диагностика: реальные файлы не создаются."
                : manager.getReport());
    }

    private void saveLog() {
        if (manager == null || manager.getState() == CodeBoxBuildManager.State.IDLE) {
            toast("Сначала запустите диагностику");
            return;
        }
        try {
            File file = manager.saveReport();
            new AlertDialog.Builder(this)
                    .setTitle("Журнал сохранён")
                    .setMessage("Внутренний файл приложения:\n" + file.getAbsolutePath()
                            + "\n\nЧтобы передать журнал, нажмите «Поделиться».")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (IOException exception) {
            showError(exception);
        }
    }

    private void shareLog() {
        if (manager == null || manager.getState() == CodeBoxBuildManager.State.IDLE) {
            toast("Сначала запустите диагностику");
            return;
        }
        // Share the full report as text without exposing a private file:// URI.
        // Sharing an actual TXT attachment will be added later via FileProvider.
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "CodeBox 5 — журнал " + projectName);
        send.putExtra(Intent.EXTRA_TEXT, manager.getReport());
        startActivity(Intent.createChooser(send, "Отправить журнал сборки"));
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView actionButton(String text, int color) {
        TextView view = label(text, 14, Color.WHITE, true);
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

    private void showError(Exception exception) {
        new AlertDialog.Builder(this)
                .setTitle("Ошибка")
                .setMessage(exception.toString())
                .setPositiveButton("OK", null)
                .show();
    }
}
