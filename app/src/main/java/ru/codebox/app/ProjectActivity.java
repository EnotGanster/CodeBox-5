package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class ProjectActivity extends Activity {

    private File projectDirectory;
    private LinearLayout fileContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String projectName =
                getIntent().getStringExtra("project_name");

        if (projectName == null ||
                !projectName.matches("[a-zA-Z][a-zA-Z0-9_]{0,49}")) {
            Toast.makeText(this,
                    "Неверное имя проекта",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        projectDirectory = new File(
                new File(getFilesDir(), "projects"),
                projectName
        );

        if (!projectDirectory.isDirectory()) {
            Toast.makeText(this,
                    "Проект не найден",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        showProject();
    }

    private void showProject() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 32, 24, 24);

        TextView title = new TextView(this);
        title.setText("Проект: " + projectDirectory.getName());
        title.setTextSize(24);
        root.addView(title);

        Button backButton = new Button(this);
        backButton.setText("Назад");
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton);

        ScrollView scrollView = new ScrollView(this);
        fileContainer = new LinearLayout(this);
        fileContainer.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(fileContainer);
        root.addView(scrollView);

        setContentView(root);

        showFiles(projectDirectory, 0);
    }

    private void showFiles(File directory, int level) {
        File[] files = directory.listFiles();

        if (files == null) {
            return;
        }

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File file : files) {
            Button button = new Button(this);

            String prefix = file.isDirectory() ? "📁 " : "📄 ";
            button.setText(prefix + file.getName());
            button.setAllCaps(false);

            button.setPadding(
                    16 + Math.min(level, 10) * 16,
                    8, 8, 8
            );

            fileContainer.addView(button);

            if (file.isDirectory()) {
                showFiles(file, level + 1);
            } else {
                button.setOnClickListener(v -> openFile(file));
            }
        }
    }

    private void openFile(File file) {
        try {
            String content = FileUtils.readText(file);

            TextView text = new TextView(this);
            text.setText(content);
            text.setTextSize(14);
            text.setPadding(24, 24, 24, 24);
            text.setTextIsSelectable(true);

            ScrollView scroll = new ScrollView(this);
            scroll.addView(text);

            new AlertDialog.Builder(this)
                    .setTitle(file.getName())
                    .setView(scroll)
                    .setPositiveButton("Закрыть", null)
                    .show();

        } catch (IOException e) {
            Toast.makeText(this,
                    "Ошибка чтения: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
