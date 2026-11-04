
package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class ProjectActivity extends Activity
        implements ProjectScreen.Listener {

    private File projectDirectory;
    private File currentDirectory;
    private ProjectFileManager fileManager;
    private ProjectScreen projectScreen;

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
        projectScreen = new ProjectScreen(this, this);

        setContentView(
                projectScreen.create(
                        projectDirectory.getName()
                )
        );

        refreshFiles();
    }

    private void refreshFiles() {
        try {
            String rootPath =
                    projectDirectory.getCanonicalPath();

            String currentPath =
                    currentDirectory.getCanonicalPath();

            if (!currentPath.equals(rootPath)
                    && !currentPath.startsWith(
                            rootPath + File.separator
                    )) {
                currentDirectory =
                        projectDirectory.getCanonicalFile();

                currentPath = rootPath;
            }

            String relative = currentPath.equals(rootPath)
                    ? "/"
                    : currentPath.substring(rootPath.length());

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

            projectScreen.updatePath(
                    relative, files.length
            );

            projectScreen.showFiles(files);

        } catch (IOException e) {
            showError(e.getMessage());
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
        wrapper.setPadding(
                dp(24), dp(12), dp(24), dp(8)
        );

        wrapper.addView(input);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                folder
                                        ? "Новая папка"
                                        : "Новый файл"
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
                            folder
                                    ? "Папка создана"
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

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    private void goUp() {
        try {
            if (currentDirectory.getCanonicalFile()
                    .equals(
                            projectDirectory.getCanonicalFile()
                    )) {
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

            String filePath =
                    file.getCanonicalPath();

            if (!filePath.startsWith(
                    rootPath + File.separator
            )) {
                showError("Файл вне проекта");
                return;
            }

            String relativePath =
                    filePath.substring(
                            rootPath.length() + 1
                    );

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
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    public void onBack() {
        finish();
    }

    @Override
    public void onUp() {
        goUp();
    }

    @Override
    public void onNewFile() {
        showCreateDialog(false);
    }

    @Override
    public void onNewFolder() {
        showCreateDialog(true);
    }

    @Override
    public void onFileSelected(File file) {
        try {
            String rootPath =
                    projectDirectory.getCanonicalPath();

            String filePath =
                    file.getCanonicalPath();

            if (!filePath.startsWith(
                    rootPath + File.separator
            )) {
                showError("Объект вне проекта");
                return;
            }

            if (file.isDirectory()) {
                currentDirectory = file;
                refreshFiles();
            } else if (file.isFile()) {
                openEditor(file);
            } else {
                showError("Объект недоступен");
            }

        } catch (IOException e) {
            showError(e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        goUp();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (projectScreen != null) {
            refreshFiles();
        }
    }
}
