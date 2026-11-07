package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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

/**
 * CodeBox 5.
 *
 * Local APK and AAB build screen.
 *
 * APK:
 * AAPT2 -> ECJ -> D8 -> APK -> APK signer.
 *
 * AAB:
 * AAPT2 -> ECJ -> D8 -> base module ->
 * bundletool -> CMS/JAR signer ->
 * signature verification -> AAB validation.
 */
public final class CodeBoxBuildActivity extends Activity {

    private static final int PICK_TOOLCHAIN_ZIP = 7124;
    private static final int PICK_ANDROID_JAR = 7125;
    private static final int SAVE_BUILT_ARTIFACT = 7126;

    private static final int MAX_CLASS_DEPTH = 40;
    private static final int MAX_CLASS_FILES = 100000;

    private CodeBoxBuildManager manager;
    private String projectName;

    private TextView statusView;
    private TextView reportView;

    private String installationReport;
    private boolean installing;

    private File artifactPendingExport;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        projectName = getIntent().getStringExtra(
                "project_name"
        );

        if (projectName == null
                || !projectName.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {

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
        refreshReport();
    }

    private void fatal(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Ошибка")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(
                        "Закрыть",
                        (d, w) -> finish()
                )
                .show();
    }

    private void buildInterface() {

        int background = Color.rgb(17, 20, 35);
        int panel = Color.rgb(29, 34, 53);
        int white = Color.WHITE;
        int muted = Color.rgb(185, 193, 215);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        root.setPadding(
                dp(16),
                dp(20),
                dp(16),
                dp(12)
        );

        root.setBackgroundColor(background);
        setContentView(root);

        root.addView(
                label("Сборка проекта", 23, white, true)
        );

        TextView subtitle = label(
                projectName + "  •  Локально, без интернета",
                13,
                muted,
                false
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(-1, -2);

        subtitleParams.topMargin = dp(5);
        subtitleParams.bottomMargin = dp(13);

        root.addView(subtitle, subtitleParams);

        statusView = label(
                "Ожидание",
                13,
                muted,
                true
        );

        statusView.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        statusView.setBackground(shape(panel, 12));
        root.addView(statusView);

        LinearLayout builds = new LinearLayout(this);

        LinearLayout.LayoutParams buildsParams =
                new LinearLayout.LayoutParams(-1, -2);

        buildsParams.topMargin = dp(10);
        root.addView(builds, buildsParams);

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

        TextView androidJarButton = actionButton(
                "Установить android.jar",
                Color.rgb(47, 121, 113)
        );

        LinearLayout.LayoutParams jarParams =
                new LinearLayout.LayoutParams(-1, dp(44));

        jarParams.topMargin = dp(10);

        root.addView(androidJarButton, jarParams);

        androidJarButton.setOnClickListener(v -> {
            if (installing) {
                toast("Дождитесь завершения текущей операции");
            } else {
                requestAndroidJar();
            }
        });

        TextView install = actionButton(
                "Установить инструменты из ZIP",
                Color.rgb(47, 93, 121)
        );

        LinearLayout.LayoutParams installParams =
                new LinearLayout.LayoutParams(-1, dp(44));

        installParams.topMargin = dp(8);
        root.addView(install, installParams);

        install.setOnClickListener(v -> {
            if (installing) {
                toast("Дождитесь завершения текущей операции");
            } else {
                requestInstall();
            }
        });

        TextView heading = label(
                "Подробный журнал",
                17,
                white,
                true
        );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(-1, -2);

        headingParams.topMargin = dp(15);
        headingParams.bottomMargin = dp(8);

        root.addView(heading, headingParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(shape(panel, 12));

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        reportView = label(
                "Журнал появится после проверки.",
                12,
                muted,
                false
        );

        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setTextIsSelectable(true);

        reportView.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
        );

        scroll.addView(reportView);

        TextView export = actionButton(
                "Сохранить APK / AAB",
                Color.rgb(47, 121, 113)
        );

        LinearLayout.LayoutParams exportParams =
                new LinearLayout.LayoutParams(-1, dp(44));

        exportParams.topMargin = dp(10);
        root.addView(export, exportParams);

        export.setOnClickListener(
                v -> requestArtifactExport()
        );

        LinearLayout tools = new LinearLayout(this);

        LinearLayout.LayoutParams toolsParams =
                new LinearLayout.LayoutParams(-1, -2);

        toolsParams.topMargin = dp(10);
        root.addView(tools, toolsParams);

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

    private void addWeightedButton(
            LinearLayout row,
            String name,
            int color,
            Runnable action,
            boolean first
    ) {

        TextView button = actionButton(name, color);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(45), 1);

        if (first) {
            params.rightMargin = dp(5);
        } else {
            params.leftMargin = dp(5);
        }

        row.addView(button, params);

        button.setOnClickListener(v -> {
            if (installing) {
                toast("Дождитесь завершения текущей операции");
                return;
            }

            action.run();
        });
    }

    private void requestBuild(
            CodeBoxBuildManager.OutputFormat format
    ) {

        if (installing) {
            toast("Дождитесь завершения текущей операции");
            return;
        }

        String message =
                "Проверим инструменты, подготовим "
                        + "AndroidManifest.xml и ресурсы "
                        + "через AAPT2, затем запустим "
                        + "ECJ и D8.\n\n";

        if (format ==
                CodeBoxBuildManager.OutputFormat.APK) {

            message +=
                    "APK: упакуем classes.dex, "
                            + "подпишем APK ключом "
                            + "Android Keystore и проверим "
                            + "подпись через apksig.\n\n";

        } else {

            message +=
                    "AAB: создадим protobuf-ресурсы, "
                            + "ZIP модуля base, запустим "
                            + "bundletool, затем подпишем "
                            + "AAB тестовым RSA-ключом "
                            + "через CMS/PKCS#7.\n\n"
                            + "Проверим JAR-подпись и "
                            + "структуру подписанного AAB "
                            + "через bundletool.\n\n"
                            + "Это тестовый ключ, "
                            + "не предназначенный "
                            + "для публикации.";
        }

        message +=
                "\n\nТребуется установленный "
                        + "build-tools/android.jar.\n\n"
                        + "Не удаляйте CodeBox: "
                        + "ключи Android Keystore "
                        + "могут быть утрачены.";

        new AlertDialog.Builder(this)
                .setTitle(
                        "Локальная сборка " + format.name()
                )
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

        if (installing) {
            toast("Дождитесь завершения текущей операции");
            return;
        }

        installing = true;
        installationReport = null;
        artifactPendingExport = null;

        statusView.setText(
                "Статус: сборка " + format.name() + "..."
        );

        reportView.setText(
                "Выполняется локальная сборка "
                        + format.name()
                        + ". Подождите..."
        );

        new Thread(() -> {

            try {

                manager.start(projectName, format);
                manager.setStage("Проверка проекта");

                File projectsRoot = new File(
                        getFilesDir(),
                        "projects"
                ).getCanonicalFile();

                File project = new File(
                        projectsRoot,
                        projectName
                ).getCanonicalFile();

                if (!projectsRoot.equals(
                        project.getParentFile()
                ) || !project.isDirectory()) {

                    manager.fail(
                            "Папка проекта не найдена "
                                    + "или путь недопустим"
                    );
                    return;
                }

                manager.info(
                        "Папка проекта найдена: "
                                + project.getAbsolutePath()
                );

                manager.info(
                        "Формат результата: " + format.name()
                );

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
                        "Примечание: инспектор проверяет "
                                + "внешние JAR, а ECJ/D8/"
                                + "bundletool подключены "
                                + "как зависимости приложения."
                );

                manager.setStage(
                        "Проверка и запуск AAPT2"
                );

                try {

                    CodeBoxAapt2Inspector.Result aapt2 =
                            CodeBoxAapt2Inspector.inspect(
                                    getApplicationContext(),
                                    true
                            );

                    manager.info(aapt2.report);

                    if (!aapt2.found) {
                        manager.warning("AAPT2 не найден.");
                    } else {

                        manager.info("AAPT2_FOUND: YES");

                        manager.info(
                                "AAPT2_ELF_VALID: "
                                        + aapt2.elfValid
                        );

                        manager.info(
                                "AAPT2_ARCH_COMPATIBLE: "
                                        + aapt2.architectureCompatible
                        );

                        manager.info(
                                "AAPT2_EXECUTABLE: "
                                        + aapt2.executable
                        );

                        manager.info(
                                "AAPT2_LAUNCH_TEST: "
                                        + (aapt2.launchSuccessful
                                        ? "OK" : "FAILED")
                        );
                    }

                } catch (Throwable error) {

                    manager.warning(
                            "Ошибка диагностики AAPT2: "
                                    + error.getClass().getName()
                                    + ": "
                                    + error.getMessage()
                    );
                }

                manager.setStage(
                        "Подготовка AndroidManifest.xml"
                );

                File preparedManifest;

                try {

                    File manifestWork = new File(
                            getCacheDir(),
                            (format ==
                                    CodeBoxBuildManager.OutputFormat.APK
                                    ? "apk-manifest-"
                                    : "aab-manifest-")
                                    + projectName
                    );

                    CodeBoxApkManifestPreparer.Result
                            manifestResult =
                            CodeBoxApkManifestPreparer.prepare(
                                    project,
                                    manifestWork
                            );

                    manager.info(manifestResult.report);

                    if (manifestResult.manifestFile == null
                            || !manifestResult.manifestFile.isFile()
                            || manifestResult.manifestFile.length() == 0) {

                        throw new IOException(
                                "Подготовленный манифест отсутствует"
                        );
                    }

                    preparedManifest =
                            manifestResult.manifestFile;

                    manager.info(
                            "applicationId: "
                                    + manifestResult.applicationId
                    );

                    manager.info("MANIFEST_PREPARED: OK");

                } catch (Exception e) {

                    manager.fail(
                            "Ошибка подготовки манифеста: "
                                    + e.getClass().getName()
                                    + ": "
                                    + e.getMessage()
                    );
                    return;
                }

                File linkedResources;

                manager.setStage(
                        format ==
                                CodeBoxBuildManager.OutputFormat.APK
                                ? "Компиляция ресурсов APK (AAPT2)"
                                : "Компиляция protobuf ресурсов AAB (AAPT2)"
                );

                try {

                    if (format ==
                            CodeBoxBuildManager.OutputFormat.APK) {

                        CodeBoxAapt2Compiler.Result resources =
                                CodeBoxAapt2Compiler.compile(
                                        getApplicationContext(),
                                        project,
                                        preparedManifest,
                                        projectName
                                );

                        manager.info(resources.report);

                        if (!resources.compiled
                                || resources.resourceApk == null
                                || !resources.resourceApk.isFile()
                                || resources.resourceApk.length() == 0) {

                            manager.fail(
                                    "AAPT2 не создал ресурсный APK."
                            );
                            return;
                        }

                        linkedResources = resources.resourceApk;

                        manager.info(
                                "RESOURCE_APK_CREATED: OK"
                        );

                    } else {

                        CodeBoxAapt2Compiler.Result resources =
                                CodeBoxAapt2Compiler.compileForBundle(
                                        getApplicationContext(),
                                        project,
                                        preparedManifest,
                                        projectName
                                );

                        manager.info(resources.report);

                        if (!resources.compiled
                                || resources.resourceApk == null
                                || !resources.resourceApk.isFile()
                                || resources.resourceApk.length() == 0) {

                            manager.fail(
                                    "AAPT2 не создал protobuf-ресурсы "
                                            + "для AAB."
                            );
                            return;
                        }

                        linkedResources = resources.resourceApk;

                        manager.info(
                                "PROTO_RESOURCE_APK_CREATED: OK"
                        );
                    }

                    manager.info(
                            "Ресурсы: "
                                    + linkedResources.getAbsolutePath()
                    );

                    manager.info(
                            "Размер: "
                                    + linkedResources.length()
                                    + " байт"
                    );

                } catch (Throwable error) {

                    manager.fail(
                            "Ошибка AAPT2: "
                                    + error.getClass().getName()
                                    + ": "
                                    + error.getMessage()
                    );
                    return;
                }

                manager.setStage("Подготовка Java");

                CodeBoxJavaCompiler.Result preparation =
                        CodeBoxJavaCompiler.inspect(
                                getApplicationContext(),
                                project
                        );

                manager.info(preparation.report);

                manager.setStage("Компиляция Java (ECJ)");

                CodeBoxEcjCompiler.Result compiled =
                        CodeBoxEcjCompiler.compile(
                                getApplicationContext(),
                                project
                        );

                manager.info(compiled.report);

                if (!compiled.compiled) {

                    manager.fail(
                            "ECJ не создал классы Java. "
                                    + "См. диагностику выше."
                    );
                    return;
                }

                manager.info(
                        "Java успешно скомпилирована. "
                                + "Каталог: "
                                + compiled.classesDirectory
                                        .getAbsolutePath()
                );

                verifyClassFilesAndRunD8(
                        compiled.classesDirectory,
                        format,
                        linkedResources
                );

            } catch (Throwable e) {

                if (manager.getState() ==
                        CodeBoxBuildManager.State.RUNNING) {

                    manager.fail(
                            "Ошибка сборки: "
                                    + e.getClass().getName()
                                    + ": "
                                    + e.getMessage()
                    );
                }

            } finally {

                runOnUiThread(() -> {
                    installing = false;
                    refreshReport();
                });
            }

        }, "CodeBoxLocalBuild").start();
    }

