package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.Layout;
import android.text.InputType;
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
import java.util.Locale;

public class CodeEditorActivity extends Activity {

    private CodeEditText editor;
    private TextView title;
    private File currentFile;
    private String savedContent = "";

    private boolean insertingIndent = false;
    private boolean pendingIndent = false;
    private int indentPosition = -1;

    private final Runnable highlightTask = () -> {
        if (editor != null && currentFile != null) {
            CodeSyntaxHighlighter.highlight(
                    editor, currentFile.getName()
            );
        }
    };

    private int dp(float value) {
        return (int) (value *
                getResources().getDisplayMetrics().density
                + 0.5f);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String projectName = getIntent()
                .getStringExtra("project_name");

        String relativePath = getIntent()
                .getStringExtra("file_path");

        if (projectName == null ||
                !projectName.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                ) ||
                relativePath == null ||
                relativePath.isEmpty()) {
            showError("Неверные данные файла");
            return;
        }

        try {
            File projectsRoot = new File(
                    getFilesDir(), "projects"
            ).getCanonicalFile();

            File project = new File(
                    projectsRoot, projectName
            ).getCanonicalFile();

            if (!project.getParentFile()
                    .equals(projectsRoot)
                    || !project.isDirectory()) {
                throw new IOException("Проект не найден");
            }

            File file = new File(
                    project, relativePath
            ).getCanonicalFile();

            if (!file.getPath().startsWith(
                    project.getPath() + File.separator
            ) || !file.isFile()) {
                throw new IOException(
                        "Файл недоступен или вне проекта"
                );
            }

            if (isImageFile(file)) {
                Intent intent = new Intent(
                        this, ImageViewerActivity.class
                );

                intent.putExtra(
                        "project_name", projectName
                );
                intent.putExtra(
                        "file_path", relativePath
                );

                startActivity(intent);
                finish();
                return;
            }

            if (file.length() > 1024 * 1024) {
                throw new IOException(
                        "Файл больше 1 МБ"
                );
            }

            if (isBinaryFile(file)) {
                throw new IOException(
                        "Бинарный файл нельзя редактировать"
                );
            }

            currentFile = file;
            savedContent = FileUtils.readText(file);

            buildEditor();

        } catch (IOException e) {
            showError(e.getMessage());
        }
    }

    private boolean isImageFile(File file) {
        String name = file.getName()
                .toLowerCase(Locale.ROOT);

        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".webp");
    }

    private boolean isBinaryFile(File file)
            throws IOException {

        try (java.io.InputStream input =
                     Files.newInputStream(file.toPath())) {

            byte[] buffer = new byte[4096];
            int count = input.read(buffer);

            for (int i = 0; i < count; i++) {
                if (buffer[i] == 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private void buildEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F5F8);

        title = new TextView(this);
        title.setText(currentFile.getName());
        title.setTextSize(18);
        title.setTypeface(
                Typeface.DEFAULT, Typeface.BOLD
        );
        title.setTextColor(0xFF252535);
        title.setPadding(
                dp(16), dp(12), dp(16), dp(8)
        );
        title.setSingleLine(true);
        root.addView(title);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);

        Button back = new Button(this);
        back.setText("Назад");

        Button save = new Button(this);
        save.setText("Сохранить");

        toolbar.addView(
                back,
                new LinearLayout.LayoutParams(
                        0, dp(52), 1
                )
        );

        toolbar.addView(
                save,
                new LinearLayout.LayoutParams(
                        0, dp(52), 1
                )
        );

        root.addView(toolbar);

        TextView info = new TextView(this);
        info.setText(
                "CodeBox 5  •  Редактор кода"
        );
        info.setTextSize(12);
        info.setTextColor(0xFF777788);
        info.setPadding(
                dp(16), dp(6), dp(8), dp(6)
        );
        root.addView(info);

        ScrollView vertical = new ScrollView(this);
        vertical.setFillViewport(true);

        HorizontalScrollView horizontal =
                new HorizontalScrollView(this);
        horizontal.setFillViewport(true);

        editor = new CodeEditText();

        editor.setTextSize(14);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(0xFF202020);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setBackgroundColor(0xFFFFFFFF);
        editor.setHorizontallyScrolling(true);
        editor.setSingleLine(false);

        editor.setInputType(
                InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );

        editor.setPadding(
                dp(58), dp(16),
                dp(16), dp(16)
        );

        editor.setText(savedContent);
        editor.setSelection(0);

        horizontal.addView(
                editor,
                new HorizontalScrollView.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                )
        );

        vertical.addView(horizontal);

        root.addView(
                vertical,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        setContentView(root);

        back.setOnClickListener(
                v -> attemptExit()
        );

        save.setOnClickListener(
                v -> saveFile()
        );

        editor.addTextChangedListener(
                new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count) {

                if (!insertingIndent &&
                        count == 1 &&
                        start < s.length() &&
                        s.charAt(start) == '\n') {

                    pendingIndent = true;
                    indentPosition = start + 1;
                }
            }

            @Override
            public void afterTextChanged(
                    Editable s) {

                if (insertingIndent) {
                    return;
                }

                if (pendingIndent) {
                    pendingIndent = false;
                    applyAutoIndent(
                            s, indentPosition
                    );
                }

                updateTitle();
                editor.invalidate();

                editor.removeCallbacks(highlightTask);
                editor.postDelayed(
                        highlightTask, 250
                );
            }
        });

        CodeSyntaxHighlighter.highlight(
                editor, currentFile.getName()
        );
    }

    private void applyAutoIndent(
            Editable text, int position) {

        if (position <= 0 ||
                position > text.length()) {
            return;
        }

        int previousLineStart =
                position - 2;

        while (previousLineStart >= 0 &&
                text.charAt(previousLineStart) != '\n') {
            previousLineStart--;
        }

        previousLineStart++;

        StringBuilder indent = new StringBuilder();

        for (int i = previousLineStart;
                i < position - 1; i++) {

            char c = text.charAt(i);

            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }

        int last = position - 2;

        while (last >= previousLineStart &&
                Character.isWhitespace(
                        text.charAt(last))) {
            last--;
        }

        if (last >= previousLineStart &&
                text.charAt(last) == '{') {
            indent.append("    ");
        }

        if (indent.length() == 0) {
            return;
        }

        insertingIndent = true;

        try {
            text.insert(
                    position, indent.toString()
            );

            editor.setSelection(
                    Math.min(
                            position + indent.length(),
                            text.length()
                    )
            );
        } finally {
            insertingIndent = false;
        }
    }

    private boolean hasUnsavedChanges() {
        return editor != null &&
                !editor.getText().toString()
                        .equals(savedContent);
    }

    private void updateTitle() {
        if (title != null && currentFile != null) {
            title.setText(
                    currentFile.getName() +
                    (hasUnsavedChanges() ? " *" : "")
            );
        }
    }

    private void saveFile() {
        if (editor == null || currentFile == null) {
            return;
        }

        if (isImageFile(currentFile)) {
            Toast.makeText(
                    this,
                    "Нельзя сохранять изображение как текст",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String content =
                editor.getText().toString();

        Path target = currentFile.toPath();
        Path temp = null;

        try {
            if (isBinaryFile(currentFile)) {
                throw new IOException(
                        "Сохранение бинарного файла запрещено"
                );
            }

            temp = Files.createTempFile(
                    target.getParent(),
                    ".codebox-", ".tmp"
            );

            Files.write(
                    temp,
                    content.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );

            savedContent = content;
            updateTitle();

            Toast.makeText(
                    this,
                    "Файл сохранён",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (IOException e) {
            Toast.makeText(
                    this,
                    "Ошибка сохранения: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

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
                        "Сохранить",
                        (dialog, which) -> {
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
                .setMessage(
                        message == null
                                ? "Неизвестная ошибка"
                                : message
                )
                .setPositiveButton(
                        "Закрыть",
                        (dialog, which) -> finish()
                )
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (editor != null) {
            editor.removeCallbacks(highlightTask);
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        attemptExit();
    }

    // Поле редактирования с номерами строк.
    private class CodeEditText extends EditText {

        private final Paint linePaint = new Paint(
                Paint.ANTI_ALIAS_FLAG
        );

        CodeEditText() {
            super(CodeEditorActivity.this);

            linePaint.setColor(0xFF9999AA);
            linePaint.setTextSize(
                    dp(12)
            );
            linePaint.setTypeface(
                    Typeface.MONOSPACE
            );
            linePaint.setTextAlign(
                    Paint.Align.RIGHT
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            Layout layout = getLayout();

            if (layout == null) {
                return;
            }

            CharSequence content = getText();

            int number = 1;
            int length = content.length();

            drawNumber(canvas, layout, 0, number);

            for (int i = 0; i < length; i++) {
                if (content.charAt(i) == '\n') {
                    number++;
                    drawNumber(
                            canvas, layout,
                            i + 1, number
                    );
                }
            }
        }

        private void drawNumber(
                Canvas canvas,
                Layout layout,
                int offset,
                int number) {

            int line = layout.getLineForOffset(
                    Math.min(offset, getText().length())
            );

            float y = getTotalPaddingTop()
                    + layout.getLineBaseline(line);

            canvas.drawText(
                    String.valueOf(number),
                    getPaddingLeft() - dp(12),
                    y,
                    linePaint
            );
        }
    }
}
