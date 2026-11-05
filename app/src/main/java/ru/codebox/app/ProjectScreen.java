package ru.codebox.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

public final class ProjectScreen {

    public interface Listener {
        void onBack();
        void onUp();
        void onNewFile();
        void onNewFolder();
        void onGraphics();
        void onFileSelected(File file);
        void onFileLongPressed(File file);
    }

    private final Activity activity;
    private final Listener listener;

    private LinearLayout fileContainer;
    private ScrollView fileScroll;
    private TextView pathText;
    private TextView countText;
    private String currentProjectName;

    public ProjectScreen(
            Activity activity,
            Listener listener) {

        this.activity = activity;
        this.listener = listener;
    }

    private int dp(float value) {
        return CodeBoxTheme.dp(activity, value);
    }

    private LinearLayout column() {
        LinearLayout layout =
                new LinearLayout(activity);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        return layout;
    }

    private TextView text(
            String value,
            int size,
            boolean bold) {

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

    private GradientDrawable rounded(
            int color, int radius) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private GradientDrawable headerGradient() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        0xFF8657F5,
                        0xFF6535D5,
                        0xFF4522A3
                }
        );
    }

    public View create(String projectName) {

        currentProjectName = projectName;

        LinearLayout root = column();

        root.setBackgroundColor(
                CodeBoxTheme.background(activity)
        );

        createHeader(root, projectName);
        createFileArea(root);
        createBottomBar(root);

        return root;
    }

    private void createHeader(
            LinearLayout root,
            String projectName) {

        LinearLayout header =
                new LinearLayout(activity);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        header.setPadding(
                dp(12), dp(10),
                dp(12), dp(10)
        );

        header.setBackground(headerGradient());

        TextView back = text("‹", 28, true);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);

        back.setBackground(
                rounded(0x33FFFFFF, 12)
        );

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(40), dp(40)
                )
        );

        back.setOnClickListener(
                v -> listener.onBack()
        );

        LinearLayout labels = column();

        labels.setPadding(
                dp(12), 0,
                dp(6), 0
        );

        TextView title = text(
                projectName, 18, true
        );

        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        title.setEllipsize(
                TextUtils.TruncateAt.END
        );

        labels.addView(title);

        TextView subtitle = text(
                "ANDROID PROJECT",
                10,
                false
        );

        subtitle.setTextColor(
                0xFFE8DDFF
        );

        labels.addView(subtitle);

        header.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView icon = text(
                "⌘", 21, true
        );

        icon.setTextColor(Color.WHITE);
        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                rounded(0x33FFFFFF, 12)
        );

        header.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(40), dp(40)
                )
        );

        root.addView(header);
    }

    private void createFileArea(
            LinearLayout root) {

        fileScroll = new ScrollView(activity);

        fileScroll.setFillViewport(false);
        fileScroll.setVerticalScrollBarEnabled(false);

        LinearLayout scrollContent = column();

        scrollContent.setPadding(
                dp(12), dp(12),
                dp(12), dp(12)
        );

        createPathBar(scrollContent);

        fileContainer = column();

        scrollContent.addView(
                fileContainer,
                new LinearLayout.LayoutParams(
                        -1, -2
                )
        );

        fileScroll.addView(scrollContent);

        root.addView(
                fileScroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );
    }

    private void createPathBar(
            LinearLayout parent) {

        LinearLayout pathCard = column();

        pathCard.setPadding(
                dp(12), dp(10),
                dp(12), dp(10)
        );

        CodeBoxTheme.styleCard(pathCard);

        TextView label = text(
                "ТЕКУЩАЯ ПАПКА",
                10,
                true
        );

        label.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        pathCard.addView(label);

        LinearLayout info =
                new LinearLayout(activity);

        info.setOrientation(
                LinearLayout.HORIZONTAL
        );

        info.setGravity(
                Gravity.CENTER_VERTICAL
        );

        pathText = text("/", 14, true);

        pathText.setTextColor(
                CodeBoxTheme.PURPLE
        );

        pathText.setSingleLine(true);

        pathText.setEllipsize(
                TextUtils.TruncateAt.MIDDLE
        );

        info.addView(
                pathText,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        countText = text(
                "0 элементов",
                11,
                false
        );

        countText.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        countText.setPadding(
                dp(8), 0, 0, 0
        );

        info.addView(countText);

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        infoParams.topMargin = dp(5);

        pathCard.addView(info, infoParams);

        parent.addView(pathCard);

        TextView libraryButton = text(
                "📚 Библиотека кода и Gradle",
                12,
                true
        );

        libraryButton.setGravity(Gravity.CENTER);

        libraryButton.setSingleLine(true);

        libraryButton.setEllipsize(
                TextUtils.TruncateAt.END
        );

        CodeBoxTheme.styleColoredButton(
                libraryButton,
                CodeBoxTheme.PURPLE
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, dp(42)
                );

        params.topMargin = dp(10);
        params.bottomMargin = dp(12);

        parent.addView(libraryButton, params);

        libraryButton.setOnClickListener(
                v -> openCodeLibrary()
        );
    }

    private void openCodeLibrary() {

        if (currentProjectName == null
                || currentProjectName.isEmpty()) {

            Toast.makeText(
                    activity,
                    "Проект не выбран",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent = new Intent(
                activity,
                CodeLibraryActivity.class
        );

        intent.putExtra(
                "project_name",
                currentProjectName
        );

        try {
            activity.startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(
                    activity,
                    "Ошибка открытия библиотеки: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void createBottomBar(
            LinearLayout root) {

        LinearLayout bar =
                new LinearLayout(activity);

        bar.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bar.setGravity(Gravity.CENTER_VERTICAL);

        bar.setPadding(
                dp(6), dp(7),
                dp(6), dp(9)
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

        addAction(
                bar,
                "🎨",
                0xFFD45C91,
                listener::onGraphics
        );

        root.addView(bar);
    }

    private void addAction(
            LinearLayout parent,
            String title,
            int color,
            Runnable action) {

        TextView button = text(
                title, 11, true
        );

        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);

        button.setEllipsize(
                TextUtils.TruncateAt.END
        );

        button.setIncludeFontPadding(false);

        CodeBoxTheme.styleColoredButton(
                button, color
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0, dp(44), 1
                );

        params.setMargins(
                dp(2), 0,
                dp(2), 0
        );

        parent.addView(button, params);

        button.setOnClickListener(
                v -> action.run()
        );
    }

    public void updatePath(
            String relativePath,
            int count) {

        if (pathText != null) {
            pathText.setText(
                    relativePath == null
                            ? "/"
                            : relativePath
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

        if (files == null
                || files.length == 0) {

            showEmptyFolder();

        } else {

            TextView heading = text(
                    "Содержимое папки",
                    14,
                    true
            );

            heading.setPadding(
                    dp(2), dp(2),
                    dp(2), dp(10)
            );

            fileContainer.addView(heading);

            for (File file : files) {
                addFileCard(file);
            }
        }

        if (fileScroll != null) {
            fileScroll.post(
                    () -> fileScroll.scrollTo(0, 0)
            );
        }
    }

    private void showEmptyFolder() {

        LinearLayout empty = column();

        empty.setGravity(Gravity.CENTER);

        empty.setPadding(
                dp(16), dp(28),
                dp(16), dp(28)
        );

        CodeBoxTheme.styleCard(empty);

        TextView icon = text(
                "📂", 36, false
        );

        icon.setGravity(Gravity.CENTER);
        empty.addView(icon);

        TextView title = text(
                "Папка пуста",
                17,
                true
        );

        title.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        titleParams.topMargin = dp(10);

        empty.addView(title, titleParams);

        TextView description = text(
                "Создай файл, папку или добавь "
                        + "изображение с помощью "
                        + "панели снизу",
                12,
                false
        );

        description.setGravity(Gravity.CENTER);

        description.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        LinearLayout.LayoutParams descParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        descParams.topMargin = dp(6);

        empty.addView(
                description, descParams
        );

        fileContainer.addView(empty);
    }

    private String fileIcon(File file) {

        if (file.isDirectory()) {
            return "📁";
        }

        String name = file.getName()
                .toLowerCase(Locale.ROOT);

        if (name.endsWith(".java")) {
            return "J";
        }

        if (name.endsWith(".xml")) {
            return "</>";
        }

        if (name.endsWith(".gradle")
                || name.endsWith(".gradle.kts")) {
            return "G";
        }

        if (name.endsWith(".json")) {
            return "{}";
        }

        if (name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".webp")) {
            return "🖼";
        }

        if (name.endsWith(".md")) {
            return "M";
        }

        return "📄";
    }

    private int fileColor(File file) {

        if (file.isDirectory()) {
            return 0xFFE6A23C;
        }

        String name = file.getName()
                .toLowerCase(Locale.ROOT);

        if (name.endsWith(".java")) {
            return 0xFFDF7544;
        }

        if (name.endsWith(".xml")) {
            return CodeBoxTheme.BLUE;
        }

        if (name.endsWith(".gradle")
                || name.endsWith(".gradle.kts")) {
            return 0xFF39A27D;
        }

        if (name.endsWith(".json")) {
            return 0xFFAD79D7;
        }

        if (name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".webp")) {
            return 0xFFD45C91;
        }

        return CodeBoxTheme.PURPLE;
    }

    private String fileType(File file) {

        if (file.isDirectory()) {
            return "Папка";
        }

        String name = file.getName()
                .toLowerCase(Locale.ROOT);

        if (name.endsWith(".java")) {
            return "Java · исходный код";
        }

        if (name.endsWith(".xml")) {
            return "XML · разметка";
        }

        if (name.endsWith(".gradle")
                || name.endsWith(".gradle.kts")) {
            return "Gradle · конфигурация";
        }

        if (name.endsWith(".json")) {
            return "JSON · данные";
        }

        if (name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".webp")) {
            return "Изображение";
        }

        if (name.endsWith(".md")) {
            return "Markdown";
        }

        return "Файл";
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
                dp(10), dp(9),
                dp(10), dp(9)
        );

        CodeBoxTheme.styleCard(card);

        TextView icon = text(
                fileIcon(file),
                19,
                true
        );

        icon.setTextColor(
                fileColor(file)
        );

        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                rounded(
                        CodeBoxTheme.isDark(activity)
                                ? 0xFF302B3E
                                : 0xFFF2EEFA,
                        12
                )
        );

        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(42), dp(42)
                )
        );

        LinearLayout labels = column();

        labels.setPadding(
                dp(10), 0,
                dp(5), 0
        );

        TextView name = text(
                file.getName(),
                14,
                true
        );

        name.setMaxLines(1);

        name.setEllipsize(
                TextUtils.TruncateAt.END
        );

        labels.addView(name);

        TextView type = text(
                fileType(file),
                11,
                false
        );

        type.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        LinearLayout.LayoutParams typeParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        typeParams.topMargin = dp(3);

        labels.addView(type, typeParams);

        card.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView arrow = text(
                "›", 24, false
        );

        arrow.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        card.addView(arrow);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.bottomMargin = dp(7);

        fileContainer.addView(card, params);

        card.setOnClickListener(
                v -> listener.onFileSelected(file)
        );

        card.setOnLongClickListener(v -> {
            listener.onFileLongPressed(file);
            return true;
        });
    }
}
