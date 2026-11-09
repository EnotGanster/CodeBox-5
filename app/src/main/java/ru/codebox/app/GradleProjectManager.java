package ru.codebox.app;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class GradleProjectManager {

    private final File projectsRoot;

    private static final Pattern COORDINATE =
            Pattern.compile(
                    "[A-Za-z0-9_.-]+:"
                    + "[A-Za-z0-9_.-]+:"
                    + "[A-Za-z0-9_.+\\-]+"
            );

    public GradleProjectManager(Context context)
            throws IOException {

        projectsRoot = new File(
                context.getFilesDir(),
                "projects"
        ).getCanonicalFile();
    }

    private File getProject(String name)
            throws IOException {

        if (name == null ||
                !name.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {
            throw new IOException(
                    "Некорректное имя проекта"
            );
        }

        File dir = new File(
                projectsRoot, name
        ).getCanonicalFile();

        if (!projectsRoot.equals(
                dir.getParentFile()
        ) || !dir.isDirectory()) {
            throw new IOException(
                    "Проект не найден: " + name
            );
        }

        return dir;
    }

    private File getChild(
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
                    "Недопустимый путь"
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
                        "Ожидался файл: "
                        + file.getName()
                );
            }
            return;
        }

        File parent = file.getParentFile();

        if (!parent.isDirectory()
                && !parent.mkdirs()) {
            throw new IOException(
                    "Не удалось создать папку"
            );
        }

        Files.write(
                file.toPath(),
                text.getBytes(
                        StandardCharsets.UTF_8
                ),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
    }

    public synchronized void ensureGradleStructure(
            String name)
            throws IOException {

        File project = getProject(name);

        String packageName =
                "com.example."
                + name.toLowerCase(Locale.ROOT);

        String javaPath =
                "app/src/main/java/"
                + packageName.replace('.', '/')
                + "/MainActivity.java";

        createIfAbsent(
                getChild(project, "settings.gradle"),
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
                + "rootProject.name = '" + name + "'\n"
                + "include ':app'\n"
        );

        createIfAbsent(
                getChild(project, "build.gradle"),
                "plugins {\n"
                + "    id 'com.android.application' "
                + "version '8.7.3' apply false\n"
                + "}\n"
        );

        createIfAbsent(
                getChild(project, "gradle.properties"),
                "org.gradle.jvmargs=-Xmx2048m "
                + "-Dfile.encoding=UTF-8\n"
                + "android.useAndroidX=true\n"
        );

        createIfAbsent(
                getChild(project, "app/build.gradle"),
                "plugins {\n"
                + "    id 'com.android.application'\n"
                + "}\n\n"
                + "android {\n"
                + "    namespace '" + packageName + "'\n"
                + "    compileSdk 35\n\n"
                + "    defaultConfig {\n"
                + "        applicationId '" + packageName
                + "'\n"
                + "        minSdk 26\n"
                + "        targetSdk 28\n"
                + "        versionCode 1\n"
                + "        versionName '1.0'\n"
                + "    }\n\n"
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
                getChild(
                        project,
                        "app/src/main/AndroidManifest.xml"
                ),
                "<?xml version=\"1.0\" "
                + "encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android="
                + "\"http://schemas.android.com/"
                + "apk/res/android\">\n\n"
                + "    <application\n"
                + "        android:label=\"" + name + "\"\n"
                + "        android:theme="
                + "\"@android:style/"
                + "Theme.Material.Light.NoActionBar\">\n\n"
                + "        <activity\n"
                + "            android:name=\".MainActivity\"\n"
                + "            android:exported=\"true\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name="
                + "\"android.intent.action.MAIN\" />\n"
                + "                <category android:name="
                + "\"android.intent.category.LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n\n"
                + "    </application>\n"
                + "</manifest>\n"
        );

        createIfAbsent(
                getChild(project, javaPath),
                "package " + packageName + ";\n\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "import android.graphics.Color;\n"
                + "import android.view.Gravity;\n"
                + "import android.widget.LinearLayout;\n"
                + "import android.widget.TextView;\n\n"
                + "public class MainActivity "
                + "extends Activity {\n\n"
                + "    @Override\n"
                + "    protected void onCreate("
                + "Bundle savedInstanceState) {\n"
                + "        super.onCreate("
                + "savedInstanceState);\n\n"
                + "        LinearLayout layout = "
                + "new LinearLayout(this);\n"
                + "        layout.setOrientation("
                + "LinearLayout.VERTICAL);\n"
                + "        layout.setGravity("
                + "Gravity.CENTER);\n"
                + "        layout.setPadding(24, 24, 24, 24);\n"
                + "        layout.setBackgroundColor("
                + "Color.WHITE);\n\n"
                + "        TextView title = "
                + "new TextView(this);\n"
                + "        title.setText(\""
                + "Hello from CodeBox 5!\");\n"
                + "        title.setTextSize(24);\n"
                + "        title.setTextColor("
                + "Color.rgb(100, 60, 180));\n"
                + "        title.setGravity("
                + "Gravity.CENTER);\n\n"
                + "        layout.addView(title);\n"
                + "        setContentView(layout);\n"
                + "    }\n"
                + "}\n"
        );
    }

    public synchronized int applyDependencies(
            String projectName,
            List<String> dependencies)
            throws IOException {

        File project = getProject(projectName);

        File file = getChild(
                project, "app/build.gradle"
        );

        if (!file.isFile()) {
            throw new IOException(
                    "Сначала создай Gradle-структуру"
            );
        }

        if (file.length() > 1024 * 1024) {
            throw new IOException(
                    "build.gradle слишком большой"
            );
        }

        if (dependencies == null) {
            throw new IOException(
                    "Список зависимостей отсутствует"
            );
        }

        String original = new String(
                Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8
        );

        String result = original;
        List<String> missing = new ArrayList<>();

        for (String coordinate : dependencies) {

            if (coordinate == null ||
                    !COORDINATE.matcher(
                            coordinate
                    ).matches()) {
                throw new IOException(
                        "Неверная зависимость: "
                        + coordinate
                );
            }

            if (missing.contains(coordinate)
                    || containsDependency(
                            result, coordinate
                    )) {
                continue;
            }

            missing.add(coordinate);
        }

        if (missing.isEmpty()) {
            return 0;
        }

        int opening = findDependenciesBlock(result);

        if (opening < 0) {

            if (!bracesBalanced(result)) {
                throw new IOException(
                        "Проверь фигурные скобки "
                        + "в build.gradle"
                );
            }

            if (!result.endsWith("\n")) {
                result += "\n";
            }

            result += "\ndependencies {\n}\n";

            opening = result.lastIndexOf(
                    "dependencies {"
            ) + "dependencies ".length();
        }

        StringBuilder lines =
                new StringBuilder();

        for (String coordinate : missing) {
            lines.append("\n    implementation '")
                    .append(coordinate)
                    .append("'");
        }

        result = result.substring(0, opening + 1)
                + lines
                + result.substring(opening + 1);

        saveWithBackup(file, project, result);

        return missing.size();
    }

    private boolean containsDependency(
            String text,
            String coordinate) {

        Pattern pattern = Pattern.compile(
                "(?m)^\\s*"
                + "(?:implementation|api|compileOnly|"
                + "runtimeOnly|testImplementation|"
                + "androidTestImplementation)"
                + "\\s*(?:\\(\\s*)?['\"]"
                + Pattern.quote(coordinate)
                + "['\"]"
        );

        return pattern.matcher(text).find();
    }

    /**
     * Finds a top-level Groovy dependencies block.
     * Text inside comments and quoted strings is ignored, so a sample
     * string or commented-out snippet cannot be mistaken for real Gradle code.
     * Returns -1 when the block is absent and throws when it is ambiguous.
     */
    private int findDependenciesBlock(String text) throws IOException {
        int depth = 0;
        int found = -1;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length()
                    ? text.charAt(i + 1) : '\0';

            if (lineComment) {
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }

            if (!singleQuote && !doubleQuote) {
                if (c == '/' && next == '/') {
                    lineComment = true;
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    blockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '\\' && (singleQuote || doubleQuote)) {
                i++;
                continue;
            }
            if (c == '\'' && !doubleQuote) {
                singleQuote = !singleQuote;
                continue;
            }
            if (c == '"' && !singleQuote) {
                doubleQuote = !doubleQuote;
                continue;
            }
            if (singleQuote || doubleQuote) continue;

            if (depth == 0 && isDependenciesKeyword(text, i)) {
                int j = i + "dependencies".length();
                while (j < text.length()
                        && Character.isWhitespace(text.charAt(j))) {
                    j++;
                }
                if (j < text.length() && text.charAt(j) == '{') {
                    if (found >= 0) {
                        throw new IOException(
                                "Найдено несколько блоков dependencies. "
                                + "Исправь app/build.gradle вручную."
                        );
                    }
                    found = j;
                }
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth < 0) {
                    throw new IOException(
                            "Нарушен баланс фигурных скобок в build.gradle"
                    );
                }
            }
        }

        if (singleQuote || doubleQuote || blockComment || depth != 0) {
            throw new IOException(
                    "Не удалось безопасно разобрать build.gradle: "
                    + "проверь кавычки, комментарии и фигурные скобки."
            );
        }
        return found;
    }

    private boolean isDependenciesKeyword(String text, int index) {
        String keyword = "dependencies";
        if (index + keyword.length() > text.length()
                || !text.regionMatches(index, keyword, 0, keyword.length())) {
            return false;
        }

        boolean leftBoundary = index == 0
                || !Character.isJavaIdentifierPart(text.charAt(index - 1));
        int end = index + keyword.length();
        boolean rightBoundary = end >= text.length()
                || !Character.isJavaIdentifierPart(text.charAt(end));

        return leftBoundary && rightBoundary;
    }

    private boolean bracesBalanced(String text) {

        int depth = 0;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < text.length(); i++) {

            char c = text.charAt(i);
            char next = i + 1 < text.length()
                    ? text.charAt(i + 1)
                    : '\0';

            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                }
                continue;
            }

            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }

            if (!singleQuote && !doubleQuote) {

                if (c == '/' && next == '/') {
                    lineComment = true;
                    i++;
                    continue;
                }

                if (c == '/' && next == '*') {
                    blockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '\\' &&
                    (singleQuote || doubleQuote)) {
                i++;
                continue;
            }

            if (c == '\'' && !doubleQuote) {
                singleQuote = !singleQuote;
                continue;
            }

            if (c == '"' && !singleQuote) {
                doubleQuote = !doubleQuote;
                continue;
            }

            if (singleQuote || doubleQuote) {
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }

        return depth == 0
                && !singleQuote
                && !doubleQuote
                && !blockComment;
    }

    private void saveWithBackup(
            File file,
            File project,
            String content)
            throws IOException {

        Path target = file.toPath();

        Path backup = getChild(
                project,
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
                    content.getBytes(
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
}
