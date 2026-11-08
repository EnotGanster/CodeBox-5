package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends Activity
        implements HomeScreen.Listener {

    private static final int HOME = 0;
    private static final int PROJECTS = 1;
    private static final int TREE = 2;

    private ProjectManager projectManager;
    private int currentPage = HOME;

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        projectManager = new ProjectManager(this);

        if (state != null) {
            currentPage = state.getInt(
                    "current_page", HOME
            );
        }

        refreshPage();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("current_page", currentPage);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (projectManager != null) {
            refreshPage();
        }
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(
            String value,
            int size,
            boolean bold) {

        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(
                CodeBoxTheme.textColor(this)
        );

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private void refreshPage() {
        if (currentPage == PROJECTS) {
            showProjects();
        } else if (currentPage == TREE) {
            showTreeProjects();
        } else {
            showHome();
        }
    }

    private void showHome() {
        currentPage = HOME;

        HomeScreen home = new HomeScreen(
                this, this
        );

        setContentView(
                home.create(
                        projectManager.getProjects().length
                )
        );
    }

    private LinearLayout createPage(
            String title,
            String subtitle) {

        LinearLayout root = column();

        root.setBackgroundColor(
                CodeBoxTheme.background(this)
        );

        LinearLayout header = column();

        header.setPadding(
                dp(20), dp(18),
                dp(20), dp(20)
        );

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 34, true);
        back.setGravity(Gravity.CENTER);

        back.setBackground(
                CodeBoxTheme.rounded(
                        CodeBoxTheme.surface(this),
                        18,
                        this
                )
        );

        top.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(48), dp(48)
                )
        );

        back.setOnClickListener(
                v -> showHome()
        );

        LinearLayout headings = column();
        headings.setPadding(
                dp(14), 0, 0, 0
        );

        TextView heading = text(
                title, 23, true
        );

        heading.setSingleLine(true);
        heading.setEllipsize(
                TextUtils.TruncateAt.END
        );

        headings.addView(heading);

        TextView description = text(
                subtitle, 12, false
        );

        description.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        subParams.topMargin = dp(3);

        headings.addView(
                description, subParams
        );

        top.addView(
                headings,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        header.addView(top);

        root.addView(header);

        return root;
    }

    private File[] sortedProjects() {
        File[] projects =
                projectManager.getProjects();

        Arrays.sort(
                projects,
                (a, b) -> a.getName()
                        .compareToIgnoreCase(
                                b.getName()
                        )
        );

        return projects;
    }

    private TextView createSectionTitle(
            String title) {

        TextView section = text(
                title, 17, true
        );

        section.setPadding(
                dp(2), dp(10),
                dp(2), dp(14)
        );

        return section;
    }

    private void showProjects() {
        currentPage = PROJECTS;

        File[] projects = sortedProjects();

        LinearLayout root = createPage(
                "Мои проекты",
                "Все Android-проекты в одном месте"
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout list = column();

        list.setPadding(
                dp(18), dp(4),
                dp(18), dp(20)
        );

        TextView counter = text(
                "Всего проектов: " + projects.length,
                14,
                true
        );

        counter.setTextColor(
                CodeBoxTheme.PURPLE
        );

        counter.setPadding(
                dp(4), dp(4),
                dp(4), dp(12)
        );

        list.addView(counter);

        list.addView(
                createSectionTitle(
                        "Рабочее пространство"
                )
        );

        if (projects.length == 0) {
            addEmptyMessage(
                    list,
                    "📂",
                    "Пока нет проектов",
                    "Создай первый Android-проект, "
                    + "чтобы начать работу"
            );
        }

        for (File project : projects) {
            addProjectCard(list, project);
        }

        scroll.addView(list);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        LinearLayout bottom = column();

        bottom.setPadding(
                dp(20), dp(10),
                dp(20), dp(20)
        );

        TextView create = text(
                "＋  Создать проект",
                18,
                true
        );

        CodeBoxTheme.styleColoredButton(
                create,
                CodeBoxTheme.PURPLE
        );

        create.setGravity(Gravity.CENTER);

        bottom.addView(
                create,
                new LinearLayout.LayoutParams(
                        -1, dp(60)
                )
        );

        create.setOnClickListener(
                v -> showCreateProjectDialog()
        );

        root.addView(bottom);

        setContentView(root);
    }

    private void addProjectCard(
            LinearLayout list,
            File project) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.HORIZONTAL
        );

        card.setGravity(
                Gravity.CENTER_VERTICAL
        );

        card.setPadding(
                dp(14), dp(16),
                dp(14), dp(16)
        );

        CodeBoxTheme.styleCard(card);

        TextView icon = text(
                "📱", 28, false
        );

        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                CodeBoxTheme.rounded(
                        CodeBoxTheme.isDark(this)
                                ? 0xFF34264E
                                : 0xFFF0E9FF,
                        18,
                        this
                )
        );

        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(62), dp(62)
                )
        );

        LinearLayout details = column();

        details.setPadding(
                dp(14), 0,
                dp(6), 0
        );

        TextView name = text(
                project.getName(),
                19,
                true
        );

        name.setSingleLine(true);
        name.setEllipsize(
                TextUtils.TruncateAt.END
        );

        details.addView(name);

        File[] children = project.listFiles();

        int count = children == null
                ? 0
                : children.length;

        TextView description = text(
                "Android Java · " + count
                + " элементов",
                13,
                false
        );

        description.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams descParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        descParams.topMargin = dp(6);

        details.addView(
                description, descParams
        );

        TextView status = text(
                "Открыть проект",
                12,
                true
        );

        status.setTextColor(
                CodeBoxTheme.PURPLE
        );

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        statusParams.topMargin = dp(8);

        details.addView(
                status, statusParams
        );

        card.addView(
                details,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView arrow = text(
                "›", 30, false
        );

        arrow.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        card.addView(arrow);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.bottomMargin = dp(12);

        list.addView(card, params);

        card.setOnClickListener(
                v -> openProject(
                        project.getName()
                )
        );
    }

    private void showTreeProjects() {
        currentPage = TREE;

        File[] projects = sortedProjects();

        LinearLayout root = createPage(
                "Дерево проектов",
                "Просмотр файлов и структуры"
        );

        ScrollView scroll = new ScrollView(this);

        LinearLayout list = column();

        list.setPadding(
                dp(18), dp(4),
                dp(18), dp(20)
        );

        list.addView(
                createSectionTitle(
                        "Выбери проект"
                )
        );

        if (projects.length == 0) {
            addEmptyMessage(
                    list,
                    "🌳",
                    "Нет проектов",
                    "Сначала создай Android-проект"
            );
        }

        for (File project : projects) {
            addTreeCard(list, project);
        }

        scroll.addView(list);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        setContentView(root);
    }

    private void addTreeCard(
            LinearLayout list,
            File project) {

        LinearLayout card =
                new LinearLayout(this);

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
                "🌳", 28, false
        );

        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                CodeBoxTheme.rounded(
                        CodeBoxTheme.isDark(this)
                                ? 0xFF263E35
                                : 0xFFE8F5EB,
                        18,
                        this
                )
        );

        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(62), dp(62)
                )
        );

        LinearLayout labels = column();

        labels.setPadding(
                dp(14), 0,
                dp(4), 0
        );

        TextView name = text(
                project.getName(),
                18,
                true
        );

        name.setSingleLine(true);
        name.setEllipsize(
                TextUtils.TruncateAt.END
        );

        labels.addView(name);

        TextView description = text(
                "Посмотреть структуру файлов",
                12,
                false
        );

        description.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams descParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        descParams.topMargin = dp(5);

        labels.addView(
                description, descParams
        );

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
                CodeBoxTheme.secondaryText(this)
        );

        card.addView(arrow);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.bottomMargin = dp(12);

        list.addView(card, params);

        card.setOnClickListener(
                v -> openProjectTree(
                        project.getName()
                )
        );
    }

    private void addEmptyMessage(
            LinearLayout list,
            String emoji,
            String title,
            String description) {

        LinearLayout card = column();

        card.setGravity(Gravity.CENTER);

        card.setPadding(
                dp(18), dp(36),
                dp(18), dp(36)
        );

        CodeBoxTheme.styleCard(card);

        TextView icon = text(
                emoji, 42, false
        );

        icon.setGravity(Gravity.CENTER);
        card.addView(icon);

        TextView heading = text(
                title, 19, true
        );

        heading.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        titleParams.topMargin = dp(12);

        card.addView(
                heading, titleParams
        );

        TextView message = text(
                description, 14, false
        );

        message.setGravity(Gravity.CENTER);

        message.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams msgParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        msgParams.topMargin = dp(8);

        card.addView(
                message, msgParams
        );

        list.addView(card);
    }

    private void openProjectTree(String name) {
        Intent intent = new Intent(
                this,
                ProjectTreeActivity.class
        );

        intent.putExtra(
                "project_name",
                name
        );

        startActivity(intent);
    }

    private void showCreateProjectDialog() {
        EditText input = new EditText(this);

        input.setHint("Например: MyApp");
        input.setSingleLine(true);

        LinearLayout wrapper = column();

        wrapper.setPadding(
                dp(24), dp(12),
                dp(24), dp(8)
        );

        wrapper.addView(input);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Новый Android-проект"
                        )
                        .setMessage(
                                "Укажи название проекта "
                                + "латинскими буквами"
                        )
                        .setView(wrapper)
                        .setPositiveButton(
                                "Создать", null
                        )
                        .setNegativeButton(
                                "Отмена", null
                        )
                        .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

                String name = input.getText()
                        .toString()
                        .trim();

                if (!name.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {
                    input.setError(
                            "Используй латинские буквы, "
                            + "цифры и _"
                    );
                    return;
                }

                try {
                    projectManager.createProject(name);

                    dialog.dismiss();

                    Toast.makeText(
                            this,
                            "Проект создан: " + name,
                            Toast.LENGTH_SHORT
                    ).show();

                    openProject(name);

                } catch (IOException e) {
                    input.setError(
                            e.getMessage() == null
                                    ? "Ошибка создания"
                                    : e.getMessage()
                    );
                }
            });
        });

        dialog.show();
    }

    private void openProject(String name) {
        Intent intent = new Intent(
                this,
                ProjectActivity.class
        );

        intent.putExtra(
                "project_name",
                name
        );

        startActivity(intent);
    }

    private void openCodeLibrary() {
        Intent intent = new Intent(
                this,
                CodeLibraryActivity.class
        );

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Не удалось открыть библиотеку: "
                    + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onCreateProject() {
        showCreateProjectDialog();
    }

    @Override
    public void onOpenProjects() {
        showProjects();
    }

    @Override
    public void onOpenProjectTree() {
        showTreeProjects();
    }

    @Override
    public void onOpenCodeLibrary() {
        openCodeLibrary();
    }

    @Override
    public void onCombineCode() {
        showComingSoon(
                "Объединить код"
        );
    }

    @Override
    public void onOpenGraphics() {
        showComingSoon(
                "Ярлыки и графика"
        );
    }

    /** Opens the local build screen for a selected project. */
    @Override
    public void onOpenBuild() {
        File[] projects = sortedProjects();

        if (projects.length == 0) {
            Toast.makeText(
                    this,
                    "Сначала создай Android-проект",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (projects.length == 1) {
            openBuildScreen(projects[0].getName());
            return;
        }

        String[] names = new String[projects.length];
        for (int i = 0; i < projects.length; i++) {
            names[i] = projects[i].getName();
        }

        new AlertDialog.Builder(this)
                .setTitle("Выбери проект для сборки")
                .setItems(names, (dialog, which) -> {
                    if (which >= 0 && which < projects.length) {
                        openBuildScreen(projects[which].getName());
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void openBuildScreen(String projectName) {
        Intent intent = new Intent(
                this,
                CodeBoxBuildActivity.class
        );
        intent.putExtra("project_name", projectName);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Не удалось открыть экран сборки: "
                            + (e.getMessage() == null
                            ? "неизвестная ошибка"
                            : e.getMessage()),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showComingSoon(
            String feature) {

        new AlertDialog.Builder(this)
                .setTitle(feature)
                .setMessage(
                        "Этот инструмент появится "
                        + "в следующих версиях CodeBox 5."
                )
                .setPositiveButton(
                        "Понятно", null
                )
                .show();
    }

    @Override
    public void onBackPressed() {
        if (currentPage != HOME) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }
                      }
