
package ru.codebox.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

public final class ProjectScreen {

    public interface Listener {
        void onBack();
        void onUp();
        void onNewFile();
        void onNewFolder();
        void onFileSelected(File file);

        // Новое действие: долгое нажатие
        void onFileLongPressed(File file);
    }

    private final Activity activity;
    private final Listener listener;

    private LinearLayout fileContainer;
    private TextView pathText;
    private TextView countText;

    public ProjectScreen(
            Activity activity,
            Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    private int dp(float value) {
        return CodeBoxTheme.dp(activity, value);
    }

    private TextView text(
            String value, int size, boolean bold) {

        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(
                CodeBoxTheme.textColor(activity)
        );

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    public View create(String projectName) {

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(
                CodeBoxTheme.background(activity)
        );

        createHeader(root, projectName);
        createPathBar(root);
        createFileArea(root);
        createBottomBar(root);

        return root;
    }

    private void createHeader(
            LinearLayout root, String projectName) {

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(
                dp(16), dp(20), dp(16), dp(20)
        );

        GradientDrawable background =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                CodeBoxTheme.PURPLE,
                                0xFF9877CF
                        }
                );

        header.setBackground(background);

        TextView back = text("‹", 36, true);
        back.setTextColor(0xFFFFFFFF);
        back.setGravity(Gravity.CENTER);

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(48), dp(48)
                )
        );

        back.setOnClickListener(
                v -> listener.onBack()
        );

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(
                projectName, 22, true
        );

        title.setTextColor(0xFFFFFFFF);
        title.setSingleLine(true);
        title.setEllipsize(
                android.text.TextUtils.TruncateAt.END
        );

        labels.addView(title);

        TextView subtitle = text(
                "Управление проектом",
                13,
                false
        );

        subtitle.setTextColor(0xFFECE3FF);
        labels.addView(subtitle);

        header.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        root.addView(header);
    }

    private void createPathBar(LinearLayout root) {

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(
                dp(18), dp(16), dp(18), dp(12)
        );

        pathText = text("/", 14, true);
        pathText.setTextColor(
                CodeBoxTheme.PURPLE
        );

        panel.addView(pathText);

        countText = text(
                "0 элементов",
                12,
                false
        );

        countText.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        panel.addView(countText);
        root.addView(panel);
    }

    private void createFileArea(LinearLayout root) {

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);

        fileContainer = new LinearLayout(activity);
        fileContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        fileContainer.setPadding(
                dp(16), dp(4), dp(16), dp(12)
        );

        scroll.addView(fileContainer);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );
    }

    private void createBottomBar(LinearLayout root) {

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(
                dp(12), dp(12), dp(12), dp(12)
        );

        bar.setBackgroundColor(
                CodeBoxTheme.surface(activity)
        );

        addAction(
                bar,
                "↑ Вверх",
                CodeBoxTheme.BLUE,
                listener::onUp
        );

        addAction(
                bar,
                "+ Файл",
                CodeBoxTheme.PURPLE,
                listener::onNewFile
        );

        addAction(
                bar,
                "+ Папка",
                CodeBoxTheme.GREEN,
                listener::onNewFolder
        );

        root.addView(bar);
    }

    private void addAction(
            LinearLayout parent,
            String title,
            int color,
            Runnable action) {

        TextView button = text(
                title, 13, true
        );

        CodeBoxTheme.styleColoredButton(
                button, color
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0, dp(54), 1
                );

        params.setMargins(
                dp(3), 0, dp(3), 0
        );

        parent.addView(button, params);

        button.setOnClickListener(
                v -> action.run()
        );
    }

    public void updatePath(
            String relativePath, int count) {

        if (pathText != null) {
            pathText.setText(
                    "Папка: " + relativePath
            );
        }

        if (countText != null) {
            countText.setText(
                    count + " элементов"
            );
        }
    }

    public void showFiles(File[] files) {

        if (fileContainer == null) {
            return;
        }

        fileContainer.removeAllViews();

        if (files == null || files.length == 0) {

            TextView empty = text(
                    "В этой папке пока нет файлов",
                    15,
                    false
            );

            empty.setGravity(Gravity.CENTER);

            empty.setPadding(
                    dp(12), dp(48),
                    dp(12), dp(48)
            );

            fileContainer.addView(empty);
            return;
        }

        for (File file : files) {
            addFileCard(file);
        }
    }

    private void addFileCard(File file) {

        LinearLayout card =
                new LinearLayout(activity);

        card.setOrientation(
                LinearLayout.HORIZONTAL
        );

        card.setGravity(
                Gravity.CENTER_VERTICAL
        );

        card.setPadding(
                dp(14), dp(14),
                dp(14), dp(14)
        );

        CodeBoxTheme.styleCard(card);

        boolean folder = file.isDirectory();

        TextView symbol = text(
                folder ? "📁" : "📄",
                27,
                false
        );

        symbol.setGravity(Gravity.CENTER);

        card.addView(
                symbol,
                new LinearLayout.LayoutParams(
                        dp(48), dp(48)
                )
        );

        LinearLayout labels =
                new LinearLayout(activity);

        labels.setOrientation(
                LinearLayout.VERTICAL
        );

        labels.setPadding(
                dp(10), 0, 0, 0
        );

        TextView name = text(
                file.getName(),
                16,
                true
        );

        name.setMaxLines(2);
        labels.addView(name);

        TextView type = text(
                folder ? "Папка" : "Файл",
                12,
                false
        );

        type.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        labels.addView(type);

        card.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView arrow = text(
                "›", 28, false
        );

        arrow.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        card.addView(arrow);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.bottomMargin = dp(10);

        fileContainer.addView(
                card, params
        );

        // Обычное нажатие: открыть файл или папку.
        card.setOnClickListener(
                v -> listener.onFileSelected(file)
        );

        // Долгое нажатие: меню действий.
        card.setOnLongClickListener(v -> {
            listener.onFileLongPressed(file);
            return true;
        });
    }
}
