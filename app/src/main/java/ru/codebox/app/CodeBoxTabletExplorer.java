package ru.codebox.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tablet project explorer for CodeBox 5.
 *
 * This class is intentionally independent from CodeEditorActivity so it can
 * be added and compiled before the editor is switched to the tablet layout.
 */
public final class CodeBoxTabletExplorer {

    public interface Listener {
        void onFileSelected(File file);
        void onHome();
        void onBack();
        void onUp();
        void onCreateFile();
        void onCreateFolder();
        void onGraphics();
    }

    private static final int PURPLE = 0xFF6535D5;
    private static final int TEXT = 0xFF252334;
    private static final int MUTED = 0xFF777487;
    private static final int MAX_DEPTH = 12;
    private static final int MAX_ENTRIES = 700;

    private final Activity activity;
    private final File projectRoot;
    private final String projectName;
    private final Listener listener;
    private final SharedPreferences preferences;

    private LinearLayout content;
    private LinearLayout list;
    private ScrollView scroll;
    private EditText searchField;
    private TextView projectSummary;
    private String activeSection = "project";
    private String activeFilter = "all";
    private String searchQuery = "";
    private int renderedEntries;

    public CodeBoxTabletExplorer(Activity activity, File projectRoot,
                                  String projectName, Listener listener) {
        if (activity == null || projectRoot == null || listener == null) {
            throw new IllegalArgumentException("Activity, project root and listener are required");
        }
        this.activity = activity;
        this.projectRoot = canonical(projectRoot);
        this.projectName = projectName == null ? "" : projectName;
        this.listener = listener;
        this.preferences = activity.getSharedPreferences(
                "codebox_tablet_explorer_" + safeKey(this.projectName),
                Activity.MODE_PRIVATE);
    }