    private void verifyClassFilesAndRunD8(
            File classesDirectory,
            CodeBoxBuildManager.OutputFormat format,
            File linkedResources
    ) throws IOException {

        manager.setStage("Проверка CLASS");

        if (classesDirectory == null
                || !classesDirectory.isDirectory()) {

            manager.fail(
                    "Каталог CLASS файлов не существует"
            );
            return;
        }

        File root = classesDirectory.getCanonicalFile();

        manager.info(
                "Каталог CLASS: " + root.getAbsolutePath()
        );

        ClassScan scan = new ClassScan();

        scanClassFiles(root, root, scan, 0);

        manager.info(
                "Найдено CLASS файлов: " + scan.count
        );

        if (scan.count == 0) {

            manager.fail(
                    "ECJ сообщил об успехе, "
                            + "но CLASS файлы отсутствуют."
            );
            return;
        }

        manager.info(
                "CLASS файлы созданы успешно."
        );

        if (scan.mainActivity != null) {

            manager.info("MainActivity.class: OK");

            manager.info(
                    "Путь: "
                            + scan.mainActivity.getAbsolutePath()
            );

            manager.info(
                    "Размер: "
                            + scan.mainActivity.length()
                            + " байт"
            );

        } else {

            manager.warning(
                    "MainActivity.class не найден. "
                            + "Возможно, главный класс "
                            + "имеет другое имя."
            );
        }

        manager.info(
                "Проверка CLASS завершена."
        );

        manager.setStage("Проверка D8");

        try {

            manager.info(
                    CodeBoxD8Inspector.inspect(
                            getApplicationContext()
                    )
            );

        } catch (Throwable error) {

            manager.warning(
                    "Ошибка проверки D8: "
                            + error.getClass().getName()
                            + ": "
                            + error.getMessage()
            );
        }

        manager.setStage("Компиляция DEX (D8)");

        CodeBoxD8Compiler.Result dex =
                CodeBoxD8Compiler.compile(
                        getApplicationContext(),
                        root,
                        projectName
                );

        manager.info(dex.report);

        if (!dex.compiled) {

            manager.fail(
                    "D8 не создал classes.dex. "
                            + "См. диагностику D8 выше."
            );
            return;
        }

        if (dex.dexFile == null
                || !dex.dexFile.isFile()
                || dex.dexFile.length() == 0) {

            manager.fail(
                    "D8 сообщил об успехе, "
                            + "но classes.dex отсутствует "
                            + "или пуст."
            );
            return;
        }

        manager.info("classes.dex: OK");

        manager.info(
                "Путь: "
                        + dex.dexFile.getAbsolutePath()
        );

        manager.info(
                "Размер: "
                        + dex.dexFile.length()
                        + " байт"
        );

        if (format ==
                CodeBoxBuildManager.OutputFormat.APK) {

            buildSignedApk(
                    linkedResources,
                    dex.dexFile
            );

        } else {

            buildSignedAab(
                    linkedResources,
                    dex.dexFile
            );
        }
    }

