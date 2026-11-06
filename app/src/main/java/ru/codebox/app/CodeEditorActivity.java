package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
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

/** CodeBox 5 editor: Java/XML editing, search, navigation and basic completion. */
public class CodeEditorActivity extends Activity {
    private static final int EDITOR_BG = 0xFF171923;
    private static final int EDITOR_TEXT = 0xFFE5E7EB;
    private static final int EDITOR_MUTED = 0xFF9298AB;
    private static final int PANEL_BG = 0xFF242635;
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final String[] JAVA_WORDS = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "if", "implements", "import", "instanceof", "int", "interface",
            "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "true", "false", "null", "String",
            "Object", "Override", "Activity", "Bundle", "Intent", "View",
            "TextView", "EditText", "Button", "LinearLayout", "Toast",
            "ArrayList", "List", "File", "IOException", "System", "Log"
    };

    private CodeEditText editor;
    private TextView title, statusText, undoButton, redoButton;
    private File currentFile;
    private String savedContent = "";
    private String currentProjectName = "";
    private EditorHistoryManager historyManager;
    private boolean internalEdit;
    private boolean pendingIndent;
    private int indentPosition;
    private char pendingPair;
    private int pairPosition;
    private boolean pendingSkip;
    private int skipPosition;

    private final Runnable highlightTask = () -> {
        if (editor != null && currentFile != null) {
            CodeSyntaxHighlighter.highlight(editor, currentFile.getName());
        }
    };

    private int dp(float value) { return CodeBoxTheme.dp(this, value); }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(CodeBoxTheme.textColor(this));
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String projectName = getIntent().getStringExtra("project_name");
        String relativePath = getIntent().getStringExtra("file_path");
        if (projectName == null || !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")
                || relativePath == null || relativePath.isEmpty()) {
            showError("Неверные данные файла");
            return;
        }
        currentProjectName = projectName;
        try {
            File root = new File(getFilesDir(), "projects").getCanonicalFile();
            File project = new File(root, projectName).getCanonicalFile();
            if (!root.equals(project.getParentFile()) || !project.isDirectory()) {
                throw new IOException("Проект не найден");
            }
            File file = new File(project, relativePath).getCanonicalFile();
            if (!file.getPath().startsWith(project.getPath() + File.separator) || !file.isFile()) {
                throw new IOException("Файл недоступен или вне проекта");
            }
            if (isImageFile(file)) {
                Intent intent = new Intent(this, ImageViewerActivity.class);
                intent.putExtra("project_name", projectName);
                intent.putExtra("file_path", relativePath);
                startActivity(intent);
                finish();
                return;
            }
            if (file.length() > MAX_FILE_BYTES) throw new IOException("Файл больше 1 МБ");
            if (isBinaryFile(file)) throw new IOException("Бинарный файл нельзя редактировать");
            currentFile = file;
            savedContent = FileUtils.readText(file);
            buildEditor();
        } catch (IOException e) { showError(e.getMessage()); }
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    private boolean isBinaryFile(File file) throws IOException {
        try (java.io.InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[4096];
            int count = input.read(buffer);
            for (int i = 0; i < count; i++) if (buffer[i] == 0) return true;
        }
        return false;
    }

    private void buildEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CodeBoxTheme.background(this));
        createHeader(root);
        createActions(root);
        createSnippetButton(root);
        createEditorArea(root);
        createBottomToolbar(root);
        setContentView(root);
        historyManager = new EditorHistoryManager(editor);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                pendingIndent = false;
                pendingPair = 0;
                pendingSkip = false;
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (internalEdit || before != 0 || count != 1 || start >= s.length()) return;
                char typed = s.charAt(start);
                if (typed == '\n') {
                    pendingIndent = true;
                    indentPosition = start + 1;
                    return;
                }
                if (isClosingCharacter(typed) && start + 1 < s.length()
                        && s.charAt(start + 1) == typed) {
                    pendingSkip = true;
                    skipPosition = start;
                    return;
                }
                char closing = matchingClose(typed);
                if (closing != 0 && isOpeningCharacter(typed)) {
                    pendingPair = closing;
                    pairPosition = start + 1;
                }
            }
            @Override public void afterTextChanged(Editable s) {
                if (internalEdit) return;
                if (pendingSkip) {
                    pendingSkip = false;
                    skipExistingClosing(s, skipPosition);
                } else if (pendingPair != 0) {
                    char close = pendingPair;
                    int position = pairPosition;
                    pendingPair = 0;
                    insertClosingPair(s, position, close);
                } else if (pendingIndent) {
                    pendingIndent = false;
                    applyAutoIndent(s, indentPosition);
                }
                updateTitle();
                editor.invalidate();
                scheduleHighlight();
                editor.post(() -> updateHistoryButtons());
            }
        });
        CodeSyntaxHighlighter.highlight(editor, currentFile.getName());
        updateTitle();
        updateHistoryButtons();
    }

    private void createHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(9), dp(10), dp(9));
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF8657F5, 0xFF6535D5, 0xFF4522A3}));
        TextView home = headerButton("⌂", "На главный экран");
        header.addView(home, new LinearLayout.LayoutParams(dp(38), dp(40)));
        home.setOnClickListener(v -> attemptExit(true));
        TextView back = headerButton("‹", "Назад к файлам");
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(38), dp(40));
        backParams.leftMargin = dp(5);
        header.addView(back, backParams);
        back.setOnClickListener(v -> attemptExit(false));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(9), 0, dp(5), 0);
        title = text(currentFile.getName(), 16, true);
        title.setTextColor(0xFFFFFFFF);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        labels.addView(title);
        TextView subtitle = text(currentProjectName + " • Редактор", 10, false);
        subtitle.setTextColor(0xFFE8DDFF);
        subtitle.setSingleLine(true);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView icon = headerButton("</>", "Редактор кода");
        header.addView(icon, new LinearLayout.LayoutParams(dp(39), dp(40)));
        root.addView(header);
    }

    private TextView headerButton(String label, String description) {
        TextView view = text(label, 20, true);
        view.setTextColor(0xFFFFFFFF);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setBackground(rounded(0x33FFFFFF, 11));
        return view;
    }

    private void createActions(LinearLayout root) {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(7), dp(6), dp(7), dp(6));
        toolbar.setBackgroundColor(CodeBoxTheme.surface(this));
        undoButton = action("↶", toolbar, 1);
        redoButton = action("↷", toolbar, 1);
        TextView search = action("⌕", toolbar, 1);
        search.setContentDescription("Поиск по файлу");
        TextView go = action("№", toolbar, 1);
        go.setContentDescription("Перейти к строке");
        TextView complete = action("Java+", toolbar, 2);
        complete.setContentDescription("Автодополнение Java");
        TextView save = action("✓ Сохранить", toolbar, 3);
        CodeBoxTheme.styleColoredButton(save, CodeBoxTheme.PURPLE);
        root.addView(toolbar);
        undoButton.setOnClickListener(v -> {
            if (historyManager != null) { historyManager.undo(); updateTitle(); scheduleHighlight(); updateHistoryButtons(); }
        });
        redoButton.setOnClickListener(v -> {
            if (historyManager != null) { historyManager.redo(); updateTitle(); scheduleHighlight(); updateHistoryButtons(); }
        });
        search.setOnClickListener(v -> showSearchDialog());
        go.setOnClickListener(v -> showGoToLineDialog());
        complete.setOnClickListener(v -> showCompletionDialog());
        save.setOnClickListener(v -> saveFile());
    }

    private TextView action(String name, LinearLayout toolbar, int weight) {
        TextView view = text(name, 12, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setBackground(rounded(CodeBoxTheme.background(this), 9));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), weight);
        p.rightMargin = dp(4);
        toolbar.addView(view, p);
        return view;
    }

    private void createSnippetButton(LinearLayout root) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(10), dp(5), dp(10), dp(5));
        TextView snippets = text("📚 Шаблоны Java / XML", 12, true);
        snippets.setGravity(Gravity.CENTER);
        CodeBoxTheme.styleColoredButton(snippets, CodeBoxTheme.PURPLE);
        wrapper.addView(snippets, new LinearLayout.LayoutParams(-1, dp(36)));
        root.addView(wrapper);
        snippets.setOnClickListener(v -> showSnippetPicker());
    }

    private void createEditorArea(LinearLayout root) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(EDITOR_BG);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(6), dp(12), dp(6));
        bar.setBackgroundColor(PANEL_BG);
        TextView language = text(getLanguageName(), 10, true);
        language.setTextColor(0xFFB9A5FF);
        bar.addView(language, new LinearLayout.LayoutParams(0, -2, 1));
        statusText = text("● Сохранено", 10, false);
        statusText.setTextColor(0xFF71D7A4);
        bar.addView(statusText);
        panel.addView(bar);
        ScrollView vertical = new ScrollView(this);
        vertical.setFillViewport(true);
        vertical.setVerticalScrollBarEnabled(false);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(true);
        horizontal.setHorizontalScrollBarEnabled(false);
        editor = new CodeEditText();
        editor.setTextSize(14);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(EDITOR_TEXT);
        editor.setHintTextColor(EDITOR_MUTED);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setBackgroundColor(EDITOR_BG);
        editor.setHorizontallyScrolling(true);
        editor.setSingleLine(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setPadding(dp(58), dp(16), dp(20), dp(20));
        editor.setText(savedContent);
        editor.setSelection(0);
        horizontal.addView(editor, new HorizontalScrollView.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        vertical.addView(horizontal);
        panel.addView(vertical, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private String getLanguageName() {
        String name = currentFile.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) return "JAVA";
        if (name.endsWith(".xml")) return "XML";
        if (name.endsWith(".gradle") || name.endsWith(".gradle.kts")) return "GRADLE";
        if (name.endsWith(".json")) return "JSON";
        return "TEXT";
    }

    private void createBottomToolbar(LinearLayout root) {
        root.addView(new EditorToolbar(this, editor), new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void showSearchDialog() {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Текст для поиска");
        query.setPadding(dp(16), dp(8), dp(16), dp(8));
        new AlertDialog.Builder(this).setTitle("Найти в файле")
                .setView(query)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Найти далее", (d, w) -> findNext(query.getText().toString()))
                .show();
    }

    private void findNext(String needle) {
        if (needle.isEmpty()) { toast("Введите текст для поиска"); return; }
        String content = editor.getText().toString();
        int start = Math.max(0, editor.getSelectionEnd());
        int found = content.indexOf(needle, start);
        if (found < 0) found = content.indexOf(needle);
        if (found < 0) { toast("Совпадений нет"); return; }
        editor.requestFocus();
        editor.setSelection(found, found + needle.length());
        toast("Найдено: строка " + lineForOffset(content, found));
    }

    private int lineForOffset(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    private void showGoToLineDialog() {
        EditText number = new EditText(this);
        number.setInputType(InputType.TYPE_CLASS_NUMBER);
        number.setHint("Номер строки");
        number.setPadding(dp(16), dp(8), dp(16), dp(8));
        new AlertDialog.Builder(this).setTitle("Перейти к строке")
                .setView(number).setNegativeButton("Отмена", null)
                .setPositiveButton("Перейти", (d, w) -> {
                    try { goToLine(Integer.parseInt(number.getText().toString())); }
                    catch (NumberFormatException e) { toast("Неверный номер строки"); }
                }).show();
    }

    private void goToLine(int requested) {
        if (requested < 1) { toast("Номер строки должен быть положительным"); return; }
        String content = editor.getText().toString();
        int line = 1, offset = 0;
        while (line < requested && offset < content.length()) {
            if (content.charAt(offset) == '\n') line++;
            offset++;
        }
        if (line != requested) { toast("Такой строки нет"); return; }
        editor.requestFocus();
        editor.setSelection(offset);
    }

    private void showCompletionDialog() {
        if (!"JAVA".equals(getLanguageName())) { toast("Автодополнение доступно для Java"); return; }
        Editable content = editor.getText();
        int cursor = Math.max(0, editor.getSelectionStart());
        int start = cursor;
        while (start > 0 && Character.isJavaIdentifierPart(content.charAt(start - 1))) start--;
        String prefix = content.subSequence(start, cursor).toString();
        List<String> matches = new ArrayList<>();
        for (String word : JAVA_WORDS) {
            if (word.startsWith(prefix) && !word.equals(prefix)) matches.add(word);
        }
        if (matches.isEmpty()) { toast("Нет подходящих подсказок"); return; }
        final int replaceStart = start;
        final int replaceEnd = cursor;
        new AlertDialog.Builder(this).setTitle("Java: дополнить " + prefix)
                .setItems(matches.toArray(new String[0]), (d, which) -> {
                    Editable latest = editor.getText();
                    if (replaceStart > latest.length() || replaceEnd > latest.length()) return;
                    latest.replace(replaceStart, replaceEnd, matches.get(which));
                    editor.setSelection(Math.min(latest.length(), replaceStart + matches.get(which).length()));
                    editor.requestFocus();
                }).setNegativeButton("Отмена", null).show();
    }

    private void showSnippetPicker() {
        if (editor == null || currentFile == null) return;
        String filename = currentFile.getName().toLowerCase(Locale.ROOT);
        String language;
        if (filename.endsWith(".java")) language = "java";
        else if (filename.endsWith(".xml")) language = "xml";
        else { toast("Шаблоны доступны для Java и XML"); return; }
        try {
            CodeLibraryManager library = new CodeLibraryManager(this);
            List<CodeLibraryManager.Snippet> all = library.getSnippets(currentProjectName);
            List<CodeLibraryManager.Snippet> matches = new ArrayList<>();
            for (CodeLibraryManager.Snippet snippet : all) {
                if (language.equals(snippet.language)) matches.add(snippet);
            }
            if (matches.isEmpty()) {
                new AlertDialog.Builder(this).setTitle("📚 Шаблоны")
                        .setMessage("Нет сохранённых шаблонов " + language.toUpperCase(Locale.ROOT)
                                + ".\n\nДобавь шаблон в библиотеке кода.")
                        .setPositiveButton("Открыть библиотеку", (d, w) -> {
                            Intent intent = new Intent(this, CodeLibraryActivity.class);
                            intent.putExtra("project_name", currentProjectName);
                            startActivity(intent);
                        }).setNegativeButton("Отмена", null).show();
                return;
            }
            String[] names = new String[matches.size()];
            for (int i = 0; i < matches.size(); i++) {
                CodeLibraryManager.Snippet snippet = matches.get(i);
                names[i] = snippet.title + (snippet.projectName.isEmpty() ? " • Общий" : " • Проект");
            }
            new AlertDialog.Builder(this).setTitle("📚 Вставить шаблон")
                    .setItems(names, (d, which) -> confirmSnippetInsertion(matches.get(which)))
                    .setNegativeButton("Отмена", null).show();
        } catch (IOException e) { toast("Ошибка библиотеки: " + e.getMessage()); }
    }

    private void confirmSnippetInsertion(CodeLibraryManager.Snippet snippet) {
        String preview = snippet.code;
        if (preview.length() > 700) preview = preview.substring(0, 700) + "\n...";
        new AlertDialog.Builder(this).setTitle(snippet.title).setMessage(preview)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Вставить", (d, w) -> insertSnippet(snippet.code)).show();
    }

    private void insertSnippet(String code) {
        if (editor == null || code == null) return;
        Editable content = editor.getText();
        if (content == null) return;
        int start = Math.max(0, Math.min(editor.getSelectionStart(), content.length()));
        int end = Math.max(0, Math.min(editor.getSelectionEnd(), content.length()));
        if (start > end) { int t = start; start = end; end = t; }
        internalEdit = true;
        try {
            content.replace(start, end, code);
            editor.setSelection(Math.min(start + code.length(), content.length()));
        } finally { internalEdit = false; }
        updateTitle();
        editor.invalidate();
        scheduleHighlight();
        editor.post(() -> updateHistoryButtons());
        toast("Шаблон вставлен");
    }

    private void scheduleHighlight() {
        if (editor == null) return;
        editor.removeCallbacks(highlightTask);
        editor.postDelayed(highlightTask, 250);
    }

    private void updateHistoryButtons() {
        if (historyManager == null) return;
        boolean canUndo = historyManager.canUndo();
        boolean canRedo = historyManager.canRedo();
        undoButton.setEnabled(canUndo);
        undoButton.setAlpha(canUndo ? 1f : 0.4f);
        redoButton.setEnabled(canRedo);
        redoButton.setAlpha(canRedo ? 1f : 0.4f);
    }

    private char matchingClose(char c) {
        switch (c) {
            case '(': return ')';
            case '[': return ']';
            case '{': return '}';
            case '"': return '"';
            case '\'': return '\'';
            default: return 0;
        }
    }

    private boolean isOpeningCharacter(char c) {
        return c == '(' || c == '[' || c == '{' || c == '"' || c == '\'';
    }

    private boolean isClosingCharacter(char c) {
        return c == ')' || c == ']' || c == '}' || c == '"' || c == '\'';
    }

    private void insertClosingPair(Editable content, int position, char closing) {
        if (position < 0 || position > content.length()) return;
        if (position < content.length() && content.charAt(position) == closing) return;
        internalEdit = true;
        try {
            content.insert(position, String.valueOf(closing));
            editor.setSelection(position);
        } finally { internalEdit = false; }
    }

    private void skipExistingClosing(Editable content, int position) {
        if (position < 0 || position + 1 >= content.length()) return;
        internalEdit = true;
        try {
            content.delete(position, position + 1);
            editor.setSelection(Math.min(position + 1, content.length()));
        } finally { internalEdit = false; }
    }

    private void applyAutoIndent(Editable content, int position) {
        if (position <= 0 || position > content.length()) return;
        int start = position - 2;
        while (start >= 0 && content.charAt(start) != '\n') start--;
        start++;
        StringBuilder indent = new StringBuilder();
        for (int i = start; i < position - 1; i++) {
            char c = content.charAt(i);
            if (c == ' ' || c == '\t') indent.append(c);
            else break;
        }
        int last = position - 2;
        while (last >= start && Character.isWhitespace(content.charAt(last))) last--;
        if (last >= start && content.charAt(last) == '{') indent.append("    ");
        if (indent.length() == 0) return;
        internalEdit = true;
        try {
            content.insert(position, indent.toString());
            editor.setSelection(Math.min(position + indent.length(), content.length()));
        } finally { internalEdit = false; }
    }

    private boolean hasUnsavedChanges() {
        return editor != null && !editor.getText().toString().equals(savedContent);
    }

    private void updateTitle() {
        if (title != null && currentFile != null) {
            title.setText(currentFile.getName() + (hasUnsavedChanges() ? " *" : ""));
        }
        if (statusText != null) {
            boolean modified = hasUnsavedChanges();
            statusText.setText(modified ? "● Изменён" : "● Сохранено");
            statusText.setTextColor(modified ? 0xFFFFC978 : 0xFF71D7A4);
        }
    }

    private void saveFile() {
        if (editor == null || currentFile == null) return;
        if (isImageFile(currentFile)) { toast("Нельзя сохранять изображение как текст"); return; }
        String content = editor.getText().toString();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            toast("Файл превышает ограничение 1 МБ");
            return;
        }
        Path target = currentFile.toPath();
        Path temp = null;
        try {
            if (isBinaryFile(currentFile)) throw new IOException("Сохранение бинарного файла запрещено");
            temp = Files.createTempFile(target.getParent(), ".codebox-", ".tmp");
            Files.write(temp, content.getBytes(StandardCharsets.UTF_8));
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            savedContent = content;
            updateTitle();
            toast("Файл сохранён");
        } catch (IOException e) { toast("Ошибка сохранения: " + e.getMessage()); }
        finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    private void attemptExit(boolean goHome) {
        if (!hasUnsavedChanges()) { navigateAway(goHome); return; }
        new AlertDialog.Builder(this).setTitle("Несохранённые изменения")
                .setMessage("Сохранить изменения перед выходом?")
                .setPositiveButton("Сохранить", (d, w) -> {
                    saveFile();
                    if (!hasUnsavedChanges()) navigateAway(goHome);
                })
                .setNegativeButton("Не сохранять", (d, w) -> navigateAway(goHome))
                .setNeutralButton("Отмена", null).show();
    }

    private void navigateAway(boolean goHome) {
        if (goHome) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("Ошибка редактора")
                .setMessage(message == null ? "Неизвестная ошибка" : message)
                .setPositiveButton("Закрыть", (d, w) -> finish())
                .setCancelable(false).show();
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed() { attemptExit(false); }

    @Override protected void onDestroy() {
        if (editor != null) editor.removeCallbacks(highlightTask);
        super.onDestroy();
    }

    private class CodeEditText extends EditText {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        CodeEditText() {
            super(CodeEditorActivity.this);
            linePaint.setColor(EDITOR_MUTED);
            linePaint.setTextSize(dp(12));
            linePaint.setTypeface(Typeface.MONOSPACE);
            linePaint.setTextAlign(Paint.Align.RIGHT);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Layout layout = getLayout();
            if (layout == null) return;
            CharSequence content = getText();
            int number = 1;
            drawNumber(canvas, layout, 0, number);
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '\n') drawNumber(canvas, layout, i + 1, ++number);
            }
        }
        private void drawNumber(Canvas canvas, Layout layout, int offset, int number) {
            int line = layout.getLineForOffset(Math.min(offset, getText().length()));
            float y = getTotalPaddingTop() + layout.getLineBaseline(line);
            canvas.drawText(String.valueOf(number), getPaddingLeft() - dp(12), y, linePaint);
        }
    }
}