    public View createView() {
        LinearLayout panel = column();
        panel.setBackgroundColor(0xFFF5F2FC);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));

        createNavigation(panel);
        createSections(panel);
        createSearch(panel);
        createFilters(panel);
        createSummary(panel);

        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        list = column();
        list.setPadding(dp(2), dp(4), dp(2), dp(8));
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        createBottomActions(panel);
        content = panel;
        refresh();
        return panel;
    }

    public void refresh() {
        if (list == null) return;
        list.removeAllViews();
        renderedEntries = 0;

        if ("recent".equals(activeSection)) {
            renderStoredFiles("recent_files", "Недавние файлы");
        } else if ("favorites".equals(activeSection)) {
            renderStoredFiles("favorite_files", "Избранное");
        } else {
            renderProjectTree(projectRoot, 0);
        }

        if (renderedEntries == 0) {
            TextView empty = label(
                    "recent".equals(activeSection) ? "Недавно открытых файлов пока нет"
                            : "Файлы не найдены",
                    13, false, MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            list.addView(empty);
        }
        if (projectSummary != null) {
            int[] counts = countEntries(projectRoot);
            projectSummary.setText(projectName + "  ·  " + counts[0]
                    + " файлов, " + counts[1] + " папок");
        }
    }

    public void recordOpenedFile(File file) {
        if (!isInsideProject(file) || !file.isFile()) return;
        String path = relativePath(file);
        if (path == null) return;

        List<String> values = readList("recent_files");
        values.remove(path);
        values.add(0, path);
        while (values.size() > 30) values.remove(values.size() - 1);
        writeList("recent_files", values);
        if ("recent".equals(activeSection)) refresh();
    }

    public void setSelectedFile(File file) {
        if (list != null) refresh();
    }

    private void createNavigation(LinearLayout parent) {
        LinearLayout row = horizontal();
        addNavButton(row, "⌂ Домой", listener::onHome, 1f);
        addNavButton(row, "‹ Назад", listener::onBack, 1f);
        addNavButton(row, "↑ Вверх", listener::onUp, 1f);
        parent.addView(row);
    }

    private void createSections(LinearLayout parent) {
        LinearLayout row = horizontal();
        addSectionButton(row, "Проект", "project");
        addSectionButton(row, "Недавние", "recent");
        addSectionButton(row, "Избранное", "favorites");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(38));
        p.topMargin = dp(8);
        parent.addView(row, p);
    }

    private void createSearch(LinearLayout parent) {
        LinearLayout row = horizontal();
        searchField = new EditText(activity);
        searchField.setSingleLine(true);
        searchField.setTextSize(13);
        searchField.setHint("Поиск в проекте...");
        searchField.setPadding(dp(10), 0, dp(8), 0);
        searchField.setBackground(round(0xFFFFFFFF, 11));
        row.addView(searchField, new LinearLayout.LayoutParams(0, dp(40), 1f));

        TextView clear = button("×", false);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(38), dp(40));
        clearParams.leftMargin = dp(5);
        row.addView(clear, clearParams);
        clear.setOnClickListener(v -> searchField.setText(""));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(40));
        p.topMargin = dp(8);
        parent.addView(row, p);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                refresh();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void createFilters(LinearLayout parent) {
        TextView heading = label("Фильтр по типу", 11, true, MUTED);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(8);
        parent.addView(heading, headingParams);

        LinearLayout row = horizontal();
        addFilterButton(row, "Все", "all");
        addFilterButton(row, "Java", "java");
        addFilterButton(row, "XML", "xml");
        addFilterButton(row, "Gradle", "gradle");
        addFilterButton(row, "Фото", "image");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(34));
        p.topMargin = dp(5);
        parent.addView(row, p);
    }

    private void createSummary(LinearLayout parent) {
        projectSummary = label(projectName, 12, true, TEXT);
        projectSummary.setSingleLine(true);
        projectSummary.setEllipsize(TextUtils.TruncateAt.END);
        projectSummary.setPadding(dp(5), dp(9), dp(5), dp(5));
        parent.addView(projectSummary);
    }

    private void createBottomActions(LinearLayout parent) {
        LinearLayout row = horizontal();
        addBottomButton(row, "+ Файл", listener::onCreateFile);
        addBottomButton(row, "+ Папка", listener::onCreateFolder);
        addBottomButton(row, "🎨", listener::onGraphics);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(42));
        p.topMargin = dp(6);
        parent.addView(row, p);
    }

    private void addNavButton(LinearLayout parent, String title, Runnable action, float weight) {
        TextView view = button(title, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(38), weight);
        p.rightMargin = dp(3);
        parent.addView(view, p);
        view.setOnClickListener(v -> action.run());
    }

    private void addSectionButton(LinearLayout parent, String title, String section) {
        TextView view = button(title, section.equals(activeSection));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
        p.rightMargin = dp(3);
        parent.addView(view, p);
        view.setOnClickListener(v -> {
            activeSection = section;
            createViewRefreshSections();
            refresh();
        });
    }

    private void createViewRefreshSections() {
        if (content == null) return;
        // Rebuild the small section selector without recreating the file list.
        int childIndex = 1;
        if (content.getChildCount() > childIndex) {
            View old = content.getChildAt(childIndex);
            content.removeViewAt(childIndex);
            LinearLayout row = horizontal();
            addSectionButton(row, "Проект", "project");
            addSectionButton(row, "Недавние", "recent");
            addSectionButton(row, "Избранное", "favorites");
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(38));
            p.topMargin = dp(8);
            content.addView(row, childIndex, p);
        }
    }

    private void addFilterButton(LinearLayout parent, String title, String filter) {
        TextView view = button(title, filter.equals(activeFilter));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
        p.rightMargin = dp(3);
        parent.addView(view, p);
        view.setOnClickListener(v -> {
            activeFilter = filter;
            refresh();
            // Update filter colors in-place on the next rebuild.
            if (content != null) rebuildFilters();
        });
    }

    private void rebuildFilters() {
        if (content == null) return;
        // Filter row is child 4: navigation, sections, search, heading, filter row.
        int index = 4;
        if (content.getChildCount() <= index) return;
        content.removeViewAt(index);
        LinearLayout row = horizontal();
        addFilterButton(row, "Все", "all");
        addFilterButton(row, "Java", "java");
        addFilterButton(row, "XML", "xml");
        addFilterButton(row, "Gradle", "gradle");
        addFilterButton(row, "Фото", "image");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(34));
        p.topMargin = dp(5);
        content.addView(row, index, p);
    }

    private void addBottomButton(LinearLayout parent, String title, Runnable action) {
        TextView view = button(title, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
        p.rightMargin = dp(4);
        parent.addView(view, p);
        view.setOnClickListener(v -> action.run());
    }

    private void renderProjectTree(File directory, int depth) {
        if (depth > MAX_DEPTH || renderedEntries >= MAX_ENTRIES) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator
                .comparing((File f) -> !f.isDirectory())
                .thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));

        for (File child : children) {
            if (renderedEntries >= MAX_ENTRIES || !isInsideProject(child)) continue;
            if (!matchesSearch(child) || !matchesFilter(child)) {
                if (child.isDirectory() && containsMatchingFile(child, depth + 1)) {
                    // Keep parent folders visible when a descendant matches.
                } else {
                    continue;
                }
            }
            addTreeRow(child, depth);
            renderedEntries++;
            if (child.isDirectory() && isExpanded(child)) {
                renderProjectTree(child, depth + 1);
            }
        }
    }

    private void addTreeRow(File file, int depth) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4 + depth * 13), 0, dp(4), 0);
        row.setMinimumHeight(dp(34));

        String icon = file.isDirectory() ? (isExpanded(file) ? "▾ 📁" : "▸ 📁")
                : fileIcon(file);
        TextView iconView = label(icon, 13, true, file.isDirectory() ? 0xFFE3A52E : PURPLE);
        iconView.setGravity(Gravity.CENTER);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(38), dp(32)));

        TextView name = label(file.getName(), 12, file.isDirectory(), TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        if (file.isDirectory()) {
            TextView count = label("(" + safeListFiles(file).length + ")", 10, false, MUTED);
            row.addView(count);
        }

        row.setBackground(round(isFavorite(file) ? 0xFFECE5FA : 0x00FFFFFF, 8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(34));
        p.bottomMargin = dp(1);
        list.addView(row, p);

        row.setOnClickListener(v -> {
            if (file.isDirectory()) {
                setExpanded(file, !isExpanded(file));
                refresh();
            } else {
                openFile(file);
            }
        });
        row.setOnLongClickListener(v -> {
            if (!file.isDirectory()) toggleFavorite(file);
            else setExpanded(file, !isExpanded(file));
            refresh();
            return true;
        });
    }

    private void renderStoredFiles(String key, String heading) {
        TextView title = label(heading, 13, true, TEXT);
        title.setPadding(dp(5), dp(5), dp(5), dp(8));
        list.addView(title);
        for (String path : readList(key)) {
            if (renderedEntries >= MAX_ENTRIES) break;
            File file = new File(projectRoot, path);
            if (!isInsideProject(file) || !file.isFile()
                    || !matchesSearch(file) || !matchesFilter(file)) continue;
            addTreeRow(file, 0);
            renderedEntries++;
        }
    }

    private void openFile(File file) {
        if (!isInsideProject(file) || !file.isFile()) return;
        recordOpenedFile(file);
        listener.onFileSelected(file);
    }

    private boolean containsMatchingFile(File directory, int depth) {
        if (depth > MAX_DEPTH) return false;
        File[] files = directory.listFiles();
        if (files == null) return false;
        for (File child : files) {
            if (!isInsideProject(child)) continue;
            if (child.isDirectory()) {
                if (containsMatchingFile(child, depth + 1)) return true;
            } else if (matchesSearch(child) && matchesFilter(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSearch(File file) {
        return searchQuery.isEmpty()
                || file.getName().toLowerCase(Locale.ROOT).contains(searchQuery);
    }

    private boolean matchesFilter(File file) {
        if (file.isDirectory() || "all".equals(activeFilter)) return true;
        String name = file.getName().toLowerCase(Locale.ROOT);
        switch (activeFilter) {
            case "java": return name.endsWith(".java");
            case "xml": return name.endsWith(".xml");
            case "gradle": return name.endsWith(".gradle") || name.endsWith(".gradle.kts");
            case "image": return name.endsWith(".png") || name.endsWith(".jpg")
                    || name.endsWith(".jpeg") || name.endsWith(".webp");
            default: return true;
        }
    }

    private String fileIcon(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) return "J";
        if (name.endsWith(".xml")) return "</>";
        if (name.endsWith(".gradle") || name.endsWith(".gradle.kts")) return "G";
        if (name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".webp")) return "▧";
        return "📄";
    }

    private boolean isExpanded(File directory) {
        String key = "expanded:" + relativePath(directory);
        return preferences.getBoolean(key, directory.equals(projectRoot)
                || directory.getName().equals("app")
                || directory.getName().equals("src")
                || directory.getName().equals("main")
                || directory.getName().equals("java"));
    }

    private void setExpanded(File directory, boolean expanded) {
        preferences.edit().putBoolean("expanded:" + relativePath(directory), expanded).apply();
    }

    private boolean isFavorite(File file) {
        String path = relativePath(file);
        return path != null && readList("favorite_files").contains(path);
    }

    private void toggleFavorite(File file) {
        String path = relativePath(file);
        if (path == null) return;
        List<String> values = readList("favorite_files");
        if (values.contains(path)) values.remove(path);
        else values.add(0, path);
        writeList("favorite_files", values);
    }

    private List<String> readList(String key) {
        Set<String> set = preferences.getStringSet(key, Collections.emptySet());
        List<String> result = new ArrayList<>(set);
        // StringSet order is undefined; persist a stable ordered companion string.
        String ordered = preferences.getString(key + "_ordered", "");
        if (!ordered.isEmpty()) {
            result.clear();
            for (String item : ordered.split("\\n")) {
                if (!item.isEmpty()) result.add(item);
            }
        }
        return result;
    }

    private void writeList(String key, List<String> values) {
        StringBuilder ordered = new StringBuilder();
        for (String value : values) {
            if (ordered.length() > 0) ordered.append('\n');
            ordered.append(value.replace("\n", ""));
        }
        preferences.edit()
                .putStringSet(key, new java.util.HashSet<>(values))
                .putString(key + "_ordered", ordered.toString())
                .apply();
    }

    private int[] countEntries(File directory) {
        int[] counts = new int[]{0, 0};
        countRecursive(directory, 0, counts);
        return counts;
    }

    private void countRecursive(File directory, int depth, int[] counts) {
        if (depth > MAX_DEPTH || counts[0] + counts[1] >= MAX_ENTRIES) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!isInsideProject(file)) continue;
            if (file.isDirectory()) {
                counts[1]++;
                countRecursive(file, depth + 1, counts);
            } else {
                counts[0]++;
            }
        }
    }

    private File[] safeListFiles(File directory) {
        File[] files = directory.listFiles();
        return files == null ? new File[0] : files;
    }

    private boolean isInsideProject(File file) {
        try {
            String root = projectRoot.getCanonicalPath();
            String path = file.getCanonicalPath();
            return path.equals(root) || path.startsWith(root + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private String relativePath(File file) {
        try {
            String root = projectRoot.getCanonicalPath();
            String path = file.getCanonicalPath();
            if (!path.startsWith(root + File.separator)) return null;
            return path.substring(root.length() + 1);
        } catch (IOException e) {
            return null;
        }
    }

    private static File canonical(File file) {
        try { return file.getCanonicalFile(); }
        catch (IOException e) { return file.getAbsoluteFile(); }
    }

    private static String safeKey(String value) {
        return value.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private int dp(float value) {
        return CodeBoxTheme.dp(activity, value);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView label(String value, int size, boolean bold, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView button(String value, boolean selected) {
        TextView view = label(value, 11, true, selected ? 0xFFFFFFFF : TEXT);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(2), 0, dp(2), 0);
        view.setBackground(round(selected ? PURPLE : 0xFFEAE5F5, 10));
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
                    }
