package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class CodeEditorActivity extends Activity {

    private EditText editor;
    private TextView title;
    private File currentFile;
    private String savedContent = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String projectName =
                getIntent().getStringExtra("project_name");

        String relativePath =
                getIntent().getStringExtra("file_path");

        if (projectName == null ||
                !projectName.matches(
                        "[a-zA-Z][a-zA-Z0-9_]{0,49}"
                ) ||
                relativePath == null ||
                relativePath.isEmpty()) {

            showError("Неверные данные файла");
            return;
        }

        try {
            File projectsRoot = new File(
                    getFilesDir(), "projects"
            );

            File project = new File(
                    projectsRoot, projectName
            );

            File file = new File(project, relativePath);

            String projectPath = project.getCanonicalPath();
            String filePath = file.getCanonicalPath();

            if (!filePath.startsWith(
                    projectPath + File.separator
            )) {
                throw new IOException(
                        "Файл находится вне проекта"
                );
            }

            if (!file.isFile()) {
                throw new IOException(
                        "Файл не найден"
                );
            }

            if (file.length() > 1024 * 1024) {
                throw new IOException(
                        "Файл слишком большой для редактора"
                );
            }

            currentFile = file;
            savedContent = FileUtils.readText(file);

            buildEditor();

        } catch (IOException e) {
            showError(e.getMessage());
        }
    }

    private void buildEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        title = new TextView(this);
        title.setText(currentFile.getName());
        title.setTextSize(20);
        title.setSingleLine(true);
        root.addView(title);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);

        Button back = new Button(this);
        back.setText("Назад");
        toolbar.addView(back, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        ));

        Button save = new Button(this);
        save.setText("Сохранить");
        toolbar.addView(save, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        ));

        root.addView(toolbar);

        ScrollView vertical = new ScrollView(this);
        vertical.setFillViewport(true);

        HorizontalScrollView horizontal =
                new HorizontalScrollView(this);
        horizontal.setFillViewport(true);

        editor = new EditText(this);
        editor.setTextSize(14);
        editor.setTypeface(
                android.graphics.Typeface.MONOSPACE
        );
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(16, 16, 16, 16);
        editor.setBackgroundColor(0xFFF7F7F7);
        editor.setTextColor(0xFF202020);
        editor.setHorizontallyScrolling(true);
        editor.setSingleLine(false);
        editor.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );

        editor.setText(savedContent);
        editor.setSelection(0);

        horizontal.addView(editor,
                new HorizontalScrollView.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                ));

        vertical.addView(horizontal);

        root.addView(vertical,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0, 1
                ));

        setContentView(root);

        back.setOnClickListener(v -> attemptExit());
        save.setOnClickListener(v -> saveFile());

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start,
                    int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start,
                    int before, int count) {
                updateTitle();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private boolean hasUnsavedChanges() {
        return editor != null &&
                !editor.getText().toString()
                        .equals(savedContent);
    }

    private void updateTitle() {
        if (title != null) {
            title.setText(
                    currentFile.getName() +
                            (hasUnsavedChanges() ? " *" : "")
            );
        }
    }

    private void saveFile() {
        String content = editor.getText().toString();
        Path target = currentFile.toPath();
        Path temp = null;

        try {
            temp = Files.createTempFile(
                    target.getParent(),
                    ".codebox-", ".tmp"
            );

            Files.write(
                    temp,
                    content.getBytes(StandardCharsets.UTF_8)
            );

            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );

            savedContent = content;
            updateTitle();

            Toast.makeText(this,
                    "Файл сохранён",
                    Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Toast.makeText(this,
                    "Ошибка сохранения: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();

        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void attemptExit() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Несохранённые изменения")
                .setMessage(
                        "Сохранить изменения перед выходом?"
                )
                .setPositiveButton(
                        "Сохранить", (dialog, which) -> {
                            saveFile();
                            if (!hasUnsavedChanges()) {
                                finish();
                            }
                        }
                )
                .setNegativeButton(
                        "Не сохранять",
                        (dialog, which) -> finish()
                )
                .setNeutralButton("Отмена", null)
                .show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Ошибка редактора")
                .setMessage(message)
                .setPositiveButton(
                        "Закрыть",
                        (dialog, which) -> finish()
                )
                .setCancelable(false)
                .show();
    }

    @Override
    public void onBackPressed() {
        attemptExit();
    }
}
