package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
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

    private void refreshPage() {
        if (currentPage == PROJECTS) {
            showProjects();
        } else if (currentPage == TREE) {
            showTreeProjects();
        } else {
            showHome();
        }
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

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
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

    private LinearLayout createPage(String title) {
        LinearLayout root = column();

        root.setBackgroundColor(
                CodeBoxTheme.background(this)
        );

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        header.setPadding(
                dp(18),
                dp(22),
                dp(18),
                dp(16)
        );

        TextView back = text("‹", 38, false);
        back.setGravity(Gravity.CENTER);

        back.setBackground(
                CodeBoxTheme.rounded(
                        CodeBoxTheme.surface(this),
                        22,
                        this
                )
        );

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(58), dp(58)
                )
        );

        back.setOnClickListener(
                v -> showHome()
        );

        TextView heading = text(
                title, 24, true
        );

        heading.setGravity(Gravity.CENTER);
        heading.setSingleLine(true);

        heading.setEllipsize(
                android.text.TextUtils.TruncateAt.END
        );

        header.addView(
                heading,
                new LinearLayout.LayoutParams(
                        0, dp(58), 1
                )
        );

        View balance = new View(this);

        header.addView(
                balance,
                new LinearLayout.LayoutParams(
                        dp(58), dp(58)
                )
        );

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

    private void showProjects() {
        currentPage = PROJECTS;

        LinearLayout root =
                createPage("Мои проекты");

        ScrollView scroll =
                new ScrollView(this);

        LinearLayout list = column();

        list.setPadding(
                dp(18), dp(12),
                dp(18), dp(16)
        );

        scroll.addView(list);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        File[] projects = sortedProjects();

        if (projects.length == 0) {
            addEmptyMessage(
                    list,
                    "Пока нет проектов"
            );
        }

        for (File project : projects) {
            addProjectCard(list, project);
        }

        LinearLayout bottom = column();

        bottom.setPadding(
                dp(26), dp(12),
                dp(26), dp(22)
        );

        TextView create = text(
                "+ Создать проект",
                19,
                true
        );

        CodeBoxTheme.styleColoredButton(
                create,
                CodeBoxTheme.PURPLE
        );

        bottom.addView(
                create,
                new LinearLayout.LayoutParams(
                        -1, dp(64)
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

        LinearLayout card = column();

        card.setPadding(
                dp(20), dp(20),
                dp(20), dp(18)
        );

        CodeBoxTheme.styleCard(card);

        TextView name = text(
                project.getName(),
                22,
                true
        );

        card.addView(name);

        TextView description = text(
                "Нет описания",
                15,
                false
        );

        description.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        descriptionParams.topMargin = dp(8);

        card.addView(
                description,
                descriptionParams
        );

        File[] children = project.listFiles();

        int count = children == null
                ? 0
                : children.length;

        TextView countText = text(
                "Элементов: " + count,
                14,
                false
        );

        countText.setTextColor(
                CodeBoxTheme.PURPLE
        );

        LinearLayout.LayoutParams countParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        countParams.topMargin = dp(12);

        card.addView(
                countText,
                countParams
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        cardParams.bottomMargin = dp(14);

        list.addView(card, cardParams);

        card.setOnClickListener(
                v -> openProject(
                        project.getName()
                )
        );
    }

    private void showTreeProjects() {
        currentPage = TREE;

        LinearLayout root =
                createPage("Дерево проектов");

        ScrollView scroll =
                new ScrollView(this);

        LinearLayout list = column();

        list.setPadding(
                dp(18), dp(12),
                dp(18), dp(20)
        );

        scroll.addView(list);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        File[] projects = sortedProjects();

        if (projects.length == 0) {
            addEmptyMessage(
                    list,
                    "Пока нет проектов"
            );
        }

        for (File project : projects) {
            addTreeCard(list, project);
        }

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
                dp(16), dp(16),
                dp(16), dp(16)
        );

        CodeBoxTheme.styleCard(card);

        TextView treeIcon = text(
                "🌳", 34, false
        );

        treeIcon.setGravity(Gravity.CENTER);

        treeIcon.setBackground(
                CodeBoxTheme.rounded(
                        CodeBoxTheme.isDark(this)
                                ? 0xFF304436
                                : 0xFFEAF4E9,
                        20,
                        this
                )
        );

        card.addView(
                treeIcon,
                new LinearLayout.LayoutParams(
                        dp(68), dp(68)
                )
        );

        LinearLayout labels = column();

        labels.setPadding(
                dp(14), 0,
                dp(6), 0
        );

        TextView name = text(
                project.getName(),
                19,
                true
        );

        labels.addView(name);

        TextView description = text(
                "Просмотреть структуру проекта",
                13,
                false
        );

        description.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        labels.addView(description);

        card.addView(
                labels,
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
                v -> openProjectTree(
                        project.getName()
                )
        );
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

    private void addEmptyMessage(
            LinearLayout list,
            String message) {

        TextView empty = text(
                message, 16, false
        );

        empty.setGravity(Gravity.CENTER);

        empty.setPadding(
                dp(12), dp(48),
                dp(12), dp(48)
        );

        list.addView(empty);
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

    // Настоящая библиотека кода.
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
