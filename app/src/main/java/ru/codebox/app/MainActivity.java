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

public class MainActivity extends Activity {

    private ProjectManager projectManager;
    private LinearLayout projectContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectManager = new ProjectManager(this);
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (projectContainer != null) {
            showProjects();
        }
    }

    private void showHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 40, 24, 24);

        TextView title = new TextView(this);
        title.setText("CodeBox 5");
        title.setTextSize(30);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Android Java IDE");
        subtitle.setTextSize(18);
        root.addView(subtitle);

        Button createButton = new Button(this);
        createButton.setText("Создать новый проект");
        root.addView(createButton);

        createButton.setOnClickListener(
                v -> showCreateProjectDialog()
        );

        TextView projectsTitle = new TextView(this);
        projectsTitle.setText("Мои проекты");
        projectsTitle.setTextSize(22);
        projectsTitle.setPadding(0, 24, 0, 12);
        root.addView(projectsTitle);

        ScrollView scrollView = new ScrollView(this);

        projectContainer = new LinearLayout(this);
        projectContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        scrollView.addView(projectContainer);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);
        showProjects();
    }

    private void showProjects() {
        projectContainer.removeAllViews();

        File[] projects = projectManager.getProjects();

        Arrays.sort(projects,
                (a, b) -> a.getName()
                        .compareToIgnoreCase(b.getName())
        );

        if (projects.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("Пока нет проектов");
            empty.setTextSize(16);
            projectContainer.addView(empty);
            return;
        }

        for (File project : projects) {
            Button button = new Button(this);
            button.setText("📁 " + project.getName());
            button.setAllCaps(false);

            button.setOnClickListener(
                    v -> openProject(project.getName())
            );

            projectContainer.addView(button);
        }
    }

    private void showCreateProjectDialog() {
        EditText input = new EditText(this);
        input.setHint("Например: MyApp");
        input.setSingleLine(true);

        LinearLayout container = new LinearLayout(this);
        container.setPadding(32, 16, 32, 8);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Новый Android-проект")
                .setView(container)
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
                        "[a-zA-Z][a-zA-Z0-9_]{0,49}"
                )) {
                    input.setError(
                            "Латинские буквы, цифры и _"
                    );
                    return;
                }

                try {
                    projectManager.createProject(name);

                    Toast.makeText(this,
                            "Проект создан: " + name,
                            Toast.LENGTH_SHORT).show();

                    dialog.dismiss();
                    showProjects();
                    openProject(name);

                } catch (IOException e) {
                    Toast.makeText(this,
                            "Ошибка: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void openProject(String projectName) {
        Intent intent = new Intent(
                this, ProjectActivity.class
        );

        intent.putExtra("project_name", projectName);
        startActivity(intent);
    }
}
