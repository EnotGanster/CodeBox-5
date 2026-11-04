package ru.codebox.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class HomeScreen {

    public interface Listener {
        void onCreateProject();
        void onOpenProjects();
        void onOpenProjectTree();
        void onOpenCodeLibrary();
        void onCombineCode();
        void onOpenGraphics();
    }

    private final Activity activity;
    private final Listener listener;

    public HomeScreen(Activity activity, Listener listener) {
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
                    Typeface.DEFAULT, Typeface.BOLD
            );
        }

        return view;
    }

    public View create(int projectCount) {

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(
                CodeBoxTheme.background(activity)
        );

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dp(20), dp(24), dp(20), dp(24)
        );

        scroll.addView(content);
        root.addView(scroll);

        createHeader(content);
        createWorkspace(content, projectCount);
        createTools(content);
        createFooter(content);

        return root;
    }

    private void createHeader(LinearLayout parent) {

        LinearLayout header =
                new LinearLayout(activity);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names =
                new LinearLayout(activity);

        names.setOrientation(
                LinearLayout.VERTICAL
        );

        TextView logo = text("CodeBox", 34, true);
        logo.setTextColor(CodeBoxTheme.PURPLE);
        names.addView(logo);

        TextView subtitle = text(
                "МОБИЛЬНАЯ СРЕДА РАЗРАБОТКИ",
                12, false
        );
        subtitle.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );
        names.addView(subtitle);

        header.addView(
                names,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        TextView themeButton = text("☾", 30, false);
        themeButton.setTextColor(
                CodeBoxTheme.PURPLE
        );
        themeButton.setGravity(Gravity.CENTER);

        themeButton.setBackground(
                CodeBoxTheme.cardBackground(activity)
        );

        header.addView(
                themeButton,
                new LinearLayout.LayoutParams(
                        dp(64), dp(64)
                )
        );

        themeButton.setOnClickListener(v -> {
            CodeBoxTheme.toggleTheme(activity);
            activity.setContentView(
                    create(
                            new ProjectManager(activity)
                                    .getProjects().length
                    )
            );
        });

        parent.addView(header);
    }

    private void createWorkspace(
            LinearLayout parent, int count) {

        LinearLayout card =
                new LinearLayout(activity);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(22), dp(20), dp(22), dp(20)
        );

        CodeBoxTheme.styleCard(card);

        TextView title = text(
                "Рабочее пространство",
                23, true
        );
        card.addView(title);

        TextView info = text(
                count + " проектов · файлы, версии "
                        + "и графика в одном месте",
                15, false
        );

        info.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );
        info.setPadding(0, dp(10), 0, 0);
        card.addView(info);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.topMargin = dp(24);
        parent.addView(card, params);
    }

    private void createTools(LinearLayout parent) {

        TextView heading = text(
                "Инструменты", 25, true
        );

        heading.setPadding(
                0, dp(30), 0, dp(16)
        );

        parent.addView(heading);

        addTool(
                parent,
                "▦",
                "Мои проекты",
                "Создавай и редактируй проекты",
                CodeBoxTheme.PURPLE,
                listener::onOpenProjects
        );

        addTool(
                parent,
                "⌘",
                "Дерево проектов",
                "Структура папок и файлов",
                CodeBoxTheme.BLUE,
                listener::onOpenProjectTree
        );

        addTool(
                parent,
                "</>",
                "Библиотека кода",
                "Код и история его версий",
                0xFF479D83,
                listener::onOpenCodeLibrary
        );

        addTool(
                parent,
                "⇄",
                "Объединить код",
                "Инструменты объединения кода",
                0xFFC18D44,
                listener::onCombineCode
        );

        addTool(
                parent,
                "▧",
                "Ярлыки и графика",
                "Изображения и графические ресурсы",
                0xFFB35E98,
                listener::onOpenGraphics
        );

        TextView create = text(
                "+ Создать новый проект",
                17, true
        );

        create.setGravity(Gravity.CENTER);

        CodeBoxTheme.styleColoredButton(
                create, CodeBoxTheme.PURPLE
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, dp(56)
                );

        params.topMargin = dp(18);

        parent.addView(create, params);

        create.setOnClickListener(
                v -> listener.onCreateProject()
        );
    }

    private void addTool(
            LinearLayout parent,
            String symbol,
            String name,
            String description,
            int accent,
            Runnable action) {

        LinearLayout card =
                new LinearLayout(activity);

        card.setOrientation(
                LinearLayout.HORIZONTAL
        );

        card.setGravity(Gravity.CENTER_VERTICAL);

        card.setPadding(
                dp(16), dp(14), dp(16), dp(14)
        );

        CodeBoxTheme.styleCard(card);

        TextView icon = text(symbol, 25, true);
        icon.setTextColor(accent);
        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                CodeBoxTheme.rounded(
                        CodeBoxTheme.isDark(activity)
                                ? 0xFF34303F
                                : 0xFFF0ECF7,
                        18, activity
                )
        );

        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(62), dp(62)
                )
        );

        LinearLayout labels =
                new LinearLayout(activity);

        labels.setOrientation(
                LinearLayout.VERTICAL
        );

        labels.setPadding(
                dp(16), 0, dp(8), 0
        );

        TextView title = text(name, 19, true);
        labels.addView(title);

        TextView subtitle = text(
                description, 13, false
        );

        subtitle.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );
        labels.addView(subtitle);

        card.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView arrow = text("›", 30, false);
        arrow.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );
        card.addView(arrow);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.bottomMargin = dp(12);

        parent.addView(card, params);

        card.setOnClickListener(v -> action.run());
    }

    private void createFooter(LinearLayout parent) {

        TextView footer = text(
                "CodeBox 5 · твой код, твои проекты",
                13, false
        );

        footer.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        footer.setGravity(Gravity.CENTER);

        footer.setPadding(
                0, dp(24), 0, dp(12)
        );

        parent.addView(footer);
    }
}