    /**
     * Existing APK build pipeline.
     */
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

            manager.fail(
                    "Упаковщик не создал APK. "
                            + "См. диагностику выше."
            );
            return;
        }

        manager.info("UNSIGNED_APK_CREATED: OK");

        manager.info(
                "Путь: "
                        + packaged.apkFile.getAbsolutePath()
        );

        manager.info(
                "Размер: "
                        + packaged.apkFile.length()
                        + " байт"
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

            manager.fail(
                    "Подпись APK не удалась. "
                            + "См. диагностику подписывателя выше."
            );
            return;
        }

        manager.info("SIGNED_APK_CREATED: OK");

        manager.info(
                "Путь: "
                        + signed.signedApk.getAbsolutePath()
        );

        manager.info(
                "Размер: "
                        + signed.signedApk.length()
                        + " байт"
        );

        manager.info(
                "APK подписан и проверен apksig."
        );

        manager.succeed(signed.signedApk);
    }

    /**
     * AAB pipeline with CMS/JAR signing
     * and bundletool structure validation.
     */
    private void buildSignedAab(
            File linkedResources,
            File dexFile
    ) throws IOException {

        manager.setStage(
                "Подготовка модуля base (AAB)"
        );

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

        manager.setStage(
                "Формирование AAB (bundletool)"
        );

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
                    "Bundletool не создал корректный AAB. "
                            + "См. диагностику выше."
            );
            return;
        }

        manager.info("UNSIGNED_AAB_CREATED: OK");

        manager.info(
                "Неподписанный AAB: "
                        + bundle.aabFile.getAbsolutePath()
        );

        manager.info(
                "Размер: "
                        + bundle.aabFile.length()
                        + " байт"
        );

        // AAB signing.

        manager.setStage(
                "Подпись AAB (CMS/PKCS#7)"
        );

        manager.info(
                "Начало JAR-подписи AAB."
        );

        manager.info(
                "Алгоритм: SHA256withRSA"
        );

        manager.info(
                "Ключ: Android Keystore (тестовый)"
        );

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
                    "Подпись или проверка AAB не удалась. "
                            + "См. журнал CodeBoxAabSigner выше."
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
                "Размер: "
                        + signed.aabFile.length()
                        + " байт"
        );

        // -------------------------------------
        // NEW: Validate signed AAB with bundletool.
        // -------------------------------------

        manager.setStage(
                "Проверка подписанного AAB (bundletool)"
        );

        manager.info(
                "Запуск CodeBoxAabValidator."
        );

        CodeBoxAabValidator.Result validation =
                CodeBoxAabValidator.validate(
                        getApplicationContext(),
                        signed.aabFile
                );

        manager.info(validation.report);

        if (!validation.valid) {

            manager.fail(
                    "Подписанный AAB не прошёл "
                            + "проверку структуры bundletool. "
                            + "См. журнал валидатора выше."
            );
            return;
        }

        manager.info(
                "SIGNED_AAB_STRUCTURE_VALID: OK"
        );

        manager.warning(
                "AAB подписан тестовым ключом. "
                        + "Для публикации нужен постоянный "
                        + "ключ подписи и дополнительная "
                        + "проверка совместимости."
        );

        manager.succeed(signed.aabFile);
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
                    "Не удалось прочитать каталог CLASS: "
                            + directory
            );
        }

        for (File child : children) {

            File canonical = child.getCanonicalFile();

            String rootPath = root.getCanonicalPath();
            String childPath = canonical.getCanonicalPath();

            if (!childPath.startsWith(
                    rootPath + File.separator
            )) {

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
                    && canonical.getName()
                    .endsWith(".class")) {

                result.count++;

                if (result.count > MAX_CLASS_FILES) {

                    throw new IOException(
                            "Слишком много CLASS файлов"
                    );
                }

                if ("MainActivity.class".equals(
                        canonical.getName()
                )) {

                    result.mainActivity = canonical;
                }
            }
        }
    }

    private void requestArtifactExport() {

        if (installing) {
            toast("Дождитесь завершения текущей операции");
            return;
        }

        if (manager.getState() !=
                CodeBoxBuildManager.State.SUCCEEDED) {

            toast(
                    "Сначала успешно соберите APK или AAB"
            );
            return;
        }

        File artifact = manager.getOutputFile();

        if (artifact == null
                || !artifact.isFile()
                || artifact.length() == 0) {

            toast(
                    "Готовый файл не найден. "
                            + "Соберите проект повторно"
            );
            return;
        }

        String lowerName =
                artifact.getName().toLowerCase(Locale.ROOT);

        boolean apk = lowerName.endsWith(".apk");
        boolean aab = lowerName.endsWith(".aab");

        if (!apk && !aab) {
            toast("Неизвестный формат результата");
            return;
        }

        artifactPendingExport = artifact;

        Intent intent = new Intent(
                Intent.ACTION_CREATE_DOCUMENT
        );

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

            toast(
                    "Не удалось открыть окно сохранения: "
                            + e.getMessage()
            );
        }
    }

    private void exportArtifact(Uri destination) {

        final File source = artifactPendingExport;
        artifactPendingExport = null;

        if (source == null
                || !source.isFile()
                || source.length() == 0) {

            toast(
                    "Исходный файл отсутствует. "
                            + "Соберите проект повторно"
            );
            return;
        }

        installing = true;

        statusView.setText(
                "Статус: сохранение "
                        + source.getName()
                        + "..."
        );

        new Thread(() -> {

            String error = null;
            long copied = 0;

            try (
                    InputStream input =
                            new FileInputStream(source);

                    OutputStream output =
                            getContentResolver()
                                    .openOutputStream(
                                            destination,
                                            "w"
                                    )
            ) {

                if (output == null) {

                    throw new IOException(
                            "Не удалось открыть файл назначения"
                    );
                }

                byte[] buffer = new byte[32768];
                int count;

                while ((count = input.read(buffer)) != -1) {

                    output.write(buffer, 0, count);
                    copied += count;
                }

                output.flush();

                if (copied != source.length()) {

                    throw new IOException(
                            "Скопировано не всё содержимое файла"
                    );
                }

            } catch (Exception e) {

                error =
                        e.getClass().getSimpleName()
                                + ": "
                                + e.getMessage();
            }

            final String failure = error;
            final long bytes = copied;

            runOnUiThread(() -> {

                installing = false;
                refreshReport();

                if (failure == null) {

                    boolean aab =
                            source.getName()
                                    .toLowerCase(Locale.ROOT)
                                    .endsWith(".aab");

                    String message =
                            "Файл: "
                                    + source.getName()
                                    + "\nРазмер: "
                                    + bytes
                                    + " байт";

                    if (aab) {

                        message +=
                                "\n\nAAB создан и прошёл "
                                        + "встроенную проверку "
                                        + "JAR-подписи.\n\n"
                                        + "Используется тестовый "
                                        + "ключ. Для публикации "
                                        + "потребуется постоянный "
                                        + "ключ и дополнительная "
                                        + "проверка.";

                    } else {

                        message +=
                                "\n\nAPK можно открыть "
                                        + "через файловый менеджер "
                                        + "и установить на Android.";
                    }

                    new AlertDialog.Builder(this)
                            .setTitle(
                                    aab
                                            ? "AAB сохранён"
                                            : "APK сохранён"
                            )
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();

                } else {

                    new AlertDialog.Builder(this)
                            .setTitle("Ошибка сохранения")
                            .setMessage(failure)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });

        }, "CodeBoxArtifactExport").start();
    }

    private void requestAndroidJar() {

        if (installing) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Установка Android API")
                .setMessage(
                        "Выберите настоящий android.jar "
                                + "из Android SDK, например "
                                + "platforms/android-35/android.jar."
                                + "\n\nCodeBox проверит структуру "
                                + "JAR, основные классы Android "
                                + "и рассчитает SHA-256."
                                + "\n\nИспользуйте только "
                                + "доверенный SDK."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Выбрать android.jar",
                        (d, w) -> {

                            Intent intent = new Intent(
                                    Intent.ACTION_OPEN_DOCUMENT
                            );

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
                                        "Не удалось открыть выбор файла: "
                                                + e.getMessage()
                                );
                            }
                        }
                )
                .show();
    }

    private void installAndroidJar(Uri uri) {

        if (installing) {
            return;
        }

        installing = true;
        installationReport = null;

        statusView.setText(
                "Статус: установка android.jar..."
        );

        reportView.setText(
                "Проверяем Android API и копируем файл. "
                        + "Подождите..."
        );

        new Thread(() -> {

            String result;

            try (
                    InputStream stream =
                            getContentResolver()
                                    .openInputStream(uri)
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
                        "CodeBox 5 — установка Android API"
                                + "\nСтатус: FAILED"
                                + "\nОшибка: "
                                + e
                                + "\nУстановка не завершена.";
            }

            final String report = result;

            runOnUiThread(() -> {
                installing = false;
                installationReport = report;
                refreshReport();
            });

        }, "CodeBoxAndroidJarInstall").start();
    }

    private void requestInstall() {

        if (installing) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Установка инструментов")
                .setMessage(
                        "Нужен заранее проверенный ZIP "
                                + "с шестью файлами в корне:"
                                + "\n\naapt2, android.jar, "
                                + "d8.jar, ecj.jar, "
                                + "apksigner.jar, bundletool.jar."
                                + "\n\nТакже потребуется "
                                + "доверенный SHA-256 "
                                + "из независимого источника."
                                + "\n\nНаличие файлов "
                                + "не означает совместимость "
                                + "с Android."
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Выбрать ZIP",
                        (d, w) -> {

                            Intent intent = new Intent(
                                    Intent.ACTION_OPEN_DOCUMENT
                            );

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
                                        "Не удалось открыть выбор файла: "
                                                + e.getMessage()
                                );
                            }
                        }
                )
                .show();
    }

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

        if (requestCode == SAVE_BUILT_ARTIFACT) {

            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null) {

                if (!installing) {
                    exportArtifact(data.getData());
                }

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

        if (installing) {

            toast(
                    "Дождитесь завершения текущей операции"
            );
            return;
        }

        final Uri uri = data.getData();

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
                dp(16),
                dp(12),
                dp(16),
                dp(12)
        );

        new AlertDialog.Builder(this)
                .setTitle("Доверенный SHA-256")
                .setMessage(
                        "Вставьте контрольную сумму "
                                + "из независимого доверенного "
                                + "источника."
                                + "\n\nНе копируйте её "
                                + "из того же непроверенного архива."
                )
                .setView(hash)
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                        "Установить",
                        (d, w) -> {

                            String expected =
                                    hash.getText()
                                            .toString()
                                            .trim();

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

    private void installZip(
            Uri uri,
            String expected
    ) {

        if (installing) {
            return;
        }

        installing = true;
        installationReport = null;

        statusView.setText(
                "Статус: установка инструментов..."
        );

        reportView.setText(
                "Проверка SHA-256 и распаковка ZIP. "
                        + "Подождите..."
        );

        new Thread(() -> {

            String result;

            try (
                    InputStream stream =
                            getContentResolver()
                                    .openInputStream(uri)
            ) {

                if (stream == null) {

                    throw new IOException(
                            "Не удалось открыть ZIP"
                    );
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
                        "CodeBox 5 — установка инструментов"
                                + "\nСтатус: файлы распакованы"
                                + "\n"
                                + installed.report
                                + "\n\n"
                                + inventory.report;

            } catch (Exception e) {

                result =
                        "CodeBox 5 — установка инструментов"
                                + "\nСтатус: FAILED"
                                + "\nОшибка: "
                                + e
                                + "\nПрежний комплект мог сохраниться; "
                                + "проверьте каталог инструментов.";
            }

            final String report = result;

            runOnUiThread(() -> {
                installing = false;
                installationReport = report;
                refreshReport();
            });

        }, "CodeBoxToolchainInstall").start();
    }

    private void refreshReport() {

        if (manager == null
                || statusView == null
                || reportView == null) {

            return;
        }

        if (installationReport != null) {

            boolean failed =
                    installationReport.contains(
                            "Статус: FAILED"
                    );

            statusView.setText(
                    failed
                            ? "Установка: ошибка"
                            : "Установка: завершена "
                            + "(совместимость не проверена)"
            );

            reportView.setText(installationReport);
            return;
        }

        CodeBoxBuildManager.State state =
                manager.getState();

        statusView.setText(
                "Статус: "
                        + state.name()
                        + "  |  Ошибок: "
                        + manager.getErrorCount()
                        + "  |  Предупреждений: "
                        + manager.getWarningCount()
        );

        reportView.setText(
                state == CodeBoxBuildManager.State.IDLE
                        ? "Выберите APK или AAB для запуска "
                        + "локальной сборки. "
                        + "Если Android API отсутствует, "
                        + "установите доверенный android.jar."
                        : manager.getReport()
        );
    }

    private String currentReport() {

        if (installationReport != null) {
            return installationReport;
        }

        if (manager == null
                || manager.getState() ==
                CodeBoxBuildManager.State.IDLE) {

            return null;
        }

        return manager.getReport();
    }

    private void saveLog() {

        if (installing) {
            toast("Дождитесь завершения текущей операции");
            return;
        }

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

                File dir = new File(
                        getFilesDir(),
                        "build-logs"
                );

                if (!dir.isDirectory()
                        && !dir.mkdirs()) {

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
                            report.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

                    out.getFD().sync();
                }
            }

            new AlertDialog.Builder(this)
                    .setTitle("Журнал сохранён")
                    .setMessage(
                            "Внутренний файл приложения:"
                                    + "\n"
                                    + file.getAbsolutePath()
                                    + "\n\nЧтобы передать текст "
                                    + "журнала, нажмите «Поделиться»."
                    )
                    .setPositiveButton("OK", null)
                    .show();

        } catch (IOException e) {

            new AlertDialog.Builder(this)
                    .setTitle("Ошибка сохранения")
                    .setMessage(e.toString())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void shareLog() {

        if (installing) {
            toast("Дождитесь завершения текущей операции");
            return;
        }

        String report = currentReport();

        if (report == null) {

            toast(
                    "Сначала запустите диагностику или установку"
            );
            return;
        }

        Intent send = new Intent(
                Intent.ACTION_SEND
        );

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
                    "Не удалось открыть меню отправки: "
                            + e.getMessage()
            );
        }
    }

    private TextView label(
            String value,
            int size,
            int color,
            boolean bold
    ) {

        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {

            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private TextView actionButton(
            String value,
            int color
    ) {

        TextView view = label(
                value,
                13,
                Color.WHITE,
                true
        );

        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);

        view.setEllipsize(
                TextUtils.TruncateAt.END
        );

        view.setBackground(
                shape(color, 11)
        );

        return view;
    }

    private GradientDrawable shape(
            int color,
            int radiusDp
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        return drawable;
    }

    private int dp(float value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
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
