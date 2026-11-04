package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Arrays;
import java.util.Locale;

public class ProjectActivity extends Activity
        implements ProjectScreen.Listener {

    private static final int PICK_IMAGE = 501;
    private static final int MENU_LIBRARY = 601;

    private File projectDirectory;
    private File currentDirectory;
    private ProjectFileManager fileManager;
    private ProjectScreen projectScreen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String name = getIntent()
                .getStringExtra("project_name");

        if (name == null ||
                !name.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {
            error("Неверное имя проекта");
            finish();
            return;
        }

        try {
            File projects = new File(
                    getFilesDir(), "projects"
            ).getCanonicalFile();

            projectDirectory = new File(
                    projects, name
            ).getCanonicalFile();

            if (!projectDirectory.getParentFile()
                    .equals(projects)
                    || !projectDirectory.isDirectory()) {
                throw new IOException(
                        "Проект не найден"
                );
            }

            fileManager = new ProjectFileManager(
                    projectDirectory
            );

            currentDirectory = projectDirectory;

        } catch (IOException e) {
            error(message(e));
            finish();
            return;
        }

        projectScreen = new ProjectScreen(this, this);

        setContentView(
                projectScreen.create(
                        projectDirectory.getName()
                )
        );

        refreshFiles();
    }

    // Меню библиотеки кода.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(
                Menu.NONE,
                MENU_LIBRARY,
                Menu.NONE,
                "📚 Библиотека кода"
        ).setShowAsAction(
                MenuItem.SHOW_AS_ACTION_NEVER
        );

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == MENU_LIBRARY) {
            openCodeLibrary();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openCodeLibrary() {

        if (projectDirectory == null) {
            error("Проект не выбран");
            return;
        }

        Intent intent = new Intent(
                this,
                CodeLibraryActivity.class
        );

        intent.putExtra(
                "project_name",
                projectDirectory.getName()
        );

        try {
            startActivity(intent);
        } catch (Exception e) {
            error(
                    "Не удалось открыть библиотеку: "
                            + message(e)
            );
        }
    }

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    private String message(Exception e) {
        return e.getMessage() == null
                ? "Ошибка файловой системы"
                : e.getMessage();
    }

    private void error(String value) {
        Toast.makeText(
                this, value, Toast.LENGTH_LONG
        ).show();
    }

    private boolean inside(File file)
            throws IOException {

        String root = projectDirectory
                .getCanonicalPath();

        String path = file.getCanonicalPath();

        return path.startsWith(
                root + File.separator
        );
    }

    private boolean safeEntry(File file)
            throws IOException {

        if (!file.getAbsoluteFile()
                .getParentFile()
                .equals(currentDirectory.getAbsoluteFile())) {
            return false;
        }

        return inside(file);
    }

    private void refreshFiles() {
        try {
            String root = projectDirectory
                    .getCanonicalPath();

            String current = currentDirectory
                    .getCanonicalPath();

            if (!current.equals(root)
                    && !current.startsWith(
                            root + File.separator
                    )) {
                currentDirectory = projectDirectory;
                current = root;
            }

            File[] files = currentDirectory.listFiles();

            if (files == null) {
                error("Не удалось открыть папку");
                return;
            }

            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName()
                        .compareToIgnoreCase(b.getName());
            });

            String relative = current.equals(root)
                    ? "/"
                    : current.substring(root.length());

            projectScreen.updatePath(
                    relative, files.length
            );

            projectScreen.showFiles(files);

        } catch (IOException e) {
            error(message(e));
        }
    }

    private void createDialog(boolean folder) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(
                folder ? "Например: images"
                       : "Например: Main.java"
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

        dialog.setOnShowListener(ignored ->
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

                    } catch (IOException e) {
                        input.setError(message(e));
                    }
                })
        );

        dialog.show();
    }

    private boolean isImageFile(File file) {
        String name = file.getName()
                .toLowerCase(Locale.ROOT);

        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".webp");
    }

    private void openFile(File file) {
        try {
            if (!safeEntry(file) || !file.isFile()) {
                error("Недопустимый файл");
                return;
            }

            String relative = file.getCanonicalPath()
                    .substring(
                            projectDirectory
                                    .getCanonicalPath()
                                    .length() + 1
                    );

            Intent intent;

            if (isImageFile(file)) {
                intent = new Intent(
                        this, ImageViewerActivity.class
                );
            } else {
                intent = new Intent(
                        this, CodeEditorActivity.class
                );
            }

            intent.putExtra(
                    "project_name",
                    projectDirectory.getName()
            );

            intent.putExtra(
                    "file_path", relative
            );

            startActivity(intent);

        } catch (IOException e) {
            error(message(e));
        }
    }

    private void showActions(File file) {
        try {
            if (!safeEntry(file)) {
                error("Объект вне папки проекта");
                return;
            }

            if (!file.exists()) {
                error("Объект не найден");
                refreshFiles();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle(file.getName())
                    .setItems(
                            new String[]{"Удалить"},
                            (dialog, which) ->
                                    confirmDelete(file)
                    )
                    .setNegativeButton("Отмена", null)
                    .show();

        } catch (IOException e) {
            error(message(e));
        }
    }

    private void confirmDelete(File file) {
        String description = file.isDirectory()
                ? "Удалить папку «" + file.getName()
                    + "» вместе со всем содержимым?"
                : "Удалить файл «" + file.getName()
                    + "»?";

        new AlertDialog.Builder(this)
                .setTitle("Подтверждение удаления")
                .setMessage(
                        description
                        + "\n\nОтменить удаление нельзя."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Удалить",
                        (dialog, which) -> {
                            try {
                                if (!safeEntry(file)) {
                                    throw new IOException(
                                            "Недопустимый путь"
                                    );
                                }

                                deleteTree(file);

                                Toast.makeText(
                                        this,
                                        "Удалено",
                                        Toast.LENGTH_SHORT
                                ).show();

                            } catch (IOException e) {
                                error(message(e));
                            }

                            refreshFiles();
                        }
                )
                .show();
    }

    private void deleteTree(File file)
            throws IOException {

        if (!inside(file)) {
            throw new IOException(
                    "Удаление вне проекта запрещено"
            );
        }

        if (Files.isSymbolicLink(file.toPath())) {
            throw new IOException(
                    "Удаление ссылок не поддерживается"
            );
        }

        if (!Files.exists(
                file.toPath(),
                LinkOption.NOFOLLOW_LINKS
        )) {
            throw new IOException(
                    "Объект не существует"
            );
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();

            if (children == null) {
                throw new IOException(
                        "Не удалось прочитать папку"
                );
            }

            for (File child : children) {
                deleteTree(child);
            }
        }

        Files.delete(file.toPath());
    }

    private void showGraphicsMenu() {
        new AlertDialog.Builder(this)
                .setTitle("Графика")
                .setItems(
                        new String[]{
                                "Добавить изображение из галереи",
                                "Создать рисунок"
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                pickImage();
                            } else {
                                openGraphicsEditor();
                            }
                        }
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void pickImage() {
        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT
        );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType("image/*");

        try {
            startActivityForResult(
                    intent, PICK_IMAGE
            );
        } catch (Exception e) {
            error("Не удалось открыть выбор изображений");
        }
    }

    private void openGraphicsEditor() {
        Intent intent = new Intent(
                this, GraphicsEditorActivity.class
        );

        intent.putExtra(
                "project_name",
                projectDirectory.getName()
        );

        try {
            String relative = currentDirectory
                    .getCanonicalPath()
                    .substring(
                            projectDirectory
                                    .getCanonicalPath()
                                    .length()
                    );

            intent.putExtra(
                    "folder_path",
                    relative.startsWith(File.separator)
                            ? relative.substring(1)
                            : relative
            );

            startActivity(intent);

        } catch (IOException e) {
            error(message(e));
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode, resultCode, data
        );

        if (requestCode != PICK_IMAGE
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        importImage(data.getData());
    }

    private String imageExtension(Uri uri) {
        String type = getContentResolver()
                .getType(uri);

        if ("image/png".equals(type)) {
            return ".png";
        }
        if ("image/webp".equals(type)) {
            return ".webp";
        }
        if ("image/jpeg".equals(type)
                || "image/jpg".equals(type)) {
            return ".jpg";
        }

        return null;
    }

    private void importImage(Uri uri) {
        try {
            String extension = imageExtension(uri);

            if (extension == null) {
                throw new IOException(
                        "Поддерживаются PNG, JPG и WebP"
                );
            }

            String originalName = "image" + extension;

            try (Cursor cursor =
                         getContentResolver().query(
                                 uri,
                                 new String[]{
                                         OpenableColumns.DISPLAY_NAME
                                 },
                                 null, null, null
                         )) {

                if (cursor != null
                        && cursor.moveToFirst()) {
                    String value = cursor.getString(0);
                    if (value != null && !value.isEmpty()) {
                        originalName = value;
                    }
                }
            }

            String base = originalName.replaceAll(
                    "(?i)\\.(png|jpg|jpeg|webp)$", ""
            );

            base = base.replaceAll(
                    "[\\\\/:*?\"<>|\\p{Cntrl}]", "_"
            ).trim();

            if (base.isEmpty()) {
                base = "image";
            }

            if (base.length() > 60) {
                base = base.substring(0, 60);
            }

            File destination = null;

            for (int number = 0; number < 10000; number++) {
                String suffix = number == 0
                        ? ""
                        : "_" + number;

                File candidate = new File(
                        currentDirectory,
                        base + suffix + extension
                );

                if (!candidate.exists()) {
                    destination = candidate;
                    break;
                }
            }

            if (destination == null
                    || !inside(destination)) {
                throw new IOException(
                        "Не удалось выбрать имя файла"
                );
            }

            long maxBytes = 30L * 1024 * 1024;
            long copied = 0;

            try (InputStream input =
                         getContentResolver()
                                 .openInputStream(uri)) {

                if (input == null) {
                    throw new IOException(
                            "Изображение недоступно"
                    );
                }

                try (FileOutputStream output =
                             new FileOutputStream(destination)) {

                    byte[] buffer = new byte[8192];
                    int count;

                    while ((count = input.read(buffer)) != -1) {
                        copied += count;

                        if (copied > maxBytes) {
                            throw new IOException(
                                    "Размер изображения больше 30 МБ"
                            );
                        }

                        output.write(buffer, 0, count);
                    }
                }

            } catch (IOException e) {
                destination.delete();
                throw e;
            }

            Toast.makeText(
                    this,
                    "Изображение добавлено",
                    Toast.LENGTH_SHORT
            ).show();

            refreshFiles();

        } catch (Exception e) {
            error(
                    "Ошибка импорта: " + message(e)
            );
        }
    }

    private void goUp() {
        try {
            if (currentDirectory.getCanonicalFile()
                    .equals(projectDirectory)) {
                finish();
                return;
            }

            File parent = currentDirectory
                    .getParentFile();

            if (parent != null) {
                currentDirectory = parent;
                refreshFiles();
            }

        } catch (IOException e) {
            error(message(e));
        }
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
        createDialog(false);
    }

    @Override
    public void onNewFolder() {
        createDialog(true);
    }

    @Override
    public void onGraphics() {
        showGraphicsMenu();
    }

    @Override
    public void onFileSelected(File file) {
        try {
            if (!safeEntry(file)) {
                error("Объект вне проекта");
                return;
            }

            if (file.isDirectory()) {
                currentDirectory = file;
                refreshFiles();

            } else if (file.isFile()) {
                openFile(file);

            } else {
                error("Объект недоступен");
            }

        } catch (IOException e) {
            error(message(e));
        }
    }

    @Override
    public void onFileLongPressed(File file) {
        showActions(file);
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
