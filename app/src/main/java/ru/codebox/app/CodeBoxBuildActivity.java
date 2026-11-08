package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Selection;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** CodeBox 5: offline APK/AAB build, SDK storage, export, install and launch. */
public final class CodeBoxBuildActivity extends Activity {

    private static final int PICK_TOOLCHAIN_ZIP = 7124;
    private static final int PICK_ANDROID_JAR = 7125;
    private static final int SAVE_BUILT_ARTIFACT = 7126;
    private static final int REQUEST_INSTALL_PERMISSION = 7127;

    private static final int MAX_CLASS_DEPTH = 40;
    private static final int MAX_CLASS_FILES = 100000;

    private CodeBoxBuildManager manager;

    private String projectName;
    private String builtApplicationId;

    private TextView statusView;
    private TextView reportView;
    private TextView sdkView;

    private String installationReport;

    private boolean busy;

    private File artifactPendingExport;
    private File signedUniversalApk;
    private File pendingInstallApk;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        projectName = getIntent().getStringExtra("project_name");

        if (projectName == null ||
                !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
            fatal("Не передано корректное имя проекта");
            return;
        }

        try {
            manager = new CodeBoxBuildManager(this);
        } catch (IOException e) {
            fatal("Невозможно создать журнал: " + e);
            return;
        }

