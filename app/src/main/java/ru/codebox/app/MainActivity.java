package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private LinearLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHome();
    }

    private void showHome() {
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("CodeBox 5");
        title.setTextSize(30);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Android Java IDE");
        subtitle.setTextSize(18);
        layout.addView(subtitle);

        Button create = new Button(this);
        create.setText("Создать новый проект");
        layout.addView(create);

        create.setOnClickListener(v -> showCreateDialog());

        setContentView(layout);
    }

    private void showCreateDialog() {
        EditText input = new EditText(this);
        input.setHint("Название проекта");
        input.setSingleLine(true);
        input.setPadding(32, 20, 32, 20);

        new AlertDialog.Builder(this)
                .setTitle("Новый проект")
                .setView(input)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = input.getText().toString().trim();

                    if (!name.matches("[a-zA-Z][a-zA-Z0-9_]{0,49}")) {
                        Toast.makeText(this,
                                "Используй латинские буквы, цифры и _",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    createProject(name);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void createProject(String name) {
        File root = new File(getFilesDir(), "projects");
        File project = new File(root, name);

        if (project.exists()) {
            Toast.makeText(this,
                    "Проект уже существует",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        File javaDir = new File(project,
                "app/src/main/java/com/example/" + name.toLowerCase());

        File resDir = new File(project,
                "app/src/main/res/layout");

        try {
            if (!javaDir.mkdirs() || !resDir.mkdirs()) {
                throw new Exception("Не удалось создать папки");
            }

            String packageName = "com.example." + name.toLowerCase();

            writeFile(new File(project, "settings.gradle"),
                    "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n"
                    + "dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\n"
                    + "rootProject.name = '" + name + "'\n"
                    + "include ':app'\n");

            writeFile(new File(project, "build.gradle"),
                    "plugins {\n"
                    + "    id 'com.android.application' version '8.7.3' apply false\n"
                    + "}\n");

            writeFile(new File(project, "app/build.gradle"),
                    "plugins { id 'com.android.application' }\n"
                    + "android {\n"
                    + "    namespace '" + packageName + "'\n"
                    + "    compileSdk 35\n"
                    + "    defaultConfig {\n"
                    + "        applicationId '" + packageName + "'\n"
                    + "        minSdk 26\n"
                    + "        targetSdk 28\n"
                    + "        versionCode 1\n"
                    + "        versionName '1.0'\n"
                    + "    }\n"
                    + "}\n");

            writeFile(new File(project,
                    "app/src/main/AndroidManifest.xml"),
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                    + "    <application android:theme=\"@android:style/Theme.Material.Light.NoActionBar\" android:label=\"" + name + "\">\n"
                    + "        <activity android:name=\".MainActivity\" android:exported=\"true\">\n"
                    + "            <intent-filter>\n"
                    + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                    + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                    + "            </intent-filter>\n"
                    + "        </activity>\n"
                    + "    </application>\n"
                    + "</manifest>\n");

            writeFile(new File(javaDir, "MainActivity.java"),
                    "package " + packageName + ";\n\n"
                    + "import android.app.Activity;\n"
                    + "import android.os.Bundle;\n"
                    + "import android.widget.TextView;\n\n"
                    + "public class MainActivity extends Activity {\n"
                    + "    @Override\n"
                    + "    protected void onCreate(Bundle state) {\n"
                    + "        super.onCreate(state);\n"
                    + "        TextView text = new TextView(this);\n"
                    + "        text.setText(\"Hello from " + name + "!\");\n"
                    + "        text.setTextSize(24);\n"
                    + "        setContentView(text);\n"
                    + "    }\n"
                    + "}\n");

            showProject(name, project);

        } catch (Exception e) {
            Toast.makeText(this,
                    "Ошибка: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void writeFile(File file, String content) throws Exception {
        File parent = file.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new Exception("Не удалось создать папку");
        }

        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void showProject(String name, File project) {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("Проект: " + name);
        title.setTextSize(26);
        screen.addView(title);

        TextView info = new TextView(this);
        info.setText("\nПроект успешно создан!\n\n"
                + "Созданы файлы:\n"
                + "settings.gradle\n"
                + "build.gradle\n"
                + "app/build.gradle\n"
                + "AndroidManifest.xml\n"
                + "MainActivity.java\n\n"
                + "Путь:\n" + project.getAbsolutePath());
        info.setTextSize(15);
        screen.addView(info);

        Button back = new Button(this);
        back.setText("На главную");
        screen.addView(back);

        back.setOnClickListener(v -> showHome());

        setContentView(screen);
    }

    @Override
    public void onBackPressed() {
        showHome();
    }
}
