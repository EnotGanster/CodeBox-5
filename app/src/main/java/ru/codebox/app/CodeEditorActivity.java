package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.Layout;
import android.text.InputType;
import android.text.TextUtils;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CodeEditorActivity extends Activity {

    private static final int EDITOR_BG = 0xFF171923;
    private static final int EDITOR_TEXT = 0xFFE5E7EB;
    private static final int EDITOR_MUTED = 0xFF9298AB;
    private static final int PANEL_BG = 0xFF242635;
    private static final int PURPLE = 0xFF7650E8;

    private CodeEditText editor;
    private TextView title;
    private TextView statusText;
    private File currentFile;
    private String savedContent = "";
    private String currentProjectName = "";

    private EditorHistoryManager historyManager;
    private Button undoButton;
    private Button redoButton;

    private boolean internalEdit = false;
    private boolean pendingIndent = false;
    private int indentPosition = -1;

    private char pendingPair = 0;
    private int pairPosition = -1;

    private boolean pendingSkip = false;
    private int skipPosition = -1;

    private final Runnable highlightTask = () -> {
        if (editor != null && currentFile != null) {
            CodeSyntaxHighlighter.highlight(
                    editor, currentFile.getName()
            );
        }
    };

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    private GradientDrawable rounded(
            int color, int radius) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable headerGradient() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        0xFF8657F5,
                        0xFF6535D5,
                        0xFF4522A3
                }
        );
    }

    private TextView text(
            String value, int size, boolean bold) {

        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(
                CodeBoxTheme.textColor(this)
        );

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT, Typeface.BOLD
            );
        }

        return view;
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

        currentProjectName = projectName;

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
        root.setBackgroundColor(
                CodeBoxTheme.background(this)
        );

        createHeader(root);
        createActions(root);
        createSnippetButton(root);
        createEditorArea(root);
        createBottomToolbar(root);

        setContentView(root);

        historyManager = new EditorHistoryManager(
                editor
        );

        editor.addTextChangedListener(
                new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after) {

                pendingIndent = false;
                pendingPair = 0;
                pendingSkip = false;
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count) {

                if (internalEdit ||
                        before != 0 ||
                        count != 1 ||
                        start >= s.length()) {
                    return;
                }

                char typed = s.charAt(start);

                if (typed == '\n') {
                    pendingIndent = true;
                    indentPosition = start + 1;
                    return;
                }

                char closing = matchingClose(typed);

                if (closing == 0) {
                    return;
                }

                if (editor.getSelectionStart()
                        != start + 1) {
                    return;
                }

                if (isClosingCharacter(typed)
                        && start + 1 < s.length()
                        && s.charAt(start + 1) == typed) {

                    pendingSkip = true;
                    skipPosition = start;
                    return;
                }

                if (isOpeningCharacter(typed)) {
                    pendingPair = closing;
                    pairPosition = start + 1;
                }
            }

            @Override
            public void afterTextChanged(
                    Editable s) {

                if (internalEdit) {
                    return;
                }

                if (pendingSkip) {
                    pendingSkip = false;
                    skipExistingClosing(
                            s, skipPosition
                    );

                } else if (pendingPair != 0) {
                    char closing = pendingPair;
                    int position = pairPosition;
                    pendingPair = 0;

                    insertClosingPair(
                            s, position, closing
                    );

                } else if (pendingIndent) {
                    pendingIndent = false;
                    applyAutoIndent(
                            s, indentPosition
                    );
                }

                updateTitle();
                editor.invalidate();
                scheduleHighlight();

                editor.post(
                        () -> updateHistoryButtons()
                );
            }
        });

        CodeSyntaxHighlighter.highlight(
                editor, currentFile.getName()
        );

        updateTitle();
        updateHistoryButtons();
    }

    private void createHeader(LinearLayout root) {

        LinearLayout header =
                new LinearLayout(this);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(Gravity.CENTER_VERTICAL);

        header.setPadding(
                dp(14), dp(14),
                dp(14), dp(14)
        );

        header.setBackground(headerGradient());

        TextView back = text("‹", 32, true);
        back.setTextColor(0xFFFFFFFF);
        back.setGravity(Gravity.CENTER);

        back.setBackground(
                rounded(0x33FFFFFF, 15)
        );

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(44), dp(44)
                )
        );

        back.setOnClickListener(
                v -> attemptExit()
        );

        LinearLayout labels =
                new LinearLayout(this);

        labels.setOrientation(
                LinearLayout.VERTICAL
        );

        labels.setPadding(
                dp(12), 0, dp(8), 0
        );

        title = text(
                currentFile.getName(), 18, true
        );

        title.setTextColor(0xFFFFFFFF);
        title.setSingleLine(true);
        title.setEllipsize(
                TextUtils.TruncateAt.END
        );

        labels.addView(title);

        TextView subtitle = text(
                currentProjectName + "  •  Редактор",
                11,
                false
        );

        subtitle.setTextColor(0xFFE8DDFF);

        labels.addView(subtitle);

        header.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView icon = text("</>", 17, true);
        icon.setTextColor(0xFFFFFFFF);
        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                rounded(0x33FFFFFF, 15)
        );

        header.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(44), dp(44)
                )
        );

        root.addView(header);
    }

    private void createActions(LinearLayout root) {

        LinearLayout toolbar =
                new LinearLayout(this);

        toolbar.setOrientation(
                LinearLayout.HORIZONTAL
        );

        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        toolbar.setPadding(
                dp(8), dp(10),
                dp(8), dp(8)
        );

        toolbar.setBackgroundColor(
                CodeBoxTheme.surface(this)
        );

        undoButton = new Button(this);
        undoButton.setText("↶");
        undoButton.setTextSize(21);
        undoButton.setAllCaps(false);
        undoButton.setContentDescription(
                "Отменить изменение"
        );

        redoButton = new Button(this);
        redoButton.setText("↷");
        redoButton.setTextSize(21);
        redoButton.setAllCaps(false);
        redoButton.setContentDescription(
                "Повторить изменение"
        );

        Button save = new Button(this);
        save.setText("✓ Сохранить");
        save.setTextSize(13);
        save.setAllCaps(false);

        toolbar.addView(
                undoButton,
                new LinearLayout.LayoutParams(
                        0, dp(48), 1
                )
        );

        toolbar.addView(
                redoButton,
                new LinearLayout.LayoutParams(
                        0, dp(48), 1
                )
        );

        toolbar.addView(
                save,
                new LinearLayout.LayoutParams(
                        0, dp(48), 2
                )
        );

        root.addView(toolbar);

        undoButton.setOnClickListener(v -> {
            if (historyManager != null) {
                historyManager.undo();
                updateTitle();
                scheduleHighlight();
                updateHistoryButtons();
            }
        });

        redoButton.setOnClickListener(v -> {
            if (historyManager != null) {
                historyManager.redo();
                updateTitle();
                scheduleHighlight();
                updateHistoryButtons();
            }
        });

        save.setOnClickListener(
                v -> saveFile()
        );
    }

    private void createSnippetButton(
            LinearLayout root) {

        LinearLayout wrapper =
                new LinearLayout(this);

        wrapper.setPadding(
                dp(12), dp(8),
                dp(12), dp(8)
        );

        TextView snippets = text(
                "📚  Шаблоны Java / XML",
                14,
                true
        );

        snippets.setGravity(Gravity.CENTER);

        CodeBoxTheme.styleColoredButton(
                snippets,
                CodeBoxTheme.PURPLE
        );

        wrapper.addView(
                snippets,
                new LinearLayout.LayoutParams(
                        -1, dp(46)
                )
        );

        root.addView(wrapper);

        snippets.setOnClickListener(
                v -> showSnippetPicker()
        );
    }

    private void createEditorArea(
            LinearLayout root) {

        LinearLayout editorPanel =
                new LinearLayout(this);

        editorPanel.setOrientation(
                LinearLayout.VERTICAL
        );

        editorPanel.setBackgroundColor(EDITOR_BG);

        LinearLayout statusBar =
                new LinearLayout(this);

        statusBar.setOrientation(
                LinearLayout.HORIZONTAL
        );

        statusBar.setGravity(Gravity.CENTER_VERTICAL);

        statusBar.setPadding(
                dp(14), dp(8),
                dp(14), dp(8)
        );

        statusBar.setBackgroundColor(PANEL_BG);

        TextView language = text(
                getLanguageName(), 11, true
        );

        language.setTextColor(
                0xFFB9A5FF
        );

        statusBar.addView(
                language,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        statusText = text(
                "● Сохранено", 11, false
        );

        statusText.setTextColor(
                0xFF71D7A4
        );

        statusBar.addView(statusText);

        editorPanel.addView(statusBar);

        ScrollView vertical =
                new ScrollView(this);

        vertical.setFillViewport(true);
        vertical.setVerticalScrollBarEnabled(false);

        HorizontalScrollView horizontal =
                new HorizontalScrollView(this);

        horizontal.setFillViewport(true);
        horizontal.setHorizontalScrollBarEnabled(false);

        editor = new CodeEditText();

        editor.setTextSize(14);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(EDITOR_TEXT);
        editor.setHintTextColor(EDITOR_MUTED);

        editor.setGravity(
                Gravity.TOP | Gravity.START
        );

        editor.setBackgroundColor(EDITOR_BG);
        editor.setHorizontallyScrolling(true);
        editor.setSingleLine(false);

        editor.setInputType(
                InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );

        editor.setPadding(
                dp(58), dp(16),
                dp(20), dp(20)
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

        editorPanel.addView(
                vertical,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        root.addView(
                editorPanel,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );
    }

    private String getLanguageName() {
        if (currentFile == null) {
            return "TEXT";
        }

        String name = currentFile.getName()
                .toLowerCase(Locale.ROOT);

        if (name.endsWith(".java")) {
            return "JAVA";
        }
        if (name.endsWith(".xml")) {
            return "XML";
        }
        if (name.endsWith(".gradle")
                || name.endsWith(".gradle.kts")) {
            return "GRADLE";
        }
        if (name.endsWith(".json")) {
            return "JSON";
        }
        return "TEXT";
    }

    private void createBottomToolbar(
            LinearLayout root) {

        EditorToolbar editorToolbar =
                new EditorToolbar(this, editor);

        root.addView(
                editorToolbar,
                new LinearLayout.LayoutParams(
                        -1, dp(52)
                )
        );
    }

    // Выбор сохранённого шаблона.
    private void showSnippetPicker() {
        if (editor == null || currentFile == null) {
            return;
        }

        String filename = currentFile.getName()
                .toLowerCase(Locale.ROOT);

        String language;

        if (filename.endsWith(".java")) {
            language = "java";
        } else if (filename.endsWith(".xml")) {
            language = "xml";
        } else {
            Toast.makeText(
                    this,
                    "Шаблоны доступны для Java и XML",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        try {
            CodeLibraryManager library =
                    new CodeLibraryManager(this);

            List<CodeLibraryManager.Snippet> all =
                    library.getSnippets(
                            currentProjectName
                    );

            List<CodeLibraryManager.Snippet> matches =
                    new ArrayList<>();

            for (CodeLibraryManager.Snippet snippet : all) {
                if (language.equals(snippet.language)) {
                    matches.add(snippet);
                }
            }

            if (matches.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("📚 Шаблоны")
                        .setMessage(
                                "Нет сохранённых шаблонов "
                                        + language.toUpperCase(Locale.ROOT)
                                        + ".\n\nДобавь шаблон "
                                        + "в библиотеке кода."
                        )
                        .setPositiveButton(
                                "Открыть библиотеку",
                                (dialog, which) -> {
                                    Intent intent = new Intent(
                                            this,
                                            CodeLibraryActivity.class
                                    );
                                    intent.putExtra(
                                            "project_name",
                                            currentProjectName
                                    );
                                    startActivity(intent);
                                }
                        )
                        .setNegativeButton("Отмена", null)
                        .show();
                return;
            }

            String[] names = new String[matches.size()];

            for (int i = 0; i < matches.size(); i++) {
                CodeLibraryManager.Snippet snippet =
                        matches.get(i);

                names[i] = snippet.title
                        + (snippet.projectName.isEmpty()
                        ? "  • Общий"
                        : "  • Проект");
            }

            new AlertDialog.Builder(this)
                    .setTitle("📚 Вставить шаблон")
                    .setItems(
                            names,
                            (dialog, which) ->
                                    confirmSnippetInsertion(
                                            matches.get(which)
                                    )
                    )
                    .setNegativeButton("Отмена", null)
                    .show();

        } catch (IOException e) {
            Toast.makeText(
                    this,
                    "Ошибка библиотеки: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void confirmSnippetInsertion(
            CodeLibraryManager.Snippet snippet) {

        String preview = snippet.code;

        if (preview.length() > 700) {
            preview = preview.substring(0, 700)
                    + "\n...";
        }

        new AlertDialog.Builder(this)
                .setTitle(snippet.title)
                .setMessage(preview)
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Вставить",
                        (dialog, which) ->
                                insertSnippet(snippet.code)
                )
                .show();
    }

    private void insertSnippet(String code) {
        if (editor == null || code == null) {
            return;
        }

        Editable content = editor.getText();

        if (content == null) {
            return;
        }

        int start = Math.max(
                0,
                Math.min(
                        editor.getSelectionStart(),
                        content.length()
                )
        );

        int end = Math.max(
                0,
                Math.min(
                        editor.getSelectionEnd(),
                        content.length()
                )
        );

        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }

        internalEdit = true;

        try {
            content.replace(start, end, code);

            editor.setSelection(
                    Math.min(
                            start + code.length(),
                            content.length()
                    )
            );
        } finally {
            internalEdit = false;
        }

        updateTitle();
        editor.invalidate();
        scheduleHighlight();

        editor.post(
                () -> updateHistoryButtons()
        );

        Toast.makeText(
                this,
                "Шаблон вставлен",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void scheduleHighlight() {
        if (editor == null) {
            return;
        }

        editor.removeCallbacks(highlightTask);
        editor.postDelayed(
                highlightTask, 250
        );
    }

    private void updateHistoryButtons() {
        if (historyManager == null) {
            return;
        }

        if (undoButton != null) {
            undoButton.setEnabled(
                    historyManager.canUndo()
            );
        }

        if (redoButton != null) {
            redoButton.setEnabled(
                    historyManager.canRedo()
            );
        }
    }

    private char matchingClose(char c) {
        switch (c) {
            case '(':
                return ')';
            case '[':
                return ']';
            case '{':
                return '}';
            case '"':
                return '"';
            case '\'':
                return '\'';
            default:
                return 0;
        }
    }

    private boolean isOpeningCharacter(char c) {
        return c == '(' || c == '[' || c == '{'
                || c == '"' || c == '\'';
    }

    private boolean isClosingCharacter(char c) {
        return c == ')' || c == ']' || c == '}'
                || c == '"' || c == '\'';
    }

    private void insertClosingPair(
            Editable text,
            int position,
            char closing) {

        if (position < 0 ||
                position > text.length()) {
            return;
        }

        if (position < text.length()
                && text.charAt(position) == closing) {
            return;
        }

        internalEdit = true;

        try {
            text.insert(
                    position,
                    String.valueOf(closing)
            );

            editor.setSelection(position);

        } finally {
            internalEdit = false;
        }
    }

    private void skipExistingClosing(
            Editable text,
            int position) {

        if (position < 0 ||
                position + 1 >= text.length()) {
            return;
        }

        internalEdit = true;

        try {
            text.delete(position, position + 1);

            editor.setSelection(
                    Math.min(
                            position + 1,
                            text.length()
                    )
            );

        } finally {
            internalEdit = false;
        }
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

        internalEdit = true;

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
            internalEdit = false;
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

        if (statusText != null) {
            boolean modified = hasUnsavedChanges();

            statusText.setText(
                    modified
                            ? "● Изменён"
                            : "● Сохранено"
            );

            statusText.setTextColor(
                    modified
                            ? 0xFFFFC978
                            : 0xFF71D7A4
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

    private class CodeEditText extends EditText {

        private final Paint linePaint = new Paint(
                Paint.ANTI_ALIAS_FLAG
        );

        CodeEditText() {
            super(CodeEditorActivity.this);

            linePaint.setColor(EDITOR_MUTED);
            linePaint.setTextSize(dp(12));
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
                    Math.min(
                            offset,
                            getText().length()
                    )
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
