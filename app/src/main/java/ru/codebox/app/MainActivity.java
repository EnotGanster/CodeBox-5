package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Arrays;

public class MainActivity extends Activity
        implements HomeScreen.Listener {

    private static final int HOME = 0;
    private static final int PROJECTS = 1;
    private static final int TREE = 2;
    private static final int EXPORT_PROJECT_INFO = 6102;
    private String pendingProjectReport;

    private ProjectManager projectManager;
    private int currentPage = HOME;

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        projectManager = new ProjectManager(this);

        if (state != null) {
            currentPage = state.getInt(
                    "current_page", HOME
            );
        }

        refreshPage();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("current_page", currentPage);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (projectManager != null) {
            refreshPage();
        }
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(
            String value,
            int size,
            boolean bold) {

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

    private void refreshPage() {
        if (currentPage == PROJECTS) {
            showProjects();
        } else if (currentPage == TREE) {
            showTreeProjects();
        } else {
            showHome();
        }
    }

    private void showHome() {
        currentPage = HOME;

        HomeScreen home = new HomeScreen(
                this, this
        );

        setContentView(
                home.create(
                        projectManager.getProjects().length
                )
        );
    }

    private LinearLayout createPage(
            String title,
            String subtitle) {

        LinearLayout root = column();

        root.setBackgroundColor(
                CodeBoxTheme.background(this)
        );

        LinearLayout header = column();

        header.setPadding(
                dp(20), dp(18),
                dp(20), dp(20)
        );

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 34, true);
        back.setGravity(Gravity.CENTER);

        back.setBackground(
                CodeBoxTheme.rounded(
                        CodeBoxTheme.surface(this),
                        18,
                        this
                )
        );

        top.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(48), dp(48)
                )
        );

        back.setOnClickListener(
                v -> showHome()
        );

        LinearLayout headings = column();
        headings.setPadding(
                dp(14), 0, 0, 0
        );

        TextView heading = text(
                title, 23, true
        );

        heading.setSingleLine(true);
        heading.setEllipsize(
                TextUtils.TruncateAt.END
        );

        headings.addView(heading);

        TextView description = text(
                subtitle, 12, false
        );

        description.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        subParams.topMargin = dp(3);

        headings.addView(
                description, subParams
        );

        top.addView(
                headings,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        header.addView(top);

        root.addView(header);

        return root;
    }

    private File[] sortedProjects() {
        File[] projects =
                projectManager.getProjects();

        Arrays.sort(
                projects,
                (a, b) -> a.getName()
                        .compareToIgnoreCase(
                                b.getName()
                        )
        );

        return projects;
    }

    private TextView createSectionTitle(
            String title) {

        TextView section = text(
                title, 17, true
        );

        section.setPadding(
                dp(2), dp(10),
                dp(2), dp(14)
        );

        return section;
    }

    private void showProjects() {
        currentPage = PROJECTS;

        File[] projects = sortedProjects();

        LinearLayout root = createPage(
                "Мои проекты",
                "Все Android-проекты в одном месте"
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout list = column();

        list.setPadding(
                dp(18), dp(4),
                dp(18), dp(20)
        );

        TextView counter = text(
                "Всего проектов: " + projects.length,
                14,
                true
        );

        counter.setTextColor(
                CodeBoxTheme.PURPLE
        );

        counter.setPadding(
                dp(4), dp(4),
                dp(4), dp(12)
        );

        list.addView(counter);

        list.addView(
                createSectionTitle(
                        "Рабочее пространство"
                )
        );

        if (projects.length == 0) {
            addEmptyMessage(
                    list,
                    "📂",
                    "Пока нет проектов",
                    "Создай первый Android-проект, "
                    + "чтобы начать работу"
            );
        }

        for (File project : projects) {
            addProjectCard(list, project);
        }

        scroll.addView(list);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        LinearLayout bottom = column();

        bottom.setPadding(
                dp(20), dp(10),
                dp(20), dp(20)
        );

        TextView create = text(
                "＋  Создать проект",
                18,
                true
        );

        CodeBoxTheme.styleColoredButton(
                create,
                CodeBoxTheme.PURPLE
        );

        create.setGravity(Gravity.CENTER);

        bottom.addView(
                create,
                new LinearLayout.LayoutParams(
                        -1, dp(60)
                )
        );

        create.setOnClickListener(
                v -> showCreateProjectDialog()
        );

        root.addView(bottom);

        setContentView(root);
    }

    private void addProjectCard(
            LinearLayout list,
            File project) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.HORIZONTAL
        );

        card.setGravity(
                Gravity.CENTER_VERTICAL
        );

        card.setPadding(
                dp(14), dp(16),
                dp(14), dp(16)
        );

        CodeBoxTheme.styleCard(card);

        TextView icon = text(
                "📱", 28, false
        );

        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                CodeBoxTheme.rounded(
                        CodeBoxTheme.isDark(this)
                                ? 0xFF34264E
                                : 0xFFF0E9FF,
                        18,
                        this
                )
        );

        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(62), dp(62)
                )
        );

        LinearLayout details = column();

        details.setPadding(
                dp(14), 0,
                dp(6), 0
        );

        TextView name = text(
                project.getName(),
                19,
                true
        );

        name.setSingleLine(true);
        name.setEllipsize(
                TextUtils.TruncateAt.END
        );

        details.addView(name);

        File[] children = project.listFiles();

        int count = children == null
                ? 0
                : children.length;

        TextView description = text(
                "Android Java · " + count
                + " элементов",
                13,
                false
        );

        description.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams descParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        descParams.topMargin = dp(6);

        details.addView(
                description, descParams
        );

        TextView status = text(
                "Открыть проект",
                12,
                true
        );

        status.setTextColor(
                CodeBoxTheme.PURPLE
        );

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        statusParams.topMargin = dp(8);

        details.addView(
                status, statusParams
        );

        card.addView(
                details,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView arrow = text(
                "›", 30, false
        );

        arrow.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout actions = column();
        actions.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView dots = text("⋮", 26, true);
        dots.setGravity(Gravity.CENTER);
        dots.setContentDescription("Действия с проектом " + project.getName());
        actions.addView(dots, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(44), dp(38));
        arrowParams.topMargin = dp(12);
        arrow.setGravity(Gravity.CENTER);
        actions.addView(arrow, arrowParams);
        card.addView(actions);
        dots.setOnClickListener(v -> showProjectActions(dots, project));
        arrow.setOnClickListener(v -> openProject(project.getName()));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.bottomMargin = dp(12);

        list.addView(card, params);

        card.setOnClickListener(
                v -> openProject(
                        project.getName()
                )
        );
    }

    private File safeProject(File project) throws IOException {
        String name = project.getName();
        if (!name.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
            throw new IOException("Недопустимое имя проекта");
        }
        File root = new File(getFilesDir(), "projects").getCanonicalFile();
        File candidate = new File(root, name);
        if (Files.isSymbolicLink(candidate.toPath())) {
            throw new IOException("Ссылка вместо папки проекта");
        }
        File canonical = candidate.getCanonicalFile();
        if (!root.equals(canonical.getParentFile()) || !canonical.isDirectory()) {
            throw new IOException("Папка проекта не найдена");
        }
        return canonical;
    }

    private void showProjectActions(View anchor, File project) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Информация о проекте");
        menu.getMenu().add(0, 2, 1, "Сохранить информацию (TXT)");
        menu.getMenu().add(0, 3, 2, "Удалить проект");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) showProjectInfo(project);
            else if (item.getItemId() == 2) exportProjectInfo(project);
            else if (item.getItemId() == 3) confirmDeleteProject(project);
            return true;
        });
        menu.show();
    }

    private String projectReport(File project) throws IOException {
        File directory = safeProject(project);
        long bytes = 0;
        int files = 0;
        int folders = 0;
        ArrayDeque<File> stack = new ArrayDeque<>();
        stack.push(directory);
        int visited = 0;
        while (!stack.isEmpty()) {
            File folder = stack.pop();
            if (++visited > 100000) throw new IOException("Слишком много элементов");
            File[] children = folder.listFiles();
            if (children == null) throw new IOException("Не удалось прочитать папку: " + folder.getName());
            for (File child : children) {
                if (Files.isSymbolicLink(child.toPath())) continue;
                if (child.isDirectory()) {
                    folders++;
                    stack.push(child);
                } else if (child.isFile()) {
                    files++;
                    bytes += child.length();
                }
            }
        }
        return "CodeBox 5 — информация о проекте\n"
                + "Название: " + directory.getName() + "\n"
                + "Тип: Android Java\n"
                + "Путь: " + directory.getAbsolutePath() + "\n"
                + "Файлов: " + files + "\n"
                + "Папок: " + folders + "\n"
                + "Размер файлов: " + bytes + " байт\n"
                + "Изменён: " + new Date(directory.lastModified()) + "\n"
                + "Отчёт создан: " + new Date() + "\n";
    }

    private void showProjectInfo(File project) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("Информация: " + project.getName())
                    .setMessage(projectReport(project))
                    .setPositiveButton("Закрыть", null)
                    .setNeutralButton("Сохранить TXT", (d, w) -> exportProjectInfo(project))
                    .show();
        } catch (IOException e) {
            showProjectError(e);
        }
    }

    private void exportProjectInfo(File project) {
        try {
            pendingProjectReport = projectReport(project);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, project.getName() + "_info.txt");
            startActivityForResult(intent, EXPORT_PROJECT_INFO);
        } catch (Exception e) {
            pendingProjectReport = null;
            showProjectError(e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_PROJECT_INFO) return;
        String report = pendingProjectReport;
        pendingProjectReport = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || report == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IOException("Не удалось открыть файл");
            out.write(report.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Информация сохранена", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showProjectError(e);
        }
    }

    private void confirmDeleteProject(File project) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить проект " + project.getName() + "?")
                .setMessage("Все файлы проекта будут удалены без возможности восстановления. Сначала сохрани нужные данные.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (d, w) -> {
                    try {
                        File directory = safeProject(project);
                        deleteProjectTree(directory);
                        Toast.makeText(this, "Проект удалён: " + project.getName(), Toast.LENGTH_SHORT).show();
                        showProjects();
                    } catch (IOException e) {
                        showProjectError(e);
                        showProjects();
                    }
                })
                .show();
    }

    private void deleteProjectTree(File root) throws IOException {
        // Do not traverse symlinks. Limit traversal to the selected project.
        ArrayDeque<File> pending = new ArrayDeque<>();
        ArrayDeque<File> reverse = new ArrayDeque<>();
        pending.push(root);
        int visited = 0;
        while (!pending.isEmpty()) {
            File current = pending.pop();
            if (++visited > 100000) throw new IOException("Превышен лимит элементов");
            reverse.push(current);
            if (Files.isSymbolicLink(current.toPath())) continue;
            if (current.isDirectory()) {
                File[] children = current.listFiles();
                if (children == null) throw new IOException("Ошибка чтения: " + current.getName());
                for (File child : children) pending.push(child);
            }
        }
        while (!reverse.isEmpty()) {
            File current = reverse.pop();
            if (!current.delete()) throw new IOException("Не удалось удалить: " + current.getName());
        }
    }

    private void showProjectError(Exception e) {
        Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void showTreeProjects() {
        currentPage = TREE;

        File[] projects = sortedProjects();

        LinearLayout root = createPage(
                "Дерево проектов",
                "Просмотр файлов и структуры"
        );

        ScrollView scroll = new ScrollView(this);

        LinearLayout list = column();

        list.setPadding(
                dp(18), dp(4),
                dp(18), dp(20)
        );

        list.addView(
                createSectionTitle(
                        "Выбери проект"
                )
        );

        if (projects.length == 0) {
            addEmptyMessage(
                    list,
                    "🌳",
                    "Нет проектов",
                    "Сначала создай Android-проект"
            );
        }

        for (File project : projects) {
            addTreeCard(list, project);
        }

        scroll.addView(list);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );

        setContentView(root);
    }

    private void addTreeCard(
            LinearLayout list,
            File project) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.HORIZONTAL
        );

        card.setGravity(
                Gravity.CENTER_VERTICAL
        );

        card.setPadding(
                dp(14), dp(14),
                dp(14), dp(14)
        );

        CodeBoxTheme.styleCard(card);

        TextView icon = text(
                "🌳", 28, false
        );

        icon.setGravity(Gravity.CENTER);

        icon.setBackground(
                CodeBoxTheme.rounded(
                        CodeBoxTheme.isDark(this)
                                ? 0xFF263E35
                                : 0xFFE8F5EB,
                        18,
                        this
                )
        );

        card.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(62), dp(62)
                )
        );

        LinearLayout labels = column();

        labels.setPadding(
                dp(14), 0,
                dp(4), 0
        );

        TextView name = text(
                project.getName(),
                18,
                true
        );

        name.setSingleLine(true);
        name.setEllipsize(
                TextUtils.TruncateAt.END
        );

        labels.addView(name);

        TextView description = text(
                "Посмотреть структуру файлов",
                12,
                false
        );

        description.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams descParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        descParams.topMargin = dp(5);

        labels.addView(
                description, descParams
        );

        card.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView arrow = text(
                "›", 28, false
        );

        arrow.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        card.addView(arrow);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        params.bottomMargin = dp(12);

        list.addView(card, params);

        card.setOnClickListener(
                v -> openProjectTree(
                        project.getName()
                )
        );
    }

    private void addEmptyMessage(
            LinearLayout list,
            String emoji,
            String title,
            String description) {

        LinearLayout card = column();

        card.setGravity(Gravity.CENTER);

        card.setPadding(
                dp(18), dp(36),
                dp(18), dp(36)
        );

        CodeBoxTheme.styleCard(card);

        TextView icon = text(
                emoji, 42, false
        );

        icon.setGravity(Gravity.CENTER);
        card.addView(icon);

        TextView heading = text(
                title, 19, true
        );

        heading.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        titleParams.topMargin = dp(12);

        card.addView(
                heading, titleParams
        );

        TextView message = text(
                description, 14, false
        );

        message.setGravity(Gravity.CENTER);

        message.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        LinearLayout.LayoutParams msgParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        msgParams.topMargin = dp(8);

        card.addView(
                message, msgParams
        );

        list.addView(card);
    }

    private void openProjectTree(String name) {
        Intent intent = new Intent(
                this,
                ProjectTreeActivity.class
        );

        intent.putExtra(
                "project_name",
                name
        );

        startActivity(intent);
    }

    private void showCreateProjectDialog() {
        EditText input = new EditText(this);

        input.setHint("Например: MyApp");
        input.setSingleLine(true);

        LinearLayout wrapper = column();

        wrapper.setPadding(
                dp(24), dp(12),
                dp(24), dp(8)
        );

        wrapper.addView(input);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Новый Android-проект"
                        )
                        .setMessage(
                                "Укажи название проекта "
                                + "латинскими буквами"
                        )
                        .setView(wrapper)
                        .setPositiveButton(
                                "Создать", null
                        )
                        .setNegativeButton(
                                "Отмена", null
                        )
                        .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

                String name = input.getText()
                        .toString()
                        .trim();

                if (!name.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {
                    input.setError(
                            "Используй латинские буквы, "
                            + "цифры и _"
                    );
                    return;
                }

                try {
                    projectManager.createProject(name);

                    dialog.dismiss();

                    Toast.makeText(
                            this,
                            "Проект создан: " + name,
                            Toast.LENGTH_SHORT
                    ).show();

                    openProject(name);

                } catch (IOException e) {
                    input.setError(
                            e.getMessage() == null
                                    ? "Ошибка создания"
                                    : e.getMessage()
                    );
                }
            });
        });

        dialog.show();
    }

    private void openProject(String name) {
        Intent intent = new Intent(
                this,
                ProjectActivity.class
        );

        intent.putExtra(
                "project_name",
                name
        );

        startActivity(intent);
    }

    private void openCodeLibrary() {
        Intent intent = new Intent(
                this,
                CodeLibraryActivity.class
        );

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Не удалось открыть библиотеку: "
                    + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onCreateProject() {
        showCreateProjectDialog();
    }

    @Override
    public void onOpenProjects() {
        showProjects();
    }

    @Override
    public void onOpenProjectTree() {
        showTreeProjects();
    }

    @Override
    public void onOpenCodeLibrary() {
        openCodeLibrary();
    }

    @Override
    public void onCombineCode() {
        showComingSoon(
                "Объединить код"
        );
    }

    @Override
    public void onOpenGraphics() {
        showComingSoon(
                "Ярлыки и графика"
        );
    }

    /** Opens the local build screen for a selected project. */
    @Override
    public void onOpenBuild() {
        File[] projects = sortedProjects();

        if (projects.length == 0) {
            Toast.makeText(
                    this,
                    "Сначала создай Android-проект",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (projects.length == 1) {
            openBuildScreen(projects[0].getName());
            return;
        }

        String[] names = new String[projects.length];
        for (int i = 0; i < projects.length; i++) {
            names[i] = projects[i].getName();
        }

        new AlertDialog.Builder(this)
                .setTitle("Выбери проект для сборки")
                .setItems(names, (dialog, which) -> {
                    if (which >= 0 && which < projects.length) {
                        openBuildScreen(projects[which].getName());
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void openBuildScreen(String projectName) {
        Intent intent = new Intent(
                this,
                CodeBoxBuildActivity.class
        );
        intent.putExtra("project_name", projectName);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Не удалось открыть экран сборки: "
                            + (e.getMessage() == null
                            ? "неизвестная ошибка"
                            : e.getMessage()),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showComingSoon(
            String feature) {

        new AlertDialog.Builder(this)
                .setTitle(feature)
                .setMessage(
                        "Этот инструмент появится "
                        + "в следующих версиях CodeBox 5."
                )
                .setPositiveButton(
                        "Понятно", null
                )
                .show();
    }

    @Override
    public void onBackPressed() {
        if (currentPage != HOME) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }
    }
