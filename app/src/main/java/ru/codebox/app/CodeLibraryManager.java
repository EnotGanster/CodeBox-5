
package ru.codebox.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CodeLibraryManager {

    private static final String FILE_NAME =
            "code_library.json";

    private final File storageFile;

    private JSONObject data;

    public CodeLibraryManager(Context context)
            throws IOException {

        storageFile = new File(
                context.getFilesDir(),
                FILE_NAME
        );

        load();
    }

    private void load() throws IOException {

        if (!storageFile.exists()) {
            data = createEmptyLibrary();
            save();
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(
                    storageFile.toPath()
            );

            data = new JSONObject(
                    new String(
                            bytes,
                            StandardCharsets.UTF_8
                    )
            );

            if (!(data.opt("snippets")
                    instanceof JSONArray)
                    || !(data.opt("dependencies")
                    instanceof JSONObject)) {

                throw new IOException(
                        "Неверный формат библиотеки"
                );
            }

        } catch (JSONException e) {
            throw new IOException(
                    "Ошибка чтения библиотеки",
                    e
            );
        }
    }

    private JSONObject createEmptyLibrary()
            throws IOException {

        try {
            JSONObject root = new JSONObject();

            root.put("version", 1);

            root.put(
                    "snippets",
                    new JSONArray()
            );

            root.put(
                    "dependencies",
                    new JSONObject()
            );

            return root;

        } catch (JSONException e) {
            throw new IOException(
                    "Ошибка создания библиотеки",
                    e
            );
        }
    }

    private void save() throws IOException {

        Path target = storageFile.toPath();

        Path temp = Files.createTempFile(
                target.getParent(),
                ".code-library-",
                ".tmp"
        );

        try {
            // Используем toString() без аргументов.
            // Это исправляет ошибку JSONException
            // при компиляции Android Java.

            Files.write(
                    temp,
                    data.toString().getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );

            } catch (
                    java.nio.file.AtomicMoveNotSupportedException e
            ) {

                Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static final class Snippet {

        public final String id;
        public final String title;
        public final String language;
        public final String code;
        public final String projectName;

        public Snippet(
                String id,
                String title,
                String language,
                String code,
                String projectName) {

            this.id = id;
            this.title = title;
            this.language = language;
            this.code = code;
            this.projectName = projectName;
        }
    }

    public synchronized void addSnippet(
            String title,
            String language,
            String code,
            String projectName)
            throws IOException {

        if (title == null
                || title.trim().isEmpty()) {

            throw new IOException(
                    "Название шаблона пустое"
            );
        }

        if (code == null
                || code.isEmpty()) {

            throw new IOException(
                    "Код шаблона пустой"
            );
        }

        String lang = language == null
                ? "java"
                : language.trim().toLowerCase(
                        Locale.ROOT
                );

        if (!lang.equals("java")
                && !lang.equals("xml")) {

            throw new IOException(
                    "Поддерживаются Java и XML"
            );
        }

        String project = projectName == null
                ? ""
                : projectName.trim();

        if (!project.isEmpty()
                && !isValidProject(project)) {

            throw new IOException(
                    "Неверное название проекта"
            );
        }

        try {
            JSONArray snippets =
                    data.getJSONArray("snippets");

            JSONObject item = new JSONObject();

            item.put(
                    "id",
                    UUID.randomUUID().toString()
            );

            item.put(
                    "title",
                    title.trim()
            );

            item.put(
                    "language",
                    lang
            );

            item.put(
                    "code",
                    code
            );

            item.put(
                    "project",
                    project
            );

            snippets.put(item);

            save();

        } catch (JSONException e) {
            throw new IOException(
                    "Ошибка добавления шаблона",
                    e
            );
        }
    }

    public synchronized List<Snippet> getSnippets(
            String projectName)
            throws IOException {

        List<Snippet> result =
                new ArrayList<>();

        String project = projectName == null
                ? ""
                : projectName;

        try {
            JSONArray snippets =
                    data.getJSONArray("snippets");

            for (int i = 0;
                    i < snippets.length();
                    i++) {

                JSONObject item =
                        snippets.getJSONObject(i);

                String owner =
                        item.optString(
                                "project", ""
                        );

                if (!owner.isEmpty()
                        && !owner.equals(project)) {
                    continue;
                }

                result.add(
                        new Snippet(
                                item.getString("id"),
                                item.getString("title"),
                                item.getString("language"),
                                item.getString("code"),
                                owner
                        )
                );
            }

            return result;

        } catch (JSONException e) {
            throw new IOException(
                    "Ошибка чтения шаблонов",
                    e
            );
        }
    }

    public synchronized boolean deleteSnippet(
            String id)
            throws IOException {

        try {
            JSONArray snippets =
                    data.getJSONArray("snippets");

            JSONArray updated =
                    new JSONArray();

            boolean removed = false;

            for (int i = 0;
                    i < snippets.length();
                    i++) {

                JSONObject item =
                        snippets.getJSONObject(i);

                if (item.optString("id")
                        .equals(id)) {

                    removed = true;
                    continue;
                }

                updated.put(item);
            }

            if (removed) {
                data.put(
                        "snippets",
                        updated
                );

                save();
            }

            return removed;

        } catch (JSONException e) {
            throw new IOException(
                    "Ошибка удаления шаблона",
                    e
            );
        }
    }

    private boolean isValidProject(
            String name) {

        return name != null
                && name.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                );
    }

    public synchronized void addDependency(
            String projectName,
            String dependency)
            throws IOException {

        if (!isValidProject(projectName)) {

            throw new IOException(
                    "Неверное название проекта"
            );
        }

        if (dependency == null
                || !dependency.matches(
                        "[A-Za-z0-9_.-]+:"
                        + "[A-Za-z0-9_.-]+:"
                        + "[A-Za-z0-9_.+\\-]+"
                )) {

            throw new IOException(
                    "Ожидается group:artifact:version"
            );
        }

        try {
            JSONObject dependencies =
                    data.getJSONObject(
                            "dependencies"
                    );

            JSONArray list =
                    dependencies.optJSONArray(
                            projectName
                    );

            if (list == null) {

                list = new JSONArray();

                dependencies.put(
                        projectName,
                        list
                );
            }

            for (int i = 0;
                    i < list.length();
                    i++) {

                if (dependency.equals(
                        list.getString(i))) {
                    return;
                }
            }

            list.put(dependency);

            save();

        } catch (JSONException e) {
            throw new IOException(
                    "Ошибка добавления зависимости",
                    e
            );
        }
    }

    public synchronized List<String>
            getDependencies(String projectName)
            throws IOException {

        List<String> result =
                new ArrayList<>();

        if (!isValidProject(projectName)) {

            throw new IOException(
                    "Неверное название проекта"
            );
        }

        try {
            JSONObject dependencies =
                    data.getJSONObject(
                            "dependencies"
                    );

            JSONArray list =
                    dependencies.optJSONArray(
                            projectName
                    );

            if (list == null) {
                return result;
            }

            for (int i = 0;
                    i < list.length();
                    i++) {

                result.add(
                        list.getString(i)
                );
            }

            return result;

        } catch (JSONException e) {
            throw new IOException(
                    "Ошибка чтения зависимостей",
                    e
            );
        }
    }

    public synchronized boolean removeDependency(
            String projectName,
            String dependency)
            throws IOException {

        if (!isValidProject(projectName)) {

            throw new IOException(
                    "Неверное название проекта"
            );
        }

        try {
            JSONObject dependencies =
                    data.getJSONObject(
                            "dependencies"
                    );

            JSONArray list =
                    dependencies.optJSONArray(
                            projectName
                    );

            if (list == null) {
                return false;
            }

            JSONArray updated =
                    new JSONArray();

            boolean removed = false;

            for (int i = 0;
                    i < list.length();
                    i++) {

                String value =
                        list.getString(i);

                if (value.equals(dependency)) {
                    removed = true;
                } else {
                    updated.put(value);
                }
            }

            if (removed) {

                dependencies.put(
                        projectName,
                        updated
                );

                save();
            }

            return removed;

        } catch (JSONException e) {
            throw new IOException(
                    "Ошибка удаления зависимости",
                    e
            );
        }
    }
}
