package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
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

    private ProjectManager projectManager;
    private boolean showingProjects = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        projectManager = new ProjectManager(this);
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (projectManager != null) {
            if (showingProjects) {
                showProjects();
            } else {
                showHome();
            }
        }
    }

    private void showHome() {
        showingProjects = false;

        HomeScreen home = new HomeScreen(this, this);

        setContentView(
                home.create(
                        projectManager.getProjects().length
                )
        );
    }

    private void showProjects() {
        showingProjects = true;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(20), dp(24), dp(20), dp(20)
        );
        root.setBackgroundColor(
                CodeBoxTheme.background(this)
        );

        TextView title = new TextView(this);
        title.setText("Мои проекты");
        title.setTextSize(28);
        title.setTextColor(
                CodeBoxTheme.textColor(this)
        );
        root.addView(title);

        Button back = new Button(this);
        back.setText("← На главную");
        root.addView(back);
        back.setOnClickListener(v -> showHome());

        Button create = new Button(this);
        create.setText("+ Создать проект");
        root.addView(create);
        create.setOnClickListener(
                v -> showCreateProjectDialog()
        );

        ScrollView scroll = new ScrollView(this);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        scroll.addView(list);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        File[] projects = projectManager.getProjects();

        Arrays.sort(
                projects,
                (a, b) -> a.getName()
                        .compareToIgnoreCase(b.getName())
        );

        if (projects.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("Пока нет проектов");
            empty.setTextSize(17);
            empty.setTextColor(
                    CodeBoxTheme.secondaryText(this)
            );
            empty.setPadding(0, dp(24), 0, 0);
            list.addView(empty);
        }

        for (File project : projects) {
            TextView card = new TextView(this);

            card.setText("📁  " + project.getName());
            card.setTextSize(19);
            card.setTextColor(
                    CodeBoxTheme.textColor(this)
            );
            card.setPadding(
                    dp(20), dp(22),
                    dp(20), dp(22)
            );

            CodeBoxTheme.styleCard(card);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            -1, -2
                    );
            params.bottomMargin = dp(12);

            list.addView(card, params);

            card.setOnClickListener(
                    v -> openProject(project.getName())
            );
        }

        setContentView(root);
    }

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    private void showCreateProjectDialog() {
        EditText input = new EditText(this);
        input.setHint("Например: MyApp");
        input.setSingleLine(true);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(
                dp(24), dp(12), dp(24), dp(8)
        );
        wrapper.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Новый Android-проект")
                .setView(wrapper)
                .setPositiveButton("Создать", null)
                .setNegativeButton("Отмена", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {
                String name = input.getText()
                        .toString().trim();

                if (!name.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {
                    input.setError(
                            "Используй латинские буквы, цифры и _"
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
                    input.setError(e.getMessage());
                }
            });
        });

        dialog.show();
    }

    private void openProject(String name) {
        Intent intent = new Intent(
                this, ProjectActivity.class
        );

        intent.putExtra("project_name", name);
        startActivity(intent);
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
        showProjects();
    }

    @Override
    public void onOpenCodeLibrary() {
        showComingSoon("Библиотека кода");
    }

    @Override
    public void onCombineCode() {
        showComingSoon("Объединить код");
    }

    @Override
    public void onOpenGraphics() {
        showComingSoon("Ярлыки и графика");
    }

    private void showComingSoon(String feature) {
        new AlertDialog.Builder(this)
                .setTitle(feature)
                .setMessage(
                        "Этот инструмент появится "
                                + "в следующих версиях CodeBox 5."
                )
                .setPositiveButton("Понятно", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (showingProjects) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }
}
