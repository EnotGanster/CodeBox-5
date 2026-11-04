
package ru.codebox.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
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
                !projectName.matches(
                        "[a-zA-Z][a-zA-Z0-9_]{0,49}"
                )) {
            showError("Неверное имя проекта");
            return;
        }

        projectDirectory = new File(
                new File(getFilesDir(), "projects"),
                projectName
        );

        if (!projectDirectory.isDirectory()) {
            showError("Проект не найден");
            return;
        }

        showProject();
    }

    private void showProject() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 32, 24, 24);

        TextView title = new TextView(this);
        title.setText(
                "Проект: " + projectDirectory.getName()
        );
        title.setTextSize(24);
        root.addView(title);

        Button back = new Button(this);
        back.setText("Назад");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        ScrollView scroll = new ScrollView(this);

        fileContainer = new LinearLayout(this);
        fileContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        scroll.addView(fileContainer);

        root.addView(scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0, 1
                ));

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
            return a.getName()
                    .compareToIgnoreCase(b.getName());
        });

        for (File file : files) {
            Button button = new Button(this);

            String prefix = file.isDirectory()
                    ? "📁 " : "📄 ";

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
                button.setOnClickListener(
                        v -> openEditor(file)
                );
            }
        }
    }

    private void openEditor(File file) {
        try {
            String rootPath =
                    projectDirectory.getCanonicalPath();

            String filePath = file.getCanonicalPath();

            if (!filePath.startsWith(
                    rootPath + File.separator
            )) {
                showError("Файл вне проекта");
                return;
            }

            String relativePath =
                    filePath.substring(rootPath.length() + 1);

            Intent intent = new Intent(
                    this, CodeEditorActivity.class
            );

            intent.putExtra(
                    "project_name",
                    projectDirectory.getName()
            );

            intent.putExtra(
                    "file_path",
                    relativePath
            );

            startActivity(intent);

        } catch (Exception e) {
            showError("Ошибка: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Toast.makeText(
                this, message, Toast.LENGTH_LONG
        ).show();
    }
}