        buildInterface();
        refreshSdkStatus();
        refreshReport();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (sdkView != null) {
            refreshSdkStatus();
        }
    }

    private void fatal(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Ошибка")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Закрыть", (d, w) -> finish())
                .show();
    }

    // ---------------------------------------------------------
    // ANDROID SDK STORAGE
    // ---------------------------------------------------------

    private File getAndroidJarFile() {
        return new File(
                getFilesDir(),
                "build-tools/android.jar"
        );
    }

    private boolean isAndroidJarInstalled() {
        File jar = getAndroidJarFile();

        return jar.isFile() && jar.length() > 0;
    }

    private void refreshSdkStatus() {
        if (sdkView == null) return;

        File jar = getAndroidJarFile();

        if (jar.isFile() && jar.length() > 0) {

            String size = String.format(
                    Locale.US,
                    "%.2f MB",
                    jar.length() / (1024.0 * 1024.0)
            );

            sdkView.setText(
                    "✓ Android SDK установлен\n"
                            + "android.jar • " + size + "\n"
                            + "Хранилище: внутренняя память CodeBox 5\n"
                            + "Файл сохранён и доступен после перезапуска."
            );

            sdkView.setTextColor(
                    Color.rgb(105, 220, 155)
            );

        } else {

            sdkView.setText(
                    "Android SDK не установлен\n"
                            + "Выберите android.jar для локальной сборки."
            );

            sdkView.setTextColor(
                    Color.rgb(255, 190, 100)
            );
        }
    }

    // ---------------------------------------------------------
    // INTERFACE
    // ---------------------------------------------------------

    private void buildInterface() {

        int bg = Color.rgb(17, 20, 35);
        int panel = Color.rgb(29, 34, 53);
        int muted = Color.rgb(185, 193, 215);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(12));
        root.setBackgroundColor(bg);

        setContentView(root);

        root.addView(
                label("Сборка проекта", 23, Color.WHITE, true)
        );

        TextView subtitle = label(
                projectName + "  •  Локально, без интернета",
                13,
                muted,
                false
        );

        LinearLayout.LayoutParams sp =
                new LinearLayout.LayoutParams(-1, -2);

        sp.topMargin = dp(5);
        sp.bottomMargin = dp(13);

        root.addView(subtitle, sp);

        statusView = label(
                "Ожидание",
                13,
                muted,
                true
        );

        statusView.setPadding(
                dp(12), dp(10), dp(12), dp(10)
        );

        statusView.setBackground(shape(panel, 12));

        root.addView(statusView);

        // SDK STATUS PANEL

        sdkView = label(
                "Проверка Android SDK...",
                12,
                muted,
                true
        );

        sdkView.setPadding(
                dp(12), dp(10), dp(12), dp(10)
        );

        sdkView.setBackground(shape(panel, 12));

        LinearLayout.LayoutParams sdkParams =
                new LinearLayout.LayoutParams(-1, -2);

        sdkParams.topMargin = dp(8);

        root.addView(sdkView, sdkParams);

        refreshSdkStatus();

        // BUILD BUTTONS

        LinearLayout builds = new LinearLayout(this);

        LinearLayout.LayoutParams bp =
                new LinearLayout.LayoutParams(-1, -2);

        bp.topMargin = dp(10);

        root.addView(builds, bp);

        addWeightedButton(
                builds,
                "Собрать APK",
                Color.rgb(107, 73, 218),
                () -> requestBuild(
                        CodeBoxBuildManager.OutputFormat.APK
                ),
                true
        );

        addWeightedButton(
                builds,
                "Собрать AAB",
                Color.rgb(37, 111, 192),
                () -> requestBuild(
                        CodeBoxBuildManager.OutputFormat.AAB
                ),
                false
        );

        LinearLayout actions = new LinearLayout(this);

        LinearLayout.LayoutParams ap =
                new LinearLayout.LayoutParams(-1, -2);

        ap.topMargin = dp(8);

        root.addView(actions, ap);

        addWeightedButton(
                actions,
                "Установить APK",
                Color.rgb(47, 121, 113),
                this::requestApkInstall,
                true
        );

        addWeightedButton(
                actions,
                "Запустить",
                Color.rgb(47, 93, 121),
                this::launchBuiltApp,
                false
        );

        addFullButton(
                root,
                "Установить android.jar",
                Color.rgb(47, 121, 113),
                this::requestAndroidJar,
                8
        );

        addFullButton(
                root,
                "Установить инструменты из ZIP",
                Color.rgb(47, 93, 121),
                this::requestInstall,
                8
        );

        TextView heading = label(
                "Журнал сборки",
                17,
                Color.WHITE,
                true
        );

        LinearLayout.LayoutParams hp =
                new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(15);
        hp.bottomMargin = dp(6);
        root.addView(heading, hp);

        // Короткий предпросмотр вместо сжатого многострочного журнала.
        ScrollView previewScroll = new ScrollView(this);
        previewScroll.setFillViewport(false);
        previewScroll.setBackground(shape(panel, 12));

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(-1, dp(76));
        root.addView(previewScroll, previewParams);

        reportView = label(
                "Журнал появится после проверки.",
                11,
                muted,
                false
        );
        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);
        reportView.setMaxLines(3);
        reportView.setPadding(dp(12), dp(10), dp(12), dp(10));
        previewScroll.addView(reportView);

        addFullButton(
                root,
                "Открыть полный журнал",
                Color.rgb(89, 69, 173),
                this::openFullJournal,
                8
        );

        addFullButton(
                root,
                "Сохранить APK / AAB",
                Color.rgb(47, 121, 113),
                this::requestArtifactExport,
                10
        );

        LinearLayout tools = new LinearLayout(this);

        LinearLayout.LayoutParams tp =
                new LinearLayout.LayoutParams(-1, -2);

        tp.topMargin = dp(10);

        root.addView(tools, tp);

        addWeightedButton(
                tools,
                "Сохранить TXT",
                Color.rgb(65, 76, 102),
                this::saveLog,
                true
        );

        addWeightedButton(
                tools,
                "Поделиться",
                Color.rgb(65, 76, 102),
                this::shareLog,
                false
        );
    }

    private void addFullButton(
            LinearLayout parent,
            String name,
            int color,
            Runnable action,
            int margin
    ) {
        TextView view = actionButton(name, color);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, dp(44));

        p.topMargin = dp(margin);

        parent.addView(view, p);

        view.setOnClickListener(v -> guarded(action));
    }

    private void addWeightedButton(
            LinearLayout row,
            String name,
            int color,
            Runnable action,
            boolean first
    ) {
        TextView view = actionButton(name, color);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(0, dp(45), 1);

        if (first) {
            p.rightMargin = dp(5);
        } else {
            p.leftMargin = dp(5);
        }

        row.addView(view, p);

        view.setOnClickListener(v -> guarded(action));
    }

    private void guarded(Runnable action) {
        if (busy) {
            toast("Дождитесь завершения текущей операции");
            return;
        }

        action.run();
    }

    // ---------------------------------------------------------
    // BUILD
    // ---------------------------------------------------------

    private void requestBuild(
            CodeBoxBuildManager.OutputFormat format
    ) {
        if (!isAndroidJarInstalled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Android SDK отсутствует")
                    .setMessage(
                            "Для локальной сборки требуется android.jar.\n\n"
                                    + "Установите Android SDK. После установки "
                                    + "файл будет храниться во внутренней памяти "
                                    + "CodeBox 5 и не потребуется выбирать "
                                    + "его при каждом запуске."
                    )
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton(
                            "Установить",
                            (d, w) -> requestAndroidJar()
                    )
                    .show();
            return;
        }

        String message =
                "Локальная сборка: AAPT2 → ECJ → D8.\n\n";

        if (format == CodeBoxBuildManager.OutputFormat.APK) {

            message +=
                    "APK будет упакован, подписан Android Keystore "
                            + "и проверен apksig.\n\n";

        } else {

            message +=
                    "AAB будет сформирован bundletool, подписан "
                            + "тестовым RSA-ключом и проверен. "
                            + "Дополнительно попробуем создать "
                            + "подписанный universal.apk. "
                            + "Ошибка дополнительного APK "
                            + "не отменяет успешный AAB.\n\n";
        }

        message +=
                "Требуется установленный android.jar. "
                        + "Не удаляйте CodeBox: тестовые ключи "
                        + "могут быть утрачены.";

        new AlertDialog.Builder(this)
                .setTitle("Локальная сборка " + format.name())
                .setMessage(message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Запустить",
                        (d, w) -> runDiagnostic(format)
                )
                .show();
    }

    private void runDiagnostic(
            CodeBoxBuildManager.OutputFormat format
    ) {
        if (busy) return;

        if (!isAndroidJarInstalled()) {
            toast("Сначала установите android.jar");
            refreshSdkStatus();
            return;
        }

        busy = true;
        installationReport = null;
        artifactPendingExport = null;
        signedUniversalApk = null;
        builtApplicationId = null;
        pendingInstallApk = null;

        statusView.setText(
                "Статус: сборка " + format.name() + "..."
        );

        reportView.setText(
                "Выполняется локальная сборка. Подождите..."
        );

        new Thread(() -> {
            try {

                manager.start(projectName, format);

                manager.setStage("Проверка проекта");

                File projectsRoot =
                        new File(getFilesDir(), "projects")
                                .getCanonicalFile();

                File project =
                        new File(projectsRoot, projectName)
                                .getCanonicalFile();

                if (!projectsRoot.equals(project.getParentFile())
                        || !project.isDirectory()) {

                    manager.fail(
                            "Папка проекта не найдена или путь недопустим"
                    );
                    return;
                }

                manager.info(
                        "Папка проекта найдена: "
                                + project.getAbsolutePath()
                );

                manager.info("Формат результата: " + format.name());

                manager.setStage("Проверка инструментов");

                CodeBoxToolchainInspector.Result tools =
                        CodeBoxToolchainInspector.inspect(
                                getApplicationContext()
                        );

                manager.info(tools.report);

                manager.info(
                        "Готовность APK по старому инспектору: "
                                + (tools.readyForApk ? "да" : "нет")
                );

                manager.info(
                        "Готовность AAB по старому инспектору: "
                                + (tools.readyForAab ? "да" : "нет")
                );

                manager.info(
                        "ECJ/D8/bundletool подключены как зависимости приложения."
                );

                manager.info(
                        "ANDROID_JAR: "
                                + getAndroidJarFile().getAbsolutePath()
                );

                manager.info(
                        "ANDROID_JAR_SIZE: "
                                + getAndroidJarFile().length()
                );

                manager.setStage("Проверка и запуск AAPT2");

                try {

                    CodeBoxAapt2Inspector.Result a =
                            CodeBoxAapt2Inspector.inspect(
                                    getApplicationContext(),
                                    true
                            );

                    manager.info(a.report);

                    if (!a.found) {
                        manager.warning("AAPT2 не найден.");
                    } else {
                        manager.info("AAPT2_FOUND: YES");
                        manager.info(
                                "AAPT2_ELF_VALID: " + a.elfValid
                        );
                        manager.info(
                                "AAPT2_ARCH_COMPATIBLE: "
                                        + a.architectureCompatible
                        );
                        manager.info(
                                "AAPT2_EXECUTABLE: " + a.executable
                        );
                        manager.info(
                                "AAPT2_LAUNCH_TEST: "
                                        + (a.launchSuccessful
                                        ? "OK" : "FAILED")
                        );
                    }

                } catch (Throwable e) {
                    manager.warning(
                            "Ошибка диагностики AAPT2: " + e
                    );
                }

                manager.setStage("Подготовка AndroidManifest.xml");

                File preparedManifest;

                try {

                    File work = new File(
                            getCacheDir(),
                            (format == CodeBoxBuildManager.OutputFormat.APK
                                    ? "apk-manifest-" : "aab-manifest-")
                                    + projectName
                    );

                    CodeBoxApkManifestPreparer.Result m =
                            CodeBoxApkManifestPreparer.prepare(
                                    project,
                                    work
                            );

                    manager.info(m.report);

                    if (m.manifestFile == null
                            || !m.manifestFile.isFile()
                            || m.manifestFile.length() == 0) {
                        throw new IOException(
                                "Подготовленный манифест отсутствует"
                        );
                    }

                    preparedManifest = m.manifestFile;

                    if (m.applicationId == null
                            || !m.applicationId.matches(
                            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"
                    )) {
                        throw new IOException(
                                "Некорректный applicationId"
                        );
                    }

                    builtApplicationId = m.applicationId;

                    manager.info(
                            "applicationId: " + m.applicationId
                    );

                    manager.info("MANIFEST_PREPARED: OK");

                } catch (Exception e) {
                    manager.fail(
                            "Ошибка подготовки манифеста: " + e
                    );
                    return;
                }

                File linkedResources;

                manager.setStage(
                        format == CodeBoxBuildManager.OutputFormat.APK
                                ? "Компиляция ресурсов APK (AAPT2)"
                                : "Компиляция protobuf ресурсов AAB (AAPT2)"
                );

                try {

                    CodeBoxAapt2Compiler.Result r =
                            format == CodeBoxBuildManager.OutputFormat.APK
                                    ? CodeBoxAapt2Compiler.compile(
                                    getApplicationContext(),
                                    project,
                                    preparedManifest,
                                    projectName
                            )
                                    : CodeBoxAapt2Compiler.compileForBundle(
                                    getApplicationContext(),
                                    project,
                                    preparedManifest,
                                    projectName
                            );

                    manager.info(r.report);

                    if (!r.compiled
                            || r.resourceApk == null
                            || !r.resourceApk.isFile()
                            || r.resourceApk.length() == 0) {

                        manager.fail(
                                "AAPT2 не создал ресурсный APK."
                        );
                        return;
                    }

                    linkedResources = r.resourceApk;

                    manager.info(
                            format == CodeBoxBuildManager.OutputFormat.APK
                                    ? "RESOURCE_APK_CREATED: OK"
                                    : "PROTO_RESOURCE_APK_CREATED: OK"
                    );

                    manager.info(
                            "Ресурсы: "
                                    + linkedResources.getAbsolutePath()
                    );

                    manager.info(
                            "Размер: " + linkedResources.length() + " байт"
                    );

                } catch (Throwable e) {
                    manager.fail("Ошибка AAPT2: " + e);
                    return;
                }

                manager.setStage("Подготовка Java");

                CodeBoxJavaCompiler.Result prep =
                        CodeBoxJavaCompiler.inspect(
                                getApplicationContext(),
                                project
                        );

                manager.info(prep.report);

                manager.setStage("Компиляция Java (ECJ)");

                CodeBoxEcjCompiler.Result compiled =
                        CodeBoxEcjCompiler.compile(
                                getApplicationContext(),
                                project
                        );

                manager.info(compiled.report);

                if (!compiled.compiled) {
                    manager.fail("ECJ не создал классы Java.");
                    return;
                }

                manager.info(
                        "Java успешно скомпилирована. Каталог: "
                                + compiled.classesDirectory.getAbsolutePath()
                );

                verifyClassFilesAndRunD8(
                        compiled.classesDirectory,
                        format,
                        linkedResources
                );

            } catch (Throwable e) {

                if (manager.getState()
                        == CodeBoxBuildManager.State.RUNNING) {

                    manager.fail(
                            "Ошибка сборки: "
                                    + e.getClass().getName()
                                    + ": "
                                    + e.getMessage()
                    );
                }

            } finally {

                runOnUiThread(() -> {
                    busy = false;
                    refreshSdkStatus();
                    refreshReport();
                });
            }

        }, "CodeBoxLocalBuild").start();
    }

    // ---------------------------------------------------------
    // CLASS AND DEX
    // ---------------------------------------------------------

    private void verifyClassFilesAndRunD8(
            File classesDirectory,
            CodeBoxBuildManager.OutputFormat format,
            File linkedResources
    ) throws IOException {

        manager.setStage("Проверка CLASS");

        if (classesDirectory == null
                || !classesDirectory.isDirectory()) {

            manager.fail("Каталог CLASS файлов не существует");
            return;
        }

        File root = classesDirectory.getCanonicalFile();

        manager.info("Каталог CLASS: " + root.getAbsolutePath());

        ClassScan scan = new ClassScan();

        scanClassFiles(root, root, scan, 0);

        manager.info(
                "Найдено CLASS файлов: " + scan.count
        );

        if (scan.count == 0) {
            manager.fail(
                    "ECJ сообщил об успехе, но CLASS файлы отсутствуют."
            );
            return;
        }

        manager.info("CLASS файлы созданы успешно.");

        if (scan.mainActivity != null) {

            manager.info("MainActivity.class: OK");

            manager.info(
                    "Путь: " + scan.mainActivity.getAbsolutePath()
            );

            manager.info(
                    "Размер: " + scan.mainActivity.length() + " байт"
            );

        } else {
            manager.warning(
                    "MainActivity.class не найден. Возможно, главный класс имеет другое имя."
            );
        }

        manager.info("Проверка CLASS завершена.");

        manager.setStage("Проверка D8");

        try {
            manager.info(
                    CodeBoxD8Inspector.inspect(
                            getApplicationContext()
                    )
            );
        } catch (Throwable e) {
            manager.warning("Ошибка проверки D8: " + e);
        }

        manager.setStage("Компиляция DEX (D8)");

        CodeBoxD8Compiler.Result dex =
                CodeBoxD8Compiler.compile(
                        getApplicationContext(),
                        root,
                        projectName
                );

        manager.info(dex.report);

        if (!dex.compiled
                || dex.dexFile == null
                || !dex.dexFile.isFile()
                || dex.dexFile.length() == 0) {

            manager.fail("D8 не создал classes.dex.");
            return;
        }

        manager.info("classes.dex: OK");

        manager.info(
                "Путь: " + dex.dexFile.getAbsolutePath()
        );

        manager.info(
                "Размер: " + dex.dexFile.length() + " байт"
        );

        if (format == CodeBoxBuildManager.OutputFormat.APK) {
            buildSignedApk(linkedResources, dex.dexFile);
        } else {
            buildSignedAab(linkedResources, dex.dexFile);
        }
    }

    private static final class ClassScan {
        int count;
        File mainActivity;
    }

    private static void scanClassFiles(
            File root,
            File directory,
            ClassScan result,
            int depth
    ) throws IOException {

        if (depth > MAX_CLASS_DEPTH) {
            throw new IOException(
                    "Превышена глубина каталогов CLASS"
            );
        }

        File[] children = directory.listFiles();

        if (children == null) {
            throw new IOException(
                    "Не удалось прочитать каталог CLASS: " + directory
            );
        }

        String rootPath =
                root.getCanonicalPath() + File.separator;

        for (File child : children) {

            File canonical = child.getCanonicalFile();

            if (!canonical.getCanonicalPath().startsWith(rootPath)) {
                throw new IOException(
                        "CLASS путь вне каталога результата"
                );
            }

            if (canonical.isDirectory()) {

                scanClassFiles(
                        root,
                        canonical,
                        result,
                        depth + 1
                );

            } else if (canonical.isFile()
                    && canonical.getName().endsWith(".class")) {

                if (++result.count > MAX_CLASS_FILES) {
                    throw new IOException(
                            "Слишком много CLASS файлов"
                    );
                }

                if ("MainActivity.class".equals(canonical.getName())) {
                    result.mainActivity = canonical;
                }
            }
        }
    }

    // ---------------------------------------------------------
    // APK BUILD
    // ---------------------------------------------------------

    private void buildSignedApk(
            File linkedResources,
            File dexFile
    ) throws IOException {

        manager.setStage("Упаковка APK");

        CodeBoxApkPackager.Result packaged =
                CodeBoxApkPackager.packageApk(
                        getApplicationContext(),
                        linkedResources,
                        dexFile,
                        projectName
                );

        manager.info(packaged.report);

        if (!packaged.packaged
                || packaged.apkFile == null
                || !packaged.apkFile.isFile()
                || packaged.apkFile.length() == 0) {

            manager.fail("Упаковщик не создал APK.");
            return;
        }

        manager.info("UNSIGNED_APK_CREATED: OK");

        manager.info(
                "Путь: " + packaged.apkFile.getAbsolutePath()
        );

        manager.info(
                "Размер: " + packaged.apkFile.length() + " байт"
        );

        manager.setStage("Подпись APK");

        CodeBoxApkSigner.Result signed =
                CodeBoxApkSigner.sign(
                        getApplicationContext(),
                        packaged.apkFile,
                        projectName
                );

        manager.info(signed.report);

        if (!signed.signed
                || signed.signedApk == null
                || !signed.signedApk.isFile()
                || signed.signedApk.length() == 0) {

            manager.fail("Подпись APK не удалась.");
            return;
        }

        manager.info("SIGNED_APK_CREATED: OK");

        manager.info(
                "Путь: " + signed.signedApk.getAbsolutePath()
        );

        manager.info(
                "Размер: " + signed.signedApk.length() + " байт"
        );

        manager.info("APK подписан и проверен apksig.");

        manager.succeed(signed.signedApk);
    }

    // ---------------------------------------------------------
    // AAB BUILD
    // ---------------------------------------------------------

    private void buildSignedAab(
            File linkedResources,
            File dexFile
    ) throws IOException {

        manager.setStage("Подготовка модуля base (AAB)");

        CodeBoxAabBuilder.Result base =
                CodeBoxAabBuilder.buildBaseModule(
                        getApplicationContext(),
                        linkedResources,
                        dexFile,
                        projectName
                );

        manager.info(base.report);

        if (!base.prepared
                || base.moduleZip == null
                || !base.moduleZip.isFile()
                || base.moduleZip.length() == 0) {

            manager.fail(
                    "Не удалось создать ZIP модуля base."
            );
            return;
        }

        manager.info("BASE_MODULE_PREPARED: OK");

        manager.setStage("Формирование AAB (bundletool)");

        CodeBoxBundletoolCompiler.Result bundle =
                CodeBoxBundletoolCompiler.build(
                        getApplicationContext(),
                        base.moduleZip,
                        projectName
                );

        manager.info(bundle.report);

        if (!bundle.built
                || bundle.aabFile == null
                || !bundle.aabFile.isFile()
                || bundle.aabFile.length() == 0) {

            manager.fail(
                    "Bundletool не создал корректный AAB."
            );
            return;
        }

        manager.info("UNSIGNED_AAB_CREATED: OK");

        manager.info(
                "Неподписанный AAB: "
                        + bundle.aabFile.getAbsolutePath()
        );

        manager.info(
                "Размер: " + bundle.aabFile.length() + " байт"
        );

        manager.setStage("Подпись AAB (CMS/PKCS#7)");

        manager.info("Начало JAR-подписи AAB.");
        manager.info("Алгоритм: SHA256withRSA");
        manager.info("Ключ: Android Keystore (тестовый)");

        CodeBoxAabSigner.Result signed =
                CodeBoxAabSigner.sign(
                        getApplicationContext(),
                        bundle.aabFile,
                        projectName
                );

        manager.info(signed.report);

        if (!signed.signed
                || signed.aabFile == null
                || !signed.aabFile.isFile()
                || signed.aabFile.length() == 0) {

            manager.fail(
                    "Подпись или проверка AAB не удалась."
            );
            return;
        }

        manager.info("SIGNED_AAB_CREATED: OK");
        manager.info("SIGNED_AAB_VERIFIED: OK");

        manager.info(
                "Подписанный AAB: "
                        + signed.aabFile.getAbsolutePath()
        );

        manager.info(
                "Размер: " + signed.aabFile.length() + " байт"
        );

        manager.setStage(
                "Проверка подписанного AAB (bundletool)"
        );

        manager.info("Запуск CodeBoxAabValidator.");

        CodeBoxAabValidator.Result validation =
                CodeBoxAabValidator.validate(
                        getApplicationContext(),
                        signed.aabFile
                );

        manager.info(validation.report);

        if (!validation.valid) {
            manager.fail(
                    "Подписанный AAB не прошёл проверку структуры bundletool."
            );
            return;
        }

        manager.info("SIGNED_AAB_STRUCTURE_VALID: OK");

        manager.warning(
                "AAB подписан тестовым ключом. "
                        + "Для публикации нужен постоянный ключ подписи."
        );

        diagnoseApksBuilder(signed.aabFile);

        manager.succeed(signed.aabFile);
    }

    private void diagnoseApksBuilder(File signedAab) {

        signedUniversalApk = null;

        try {

            manager.info("\n--- APKS Builder diagnostics ---");

            manager.info(
                    "Создание универсального APK из подписанного AAB."
            );

            CodeBoxApksBuilder.Result result =
                    CodeBoxApksBuilder.build(
                            getApplicationContext(),
                            signedAab,
                            projectName
                    );

            if (result.report != null) {
                manager.info(result.report);
            }

            manager.info("APKS_DIAGNOSTICS: COMPLETED");
            manager.info("APKS_BUILT: " + result.built);

            if (result.built
                    && result.universalApk != null
                    && result.universalApk.isFile()
                    && result.universalApk.length() > 0) {

                signedUniversalApk = result.universalApk;

                manager.info("SIGNED_UNIVERSAL_APK: READY");

                manager.info(
                        "APK: "
                                + signedUniversalApk.getAbsolutePath()
                );

                manager.info(
                        "APK_SIZE: " + signedUniversalApk.length()
                );

                manager.info("APK_EXPORT_AVAILABLE: YES");

            } else {

                manager.info("SIGNED_UNIVERSAL_APK: UNAVAILABLE");
                manager.info("APK_EXPORT_AVAILABLE: NO");
                manager.info("Готовый AAB сохраняется.");
            }

        } catch (Throwable e) {

            signedUniversalApk = null;

            manager.warning(
                    "Дополнительная сборка APK недоступна: " + e
            );

            manager.info("Рабочая сборка AAB сохраняется.");
        }
    }

    // ---------------------------------------------------------
    // APK INSTALLATION
    // ---------------------------------------------------------

    private File availableApk() {

        if (manager.getState()
                != CodeBoxBuildManager.State.SUCCEEDED) {
            return null;
        }

        File primary = manager.getOutputFile();

        if (primary != null
                && primary.isFile()
                && primary.length() > 0
                && primary.getName().toLowerCase(Locale.ROOT)
                .endsWith(".apk")) {
            return primary;
        }

        if (signedUniversalApk != null
                && signedUniversalApk.isFile()
                && signedUniversalApk.length() > 0
                && signedUniversalApk.getName()
                .toLowerCase(Locale.ROOT).endsWith(".apk")) {
            return signedUniversalApk;
        }

        return null;
    }

    private void requestApkInstall() {

        File apk = availableApk();

        if (apk == null) {

            new AlertDialog.Builder(this)
                    .setTitle("APK недоступен")
                    .setMessage(
                            "Сначала соберите APK или AAB "
                                    + "с готовым universal.apk."
                    )
                    .setPositiveButton("OK", null)
                    .show();

            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Установка APK")
                .setMessage(
                        "Файл: " + apk.getName()
                                + "\nРазмер: " + apk.length()
                                + " байт\n\n"
                                + "Android откроет системное "
                                + "подтверждение установки."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Продолжить",
                        (d, w) -> prepareApkInstall(apk)
                )
                .show();
    }

    private void prepareApkInstall(File source) {

        if (busy) return;

        busy = true;

        statusView.setText(
                "Статус: подготовка APK к установке..."
        );

        new Thread(() -> {

            File prepared = null;
            String error = null;

            try {

                File dir =
                        new File(getCacheDir(), "apk-install")
                                .getCanonicalFile();

                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException(
                            "Не удалось создать каталог установки"
                    );
                }

                File target =
                        new File(dir, "codebox-install.apk")
                                .getCanonicalFile();

                if (!dir.equals(target.getParentFile())) {
                    throw new IOException(
                            "Неверный путь установки"
                    );
                }

                File canonicalSource =
                        source.getCanonicalFile();

                if (!canonicalSource.isFile()
                        || canonicalSource.length() == 0) {
                    throw new IOException("APK отсутствует");
                }

                File temp =
                        new File(dir, "codebox-install.tmp");

                try (
                        InputStream in =
                                new FileInputStream(canonicalSource);
                        FileOutputStream out =
                                new FileOutputStream(temp)
                ) {

                    byte[] buffer = new byte[32768];
                    int count;

                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }

                    out.getFD().sync();
                }

                if (temp.length() != canonicalSource.length()) {
                    throw new IOException(
                            "APK скопирован не полностью"
                    );
                }

                if (target.exists() && !target.delete()) {
                    throw new IOException(
                            "Не удалось заменить предыдущий APK"
                    );
                }

                if (!temp.renameTo(target)) {
                    throw new IOException(
                            "Не удалось подготовить APK"
                    );
                }

                prepared = target;

            } catch (Exception e) {
                error = e.toString();
            }

            final File ready = prepared;
            final String failure = error;

            runOnUiThread(() -> {

                busy = false;
                refreshReport();

                if (failure != null) {
                    showError("Подготовка APK", failure);
                    return;
                }

                pendingInstallApk = ready;
                continueApkInstall();
            });

        }, "CodeBoxApkInstallPrepare").start();
    }

    private void continueApkInstall() {

        if (pendingInstallApk == null
                || !pendingInstallApk.isFile()) {

            toast("Файл установки отсутствует");
            return;
        }

        if (Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {

            new AlertDialog.Builder(this)
                    .setTitle("Разрешение на установку")
                    .setMessage(
                            "Разрешите установку приложений "
                                    + "из CodeBox 5 в системных настройках, "
                                    + "затем вернитесь сюда."
                    )
                    .setNegativeButton(
                            "Отмена",
                            (d, w) -> pendingInstallApk = null
                    )
                    .setPositiveButton(
                            "Открыть настройки",
                            (d, w) -> {
                                try {

                                    Intent settings = new Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse(
                                                    "package:" + getPackageName()
                                            )
                                    );

                                    startActivityForResult(
                                            settings,
                                            REQUEST_INSTALL_PERMISSION
                                    );

                                } catch (Exception e) {
                                    showError(
                                            "Настройки установки",
                                            e.toString()
                                    );
                                }
                            }
                    )
                    .show();

            return;
        }

        try {

            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    pendingInstallApk
            );

            Intent install = new Intent(Intent.ACTION_VIEW);

            install.setDataAndType(
                    uri,
                    "application/vnd.android.package-archive"
            );

            install.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            startActivity(install);

            pendingInstallApk = null;

        } catch (Exception e) {
            showError(
                    "Не удалось открыть установщик",
                    e.toString()
            );
        }
    }

    // ---------------------------------------------------------
    // LAUNCH
    // ---------------------------------------------------------

    private void launchBuiltApp() {

        if (manager.getState()
                != CodeBoxBuildManager.State.SUCCEEDED
                || builtApplicationId == null) {

            toast(
                    "Сначала успешно соберите проект в текущем сеансе"
            );
            return;
        }

        try {

            Intent launch =
                    getPackageManager().getLaunchIntentForPackage(
                            builtApplicationId
                    );

            if (launch == null) {

                new AlertDialog.Builder(this)
                        .setTitle("Приложение не найдено")
                        .setMessage(
                                "Не удалось найти запускаемую Activity для "
                                        + builtApplicationId
                                        + ". Убедитесь, что APK установлен "
                                        + "и имеет LAUNCHER Activity."
                        )
                        .setPositiveButton("OK", null)
                        .show();

                return;
            }

            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(launch);

        } catch (Exception e) {
            showError("Ошибка запуска", e.toString());
        }
    }

    // ---------------------------------------------------------
    // ARTIFACT EXPORT
    // ---------------------------------------------------------

    private void requestArtifactExport() {

        if (manager.getState()
                != CodeBoxBuildManager.State.SUCCEEDED) {

            toast("Сначала успешно соберите APK или AAB");
            return;
        }

        File primary = manager.getOutputFile();

        if (primary == null
                || !primary.isFile()
                || primary.length() == 0) {

            toast(
                    "Готовый файл не найден. Соберите проект повторно"
            );
            return;
        }

        File additional = signedUniversalApk;

        boolean aab = primary.getName()
                .toLowerCase(Locale.ROOT).endsWith(".aab");

        if (!aab
                || additional == null
                || !additional.isFile()
                || additional.length() == 0) {

            openArtifactSaveDialog(primary);
            return;
        }

        String[] choices = {
                "Сохранить AAB — " + primary.getName(),
                "Сохранить APK — " + additional.getName()
        };

        new AlertDialog.Builder(this)
                .setTitle("Выберите формат")
                .setItems(
                        choices,
                        (d, which) -> openArtifactSaveDialog(
                                which == 0 ? primary : additional
                        )
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void openArtifactSaveDialog(File artifact) {

        if (artifact == null
                || !artifact.isFile()
                || artifact.length() == 0) {

            toast("Файл для сохранения отсутствует");
            return;
        }

        String name =
                artifact.getName().toLowerCase(Locale.ROOT);

        boolean apk = name.endsWith(".apk");
        boolean aab = name.endsWith(".aab");

        if (!apk && !aab) {
            toast("Неизвестный формат результата");
            return;
        }

        artifactPendingExport = artifact;

        Intent intent =
                new Intent(Intent.ACTION_CREATE_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType(
                apk
                        ? "application/vnd.android.package-archive"
                        : "application/octet-stream"
        );

        intent.putExtra(
                Intent.EXTRA_TITLE,
                artifact.getName()
        );

        try {
            startActivityForResult(
                    intent,
                    SAVE_BUILT_ARTIFACT
            );
        } catch (Exception e) {
            artifactPendingExport = null;
            toast("Ошибка сохранения: " + e.getMessage());
        }
    }

    private void exportArtifact(Uri destination) {

        final File source = artifactPendingExport;
        artifactPendingExport = null;

        if (source == null
                || !source.isFile()
                || source.length() == 0) {

            toast("Исходный файл отсутствует");
            return;
        }

        busy = true;

        statusView.setText(
                "Статус: сохранение " + source.getName() + "..."
        );

        new Thread(() -> {

            String error = null;
            long copied = 0;

            try (
                    InputStream in = new FileInputStream(source);
                    OutputStream out =
                            getContentResolver().openOutputStream(
                                    destination,
                                    "w"
                            )
            ) {

                if (out == null) {
                    throw new IOException(
                            "Не удалось открыть файл назначения"
                    );
                }

                byte[] buffer = new byte[32768];
                int count;

                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    copied += count;
                }

                out.flush();

                if (copied != source.length()) {
                    throw new IOException(
                            "Скопировано не всё содержимое файла"
                    );
                }

            } catch (Exception e) {
                error = e.toString();
            }

            final String failure = error;
            final long bytes = copied;

            runOnUiThread(() -> {

                busy = false;
                refreshReport();

                if (failure != null) {
                    showError("Ошибка сохранения", failure);
                    return;
                }

                boolean aab = source.getName()
                        .toLowerCase(Locale.ROOT).endsWith(".aab");

                String msg =
                        "Файл: " + source.getName()
                                + "\nРазмер: " + bytes + " байт";

                msg += aab
                        ? "\n\nAAB подписан тестовым ключом. "
                        + "Для публикации нужен постоянный ключ."
                        : "\n\nAPK подписан и прошёл проверку подписи.";

                new AlertDialog.Builder(this)
                        .setTitle(aab ? "AAB сохранён" : "APK сохранён")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();
            });

        }, "CodeBoxArtifactExport").start();
    }

    // ---------------------------------------------------------
    // ANDROID.JAR INSTALLATION
    // ---------------------------------------------------------

    private void requestAndroidJar() {

        new AlertDialog.Builder(this)
                .setTitle("Установка Android API")
                .setMessage(
                        "Выберите настоящий android.jar из Android SDK, "
                                + "например platforms/android-35/android.jar.\n\n"
                                + "После проверки CodeBox сохранит файл "
                                + "во внутренней памяти приложения:\n\n"
                                + getAndroidJarFile().getAbsolutePath()
                                + "\n\n"
                                + "При следующих запусках повторно "
                                + "выбирать файл не потребуется."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Выбрать android.jar",
                        (d, w) -> {

                            Intent intent =
                                    new Intent(Intent.ACTION_OPEN_DOCUMENT);

                            intent.addCategory(
                                    Intent.CATEGORY_OPENABLE
                            );

                            intent.setType("*/*");

                            intent.putExtra(
                                    Intent.EXTRA_MIME_TYPES,
                                    new String[]{
                                            "application/java-archive",
                                            "application/zip",
                                            "application/octet-stream"
                                    }
                            );

                            try {
                                startActivityForResult(
                                        intent,
                                        PICK_ANDROID_JAR
                                );
                            } catch (Exception e) {
                                toast(
                                        "Ошибка выбора файла: "
                                                + e.getMessage()
                                );
                            }
                        }
                )
                .show();
    }

    private void installAndroidJar(Uri uri) {

        busy = true;
        installationReport = null;

        statusView.setText(
                "Статус: установка android.jar..."
        );

        reportView.setText(
                "Проверяем Android API и копируем файл "
                        + "во внутреннее хранилище CodeBox 5. Подождите..."
        );

        new Thread(() -> {

            String result;

            try (
                    InputStream stream =
                            getContentResolver().openInputStream(uri)
            ) {

                if (stream == null) {
                    throw new IOException(
                            "Не удалось открыть android.jar"
                    );
                }

                CodeBoxAndroidJarInstaller.Result installed =
                        CodeBoxAndroidJarInstaller.install(
                                getApplicationContext(),
                                stream
                        );

                result = installed.report;

            } catch (Exception e) {

                result =
                        "CodeBox 5 — установка Android API\n"
                                + "Статус: FAILED\n"
                                + "Ошибка: " + e;
            }

            final String report = result;

            runOnUiThread(() -> {

                busy = false;
                installationReport = report;

                refreshSdkStatus();
                refreshReport();

            });

        }, "CodeBoxAndroidJarInstall").start();
    }

    // ---------------------------------------------------------
    // TOOLCHAIN ZIP INSTALLATION
    // ---------------------------------------------------------

    private void requestInstall() {

        new AlertDialog.Builder(this)
                .setTitle("Установка инструментов")
                .setMessage(
                        "Нужен проверенный ZIP с шестью файлами в корне:\n\n"
                                + "aapt2\n"
                                + "android.jar\n"
                                + "d8.jar\n"
                                + "ecj.jar\n"
                                + "apksigner.jar\n"
                                + "bundletool.jar\n\n"
                                + "Потребуется доверенный SHA-256 "
                                + "из независимого источника."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Выбрать ZIP",
                        (d, w) -> {

                            Intent intent =
                                    new Intent(Intent.ACTION_OPEN_DOCUMENT);

                            intent.addCategory(
                                    Intent.CATEGORY_OPENABLE
                            );

                            intent.setType("application/zip");

                            intent.putExtra(
                                    Intent.EXTRA_MIME_TYPES,
                                    new String[]{
                                            "application/zip",
                                            "application/octet-stream"
                                    }
                            );

                            try {
                                startActivityForResult(
                                        intent,
                                        PICK_TOOLCHAIN_ZIP
                                );
                            } catch (Exception e) {
                                toast(
                                        "Ошибка выбора файла: "
                                                + e.getMessage()
                                );
                            }
                        }
                )
                .show();
    }

    private void installZip(Uri uri, String expected) {

        busy = true;
        installationReport = null;

        statusView.setText(
                "Статус: установка инструментов..."
        );

        reportView.setText(
                "Проверка SHA-256 и распаковка ZIP. Подождите..."
        );

        new Thread(() -> {

            String result;

            try (
                    InputStream stream =
                            getContentResolver().openInputStream(uri)
            ) {

                if (stream == null) {
                    throw new IOException("Не удалось открыть ZIP");
                }

                CodeBoxToolchainInstaller.Result installed =
                        CodeBoxToolchainInstaller.install(
                                getApplicationContext(),
                                stream,
                                expected
                        );

                CodeBoxToolchainInspector.Result inventory =
                        CodeBoxToolchainInspector.inspect(
                                getApplicationContext()
                        );

                result =
                        "CodeBox 5 — установка инструментов\n"
                                + "Статус: файлы распакованы\n"
                                + installed.report
                                + "\n\n"
                                + inventory.report;

            } catch (Exception e) {

                result =
                        "CodeBox 5 — установка инструментов\n"
                                + "Статус: FAILED\n"
                                + "Ошибка: " + e;
            }

            final String report = result;

            runOnUiThread(() -> {

                busy = false;
                installationReport = report;

                refreshSdkStatus();
                refreshReport();

            });

        }, "CodeBoxToolchainInstall").start();
    }

    // ---------------------------------------------------------
    // ACTIVITY RESULTS
    // ---------------------------------------------------------

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == REQUEST_INSTALL_PERMISSION) {

            if (Build.VERSION.SDK_INT < 26
                    || getPackageManager()
                    .canRequestPackageInstalls()) {

                continueApkInstall();

            } else {

                toast(
                        "Разрешение установки не предоставлено"
                );
            }

            return;
        }

        if (requestCode == SAVE_BUILT_ARTIFACT) {

            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null
                    && !busy) {

                exportArtifact(data.getData());

            } else {

                artifactPendingExport = null;
            }

            return;
        }

        if (resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        if (busy) {
            toast(
                    "Дождитесь завершения текущей операции"
            );
            return;
        }

        Uri uri = data.getData();

        if (requestCode == PICK_ANDROID_JAR) {
            installAndroidJar(uri);
            return;
        }

        if (requestCode != PICK_TOOLCHAIN_ZIP) {
            return;
        }

        EditText hash = new EditText(this);

        hash.setSingleLine(true);

        hash.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );

        hash.setHint("64 символа SHA-256");

        hash.setPadding(
                dp(16), dp(12), dp(16), dp(12)
        );

        new AlertDialog.Builder(this)
                .setTitle("Доверенный SHA-256")
                .setMessage(
                        "Вставьте контрольную сумму "
                                + "из независимого доверенного источника."
                )
                .setView(hash)
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Установить",
                        (d, w) -> {

                            String expected =
                                    hash.getText().toString().trim();

                            if (!expected.matches(
                                    "(?i)[0-9a-f]{64}"
                            )) {

                                toast(
                                        "SHA-256 должен содержать "
                                                + "64 шестнадцатеричных символа"
                                );
                                return;
                            }

                            installZip(uri, expected);
                        }
                )
                .show();
    }

    // ---------------------------------------------------------
    // REPORT
    // ---------------------------------------------------------

    private void refreshReport() {

        if (manager == null
                || statusView == null
                || reportView == null) {
            return;
        }

        if (installationReport != null) {

            boolean failed =
                    installationReport.contains("Статус: FAILED");

            statusView.setText(
                    failed
                            ? "Установка: ошибка"
                            : "Установка: завершена"
            );

            setReportPreview(installationReport);

            return;
        }

        CodeBoxBuildManager.State state =
                manager.getState();

        statusView.setText(
                "Статус: " + state.name()
                        + "  |  Ошибок: " + manager.getErrorCount()
                        + "  |  Предупреждений: "
                        + manager.getWarningCount()
        );

        if (state == CodeBoxBuildManager.State.IDLE) {

            setReportPreview(
                    "CodeBox 5 готов к локальной сборке.\n\n"
                            + "Android SDK проверяется автоматически.\n"
                            + "Установленный android.jar хранится "
                            + "во внутренней памяти приложения.\n\n"
                            + "Расположение:\n"
                            + getAndroidJarFile().getAbsolutePath()
            );

        } else {

            setReportPreview(manager.getReport());
        }
    }

    // ---------------------------------------------------------
    // FULL-SCREEN BUILD JOURNAL
    // ---------------------------------------------------------

    private void setReportPreview(String report) {
        if (reportView == null) return;
        String value = report == null || report.trim().isEmpty()
                ? "Журнал пока пуст." : report;
        reportView.setText(colorizeReport(value));
    }

    private void openFullJournal() {
        String report = currentReport();
        if (report == null || report.trim().isEmpty()) {
            report = "CodeBox 5 готов к локальной сборке.\n\n"
                    + "Журнал появится после запуска сборки "
                    + "или установки инструментов.";
        }
        final String fullReport = report;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(14), dp(14), dp(12));
        page.setBackgroundColor(Color.rgb(17, 20, 35));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = label("Полный журнал сборки", 19, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView close = actionButton("Закрыть", Color.rgb(65, 76, 102));
        header.addView(close, new LinearLayout.LayoutParams(dp(88), dp(40)));
        page.addView(header);

        TextView hint = label(
                "Ошибки — красным • предупреждения — жёлтым • успех — зелёным",
                11, Color.rgb(185, 193, 215), false
        );
        LinearLayout.LayoutParams hintParams =
                new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(8);
        hintParams.bottomMargin = dp(10);
        page.addView(hint, hintParams);

        LinearLayout firstActions = new LinearLayout(this);
        firstActions.setOrientation(LinearLayout.HORIZONTAL);

        TextView selectAll = actionButton(
                "Выделить всё", Color.rgb(65, 76, 102));
        TextView copySelected = actionButton(
                "Копировать выделенное", Color.rgb(47, 93, 121));

        LinearLayout.LayoutParams selectParams =
                new LinearLayout.LayoutParams(0, dp(44), 1);
        selectParams.rightMargin = dp(4);
        firstActions.addView(selectAll, selectParams);

        LinearLayout.LayoutParams copySelectedParams =
                new LinearLayout.LayoutParams(0, dp(44), 1);
        copySelectedParams.leftMargin = dp(4);
        firstActions.addView(copySelected, copySelectedParams);
        page.addView(firstActions);

        TextView copyAll = actionButton(
                "Копировать весь журнал", Color.rgb(47, 121, 113));
        LinearLayout.LayoutParams copyAllParams =
                new LinearLayout.LayoutParams(-1, dp(44));
        copyAllParams.topMargin = dp(8);
        copyAllParams.bottomMargin = dp(10);
        page.addView(copyAll, copyAllParams);

        ScrollView fullScroll = new ScrollView(this);
        fullScroll.setFillViewport(true);
        fullScroll.setBackground(shape(Color.rgb(29, 34, 53), 12));
        page.addView(fullScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView fullText = label("", 12, Color.rgb(205, 211, 226), false);
        fullText.setTypeface(Typeface.MONOSPACE);
        fullText.setTextIsSelectable(true);
        fullText.setPadding(dp(12), dp(12), dp(12), dp(18));
        fullText.setText(colorizeReport(fullReport));
        fullScroll.addView(fullText);

        close.setOnClickListener(v -> dialog.dismiss());

        selectAll.setOnClickListener(v -> {
            CharSequence text = fullText.getText();
            if (text instanceof Spannable) {
                Selection.selectAll((Spannable) text);
                toast("Весь журнал выделен");
            }
        });

        copySelected.setOnClickListener(v -> {
            int start = Selection.getSelectionStart(fullText.getText());
            int end = Selection.getSelectionEnd(fullText.getText());
            if (start < 0 || end < 0 || start == end) {
                toast("Сначала выделите текст в журнале");
                return;
            }
            int from = Math.min(start, end);
            int to = Math.max(start, end);
            String selected = fullText.getText()
                    .subSequence(from, to).toString();
            copyToClipboard("CodeBox 5 — выделенный журнал", selected);
        });

        copyAll.setOnClickListener(v ->
                copyToClipboard(
                        "CodeBox 5 — полный журнал " + projectName,
                        fullReport
                )
        );

        dialog.setContentView(page);
        dialog.setCancelable(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }
    }

    private void copyToClipboard(String label, String value) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                toast("Буфер обмена недоступен");
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            toast("Скопировано в буфер обмена");
        } catch (Exception e) {
            toast("Не удалось скопировать: " + e.getMessage());
        }
    }

    /**
     * Подсвечивает всю строку в зависимости от типа сообщения.
     * Ошибки красные, предупреждения жёлтые, успешные этапы зелёные.
     */
    private CharSequence colorizeReport(String report) {
        String source = report == null ? "" : report;
        SpannableStringBuilder styled = new SpannableStringBuilder(source);

        int errorColor = Color.rgb(255, 105, 105);
        int warningColor = Color.rgb(255, 195, 95);
        int successColor = Color.rgb(105, 220, 155);
        int normalColor = Color.rgb(205, 211, 226);

        int start = 0;
        while (start < styled.length()) {
            int newline = source.indexOf('\n', start);
            int end = newline >= 0 ? newline : styled.length();

            String line = styled.subSequence(start, end)
                    .toString().toLowerCase(Locale.ROOT);

            boolean warning = line.contains("[warn]")
                    || line.contains("warning")
                    || line.contains("предупреждение");

            boolean error = line.contains("[error]")
                    || line.contains(" fatal")
                    || line.startsWith("error")
                    || line.contains("failed")
                    || line.contains("exception")
                    || line.contains("caused by:")
                    || line.contains("ошибка")
                    || line.contains("сбой");

            int color = normalColor;
            if (error && !warning) {
                color = errorColor;
            } else if (warning) {
                color = warningColor;
            } else if (line.contains("[success]")
                    || line.contains("succeeded")
                    || line.contains("created: ok")
                    || line.contains("verified: ok")
                    || line.contains("успешно")
                    || line.contains("завершена: ok")) {
                color = successColor;
            }

            if (end > start) {
                styled.setSpan(
                        new ForegroundColorSpan(color),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }

            start = newline >= 0 ? newline + 1 : styled.length();
        }
        return styled;
    }

    private String currentReport() {

        if (installationReport != null) {
            return installationReport;
        }

        if (manager == null
                || manager.getState()
                == CodeBoxBuildManager.State.IDLE) {
            return null;
        }

        return manager.getReport();
    }

    // ---------------------------------------------------------
    // SAVE LOG
    // ---------------------------------------------------------

    private void saveLog() {

        String report = currentReport();

        if (report == null) {
            toast(
                    "Сначала запустите диагностику или установку"
            );
            return;
        }

        try {

            File file;

            if (installationReport == null) {

                file = manager.saveReport();

            } else {

                File dir =
                        new File(getFilesDir(), "build-logs");

                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException(
                            "Не удалось создать каталог журналов"
                    );
                }

                String stamp =
                        new SimpleDateFormat(
                                "yyyyMMdd_HHmmss_SSS",
                                Locale.US
                        ).format(new Date());

                file = new File(
                        dir,
                        "toolchain_" + stamp + ".txt"
                );

                try (
                        FileOutputStream out =
                                new FileOutputStream(file)
                ) {

                    out.write(
                            report.getBytes(StandardCharsets.UTF_8)
                    );

                    out.getFD().sync();
                }
            }

            new AlertDialog.Builder(this)
                    .setTitle("Журнал сохранён")
                    .setMessage(
                            "Внутренний файл приложения:\n"
                                    + file.getAbsolutePath()
                                    + "\n\n"
                                    + "Чтобы передать журнал, "
                                    + "нажмите «Поделиться»."
                    )
                    .setPositiveButton("OK", null)
                    .show();

        } catch (IOException e) {

            showError(
                    "Ошибка сохранения",
                    e.toString()
            );
        }
    }

    // ---------------------------------------------------------
    // SHARE LOG
    // ---------------------------------------------------------

    private void shareLog() {

        String report = currentReport();

        if (report == null) {
            toast(
                    "Сначала запустите диагностику или установку"
            );
            return;
        }

        Intent send = new Intent(Intent.ACTION_SEND);

        send.setType("text/plain");

        send.putExtra(
                Intent.EXTRA_SUBJECT,
                "CodeBox 5 — журнал " + projectName
        );

        send.putExtra(
                Intent.EXTRA_TEXT,
                report
        );

        try {

            startActivity(
                    Intent.createChooser(
                            send,
                            "Отправить журнал"
                    )
            );

        } catch (Exception e) {

            toast(
                    "Ошибка отправки: " + e.getMessage()
            );
        }
    }

    // ---------------------------------------------------------
    // UI HELPERS
    // ---------------------------------------------------------

    private void showError(
            String title,
            String message
    ) {

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private TextView label(
            String value,
            int size,
            int color,
            boolean bold
    ) {

        TextView v = new TextView(this);

        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);

        if (bold) {
            v.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return v;
    }

    private TextView actionButton(
            String value,
            int color
    ) {

        TextView v = label(
                value,
                13,
                Color.WHITE,
                true
        );

        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);

        v.setEllipsize(
                TextUtils.TruncateAt.END
        );

        v.setBackground(
                shape(color, 11)
        );

        return v;
    }

    private GradientDrawable shape(
            int color,
            int radius
    ) {

        GradientDrawable d =
                new GradientDrawable();

        d.setColor(color);
        d.setCornerRadius(dp(radius));

        return d;
    }

    private int dp(float value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics().density
                        + 0.5f
        );
    }

    private void toast(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
                        }
