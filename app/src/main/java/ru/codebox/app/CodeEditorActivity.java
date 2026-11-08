package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
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
import android.view.View;
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
import java.nio.file.AtomicMoveNotSupportedException;
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
    private static final int REQUEST_EXPORT_TEXT = 7127;
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
    private TextView title, statusText, undoButton, redoButton, orientationButton;
    private File currentFile;
    private File projectDirectory;
    private CodeBoxTabletExplorer tabletExplorer;
    private final List<EditorTab> openTabs = new ArrayList<>();
    private LinearLayout tabsContainer;
    private static final int MAX_OPEN_TABS = 12;

    private static final class EditorTab {
        final File file;
        String saved;
        String draft;
        int cursor;

        EditorTab(File file, String content) {
            this.file = file;
            this.saved = content;
            this.draft = content;
            this.cursor = 0;
        }

        String title() {
            return file.getName();
        }
    }
    private String savedContent = "";
    private String initialEditorContent;
    private int restoredCursorPosition = -1;
    private int editorOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
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
        if (state != null) {
            editorOrientation = state.getInt("editor_orientation",
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (editorOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                setRequestedOrientation(editorOrientation);
            }
            initialEditorContent = state.getString("editor_draft");
            restoredCursorPosition = state.getInt("editor_cursor", -1);
        }
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
            projectDirectory = project;
            File file = resolveSafeProjectFile(relativePath, true);
            if (!file.isFile()) {
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
            if (initialEditorContent == null) initialEditorContent = savedContent;
            EditorTab initialTab = new EditorTab(file, savedContent);
            initialTab.draft = initialEditorContent;
            initialTab.cursor = Math.max(0, restoredCursorPosition);
            openTabs.add(initialTab);
            if (state != null) restoreTabsFromState(state, project);
            buildEditor();
        } catch (IOException e) { showError(e.getMessage()); }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (editor != null) {
            outState.putString("editor_draft", editor.getText().toString());
            outState.putInt("editor_cursor", editor.getSelectionStart());
        }
        outState.putInt("editor_orientation", editorOrientation);
        captureCurrentTab();
        try {
            if (currentFile != null) outState.putString("active_tab_path", currentFile.getCanonicalPath());
        } catch (IOException ignored) { }
        ArrayList<String> paths = new ArrayList<>();
        ArrayList<String> drafts = new ArrayList<>();
        ArrayList<String> saved = new ArrayList<>();
        ArrayList<Integer> cursors = new ArrayList<>();
        for (EditorTab tab : openTabs) {
            try {
                paths.add(tab.file.getCanonicalPath());
                drafts.add(tab.draft);
                saved.add(tab.saved);
                cursors.add(tab.cursor);
            } catch (IOException ignored) { }
        }
        outState.putStringArrayList("open_tab_paths", paths);
        outState.putStringArrayList("open_tab_drafts", drafts);
        outState.putStringArrayList("open_tab_saved", saved);
        int[] cursorValues = new int[cursors.size()];
        for (int i = 0; i < cursors.size(); i++) cursorValues[i] = cursors.get(i);
        outState.putIntArray("open_tab_cursors", cursorValues);
        super.onSaveInstanceState(outState);
    }

    private void restoreTabsFromState(Bundle state, File project) {
        ArrayList<String> paths = state.getStringArrayList("open_tab_paths");
        ArrayList<String> drafts = state.getStringArrayList("open_tab_drafts");
        ArrayList<String> saved = state.getStringArrayList("open_tab_saved");
        int[] cursors = state.getIntArray("open_tab_cursors");
        if (paths == null || drafts == null || saved == null) return;
        openTabs.clear();
        for (int i = 0; i < paths.size() && i < drafts.size() && i < saved.size(); i++) {
            try {
                File candidate = new File(paths.get(i)).getCanonicalFile();
                String root = project.getCanonicalPath();
                if (!candidate.getPath().startsWith(root + File.separator)
                        || !candidate.isFile() || candidate.length() > MAX_FILE_BYTES
                        || isBinaryFile(candidate)) continue;
                EditorTab tab = new EditorTab(candidate, saved.get(i));
                tab.draft = drafts.get(i);
                tab.cursor = cursors != null && i < cursors.length ? cursors[i] : 0;
                openTabs.add(tab);
            } catch (IOException ignored) { }
        }
        String activePath = state.getString("active_tab_path");
        EditorTab active = null;
        if (activePath != null) {
            for (EditorTab tab : openTabs) {
                try {
                    if (tab.file.getCanonicalPath().equals(activePath)) {
                        active = tab;
                        break;
                    }
                } catch (IOException ignored) { }
            }
        }
        if (active == null) active = findTab(currentFile);
        if (active != null) {
            currentFile = active.file;
            savedContent = active.saved;
            initialEditorContent = active.draft;
            restoredCursorPosition = Math.min(active.cursor, active.draft.length());
        } else if (!openTabs.isEmpty()) {
            EditorTab first = openTabs.get(0);
            currentFile = first.file;
            savedContent = first.saved;
            initialEditorContent = first.draft;
            restoredCursorPosition = Math.min(first.cursor, first.draft.length());
        }
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

    /**
     * Resolves a project-relative path without following symbolic links.
     * Every existing path component is checked before the canonical path is accepted.
     */
    private File resolveSafeProjectFile(String relativePath, boolean mustExist) throws IOException {
        if (projectDirectory == null || relativePath == null || relativePath.trim().isEmpty()) {
            throw new IOException("Путь к файлу не указан");
        }
        Path root = projectDirectory.getCanonicalFile().toPath();
        Path relative = new File(relativePath).toPath();
        if (relative.isAbsolute()) throw new IOException("Абсолютные пути запрещены");

        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            throw new IOException("Путь выходит за пределы проекта");
        }

        Path current = root;
        Path partPath = root.relativize(candidate);
        for (Path part : partPath) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Символические ссылки в проекте не поддерживаются");
            }
        }

        File canonical = candidate.toFile().getCanonicalFile();
        if (!canonical.toPath().startsWith(root)) {
            throw new IOException("Файл находится вне проекта");
        }
        if (mustExist && !Files.exists(canonical.toPath())) {
            throw new IOException("Файл не найден");
        }
        return canonical;
    }

    private void verifySafeTarget(File file) throws IOException {
        if (file == null || projectDirectory == null) {
            throw new IOException("Файл проекта недоступен");
        }
        Path root = projectDirectory.getCanonicalFile().toPath();
        Path target = file.toPath().toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Запись за пределами проекта запрещена");
        }
        Path current = root;
        for (Path part : root.relativize(target)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Запись через символическую ссылку запрещена");
            }
        }
        File canonical = file.getCanonicalFile();
        if (!canonical.toPath().startsWith(root) || canonical.equals(root.toFile())) {
            throw new IOException("Файл находится вне проекта");
        }
        if (!file.isFile()) throw new IOException("Файл больше не существует");
    }

    /**
     * Writes UTF-8 text through a temporary file. Uses atomic replacement where supported
     * and a regular replacement as a compatibility fallback.
     */
    private void writeTextSafely(File file, String content) throws IOException {
        verifySafeTarget(file);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("Файл превышает ограничение 1 МБ");
        }

        Path target = file.toPath();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Папка файла не найдена");
        }

        Path temp = null;
        try {
            temp = Files.createTempFile(parent, ".codebox-", ".tmp");
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        }
    }

    private void buildEditor() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CodeBoxTheme.background(this));
        createHeader(root);
        createActions(root);
        createSnippetButton(root);
        createTabsBar(root);

        if (isTabletLayout()) {
            LinearLayout workspace = new LinearLayout(this);
            workspace.setOrientation(LinearLayout.HORIZONTAL);
            workspace.setGravity(Gravity.CENTER_VERTICAL);

            tabletExplorer = new CodeBoxTabletExplorer(this, projectDirectory,
                    currentProjectName, new CodeBoxTabletExplorer.Listener() {
                @Override public void onFileSelected(File file) {
                    openFileFromExplorer(file);
                }
                @Override public void onHome() { attemptExit(true); }
                @Override public void onBack() { attemptExit(false); }
                @Override public void onUp() {
                    if (tabletExplorer != null) tabletExplorer.refresh();
                    toast("Показан корень проекта");
                }
                @Override public void onCreateFile() { showTabletCreateDialog(false); }
                @Override public void onCreateFolder() { showTabletCreateDialog(true); }
                @Override public void onGraphics() { openTabletGraphicsEditor(); }
            });

            View explorerView = tabletExplorer.createView();
            LinearLayout.LayoutParams explorerParams =
                    new LinearLayout.LayoutParams(dp(270), -1);
            explorerParams.rightMargin = dp(1);
            workspace.addView(explorerView, explorerParams);

            LinearLayout editorColumn = new LinearLayout(this);
            editorColumn.setOrientation(LinearLayout.VERTICAL);
            createEditorArea(editorColumn);
            createBottomToolbar(editorColumn);
            workspace.addView(editorColumn,
                    new LinearLayout.LayoutParams(0, -1, 1f));

            root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            createEditorArea(root);
            createBottomToolbar(root);
        }
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

    private boolean isTabletLayout() {
        android.content.res.Configuration config = getResources().getConfiguration();
        return config.smallestScreenWidthDp >= 600 || config.screenWidthDp >= 720;
    }

    private void openFileFromExplorer(File file) {
        if (file == null || projectDirectory == null) return;
        try {
            Path rootPath = projectDirectory.getCanonicalFile().toPath();
            Path selectedPath = file.toPath().toAbsolutePath().normalize();
            if (!selectedPath.startsWith(rootPath) || selectedPath.equals(rootPath)) {
                toast("Файл недоступен или находится вне проекта");
                return;
            }
            Path walk = rootPath;
            for (Path part : rootPath.relativize(selectedPath)) {
                walk = walk.resolve(part);
                if (Files.isSymbolicLink(walk)) {
                    toast("Символические ссылки не поддерживаются");
                    return;
                }
            }
            File canonical = file.getCanonicalFile();
            if (!canonical.toPath().startsWith(rootPath) || !canonical.isFile()) {
                toast("Файл недоступен или находится вне проекта");
                return;
            }
            if (isImageFile(canonical)) {
                toast("Изображения открываются в просмотрщике проекта");
                return;
            }
            EditorTab existing = findTab(canonical);
            if (existing != null) {
                switchToTab(existing);
                return;
            }
            if (openTabs.size() >= MAX_OPEN_TABS) {
                toast("Открыто максимум " + MAX_OPEN_TABS + " файлов. Закройте лишнюю вкладку.");
                return;
            }
            if (canonical.length() > MAX_FILE_BYTES || isBinaryFile(canonical)) {
                toast("Этот файл нельзя открыть в текстовом редакторе");
                return;
            }
            if (hasUnsavedChanges()) {
                new AlertDialog.Builder(this)
                        .setTitle("Несохранённые изменения")
                        .setMessage("Сохранить текущий файл перед открытием другого?")
                        .setPositiveButton("Сохранить", (d, w) -> {
                            saveFile();
                            if (!hasUnsavedChanges()) addAndSwitchTab(canonical);
                        })
                        .setNegativeButton("Открыть без сохранения",
                                (d, w) -> addAndSwitchTab(canonical))
                        .setNeutralButton("Отмена", null)
                        .show();
            } else {
                addAndSwitchTab(canonical);
            }
        } catch (IOException e) {
            toast("Ошибка открытия: " + e.getMessage());
        }
    }

    private EditorTab findTab(File file) {
        if (file == null) return null;
        try {
            String target = file.getCanonicalPath();
            for (EditorTab tab : openTabs) {
                if (tab.file.getCanonicalPath().equals(target)) return tab;
            }
        } catch (IOException ignored) { }
        return null;
    }

    private void captureCurrentTab() {
        EditorTab active = findTab(currentFile);
        if (active == null || editor == null) return;
        active.draft = editor.getText().toString();
        active.saved = savedContent;
        active.cursor = editor.getSelectionStart();
    }

    private void addAndSwitchTab(File file) {
        try {
            String content = FileUtils.readText(file);
            EditorTab tab = new EditorTab(file, content);
            openTabs.add(tab);
            switchToTab(tab);
        } catch (IOException e) {
            toast("Не удалось прочитать файл: " + e.getMessage());
        }
    }

    private void switchToTab(EditorTab tab) {
        if (tab == null) return;
        captureCurrentTab();
        currentFile = tab.file;
        savedContent = tab.saved;
        initialEditorContent = tab.draft;
        restoredCursorPosition = Math.min(tab.cursor, tab.draft.length());
        buildEditor();
    }

    private void createTabsBar(LinearLayout parent) {
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setFillViewport(false);
        horizontal.setBackgroundColor(CodeBoxTheme.surface(this));
        tabsContainer = new LinearLayout(this);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setGravity(Gravity.CENTER_VERTICAL);
        tabsContainer.setPadding(dp(6), dp(4), dp(6), dp(4));
        horizontal.addView(tabsContainer, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT, dp(42)));
        parent.addView(horizontal, new LinearLayout.LayoutParams(-1, dp(42)));
        refreshTabsBar();
    }

    private void refreshTabsBar() {
        if (tabsContainer == null) return;
        tabsContainer.removeAllViews();
        for (EditorTab tab : openTabs) {
            boolean active = tab.file.equals(currentFile);
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(9), 0, dp(5), 0);
            item.setBackground(rounded(active ? 0xFF6535D5 : 0xFFEAE6F2, 9));

            TextView name = text(tab.title(), 11, active);
            name.setTextColor(active ? 0xFFFFFFFF : CodeBoxTheme.textColor(this));
            name.setSingleLine(true);
            name.setMaxWidth(dp(145));
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            item.addView(name);

            if (tab.file.equals(currentFile) && hasUnsavedChanges()) {
                TextView dirty = text(" •", 10, true);
                dirty.setTextColor(0xFFFFD27A);
                item.addView(dirty);
            }

            TextView close = text(" × ", 16, true);
            close.setTextColor(active ? 0xFFFFFFFF : CodeBoxTheme.textColor(this));
            close.setGravity(Gravity.CENTER);
            item.addView(close, new LinearLayout.LayoutParams(dp(27), dp(34)));
            close.setOnClickListener(v -> requestCloseTab(tab));
            item.setOnClickListener(v -> {
                if (!tab.file.equals(currentFile)) {
                    if (hasUnsavedChanges()) {
                        new AlertDialog.Builder(this)
                                .setTitle("Несохранённые изменения")
                                .setMessage("Сохранить текущий файл перед переключением?")
                                .setPositiveButton("Сохранить", (d, w) -> {
                                    saveFile();
                                    if (!hasUnsavedChanges()) switchToTab(tab);
                                })
                                .setNegativeButton("Переключить без сохранения",
                                        (d, w) -> switchToTab(tab))
                                .setNeutralButton("Отмена", null).show();
                    } else {
                        switchToTab(tab);
                    }
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(34));
            p.rightMargin = dp(5);
            tabsContainer.addView(item, p);
        }
        TextView add = text("+", 20, true);
        add.setGravity(Gravity.CENTER);
        add.setTextColor(CodeBoxTheme.PURPLE);
        tabsContainer.addView(add, new LinearLayout.LayoutParams(dp(38), dp(34)));
        add.setOnClickListener(v -> showOpenFileDialog());
    }

    private void showOpenFileDialog() {
        if (projectDirectory == null) {
            toast("Папка проекта недоступна");
            return;
        }
        List<File> files = new ArrayList<>();
        collectProjectFiles(projectDirectory, 0, files);
        if (files.isEmpty()) {
            toast("В проекте пока нет текстовых файлов");
            return;
        }

        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            String relative = projectDirectory.toPath().relativize(file.toPath()).toString();
            labels[i] = relative.replace(File.separatorChar, '/');
            EditorTab opened = findTab(file);
            if (opened != null) labels[i] += "  • открыта";
        }

        new AlertDialog.Builder(this)
                .setTitle("Открыть файл проекта")
                .setItems(labels, (dialog, which) -> openFileFromExplorer(files.get(which)))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void collectProjectFiles(File directory, int depth, List<File> result) {
        if (directory == null || depth > 20 || result.size() >= 500) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File child : children) {
            if (result.size() >= 500) return;
            try {
                String rootPath = projectDirectory.getCanonicalPath();
                String childPath = child.getCanonicalPath();
                if (!childPath.startsWith(rootPath + File.separator)) continue;
                if (java.nio.file.Files.isSymbolicLink(child.toPath())) continue;
                if (child.isDirectory()) {
                    collectProjectFiles(child, depth + 1, result);
                } else if (child.isFile() && !isImageFile(child)
                        && child.length() <= MAX_FILE_BYTES) {
                    result.add(child);
                }
            } catch (IOException ignored) {
                // Skip inaccessible entries; never expose paths outside the project.
            }
        }
    }

    private void requestCloseTab(EditorTab tab) {
        if (tab == null || openTabs.size() <= 1) {
            toast("Оставьте хотя бы одну открытую вкладку");
            return;
        }
        boolean active = tab.file.equals(currentFile);
        boolean dirty = active ? hasUnsavedChanges() : !tab.draft.equals(tab.saved);
        if (dirty) {
            new AlertDialog.Builder(this)
                    .setTitle("Закрыть вкладку?")
                    .setMessage("В файле есть несохранённые изменения.")
                    .setPositiveButton("Сохранить", (d, w) -> {
                        if (active) {
                            saveFile();
                            if (!hasUnsavedChanges()) closeTabNow(tab);
                        } else if (saveInactiveTab(tab)) {
                            closeTabNow(tab);
                        }
                    })
                    .setNegativeButton("Закрыть без сохранения",
                            (d, w) -> closeTabNow(tab))
                    .setNeutralButton("Отмена", null).show();
        } else {
            closeTabNow(tab);
        }
    }

    private boolean saveInactiveTab(EditorTab tab) {
        if (tab == null || tab.draft == null) return false;
        try {
            writeTextSafely(tab.file, tab.draft);
            tab.saved = tab.draft;
            toast("Файл сохранён");
            return true;
        } catch (IOException e) {
            toast("Ошибка сохранения: " + e.getMessage());
            return false;
        }
    }

    private void closeTabNow(EditorTab tab) {
        int index = openTabs.indexOf(tab);
        if (index < 0 || openTabs.size() <= 1) return;
        boolean active = tab.file.equals(currentFile);
        if (active) captureCurrentTab();
        openTabs.remove(tab);
        if (active) {
            int next = Math.min(index, openTabs.size() - 1);
            switchToTab(openTabs.get(next));
        } else {
            refreshTabsBar();
        }
    }

    private void showTabletCreateDialog(boolean folder) {
        if (projectDirectory == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(folder ? "Например: utils" : "Например: Main.java");
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(24), dp(12), dp(24), dp(8));
        wrapper.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(folder ? "Новая папка в проекте" : "Новый файл в проекте")
                .setView(wrapper)
                .setPositiveButton("Создать", null)
                .setNegativeButton("Отмена", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    try {
                        ProjectFileManager manager = new ProjectFileManager(projectDirectory);
                        if (folder) manager.createFolder(projectDirectory, name);
                        else manager.createFile(projectDirectory, name, "");
                        dialog.dismiss();
                        if (tabletExplorer != null) tabletExplorer.refresh();
                        toast(folder ? "Папка создана" : "Файл создан");
                    } catch (IOException e) {
                        input.setError(e.getMessage() == null
                                ? "Не удалось создать объект" : e.getMessage());
                    }
                }));
        dialog.show();
    }

    private void openTabletGraphicsEditor() {
        if (projectDirectory == null) return;
        Intent intent = new Intent(this, GraphicsEditorActivity.class);
        intent.putExtra("project_name", currentProjectName);
        intent.putExtra("folder_path", "");
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("Не удалось открыть графический редактор");
        }
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
        orientationButton = headerButton("↔", "Переключить ориентацию экрана");
        LinearLayout.LayoutParams orientationParams =
                new LinearLayout.LayoutParams(dp(39), dp(40));
        orientationParams.rightMargin = dp(5);
        header.addView(orientationButton, orientationParams);
        orientationButton.setOnClickListener(v -> toggleEditorOrientation());
        updateOrientationButton();
        TextView icon = headerButton("</>", "Редактор кода");
        header.addView(icon, new LinearLayout.LayoutParams(dp(39), dp(40)));
        root.addView(header);
    }

    private void toggleEditorOrientation() {
        int current = getResources().getConfiguration().orientation;
        if (current == Configuration.ORIENTATION_LANDSCAPE) {
            editorOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else {
            editorOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        setRequestedOrientation(editorOrientation);
        toast(editorOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ? "Горизонтальная ориентация" : "Вертикальная ориентация");
    }

    private void updateOrientationButton() {
        if (orientationButton == null) return;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        orientationButton.setText(landscape ? "↕" : "↔");
        orientationButton.setContentDescription(landscape
                ? "Переключить на вертикальную ориентацию"
                : "Переключить на горизонтальную ориентацию");
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
        save.setContentDescription("Сохранить изменения в проекте");
        save.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
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
        view.setEllipsize(null);
        view.setPadding(dp(2), 0, dp(2), 0);
        view.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        view.setBackground(rounded(CodeBoxTheme.background(this), 9));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), weight);
        p.rightMargin = dp(4);
        toolbar.addView(view, p);
        return view;
    }

    private void createSnippetButton(LinearLayout root) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.setPadding(dp(10), dp(5), dp(10), dp(5));

        TextView snippets = text("📚 Шаблоны", 12, true);
        snippets.setGravity(Gravity.CENTER);
        snippets.setSingleLine(true);
        snippets.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        CodeBoxTheme.styleColoredButton(snippets, CodeBoxTheme.PURPLE);

        TextView export = text("⇩ Экспорт TXT", 12, true);
        export.setGravity(Gravity.CENTER);
        export.setSingleLine(true);
        export.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        CodeBoxTheme.styleColoredButton(export, CodeBoxTheme.PURPLE);

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(38), 1);
        left.rightMargin = dp(4);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(38), 1);
        right.leftMargin = dp(4);
        wrapper.addView(snippets, left);
        wrapper.addView(export, right);
        root.addView(wrapper);

        snippets.setOnClickListener(v -> showSnippetPicker());
        export.setOnClickListener(v -> exportCurrentText());
    }

    private void exportCurrentText() {
        if (editor == null || currentFile == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, currentFile.getName() + ".txt");
        try {
            startActivityForResult(intent, REQUEST_EXPORT_TEXT);
        } catch (android.content.ActivityNotFoundException e) {
            toast("Не найдено приложение для сохранения файла");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_TEXT || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        try (java.io.OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) throw new IOException("Не удалось открыть файл для записи");
            byte[] bytes = editor.getText().toString().getBytes(StandardCharsets.UTF_8);
            output.write(bytes);
            output.flush();
            toast("Текст экспортирован в TXT");
        } catch (IOException e) {
            toast("Ошибка экспорта: " + e.getMessage());
        }
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
        editor.setText(initialEditorContent == null ? savedContent : initialEditorContent);
        int cursor = restoredCursorPosition < 0 ? 0
                : Math.min(restoredCursorPosition, editor.length());
        editor.setSelection(cursor);
        restoredCursorPosition = -1;
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
        refreshTabsBar();
    }

    private void saveFile() {
        if (editor == null || currentFile == null) return;
        if (isImageFile(currentFile)) {
            toast("Нельзя сохранять изображение как текст");
            return;
        }
        String content = editor.getText().toString();
        try {
            if (isBinaryFile(currentFile)) {
                throw new IOException("Сохранение бинарного файла запрещено");
            }
            writeTextSafely(currentFile, content);
            savedContent = content;
            EditorTab activeTab = findTab(currentFile);
            if (activeTab != null) {
                activeTab.saved = content;
                activeTab.draft = content;
                activeTab.cursor = editor.getSelectionStart();
            }
            updateTitle();
            refreshTabsBar();
            toast("Файл сохранён");
        } catch (IOException e) {
            toast("Ошибка сохранения: " + e.getMessage());
        }
    }

    private boolean hasAnyUnsavedChanges() {
        captureCurrentTab();
        for (EditorTab tab : openTabs) {
            if (tab != null && tab.draft != null && !tab.draft.equals(tab.saved)) return true;
        }
        return false;
    }

    private boolean saveAllOpenTabs() {
        captureCurrentTab();
        for (EditorTab tab : openTabs) {
            if (tab == null || tab.draft == null || tab.draft.equals(tab.saved)) continue;
            try {
                if (isBinaryFile(tab.file)) {
                    throw new IOException("Бинарный файл нельзя сохранять");
                }
                writeTextSafely(tab.file, tab.draft);
                tab.saved = tab.draft;
            } catch (IOException e) {
                toast("Не удалось сохранить " + tab.file.getName() + ": " + e.getMessage());
                return false;
            }
        }
        EditorTab active = findTab(currentFile);
        if (active != null) {
            savedContent = active.saved;
            if (editor != null) active.cursor = editor.getSelectionStart();
        }
        updateTitle();
        refreshTabsBar();
        toast("Все открытые файлы сохранены");
        return true;
    }

    private void attemptExit(boolean goHome) {
        if (!hasAnyUnsavedChanges()) {
            navigateAway(goHome);
            return;
        }
        new AlertDialog.Builder(this).setTitle("Несохранённые изменения")
                .setMessage("Сохранить изменения во всех открытых вкладках перед выходом?")
                .setPositiveButton("Сохранить все", (d, w) -> {
                    if (saveAllOpenTabs()) navigateAway(goHome);
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

    @Override protected void onResume() {
        super.onResume();
        if (tabletExplorer != null) tabletExplorer.refresh();
    }

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
