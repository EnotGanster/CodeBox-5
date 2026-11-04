package ru.codebox.app;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GradleProjectManager {

    private final File projectsRoot;

    public GradleProjectManager(Context context)
            throws IOException {

        projectsRoot = new File(
                context.getFilesDir(),
                "projects"
        ).getCanonicalFile();
    }

    private File project(String name)
            throws IOException {

        if (name == null ||
                !name.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {
            throw new IOException(
                    "Некорректное название проекта"
            );
        }

        File dir = new File(
                projectsRoot, name
        ).getCanonicalFile();

        if (!projectsRoot.equals(
                dir.getParentFile()
        ) || !dir.isDirectory()) {

            throw new IOException(
                    "Проект не найден"
            );
        }

        return dir;
    }

    private File child(
            File project,
            String relative)
            throws IOException {

        File result = new File(
                project, relative
        ).getCanonicalFile();

        if (!result.getPath().startsWith(
                project.getPath() + File.separator
        )) {
            throw new IOException(
                    "Путь выходит за пределы проекта"
            );
        }

        return result;
    }

    private void createIfAbsent(
            File file,
            String text)
            throws IOException {

        if (file.exists()) {
            if (!file.isFile()) {
                throw new IOException(
                        "Путь занят папкой: "
                                + file.getName()
                );
            }
            return;
        }

        File parent = file.getParentFile();

        if (!parent.isDirectory()
                && !parent.mkdirs()) {

            throw new IOException(
                    "Не удалось создать папку: "
                            + parent.getName()
            );
        }

        Files.write(
                file.toPath(),
                text.getBytes(
                        StandardCharsets.UTF_8
                ),
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE
        );
    }

    public synchronized void ensureGradleStructure(
            String name)
            throws IOException {

        File dir = project(name);

        String namespace =
                "ru.codebox.projects."
                        + name.toLowerCase(Locale.ROOT);

        createIfAbsent(
                child(dir, "settings.gradle"),
                "pluginManagement {\n"
                        + "    repositories {\n"
                        + "        google()\n"
                        + "        mavenCentral()\n"
                        + "        gradlePluginPortal()\n"
                        + "    }\n"
                        + "}\n\n"
                        + "dependencyResolutionManagement {\n"
                        + "    repositoriesMode.set("
                        + "RepositoriesMode.FAIL_ON_PROJECT_REPOS"
                        + ")\n"
                        + "    repositories {\n"
                        + "        google()\n"
                        + "        mavenCentral()\n"
                        + "    }\n"
                        + "}\n\n"
                        + "rootProject.name = '"
                        + name + "'\n"
                        + "include ':app'\n"
        );

        createIfAbsent(
                child(dir, "build.gradle"),
                "plugins {\n"
                        + "    id 'com.android.application' "
                        + "version '8.7.3' apply false\n"
                        + "}\n"
        );

        createIfAbsent(
                child(dir, "gradle.properties"),
                "org.gradle.jvmargs=-Xmx2048m "
                        + "-Dfile.encoding=UTF-8\n"
                        + "android.useAndroidX=true\n"
        );

        createIfAbsent(
                child(dir, "app/build.gradle"),
                "plugins {\n"
                        + "    id 'com.android.application'\n"
                        + "}\n\n"
                        + "android {\n"
                        + "    namespace '"
                        + namespace + "'\n"
                        + "    compileSdk 35\n\n"
                        + "    defaultConfig {\n"
                        + "        applicationId '"
                        + namespace + "'\n"
                        + "        minSdk 26\n"
                        + "        targetSdk 28\n"
                        + "        versionCode 1\n"
                        + "        versionName '1.0'\n"
                        + "    }\n"
                        + "    compileOptions {\n"
                        + "        sourceCompatibility "
                        + "JavaVersion.VERSION_17\n"
                        + "        targetCompatibility "
                        + "JavaVersion.VERSION_17\n"
                        + "    }\n"
                        + "}\n\n"
                        + "dependencies {\n"
                        + "}\n"
        );

        createIfAbsent(
                child(
                        dir,
                        "app/src/main/AndroidManifest.xml"
                ),
                "<?xml version=\"1.0\" "
                        + "encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android="
                        + "\"http://schemas.android.com/"
                        + "apk/res/android\">\n"
                        + "    <application "
                        + "android:label=\""
                        + name
                        + "\" android:theme="
                        + "\"@android:style/"
                        + "Theme.Material.Light.NoActionBar\""
                        + " />\n"
                        + "</manifest>\n"
        );
    }

    public synchronized int applyDependencies(
            String name,
            List<String> dependencies)
            throws IOException {

        File dir = project(name);

        File file = child(
                dir, "app/build.gradle"
        );

        if (!file.isFile()) {
            throw new IOException(
                    "Нет app/build.gradle. "
                            + "Сначала создай Gradle-структуру."
            );
        }

        if (file.length() > 1024 * 1024) {
            throw new IOException(
                    "build.gradle слишком большой"
            );
        }

        String original = new String(
                Files.readAllBytes(
                        file.toPath()
                ),
                StandardCharsets.UTF_8
        );

        String result = original;
        int added = 0;

        for (String coordinate : dependencies) {

            if (coordinate == null ||
                    !coordinate.matches(
                            "[A-Za-z0-9_.-]+:"
                                    + "[A-Za-z0-9_.-]+:"
                                    + "[A-Za-z0-9_.+\\-]+"
                    )) {

                throw new IOException(
                        "Некорректная зависимость: "
                                + coordinate
                );
            }

            Pattern existing = Pattern.compile(
                    "(?m)^\\s*"
                            + "(?:implementation|api|"
                            + "compileOnly|runtimeOnly|"
                            + "testImplementation|"
                            + "androidTestImplementation)"
                            + "\\s*(?:\\(\\s*)?['\"]"
                            + Pattern.quote(coordinate)
                            + "['\"]"
            );

            if (existing.matcher(result).find()) {
                continue;
            }

            int opening =
                    findDependenciesBlock(result);

            if (opening < 0) {
                throw new IOException(
                        "Не найден однозначный блок "
                                + "dependencies { ... } "
                                + "в app/build.gradle"
                );
            }

            result =
                    result.substring(0, opening + 1)
                            + "\n    implementation '"
                            + coordinate + "'"
                            + result.substring(opening + 1);

            added++;
        }

        if (added == 0) {
            return 0;
        }

        Path target = file.toPath();

        Path backup = child(
                dir,
                "app/build.gradle.codebox.bak"
        ).toPath();

        if (!Files.exists(backup)) {
            Files.copy(target, backup);
        }

        Path temp = Files.createTempFile(
                target.getParent(),
                ".codebox-gradle-",
                ".tmp"
        );

        try {
            Files.write(
                    temp,
                    result.getBytes(
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

        return added;
    }

    private int findDependenciesBlock(
            String text) {

        Matcher matcher = Pattern.compile(
                "(?m)^\\s*dependencies\\s*\\{"
        ).matcher(text);

        int found = -1;

        while (matcher.find()) {

            if (found >= 0) {
                return -1;
            }

            found = text.indexOf(
                    '{', matcher.start()
            );
        }

        return found;
    }
}
