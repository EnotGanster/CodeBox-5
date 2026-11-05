package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CodeLibraryActivity extends Activity {

    private CodeLibraryManager manager;
    private GradleProjectManager gradleManager;

    private LinearLayout root;
    private LinearLayout content;
    private Spinner projectSpinner;

    private TextView snippetsTab;
    private TextView gradleTab;

    private String selectedProject = "";
    private boolean showingGradle = false;

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
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
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
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

    private void addSpacing(
            LinearLayout parent, int height) {

        View space = new View(this);

        parent.addView(
                space,
                new LinearLayout.LayoutParams(
                        1, dp(height)
                )
        );
    }

    private void addCard(
            LinearLayout parent,
            LinearLayout card) {

        CodeBoxTheme.styleCard(card);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.bottomMargin = dp(12);
        parent.addView(card, params);
    }

    private TextView actionButton(
            String value, int color) {

        TextView button = text(
                value, 14, true
        );

        button.setGravity(Gravity.CENTER);

        CodeBoxTheme.styleColoredButton(
                button, color
        );

        button.setMinHeight(dp(46));

        return button;
    }

    private TextView secondaryButton(String value) {

        TextView button = text(
                value, 13, true
        );

        button.setGravity(Gravity.CENTER);
        button.setPadding(
                dp(12), dp(10),
                dp(12), dp(10)
        );

        button.setBackground(
                rounded(
                        CodeBoxTheme.isDark(this)
                                ? 0xFF353047
                                : 0xFFF0ECF9,
                        12
                )
        );

        return button;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        try {
            manager = new CodeLibraryManager(this);
            gradleManager =
                    new GradleProjectManager(this);

        } catch (IOException e) {
            showError(e.getMessage());
            return;
        }

        String initialProject = getIntent()
                .getStringExtra("project_name");

        if (initialProject != null) {
            selectedProject = initialProject;
        }

        buildScreen();
    }

    private void buildScreen() {

        root = column();

        root.setBackgroundColor(
                CodeBoxTheme.background(this)
        );

        createHeader();
        createProjectSelector();
        createTabs();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        content = column();

        content.setPadding(
                dp(16), dp(14),
                dp(16), dp(24)
        );

        scroll.addView(content);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        setContentView(root);
        refreshContent();
    }

    private void createHeader() {

        LinearLayout header =
                new LinearLayout(this);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(Gravity.CENTER_VERTICAL);

        header.setPadding(
                dp(16), dp(20),
                dp(16), dp(20)
        );

        header.setBackground(headerGradient());

        TextView back = text(
                "‹", 34, true
        );

        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);

        back.setBackground(
                rounded(0x33FFFFFF, 15)
        );

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(46), dp(46)
                )
        );

        back.setOnClickListener(v -> finish());

        LinearLayout labels = column();

        labels.setPadding(
                dp(14), 0, dp(8), 0
        );

        TextView title = text(
                "Библиотека кода",
                21,
                true
        );

        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);

        title.setEllipsize(
                TextUtils.TruncateAt.END
        );

        labels.addView(title);

        TextView subtitle = text(
                "ШАБЛОНЫ И GRADLE",
                11,
                true
        );

        subtitle.setTextColor(0xFFE8DDFF);

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        subParams.topMargin = dp(4);

        labels.addView(subtitle, subParams);

        header.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView icon = text(
                "📚", 23, false
        );

        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                rounded(0x33FFFFFF, 15)
        );

        header.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(46), dp(46)
                )
        );

        root.addView(header);
    }

    private void createProjectSelector() {

        LinearLayout wrapper = column();

        wrapper.setPadding(
                dp(16), dp(16),
                dp(16), dp(10)
        );

        LinearLayout card = column();

        card.setPadding(
                dp(14), dp(12),
                dp(14), dp(12)
        );

        CodeBoxTheme.styleCard(card);

        TextView label = text(
                "АКТИВНЫЙ ПРОЕКТ",
                11,
                true
        );

        label.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        card.addView(label);

        projectSpinner = new Spinner(this);

        LinearLayout.LayoutParams spinnerParams =
                new LinearLayout.LayoutParams(
                        -1, dp(48)
                );

        spinnerParams.topMargin = dp(4);

        card.addView(
                projectSpinner, spinnerParams
        );

        wrapper.addView(card);
        root.addView(wrapper);

        setupProjects();
    }

    private void setupProjects() {

        List<String> projects = new ArrayList<>();
        projects.add("Все проекты");

        File projectsRoot = new File(
                getFilesDir(), "projects"
        );

        File[] files = projectsRoot.listFiles();

        if (files != null) {
            Arrays.sort(
                    files,
                    (a, b) -> a.getName()
                            .compareToIgnoreCase(
                                    b.getName()
                            )
            );

            for (File file : files) {
                if (file.isDirectory()
                        && file.getName().matches(
                                "[A-Za-z][A-Za-z0-9_]{0,49}"
                        )) {
                    projects.add(file.getName());
                }
            }
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        projects
                );

        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item
        );

        projectSpinner.setAdapter(adapter);

        int initialPosition = 0;

        for (int i = 1; i < projects.size(); i++) {
            if (projects.get(i)
                    .equals(selectedProject)) {
                initialPosition = i;
                break;
            }
        }

        projectSpinner.setSelection(initialPosition);

        projectSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {

                selectedProject = position == 0
                        ? ""
                        : projects.get(position);

                refreshContent();
            }

            @Override
            public void onNothingSelected(
                    AdapterView<?> parent) {
            }
        });
    }

    private void createTabs() {

        LinearLayout wrapper =
                new LinearLayout(this);

        wrapper.setPadding(
                dp(16), dp(4),
                dp(16), dp(8)
        );

        LinearLayout tabs =
                new LinearLayout(this);

        tabs.setOrientation(
                LinearLayout.HORIZONTAL
        );

        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));

        tabs.setBackground(
                rounded(
                        CodeBoxTheme.surface(this),
                        16
                )
        );

        snippetsTab = text(
                "📄 Шаблоны",
                14,
                true
        );

        gradleTab = text(
                "📦 Gradle",
                14,
                true
        );

        snippetsTab.setGravity(Gravity.CENTER);
        gradleTab.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams tabParams1 =
                new LinearLayout.LayoutParams(
                        0, dp(46), 1
                );

        LinearLayout.LayoutParams tabParams2 =
                new LinearLayout.LayoutParams(
                        0, dp(46), 1
                );

        tabs.addView(snippetsTab, tabParams1);
        tabs.addView(gradleTab, tabParams2);

        wrapper.addView(
                tabs,
                new LinearLayout.LayoutParams(
                        -1, -2
                )
        );

        root.addView(wrapper);

        snippetsTab.setOnClickListener(v -> {
            showingGradle = false;
            refreshContent();
        });

        gradleTab.setOnClickListener(v -> {
            showingGradle = true;
            refreshContent();
        });

        updateTabs();
    }

    private void updateTabs() {

        if (snippetsTab == null
                || gradleTab == null) {
            return;
        }

        int inactiveText =
                CodeBoxTheme.secondaryText(this);

        snippetsTab.setBackground(
                showingGradle
                        ? rounded(
                                Color.TRANSPARENT, 12
                        )
                        : rounded(
                                CodeBoxTheme.PURPLE, 12
                        )
        );

        gradleTab.setBackground(
                showingGradle
                        ? rounded(
                                CodeBoxTheme.PURPLE, 12
                        )
                        : rounded(
                                Color.TRANSPARENT, 12
                        )
        );

        snippetsTab.setTextColor(
                showingGradle
                        ? inactiveText
                        : Color.WHITE
        );

        gradleTab.setTextColor(
                showingGradle
                        ? Color.WHITE
                        : inactiveText
        );
    }

    private void refreshContent() {

        if (content == null) {
            return;
        }

        content.removeAllViews();
        updateTabs();

        if (showingGradle) {
            showDependencies();
        } else {
            showSnippets();
        }
    }

    private void sectionHeading(
            String heading,
            String description) {

        TextView title = text(
                heading, 19, true
        );

        content.addView(title);

        TextView subtitle = text(
                description, 12, false
        );

        subtitle.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.topMargin = dp(5);
        params.bottomMargin = dp(16);

        content.addView(subtitle, params);
    }

    private void showEmpty(
            String symbol,
            String heading,
            String description) {

        LinearLayout card = column();

        card.setGravity(Gravity.CENTER);

        card.setPadding(
                dp(18), dp(34),
                dp(18), dp(34)
        );

        TextView icon = text(
                symbol, 40, false
        );

        icon.setGravity(Gravity.CENTER);
        card.addView(icon);

        TextView title = text(
                heading, 17, true
        );

        title.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        titleParams.topMargin = dp(12);

        card.addView(title, titleParams);

        TextView subtitle = text(
                description, 13, false
        );

        subtitle.setGravity(Gravity.CENTER);

        subtitle.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        subParams.topMargin = dp(8);

        card.addView(subtitle, subParams);

        addCard(content, card);
    }

    private void showSnippets() {

        sectionHeading(
                "Мои шаблоны",
                "Сохраняй фрагменты Java и XML "
                        + "для повторного использования"
        );

        TextView addButton = actionButton(
                "＋ Добавить шаблон",
                CodeBoxTheme.PURPLE
        );

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(
                        -1, dp(48)
                );

        addParams.bottomMargin = dp(16);

        content.addView(addButton, addParams);

        addButton.setOnClickListener(
                v -> showAddSnippetDialog()
        );

        try {
            List<CodeLibraryManager.Snippet> snippets =
                    manager.getSnippets(selectedProject);

            if (snippets.isEmpty()) {
                showEmpty(
                        "📄",
                        "Шаблонов пока нет",
                        "Добавь первый Java или XML шаблон"
                );
                return;
            }

            for (CodeLibraryManager.Snippet snippet
                    : snippets) {

                LinearLayout card = column();

                card.setPadding(
                        dp(16), dp(16),
                        dp(16), dp(16)
                );

                LinearLayout heading =
                        new LinearLayout(this);

                heading.setOrientation(
                        LinearLayout.HORIZONTAL
                );

                heading.setGravity(
                        Gravity.CENTER_VERTICAL
                );

                TextView symbol = text(
                        "java".equalsIgnoreCase(
                                snippet.language
                        ) ? "J" : "</>",
                        17,
                        true
                );

                symbol.setGravity(Gravity.CENTER);

                symbol.setTextColor(
                        "java".equalsIgnoreCase(
                                snippet.language
                        )
                                ? 0xFFDF7544
                                : CodeBoxTheme.BLUE
                );

                symbol.setBackground(
                        rounded(
                                CodeBoxTheme.isDark(this)
                                        ? 0xFF302B3E
                                        : 0xFFF2EEFA,
                                12
                        )
                );

                heading.addView(
                        symbol,
                        new LinearLayout.LayoutParams(
                                dp(46), dp(46)
                        )
                );

                LinearLayout labels = column();

                labels.setPadding(
                        dp(12), 0, 0, 0
                );

                TextView name = text(
                        snippet.title, 16, true
                );

                name.setMaxLines(2);
                labels.addView(name);

                TextView owner = text(
                        snippet.projectName.isEmpty()
                                ? "Общий шаблон"
                                : "Проект: "
                                        + snippet.projectName,
                        12,
                        false
                );

                owner.setTextColor(
                        CodeBoxTheme.secondaryText(this)
                );

                labels.addView(owner);

                heading.addView(
                        labels,
                        new LinearLayout.LayoutParams(
                                0, -2, 1
                        )
                );

                card.addView(heading);

                TextView preview = text(
                        snippet.code, 12, false
                );

                preview.setTypeface(Typeface.MONOSPACE);
                preview.setMaxLines(5);
                preview.setEllipsize(
                        TextUtils.TruncateAt.END
                );

                preview.setPadding(
                        dp(12), dp(12),
                        dp(12), dp(12)
                );

                preview.setBackground(
                        rounded(
                                CodeBoxTheme.isDark(this)
                                        ? 0xFF20222C
                                        : 0xFFF4F5F9,
                                12
                        )
                );

                LinearLayout.LayoutParams previewParams =
                        new LinearLayout.LayoutParams(
                                -1, -2
                        );

                previewParams.topMargin = dp(14);
                previewParams.bottomMargin = dp(12);

                card.addView(preview, previewParams);

                LinearLayout actions =
                        new LinearLayout(this);

                actions.setOrientation(
                        LinearLayout.HORIZONTAL
                );

                TextView viewButton =
                        secondaryButton("Посмотреть");

                TextView deleteButton =
                        secondaryButton("Удалить");

                LinearLayout.LayoutParams left =
                        new LinearLayout.LayoutParams(
                                0, dp(44), 1
                        );

                left.rightMargin = dp(8);

                actions.addView(viewButton, left);

                actions.addView(
                        deleteButton,
                        new LinearLayout.LayoutParams(
                                0, dp(44), 1
                        )
                );

                card.addView(actions);
                addCard(content, card);

                viewButton.setOnClickListener(
                        v -> new AlertDialog.Builder(this)
                                .setTitle(snippet.title)
                                .setMessage(snippet.code)
                                .setPositiveButton(
                                        "Закрыть", null
                                )
                                .show()
                );

                deleteButton.setOnClickListener(
                        v -> confirmDeleteSnippet(snippet)
                );
            }

        } catch (IOException e) {
            showToast(e.getMessage());
        }
    }

    private void showAddSnippetDialog() {

        LinearLayout layout = column();

        layout.setPadding(
                dp(20), dp(10),
                dp(20), dp(4)
        );

        EditText name = new EditText(this);
        name.setHint("Название шаблона");
        name.setSingleLine(true);
        layout.addView(name);

        Spinner language = new Spinner(this);

        String[] languages = {"java", "xml"};

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        languages
                );

        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item
        );

        language.setAdapter(adapter);
        layout.addView(language);

        EditText code = new EditText(this);

        code.setHint("Вставь код шаблона");
        code.setMinLines(5);
        code.setGravity(Gravity.TOP);
        code.setTypeface(Typeface.MONOSPACE);

        code.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );

        layout.addView(code);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Новый шаблон")
                        .setView(layout)
                        .setPositiveButton(
                                "Сохранить", null
                        )
                        .setNegativeButton(
                                "Отмена", null
                        )
                        .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(v -> {
                    try {
                        manager.addSnippet(
                                name.getText().toString(),
                                language
                                        .getSelectedItem()
                                        .toString(),
                                code.getText().toString(),
                                selectedProject
                        );

                        dialog.dismiss();
                        refreshContent();

                    } catch (IOException e) {
                        showToast(e.getMessage());
                    }
                })
        );

        dialog.show();
    }

    private void confirmDeleteSnippet(
            CodeLibraryManager.Snippet snippet) {

        new AlertDialog.Builder(this)
                .setTitle("Удалить шаблон?")
                .setMessage(snippet.title)
                .setPositiveButton(
                        "Удалить",
                        (dialog, which) -> {
                            try {
                                manager.deleteSnippet(
                                        snippet.id
                                );

                                refreshContent();

                            } catch (IOException e) {
                                showToast(e.getMessage());
                            }
                        }
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showDependencies() {

        sectionHeading(
                "Gradle-библиотеки",
                "Управление зависимостями "
                        + "Android-проекта"
        );

        if (selectedProject.isEmpty()) {
            showEmpty(
                    "📦",
                    "Проект не выбран",
                    "Выбери конкретный проект "
                            + "в списке выше"
            );
            return;
        }

        LinearLayout tools = column();

        tools.setPadding(
                dp(14), dp(16),
                dp(14), dp(16)
        );

        TextView project = text(
                "Проект: " + selectedProject,
                15,
                true
        );

        tools.addView(project);

        addSpacing(tools, 12);

        TextView createGradleButton = actionButton(
                "🛠 Создать Gradle-структуру",
                CodeBoxTheme.BLUE
        );

        tools.addView(
                createGradleButton,
                new LinearLayout.LayoutParams(
                        -1, dp(48)
                )
        );

        addSpacing(tools, 10);

        TextView applyButton = actionButton(
                "✓ Применить зависимости",
                CodeBoxTheme.PURPLE
        );

        tools.addView(
                applyButton,
                new LinearLayout.LayoutParams(
                        -1, dp(48)
                )
        );

        addSpacing(tools, 12);

        TextView hint = text(
                "Gradle-файлы создаются только при "
                        + "их отсутствии. Применение "
                        + "добавляет зависимости в "
                        + "app/build.gradle.",
                12,
                false
        );

        hint.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        tools.addView(hint);

        addCard(content, tools);

        createGradleButton.setOnClickListener(
                v -> confirmCreateGradle()
        );

        applyButton.setOnClickListener(
                v -> confirmApplyDependencies()
        );

        TextView addButton = actionButton(
                "＋ Добавить Gradle-библиотеку",
                CodeBoxTheme.PURPLE
        );

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(
                        -1, dp(48)
                );

        addParams.bottomMargin = dp(16);

        content.addView(addButton, addParams);

        addButton.setOnClickListener(
                v -> showAddDependencyDialog()
        );

        try {
            List<String> dependencies =
                    manager.getDependencies(
                            selectedProject
                    );

            if (dependencies.isEmpty()) {
                showEmpty(
                        "📦",
                        "Зависимостей пока нет",
                        "Добавь библиотеку в формате "
                                + "group:artifact:version"
                );
                return;
            }

            for (String dependency : dependencies) {

                LinearLayout card = column();

                card.setPadding(
                        dp(16), dp(14),
                        dp(16), dp(14)
                );

                TextView label = text(
                        "GRADLE DEPENDENCY",
                        11,
                        true
                );

                label.setTextColor(
                        CodeBoxTheme.secondaryText(this)
                );

                card.addView(label);

                TextView name = text(
                        dependency, 13, true
                );

                name.setTypeface(Typeface.MONOSPACE);
                name.setTextIsSelectable(true);

                LinearLayout.LayoutParams nameParams =
                        new LinearLayout.LayoutParams(
                                -1, -2
                        );

                nameParams.topMargin = dp(8);
                nameParams.bottomMargin = dp(12);

                card.addView(name, nameParams);

                TextView deleteButton =
                        secondaryButton(
                                "Удалить из библиотеки"
                        );

                card.addView(
                        deleteButton,
                        new LinearLayout.LayoutParams(
                                -1, dp(44)
                        )
                );

                addCard(content, card);

                deleteButton.setOnClickListener(
                        v -> confirmDeleteDependency(
                                dependency
                        )
                );
            }

        } catch (IOException e) {
            showToast(e.getMessage());
        }
    }

    private void confirmCreateGradle() {

        final String projectName = selectedProject;

        if (projectName.isEmpty()) {
            showToast("Сначала выбери проект");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Создать Gradle-структуру?")
                .setMessage(
                        "Проект: " + projectName
                                + "\n\nБудут созданы "
                                + "недостающие Gradle-файлы "
                                + "и AndroidManifest.xml."
                                + "\n\nСуществующие файлы "
                                + "не будут перезаписаны."
                )
                .setPositiveButton(
                        "Создать",
                        (dialog, which) -> {
                            try {
                                gradleManager
                                        .ensureGradleStructure(
                                                projectName
                                        );

                                showToast(
                                        "Gradle-структура проверена"
                                );

                                refreshContent();

                            } catch (IOException e) {
                                showToast(
                                        "Ошибка: "
                                                + e.getMessage()
                                );
                            }
                        }
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmApplyDependencies() {

        final String projectName = selectedProject;

        if (projectName.isEmpty()) {
            showToast("Сначала выбери проект");
            return;
        }

        final List<String> dependencies;

        try {
            dependencies = manager.getDependencies(
                    projectName
            );
        } catch (IOException e) {
            showToast(e.getMessage());
            return;
        }

        if (dependencies.isEmpty()) {
            showToast("Сначала добавь зависимости");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Применить зависимости?")
                .setMessage(
                        "Проект: " + projectName
                                + "\nЗависимостей в библиотеке: "
                                + dependencies.size()
                                + "\n\nОни будут добавлены "
                                + "в app/build.gradle."
                                + "\n\nПеред изменением "
                                + "будет создана резервная "
                                + "копия, если её ещё нет."
                )
                .setPositiveButton(
                        "Применить",
                        (dialog, which) -> {
                            try {
                                int added = gradleManager
                                        .applyDependencies(
                                                projectName,
                                                dependencies
                                        );

                                new AlertDialog.Builder(this)
                                        .setTitle(
                                                "Gradle обновлён"
                                        )
                                        .setMessage(
                                                "Добавлено новых "
                                                        + "зависимостей: "
                                                        + added
                                                        + "\n\n"
                                                        + "Уже найденные "
                                                        + "зависимости "
                                                        + "пропущены."
                                        )
                                        .setPositiveButton(
                                                "OK", null
                                        )
                                        .show();

                            } catch (IOException e) {
                                showToast(
                                        "Ошибка Gradle: "
                                                + e.getMessage()
                                );
                            }
                        }
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showAddDependencyDialog() {

        final String projectName = selectedProject;

        EditText input = new EditText(this);

        input.setHint("group:artifact:version");
        input.setSingleLine(true);
        input.setTypeface(Typeface.MONOSPACE);

        LinearLayout wrapper = column();

        wrapper.setPadding(
                dp(20), dp(8),
                dp(20), dp(4)
        );

        wrapper.addView(input);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Добавить Gradle-библиотеку"
                        )
                        .setMessage(
                                "Проект: " + projectName
                        )
                        .setView(wrapper)
                        .setPositiveButton(
                                "Добавить", null
                        )
                        .setNegativeButton(
                                "Отмена", null
                        )
                        .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(v -> {
                    try {
                        manager.addDependency(
                                projectName,
                                input.getText()
                                        .toString()
                                        .trim()
                        );

                        dialog.dismiss();
                        refreshContent();

                    } catch (IOException e) {
                        showToast(e.getMessage());
                    }
                })
        );

        dialog.show();
    }

    private void confirmDeleteDependency(
            String dependency) {

        final String projectName = selectedProject;

        new AlertDialog.Builder(this)
                .setTitle("Удалить зависимость?")
                .setMessage(
                        dependency
                                + "\n\nУдаление из библиотеки "
                                + "не меняет build.gradle."
                )
                .setPositiveButton(
                        "Удалить",
                        (dialog, which) -> {
                            try {
                                manager.removeDependency(
                                        projectName,
                                        dependency
                                );

                                refreshContent();

                            } catch (IOException e) {
                                showToast(e.getMessage());
                            }
                        }
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(
                this,
                message == null
                        ? "Неизвестная ошибка"
                        : message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Ошибка библиотеки")
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
}
