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

public class ProjectActivity extends Activity {

    private File projectDirectory;
    private File currentDirectory;
    private ProjectFileManager fileManager;

    private LinearLayout fileContainer;
    private TextView pathText;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String projectName =
                getIntent().getStringExtra("project_name");

        if (projectName == null ||
                !projectName.matches(
                        "[a-zA-Z][a-zA-Z0-9_]{0,49}"
                )) {
            showError("Неверное имя проекта");
            finish();
            return;
        }

        projectDirectory = new File(
                new File(getFilesDir(), "projects"),
                projectName
        );

        try {
            fileManager = new ProjectFileManager(
                    projectDirectory
            );
            currentDirectory =
                    projectDirectory.getCanonicalFile();

        } catch (IOException e) {
            showError(e.getMessage());
            finish();
            return;
        }

        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 20, 16, 16);

        TextView title = new TextView(this);
        title.setText(
                "Проект: " + projectDirectory.getName()
        );
        title.setTextSize(22);
        root.addView(title);

        pathText = new TextView(this);
        pathText.setTextSize(13);
        pathText.setPadding(0, 12, 0, 12);
        root.addView(pathText);

        Button newFile = new Button(this);
        newFile.setText("+ Новый файл");
        root.addView(newFile);

        Button newFolder = new Button(this);
        newFolder.setText("+ Новая папка");
        root.addView(newFolder);

        Button up = new Button(this);
        up.setText("⬆ На уровень выше");
        root.addView(up);

        ScrollView scroll = new ScrollView(this);

        fileContainer = new LinearLayout(this);
        fileContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        scroll.addView(fileContainer);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);

        newFile.setOnClickListener(
                v -> showCreateDialog(false)
        );

        newFolder.setOnClickListener(
                v -> showCreateDialog(true)
        );

        up.setOnClickListener(v -> goUp());

        refreshFiles();
    }

    private void refreshFiles() {
        fileContainer.removeAllViews();

        try {
            String rootPath =
                    projectDirectory.getCanonicalPath();

            String currentPath =
                    currentDirectory.getCanonicalPath();

            if (!currentPath.equals(rootPath)
                    && !currentPath.startsWith(
                            rootPath + File.separator
                    )) {
                currentDirectory = projectDirectory;
                currentPath = rootPath;
            }

            String relative = currentPath.equals(rootPath)
                    ? "/"
                    : currentPath.substring(rootPath.length());

            pathText.setText("Папка: " + relative);

        } catch (IOException e) {
            showError(e.getMessage());
            return;
        }

        File[] files = currentDirectory.listFiles();

        if (files == null) {
            showError("Не удалось прочитать папку");
            return;
        }

        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName()
                    .compareToIgnoreCase(b.getName());
        });

        if (files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("Папка пуста");
            fileContainer.addView(empty);
        }

        for (File file : files) {
            Button button = new Button(this);

            button.setText(
                    (file.isDirectory() ? "📁 " : "📄 ")
                            + file.getName()
            );
            button.setAllCaps(false);

            fileContainer.addView(button);

            button.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    currentDirectory = file;
                    refreshFiles();
                } else {
                    openEditor(file);
                }
            });
        }
    }

    private void showCreateDialog(boolean folder) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(
                folder ? "Например: images"
                        : "Например: Test.java"
        );

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(32, 12, 32, 8);
        wrapper.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(
                        folder ? "Новая папка" : "Новый файл"
                )
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

                try {
                    if (folder) {
                        fileManager.createFolder(
                                currentDirectory, name
                        );
                    } else {
                        fileManager.createFile(
                                currentDirectory, name, ""
                        );
                    }

                    dialog.dismiss();
                    refreshFiles();

                    Toast.makeText(
                            this,
                            folder ? "Папка создана"
                                    : "Файл создан",
                            Toast.LENGTH_SHORT
                    ).show();

                } catch (IOException e) {
                    input.setError(e.getMessage());
                }
            });
        });

        dialog.show();
    }

    private void goUp() {
        try {
            if (currentDirectory.getCanonicalFile()
                    .equals(projectDirectory.getCanonicalFile())) {
                finish();
                return;
            }

            File parent = currentDirectory.getParentFile();

            if (parent != null) {
                currentDirectory = parent;
                refreshFiles();
            }

        } catch (IOException e) {
            showError(e.getMessage());
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

        } catch (IOException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        Toast.makeText(
                this, message, Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public void onBackPressed() {
        goUp();
    }
}
