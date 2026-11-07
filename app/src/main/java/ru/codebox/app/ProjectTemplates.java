package ru.codebox.app;

import java.util.Locale;

public final class ProjectTemplates {

    private ProjectTemplates() {
        // Класс шаблонов
    }

    public static String getPackageName(String projectName) {
        return "com.example."
                + projectName.toLowerCase(Locale.ROOT);
    }

    public static String settingsGradle(String projectName) {
        return "pluginManagement {\n"
                + "    repositories {\n"
                + "        google()\n"
                + "        mavenCentral()\n"
                + "        gradlePluginPortal()\n"
                + "    }\n"
                + "}\n\n"
                + "dependencyResolutionManagement {\n"
                + "    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n"
                + "    repositories {\n"
                + "        google()\n"
                + "        mavenCentral()\n"
                + "    }\n"
                + "}\n\n"
                + "rootProject.name = '" + projectName + "'\n"
                + "include ':app'\n";
    }

    public static String rootBuildGradle() {
        return "plugins {\n"
                + "    id 'com.android.application' version '8.7.3' apply false\n"
                + "}\n";
    }

    public static String appBuildGradle(String packageName) {
        return "plugins {\n"
                + "    id 'com.android.application'\n"
                + "}\n\n"
                + "android {\n"
                + "    namespace '" + packageName + "'\n"
                + "    compileSdk 35\n\n"
                + "    defaultConfig {\n"
                + "        applicationId '" + packageName + "'\n"
                + "        minSdk 26\n"
                + "        targetSdk 28\n"
                + "        versionCode 1\n"
                + "        versionName '1.0'\n"
                + "    }\n\n"
                + "    compileOptions {\n"
                + "        sourceCompatibility JavaVersion.VERSION_17\n"
                + "        targetCompatibility JavaVersion.VERSION_17\n"
                + "    }\n"
                + "}\n";
    }

    public static String manifest(String projectName) {
        String packageName = getPackageName(projectName);

        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"" + packageName + "\">\n\n"
                + "    <application\n"
                + "        android:label=\"" + projectName + "\"\n"
                + "        android:theme=\"@android:style/Theme.Material.Light.NoActionBar\">\n\n"
                + "        <activity\n"
                + "            android:name=\".MainActivity\"\n"
                + "            android:exported=\"true\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n\n"
                + "    </application>\n"
                + "</manifest>\n";
    }

    public static String mainActivity(String packageName,
                                      String projectName) {
        return "package " + packageName + ";\n\n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "import android.widget.TextView;\n\n"
                + "public class MainActivity extends Activity {\n\n"
                + "    @Override\n"
                + "    protected void onCreate(Bundle state) {\n"
                + "        super.onCreate(state);\n\n"
                + "        TextView text = new TextView(this);\n"
                + "        text.setText(\"Hello from " + projectName + "!\");\n"
                + "        text.setTextSize(24);\n"
                + "        setContentView(text);\n"
                + "    }\n"
                + "}\n";
    }

    public static String gradleProperties() {
        return "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n"
                + "android.useAndroidX=true\n";
    }

    public static String gitignore() {
        return ".gradle/\n"
                + "build/\n"
                + "local.properties\n"
                + "*.iml\n";
    }
}
