package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CodeLibraryActivity extends Activity {

    private CodeLibraryManager manager;

    private LinearLayout root;
    private LinearLayout content;

    private Spinner projectSpinner;

    private String selectedProject = "";
    private boolean showingGradle = false;

    private int dp(int value) {
        return (int) (
                value * getResources()
                        .getDisplayMetrics().density
                        + 0.5f
        );
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        try {
            manager = new CodeLibraryManager(this);
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

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F5F8);

        TextView title = new TextView(this);
        title.setText("📚 Библиотека кода");
        title.setTextSize(22);
        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        title.setTextColor(0xFF222233);
        title.setPadding(
                dp(16), dp(18),
                dp(16), dp(12)
        );
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(
                "Шаблоны Java/XML и Gradle-библиотеки"
        );
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF777788);
        subtitle.setPadding(
                dp(16), 0,
                dp(16), dp(12)
        );
        root.addView(subtitle);

        TextView projectLabel = new TextView(this);
        projectLabel.setText("Выбранный проект");
        projectLabel.setTextSize(14);
        projectLabel.setPadding(
                dp(16), dp(8),
                dp(16), dp(4)
        );
        root.addView(projectLabel);

        projectSpinner = new Spinner(this);

        root.addView(
                projectSpinner,
                new LinearLayout.LayoutParams(
                        -1, dp(52)
                )
        );

        setupProjects();

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);

        Button snippetsButton = new Button(this);
        snippetsButton.setText("📄 Шаблоны");
        snippetsButton.setAllCaps(false);

        Button gradleButton = new Button(this);
        gradleButton.setText("📦 Gradle");
        gradleButton.setAllCaps(false);

        tabs.addView(
                snippetsButton,
                new LinearLayout.LayoutParams(
                        0, dp(52), 1
                )
        );

        tabs.addView(
                gradleButton,
                new LinearLayout.LayoutParams(
                        0, dp(52), 1
                )
        );

        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(
                LinearLayout.VERTICAL
        );
        content.setPadding(
                dp(12), dp(12),
                dp(12), dp(20)
        );

        scroll.addView(content);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        Button backButton = new Button(this);
        backButton.setText("← Назад");
        backButton.setAllCaps(false);

        root.addView(
                backButton,
                new LinearLayout.LayoutParams(
                        -1, dp(52)
                )
        );

        snippetsButton.setOnClickListener(v -> {
            showingGradle = false;
            refreshContent();
        });

        gradleButton.setOnClickListener(v -> {
            showingGradle = true;
            refreshContent();
        });

        backButton.setOnClickListener(v -> finish());

        setContentView(root);

        refreshContent();
    }

    private void setupProjects() {

        List<String> projects = new ArrayList<>();

        projects.add("Все проекты");

        File projectsRoot = new File(
                getFilesDir(), "projects"
        );

        File[] files = projectsRoot.listFiles();

        if (files != null) {
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
                        android.R.layout
                                .simple_spinner_item,
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
                new android.widget.AdapterView
                        .OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    android.widget.AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {

                selectedProject = position == 0
                        ? ""
                        : projects.get(position);

                if (content != null) {
                    refreshContent();
                }
            }

            @Override
            public void onNothingSelected(
                    android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void refreshContent() {

        if (content == null) {
            return;
        }

        content.removeAllViews();

        if (showingGradle) {
            showDependencies();
        } else {
            showSnippets();
        }
    }

    private TextView createLabel(String text) {

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTextColor(0xFF222233);
        label.setPadding(
                dp(8), dp(10),
                dp(8), dp(10)
        );

        return label;
    }

    private Button createButton(String text) {

        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);

        return button;
    }

    private void showSnippets() {

        Button addButton = createButton(
                "+ Добавить шаблон"
        );

        content.addView(addButton);

        addButton.setOnClickListener(
                v -> showAddSnippetDialog()
        );

        try {
            List<CodeLibraryManager.Snippet> snippets =
                    manager.getSnippets(selectedProject);

            if (snippets.isEmpty()) {
                content.addView(
                        createLabel(
                                "Шаблонов пока нет"
                        )
                );
                return;
            }

            for (CodeLibraryManager.Snippet snippet
                    : snippets) {

                LinearLayout item =
                        new LinearLayout(this);

                item.setOrientation(
                        LinearLayout.VERTICAL
                );
                item.setPadding(
                        dp(12), dp(10),
                        dp(12), dp(10)
                );
                item.setBackgroundColor(Color.WHITE);

                TextView name = createLabel(
                        snippet.title + "  [" +
                                snippet.language + "]"
                );

                name.setTypeface(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                );

                item.addView(name);

                TextView owner = createLabel(
                        snippet.projectName.isEmpty()
                                ? "Доступен всем проектам"
                                : "Проект: " +
                                        snippet.projectName
                );

                owner.setTextSize(12);
                owner.setTextColor(0xFF777788);

                item.addView(owner);

                TextView preview = createLabel(
                        snippet.code
                );

                preview.setTypeface(
                        Typeface.MONOSPACE
                );
                preview.setTextSize(12);
                preview.setMaxLines(5);

                item.addView(preview);

                Button viewButton = createButton(
                        "Посмотреть код"
                );

                Button deleteButton = createButton(
                        "Удалить"
                );

                item.addView(viewButton);
                item.addView(deleteButton);

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

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                -1, -2
                        );

                params.bottomMargin = dp(12);

                content.addView(item, params);
            }

        } catch (IOException e) {
            showToast(e.getMessage());
        }
    }

    private void showAddSnippetDialog() {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                dp(20), dp(10),
                dp(20), dp(4)
        );

        EditText name = new EditText(this);
        name.setHint("Название шаблона");
        name.setSingleLine(true);

        layout.addView(name);

        Spinner language = new Spinner(this);

        String[] languages = {
                "java", "xml"
        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_spinner_item,
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

        new AlertDialog.Builder(this)
                .setTitle("Новый шаблон")
                .setView(layout)
                .setPositiveButton(
                        "Сохранить",
                        (dialog, which) -> {
                            try {
                                manager.addSnippet(
                                        name.getText().toString(),
                                        language
                                                .getSelectedItem()
                                                .toString(),
                                        code.getText().toString(),
                                        selectedProject
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

        if (selectedProject.isEmpty()) {
            content.addView(
                    createLabel(
                            "Выбери проект, чтобы управлять " +
                                    "его Gradle-библиотеками"
                    )
            );
            return;
        }

        Button addButton = createButton(
                "+ Добавить Gradle-библиотеку"
        );

        content.addView(addButton);

        addButton.setOnClickListener(
                v -> showAddDependencyDialog()
        );

        try {
            List<String> dependencies =
                    manager.getDependencies(
                            selectedProject
                    );

            if (dependencies.isEmpty()) {
                content.addView(
                        createLabel(
                                "Зависимостей пока нет"
                        )
                );
                return;
            }

            for (String dependency : dependencies) {

                LinearLayout item =
                        new LinearLayout(this);

                item.setOrientation(
                        LinearLayout.VERTICAL
                );
                item.setPadding(
                        dp(10), dp(10),
                        dp(10), dp(10)
                );
                item.setBackgroundColor(Color.WHITE);

                TextView name = createLabel(dependency);
                name.setTypeface(Typeface.MONOSPACE);
                name.setTextIsSelectable(true);

                item.addView(name);

                Button deleteButton = createButton(
                        "Удалить зависимость"
                );

                item.addView(deleteButton);

                deleteButton.setOnClickListener(
                        v -> confirmDeleteDependency(
                                dependency
                        )
                );

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                -1, -2
                        );

                params.bottomMargin = dp(10);

                content.addView(item, params);
            }

        } catch (IOException e) {
            showToast(e.getMessage());
        }
    }

    private void showAddDependencyDialog() {

        EditText input = new EditText(this);

        input.setHint(
                "group:artifact:version"
        );

        input.setSingleLine(true);
        input.setTypeface(Typeface.MONOSPACE);

        new AlertDialog.Builder(this)
                .setTitle("Добавить Gradle-библиотеку")
                .setMessage(
                        "Проект: " + selectedProject
                )
                .setView(input)
                .setPositiveButton(
                        "Добавить",
                        (dialog, which) -> {
                            try {
                                manager.addDependency(
                                        selectedProject,
                                        input.getText()
                                                .toString()
                                                .trim()
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

    private void confirmDeleteDependency(
            String dependency) {

        new AlertDialog.Builder(this)
                .setTitle("Удалить зависимость?")
                .setMessage(dependency)
                .setPositiveButton(
                        "Удалить",
                        (dialog, which) -> {
                            try {
                                manager.removeDependency(
                                        selectedProject,
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
                .setMessage(message)
                .setPositiveButton(
                        "Закрыть",
                        (dialog, which) -> finish()
                )
                .setCancelable(false)
                .show();
    }
}
