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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ProjectActivity extends Activity
        implements ProjectScreen.Listener {

    private static final int PICK_IMAGE = 501;
    private static final int PICK_ZIP_PROJECT = 502;
    private static final int MAX_ZIP_ENTRIES = 5000;
    private static final long MAX_ZIP_TOTAL_BYTES = 200L * 1024 * 1024;
    private static final long MAX_ZIP_ENTRY_BYTES = 50L * 1024 * 1024;
    private static final int MENU_LIBRARY = 601;
    private static final int MENU_CHECK_PROJECT = 602;
    private static final int MENU_BUILD = 603;

    private File projectDirectory;
    private File currentDirectory;
    private ProjectFileManager fileManager;
    private ProjectScreen projectScreen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String name = getIntent().getStringExtra("project_name");
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
            error("Неверное имя проекта");
            finish();
            return;
        }

        try {
            File projects = new File(getFilesDir(), "projects").getCanonicalFile();
            projectDirectory = new File(projects, name).getCanonicalFile();
            if (!projectDirectory.getParentFile().equals(projects)
                    || !projectDirectory.isDirectory()) {
                throw new IOException("Проект не найден");
            }
            fileManager = new ProjectFileManager(projectDirectory);
            currentDirectory = projectDirectory;
        } catch (IOException e) {
            error(message(e));
            finish();
            return;
        }

        projectScreen = new ProjectScreen(this, this);
        setContentView(projectScreen.create(projectDirectory.getName()));
        refreshFiles();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_LIBRARY, Menu.NONE, "📚 Библиотека кода")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_BUILD, Menu.NONE, "🔨 Собрать APK / AAB")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_CHECK_PROJECT, Menu.NONE, "🔎 Проверить проект")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_LIBRARY) {
            openCodeLibrary();
            return true;
        }
        if (item.getItemId() == MENU_BUILD) {
            openBuildScreen();
            return true;
        }
        if (item.getItemId() == MENU_CHECK_PROJECT) {
            checkProject();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Opens the existing offline APK/AAB build screen for this project. */
    private void openBuildScreen() {
        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            error("Проект не выбран или недоступен");
            return;
        }
        try {
            Intent intent = new Intent(this, CodeBoxBuildActivity.class);
            intent.putExtra("project_name", projectDirectory.getName());
            intent.putExtra("project_path", projectDirectory.getCanonicalPath());
            startActivity(intent);
        } catch (Exception e) {
            error("Не удалось открыть сборку: " + message(e));
        }
    }

    private void checkProject() {
        if (projectDirectory == null) {
            error("Проект не выбран");
            return;
        }
        try {
            String report = CodeBoxProjectBuildValidator.validate(projectDirectory);
            new AlertDialog.Builder(this)
                    .setTitle("🔨 Проверка Android-проекта")
                    .setMessage(report)
                    .setPositiveButton("Понятно", null)
                    .show();
        } catch (IOException e) {
            error("Ошибка проверки: " + message(e));
        }
    }

    private void openCodeLibrary() {
        if (projectDirectory == null) {
            error("Проект не выбран");
            return;
        }
        Intent intent = new Intent(this, CodeLibraryActivity.class);
        intent.putExtra("project_name", projectDirectory.getName());
        try {
            startActivity(intent);
        } catch (Exception e) {
            error("Не удалось открыть библиотеку: " + message(e));
        }
    }

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    private String message(Exception e) {
        return e.getMessage() == null ? "Ошибка файловой системы" : e.getMessage();
    }

    private void error(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private boolean inside(File file) throws IOException {
        String root = projectDirectory.getCanonicalPath();
        String path = file.getCanonicalPath();
        return path.startsWith(root + File.separator);
    }

    private boolean safeEntry(File file) throws IOException {
        if (!file.getAbsoluteFile().getParentFile()
                .equals(currentDirectory.getAbsoluteFile())) {
            return false;
        }
        return inside(file);
    }

    private void refreshFiles() {
        try {
            String root = projectDirectory.getCanonicalPath();
            String current = currentDirectory.getCanonicalPath();
            if (!current.equals(root)
                    && !current.startsWith(root + File.separator)) {
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
                return a.getName().compareToIgnoreCase(b.getName());
            });
            String relative = current.equals(root)
                    ? "/" : current.substring(root.length());
            projectScreen.updatePath(relative, files.length);
            projectScreen.showFiles(files);
        } catch (IOException e) {
            error(message(e));
        }
    }

    private void createDialog(boolean folder) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(folder ? "Например: images" : "Например: Main.java");
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(24), dp(12), dp(24), dp(8));
        wrapper.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(folder ? "Новая папка" : "Новый файл")
                .setView(wrapper)
                .setPositiveButton("Создать", null)
                .setNegativeButton("Отмена", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String name = input.getText().toString().trim();
                            try {
                                if (folder) {
                                    fileManager.createFolder(currentDirectory, name);
                                } else {
                                    fileManager.createFile(currentDirectory, name, "");
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
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    private void openFile(File file) {
        try {
            if (!safeEntry(file) || !file.isFile()) {
                error("Недопустимый файл");
                return;
            }
            String relative = file.getCanonicalPath().substring(
                    projectDirectory.getCanonicalPath().length() + 1);
            Intent intent = isImageFile(file)
                    ? new Intent(this, ImageViewerActivity.class)
                    : new Intent(this, CodeEditorActivity.class);
            intent.putExtra("project_name", projectDirectory.getName());
            intent.putExtra("file_path", relative);
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
                    .setItems(new String[]{"Удалить"},
                            (dialog, which) -> confirmDelete(file))
                    .setNegativeButton("Отмена", null)
                    .show();
        } catch (IOException e) {
            error(message(e));
        }
    }

    private void confirmDelete(File file) {
        String description = file.isDirectory()
                ? "Удалить папку «" + file.getName() + "» вместе со всем содержимым?"
                : "Удалить файл «" + file.getName() + "»?";
        new AlertDialog.Builder(this)
                .setTitle("Подтверждение удаления")
                .setMessage(description + "\n\nОтменить удаление нельзя.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> {
                    try {
                        if (!safeEntry(file)) {
                            throw new IOException("Недопустимый путь");
                        }
                        deleteTree(file);
                        Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        error(message(e));
                    }
                    refreshFiles();
                })
                .show();
    }

    private void deleteTree(File file) throws IOException {
        if (!inside(file)) {
            throw new IOException("Удаление вне проекта запрещено");
        }
        if (Files.isSymbolicLink(file.toPath())) {
            throw new IOException("Удаление ссылок не поддерживается");
        }
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Объект не существует");
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Не удалось прочитать папку");
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
                .setItems(new String[]{
                        "Добавить изображение из галереи",
                        "Создать рисунок"
                }, (dialog, which) -> {
                    if (which == 0) {
                        pickImage();
                    } else {
                        openGraphicsEditor();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, PICK_IMAGE);
        } catch (Exception e) {
            error("Не удалось открыть выбор изображений");
        }
    }

    private void openGraphicsEditor() {
        Intent intent = new Intent(this, GraphicsEditorActivity.class);
        intent.putExtra("project_name", projectDirectory.getName());
        try {
            String relative = currentDirectory.getCanonicalPath().substring(
                    projectDirectory.getCanonicalPath().length());
            intent.putExtra("folder_path",
                    relative.startsWith(File.separator)
                            ? relative.substring(1) : relative);
            startActivity(intent);
        } catch (IOException e) {
            error(message(e));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == PICK_IMAGE) {
            importImage(data.getData());
        } else if (requestCode == PICK_ZIP_PROJECT) {
            importZipProject(data.getData());
        }
    }

    private void pickZipProject() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed", "application/octet-stream"
        });
        try {
            startActivityForResult(intent, PICK_ZIP_PROJECT);
        } catch (Exception e) {
            error("Не удалось открыть выбор ZIP-архива: " + message(e));
        }
    }

    /** Imports ZIP contents into the currently opened project directory. */
    private void importZipProject(Uri uri) {
        if (projectDirectory == null || currentDirectory == null) {
            error("Папка проекта недоступна");
            return;
        }
        final List<File> createdFiles = new ArrayList<>();
        final List<File> createdDirectories = new ArrayList<>();
        final Set<String> archivePaths = new HashSet<>();
        long totalBytes = 0;
        int entries = 0;
        try {
            String rootPath = projectDirectory.getCanonicalPath();
            String targetPath = currentDirectory.getCanonicalPath();
            if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
                throw new IOException("Текущая папка находится вне проекта");
            }
            try (InputStream raw = getContentResolver().openInputStream(uri)) {
                if (raw == null) throw new IOException("ZIP-архив недоступен");
                try (ZipInputStream zip = new ZipInputStream(raw)) {
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];
                    while ((entry = zip.getNextEntry()) != null) {
                        entries++;
                        if (entries > MAX_ZIP_ENTRIES) {
                            throw new IOException("В архиве больше 5000 элементов");
                        }
                        String entryName = entry.getName();
                        File destination = resolveZipEntry(currentDirectory, entryName);
                        String normalized = destination.getCanonicalPath();
                        if (!archivePaths.add(normalized)) {
                            throw new IOException("В архиве повторяется путь: " + entryName);
                        }
                        ensureNoSymbolicLinks(currentDirectory, destination);

                        if (entry.isDirectory() || entryName.endsWith("/")) {
                            createMissingDirectories(currentDirectory, destination, createdDirectories);
                            if (!destination.isDirectory()) {
                                throw new IOException("Не удалось создать папку: " + entryName);
                            }
                        } else {
                            File parent = destination.getParentFile();
                            if (parent == null) throw new IOException("Некорректный путь в ZIP");
                            createMissingDirectories(currentDirectory, parent, createdDirectories);
                            if (destination.exists()) {
                                throw new IOException("Файл уже существует, перезапись запрещена: " + entryName);
                            }
                            long entryBytes = 0;
                            try (FileOutputStream output = new FileOutputStream(destination)) {
                                createdFiles.add(destination);
                                int count;
                                while ((count = zip.read(buffer)) != -1) {
                                    entryBytes += count;
                                    totalBytes += count;
                                    if (entryBytes > MAX_ZIP_ENTRY_BYTES) {
                                        throw new IOException("Файл в архиве больше 50 МБ: " + entryName);
                                    }
                                    if (totalBytes > MAX_ZIP_TOTAL_BYTES) {
                                        throw new IOException("Общий размер распаковки больше 200 МБ");
                                    }
                                    output.write(buffer, 0, count);
                                }
                            }
                        }
                        zip.closeEntry();
                    }
                }
            }
            if (entries == 0) throw new IOException("ZIP-архив пуст или повреждён");
            Toast.makeText(this, "Импорт завершён: " + entries + " элементов", Toast.LENGTH_LONG).show();
            refreshFiles();
        } catch (Exception e) {
            // Roll back only objects created by this import; existing project data is untouched.
            for (int i = createdFiles.size() - 1; i >= 0; i--) {
                try { Files.deleteIfExists(createdFiles.get(i).toPath()); } catch (IOException ignored) { }
            }
            for (int i = createdDirectories.size() - 1; i >= 0; i--) {
                try { Files.deleteIfExists(createdDirectories.get(i).toPath()); } catch (IOException ignored) { }
            }
            error("Ошибка импорта ZIP: " + message(e));
            refreshFiles();
        }
    }

    private File resolveZipEntry(File base, String entryName) throws IOException {
        if (entryName == null || entryName.trim().isEmpty() || entryName.indexOf('\0') >= 0) {
            throw new IOException("В архиве найден пустой или некорректный путь");
        }
        String normalizedName = entryName.replace('\\', '/');
        if (normalizedName.startsWith("/") || normalizedName.matches("^[A-Za-z]:.*")) {
            throw new IOException("Абсолютные пути в ZIP запрещены: " + entryName);
        }
        String[] parts = normalizedName.split("/");
        for (String part : parts) {
            if (part.equals("..") || part.equals(".")) {
                throw new IOException("Небезопасный путь в ZIP: " + entryName);
            }
        }
        File destination = new File(base, normalizedName).getCanonicalFile();
        String basePath = base.getCanonicalPath();
        if (!destination.getPath().startsWith(basePath + File.separator)) {
            throw new IOException("Путь архива выходит за пределы проекта: " + entryName);
        }
        return destination;
    }

    private void ensureNoSymbolicLinks(File base, File destination) throws IOException {
        java.nio.file.Path basePath = base.getCanonicalFile().toPath();
        java.nio.file.Path targetPath = destination.toPath();
        java.nio.file.Path current = basePath;
        for (java.nio.file.Path part : basePath.relativize(targetPath)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Символические ссылки в пути ZIP запрещены");
            }
        }
    }

    private void createMissingDirectories(File base, File parent, List<File> created)
            throws IOException {
        String basePath = base.getCanonicalPath();
        String parentPath = parent.getCanonicalPath();
        if (!parentPath.equals(basePath) && !parentPath.startsWith(basePath + File.separator)) {
            throw new IOException("Папка распаковки находится вне проекта");
        }
        if (parent.exists()) {
            if (!parent.isDirectory()) throw new IOException("Путь для распаковки занят файлом");
            return;
        }
        File ancestor = parent;
        List<File> missing = new ArrayList<>();
        while (ancestor != null && !ancestor.exists() && !ancestor.equals(base)) {
            missing.add(ancestor);
            ancestor = ancestor.getParentFile();
        }
        if (ancestor == null || !ancestor.isDirectory()) {
            throw new IOException("Не удалось определить папку распаковки");
        }
        for (int i = missing.size() - 1; i >= 0; i--) {
            File dir = missing.get(i);
            if (!dir.mkdir()) throw new IOException("Не удалось создать папку: " + dir.getName());
            created.add(dir);
        }
    }

    @Override
    public void onImportZip() {
        new AlertDialog.Builder(this)
                .setTitle("Импорт ZIP-проекта")
                .setMessage("Содержимое ZIP-архива будет добавлено в текущую папку проекта. Существующие файлы не перезаписываются.")
                .setPositiveButton("Выбрать ZIP", (dialog, which) -> pickZipProject())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private String imageExtension(Uri uri) {
        String type = getContentResolver().getType(uri);
        if ("image/png".equals(type)) return ".png";
        if ("image/webp".equals(type)) return ".webp";
        if ("image/jpeg".equals(type) || "image/jpg".equals(type)) return ".jpg";
        return null;
    }

    private void importImage(Uri uri) {
        try {
            String extension = imageExtension(uri);
            if (extension == null) {
                throw new IOException("Поддерживаются PNG, JPG и WebP");
            }
            String originalName = "image" + extension;
            try (Cursor cursor = getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String value = cursor.getString(0);
                    if (value != null && !value.isEmpty()) originalName = value;
                }
            }
            String base = originalName.replaceAll(
                    "(?i)\\.(png|jpg|jpeg|webp)$", "");
            base = base.replaceAll(
                    "[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
            if (base.isEmpty()) base = "image";
            if (base.length() > 60) base = base.substring(0, 60);

            File destination = null;
            for (int number = 0; number < 10000; number++) {
                String suffix = number == 0 ? "" : "_" + number;
                File candidate = new File(currentDirectory,
                        base + suffix + extension);
                if (!candidate.exists()) {
                    destination = candidate;
                    break;
                }
            }
            if (destination == null || !inside(destination)) {
                throw new IOException("Не удалось выбрать имя файла");
            }
            long maxBytes = 30L * 1024 * 1024;
            long copied = 0;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("Изображение недоступно");
                }
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        copied += count;
                        if (copied > maxBytes) {
                            throw new IOException("Размер изображения больше 30 МБ");
                        }
                        output.write(buffer, 0, count);
                    }
                }
            } catch (IOException e) {
                destination.delete();
                throw e;
            }
            Toast.makeText(this, "Изображение добавлено", Toast.LENGTH_SHORT).show();
            refreshFiles();
        } catch (Exception e) {
            error("Ошибка импорта: " + message(e));
        }
    }

    private void goUp() {
        try {
            if (currentDirectory.getCanonicalFile().equals(projectDirectory)) {
                finish();
                return;
            }
            File parent = currentDirectory.getParentFile();
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
        if (projectScreen != null) refreshFiles();
    }
                            }
