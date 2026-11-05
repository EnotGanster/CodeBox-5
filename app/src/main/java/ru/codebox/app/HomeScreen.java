package ru.codebox.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

    public HomeScreen(
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

    private GradientDrawable background(
            int color,
            int radius) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private GradientDrawable gradient() {

        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[] {
                                0xFF8657F5,
                                0xFF6535D5,
                                0xFF4522A3
                        }
                );

        drawable.setCornerRadius(dp(26));

        return drawable;
    }

    public View create(int projectCount) {

        LinearLayout root = column();

        root.setBackgroundColor(
                CodeBoxTheme.background(activity)
        );

        ScrollView scroll =
                new ScrollView(activity);

        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = column();

        content.setPadding(
                dp(18), dp(20),
                dp(18), dp(26)
        );

        createHeader(content);
        createWorkspace(content, projectCount);
        createTools(content);
        createFooter(content);

        scroll.addView(content);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, -1
                )
        );

        return root;
    }

    private void createHeader(
            LinearLayout parent) {

        LinearLayout header =
                new LinearLayout(activity);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView logo = text(
                "CB", 19, true
        );

        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);

        logo.setBackground(
                background(
                        CodeBoxTheme.PURPLE,
                        17
                )
        );

        header.addView(
                logo,
                new LinearLayout.LayoutParams(
                        dp(54), dp(54)
                )
        );

        LinearLayout names = column();

        names.setPadding(
                dp(12), 0,
                dp(6), 0
        );

        TextView title = text(
                "CodeBox 5", 25, true
        );

        title.setSingleLine(true);
        names.addView(title);

        TextView subtitle = text(
                "ANDROID JAVA IDE",
                11,
                true
        );

        subtitle.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        names.addView(subtitle);

        header.addView(
                names,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        boolean dark =
                CodeBoxTheme.isDark(activity);

        TextView theme = text(
                dark ? "☀" : "☾",
                27,
                false
        );

        theme.setGravity(Gravity.CENTER);

        theme.setTextColor(
                CodeBoxTheme.PURPLE
        );

        theme.setBackground(
                CodeBoxTheme.cardBackground(activity)
        );

        header.addView(
                theme,
                new LinearLayout.LayoutParams(
                        dp(52), dp(52)
                )
        );

        theme.setContentDescription(
                dark
                        ? "Включить светлую тему"
                        : "Включить тёмную тему"
        );

        theme.setOnClickListener(v -> {
            CodeBoxTheme.toggleTheme(activity);

            int count = new ProjectManager(
                    activity
            ).getProjects().length;

            activity.setContentView(
                    create(count)
            );
        });

        parent.addView(header);
    }

    private void createWorkspace(
            LinearLayout parent,
            int projectCount) {

        LinearLayout card = column();

        card.setPadding(
                dp(22), dp(24),
                dp(22), dp(24)
        );

        card.setBackground(gradient());

        TextView badge = text(
                "●  РАБОЧЕЕ ПРОСТРАНСТВО",
                11,
                true
        );

        badge.setTextColor(
                0xFFE8DDFF
        );

        card.addView(badge);

        TextView title = text(
                "Создавай.\nПиши. Развивай.",
                27,
                true
        );

        title.setTextColor(Color.WHITE);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        titleParams.topMargin = dp(14);

        card.addView(
                title, titleParams
        );

        TextView description = text(
                "Твоя мобильная среда "
                + "для Android-разработки",
                14,
                false
        );

        description.setTextColor(
                0xFFE7DFFF
        );

        LinearLayout.LayoutParams descParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        descParams.topMargin = dp(10);

        card.addView(
                description, descParams
        );

        LinearLayout stats =
                new LinearLayout(activity);

        stats.setOrientation(
                LinearLayout.HORIZONTAL
        );

        stats.setGravity(
                Gravity.CENTER_VERTICAL
        );

        stats.setPadding(
                dp(14), dp(12),
                dp(14), dp(12)
        );

        stats.setBackground(
                background(
                        0xFF7451D2,
                        17
                )
        );

        TextView count = text(
                String.valueOf(projectCount),
                30,
                true
        );

        count.setTextColor(Color.WHITE);

        stats.addView(count);

        LinearLayout labels = column();

        labels.setPadding(
                dp(12), 0,
                0, 0
        );

        TextView label = text(
                "Мои проекты",
                15,
                true
        );

        label.setTextColor(Color.WHITE);
        labels.addView(label);

        TextView detail = text(
                "Открыть рабочее пространство",
                11,
                false
        );

        detail.setTextColor(
                0xFFE8DDFF
        );

        labels.addView(detail);

        stats.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView arrow = text(
                "›", 28, false
        );

        arrow.setTextColor(Color.WHITE);
        stats.addView(arrow);

        LinearLayout.LayoutParams statsParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        statsParams.topMargin = dp(22);

        card.addView(stats, statsParams);

        stats.setOnClickListener(
                v -> listener.onOpenProjects()
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.topMargin = dp(24);

        parent.addView(card, params);
    }

    private void createTools(
            LinearLayout parent) {

        LinearLayout headingRow =
                new LinearLayout(activity);

        headingRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headingRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView heading = text(
                "Инструменты",
                22,
                true
        );

        headingRow.addView(
                heading,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView caption = text(
                "5 разделов",
                12,
                false
        );

        caption.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        headingRow.addView(caption);

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        headingParams.topMargin = dp(28);
        headingParams.bottomMargin = dp(16);

        parent.addView(
                headingRow,
                headingParams
        );

        addTool(
                parent,
                "▦",
                "Мои проекты",
                "Редактирование Android-проектов",
                CodeBoxTheme.PURPLE,
                listener::onOpenProjects
        );

        addTool(
                parent,
                "⌘",
                "Дерево проектов",
                "Папки и структура файлов",
                CodeBoxTheme.BLUE,
                listener::onOpenProjectTree
        );

        addTool(
                parent,
                "</>",
                "Библиотека кода",
                "Шаблоны Java, XML и Gradle",
                0xFF36A389,
                listener::onOpenCodeLibrary
        );

        addTool(
                parent,
                "⇄",
                "Объединить код",
                "Объединение исходных файлов",
                0xFFD49A47,
                listener::onCombineCode
        );

        addTool(
                parent,
                "▧",
                "Ярлыки и графика",
                "Изображения и ресурсы",
                0xFFCB70A9,
                listener::onOpenGraphics
        );

        TextView create = text(
                "＋  Создать новый проект",
                17,
                true
        );

        create.setGravity(Gravity.CENTER);

        CodeBoxTheme.styleColoredButton(
                create,
                CodeBoxTheme.PURPLE
        );

        LinearLayout.LayoutParams createParams =
                new LinearLayout.LayoutParams(
                        -1, dp(60)
                );

        createParams.topMargin = dp(12);

        parent.addView(
                create,
                createParams
        );

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

        card.setGravity(
                Gravity.CENTER_VERTICAL
        );

        card.setPadding(
                dp(14), dp(14),
                dp(14), dp(14)
        );

        CodeBoxTheme.styleCard(card);

        TextView icon = text(
                symbol, 24, true
        );

        icon.setTextColor(accent);
        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                background(
                        CodeBoxTheme.isDark(activity)
                                ? 0xFF302B3E
                                : 0xFFF2EEFA,
                        17
                )
        );

        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(58), dp(58)
                )
        );

        LinearLayout labels = column();

        labels.setPadding(
                dp(14), 0,
                dp(6), 0
        );

        TextView title = text(
                name, 17, true
        );

        title.setSingleLine(true);
        labels.addView(title);

        TextView subtitle = text(
                description,
                12,
                false
        );

        subtitle.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        subParams.topMargin = dp(4);

        labels.addView(
                subtitle, subParams
        );

        card.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView arrow = text(
                "›", 27, false
        );

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

        card.setOnClickListener(
                v -> action.run()
        );
    }

    private void createFooter(
            LinearLayout parent) {

        TextView footer = text(
                "CODEBOX 5  •  ANDROID JAVA IDE",
                11,
                true
        );

        footer.setTextColor(
                CodeBoxTheme.secondaryText(activity)
        );

        footer.setGravity(Gravity.CENTER);

        footer.setPadding(
                0, dp(24),
                0, dp(12)
        );

        parent.addView(footer);
    }
}
