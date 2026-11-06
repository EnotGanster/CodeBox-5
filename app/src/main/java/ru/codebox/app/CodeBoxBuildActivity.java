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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CodeBox 5 - Local Build Screen.
 *
 * Current pipeline:
 * 1. Project validation
 * 2. Toolchain inspection
 * 3. AAPT2 ELF inspection and version launch test
 * 4. AndroidManifest.xml preparation
 * 5. Java preparation
 * 6. ECJ: Java -> CLASS
 * 7. CLASS verification
 * 8. D8: CLASS -> DEX
 *
 * Resource linking, APK/AAB packaging and signing
 * are not implemented yet.
 */
public final class CodeBoxBuildActivity extends Activity {

    private static final int PICK_TOOLCHAIN_ZIP = 7124;
    private static final int PICK_ANDROID_JAR = 7125;

    private static final int MAX_CLASS_DEPTH = 40;
    private static final int MAX_CLASS_FILES = 100000;

    private CodeBoxBuildManager manager;
    private String projectName;

    private TextView statusView;
    private TextView reportView;

    private String installationReport;
    private boolean installing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        projectName = getIntent().getStringExtra(
                "project_name"
        );

        if (projectName == null ||
                !projectName.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {

            fatal(
                    "Не передано корректное имя проекта"
            );
            return;
        }

        try {
            manager = new CodeBoxBuildManager(this);

        } catch (IOException e) {

            fatal(
                    "Невозможно создать журнал: " + e
            );
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
        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                dp(16),
                dp(20),
                dp(16),
                dp(12)
        );

        root.setBackgroundColor(background);

        setContentView(root);

        root.addView(
                label(
                        "Сборка проекта",
                        23,
                        white,
                        true
                )
        );

        TextView subtitle = label(
                projectName
                        + "  •  Локально, без интернета",
                13,
                muted,
                false
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        subtitleParams.topMargin = dp(5);
        subtitleParams.bottomMargin = dp(13);

        root.addView(
                subtitle,
                subtitleParams
        );

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

        statusView.setBackground(
                shape(panel, 12)
        );

        root.addView(statusView);

        LinearLayout builds = new LinearLayout(this);

        LinearLayout.LayoutParams buildsParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        buildsParams.topMargin = dp(10);

        root.addView(
                builds,
                buildsParams
        );

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
                new LinearLayout.LayoutParams(
                        -1,
                        dp(44)
                );

        jarParams.topMargin = dp(10);

        root.addView(
                androidJarButton,
                jarParams
        );

        androidJarButton.setOnClickListener(v -> {

            if (installing) {

                toast(
                        "Дождитесь завершения текущей операции"
                );

            } else {

                requestAndroidJar();
            }
        });

        TextView install = actionButton(
                "Установить инструменты из ZIP",
                Color.rgb(47, 93, 121)
        );

        LinearLayout.LayoutParams installParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(44)
                );

        installParams.topMargin = dp(8);

        root.addView(
                install,
                installParams
        );

        install.setOnClickListener(v -> {

            if (installing) {

                toast(
                        "Дождитесь завершения текущей операции"
                );

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
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        headingParams.topMargin = dp(15);
        headingParams.bottomMargin = dp(8);

        root.addView(
                heading,
                headingParams
        );

        ScrollView scroll = new ScrollView(this);

        scroll.setFillViewport(true);
        scroll.setBackground(
                shape(panel, 12)
        );

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        reportView = label(
                "Журнал появится после проверки.",
                12,
                muted,
                false
        );

        reportView.setTypeface(
                Typeface.MONOSPACE
        );

        reportView.setTextIsSelectable(true);

        reportView.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
        );

        scroll.addView(reportView);

        LinearLayout tools = new LinearLayout(this);

        LinearLayout.LayoutParams toolsParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        toolsParams.topMargin = dp(10);

        root.addView(
                tools,
                toolsParams
        );

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

        TextView button = actionButton(
                name,
                color
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(45),
                        1
                );

        if (first) {

            params.rightMargin = dp(5);

        } else {

            params.leftMargin = dp(5);
        }

        row.addView(
                button,
                params
        );

        button.setOnClickListener(v -> {

            if (installing) {

                toast(
                        "Дождитесь завершения текущей операции"
                );
                return;
            }

            action.run();
        });
    }

    private void requestBuild(
            CodeBoxBuildManager.OutputFormat format
    ) {

        if (installing) {

            toast(
                    "Дождитесь завершения текущей операции"
            );
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "Локальная сборка "
                                + format.name()
                )
                .setMessage(
                        "Запустим проверку инструментов, "
                        + "настоящий тест AAPT2 version, "
                        + "подготовку манифеста, ECJ, "
                        + "проверку CLASS и D8.\n\n"
                        + "Для компиляции необходим "
                        + "build-tools/android.jar.\n\n"
                        + "Подготовка манифеста пока "
                        + "проверяется для MyApp.\n\n"
                        + "AAPT2 будет запущен только "
                        + "для проверки версии.\n\n"
                        + "Упаковка ресурсов и подпись "
                        + "APK/AAB ещё не подключены."
                )
                .setNegativeButton(
                        "Отмена",
                        null
                )
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

            toast(
                    "Дождитесь завершения текущей операции"
            );
            return;
        }

        installing = true;
        installationReport = null;

        statusView.setText(
                "Статус: AAPT2 version, XML, ECJ + D8..."
        );

        reportView.setText(
                "Проверяем запуск AAPT2, "
                + "затем компилируем Java. "
                + "Подождите..."
        );

        new Thread(() -> {

            try {

                manager.start(
                        projectName,
                        format
                );

                manager.setStage(
                        "Проверка проекта"
                );

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

                } else {

                    manager.info(
                            "Папка проекта найдена: "
                            + project.getAbsolutePath()
                    );

                    manager.info(
                            "Формат результата: "
                            + format.name()
                    );

                    // ----------------------------
                    // 1. Toolchain inspection
                    // ----------------------------

                    manager.setStage(
                            "Проверка инструментов"
                    );

                    CodeBoxToolchainInspector.Result tools =
                            CodeBoxToolchainInspector.inspect(
                                    getApplicationContext()
                            );

                    manager.info(tools.report);

                    manager.info(
                            "Готовность APK: "
                            + (
                                    tools.readyForApk
                                            ? "да"
                                            : "нет"
                            )
                    );

                    manager.info(
                            "Готовность AAB: "
                            + (
                                    tools.readyForAab
                                            ? "да"
                                            : "нет"
                            )
                    );

                    // ----------------------------
                    // 2. Real AAPT2 launch test
                    // ----------------------------

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

                            manager.info(
                                    "AAPT2_STATUS: NOT_FOUND"
                            );

                            manager.warning(
                                    "AAPT2 не найден. "
                                    + "ECJ и D8 продолжат работу."
                            );

                        } else {

                            manager.info(
                                    "AAPT2_FOUND: YES"
                            );

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
                                    + (
                                            aapt2.launchSuccessful
                                                    ? "OK"
                                                    : "FAILED"
                                    )
                            );

                            if (!aapt2.launchSuccessful) {

                                manager.warning(
                                        "AAPT2 не прошёл тест запуска. "
                                        + "ECJ и D8 продолжат работу."
                                );

                            } else {

                                manager.info(
                                        "AAPT2 успешно запущен "
                                        + "на этом устройстве."
                                );
                            }
                        }

                    } catch (Throwable error) {

                        manager.warning(
                                "Ошибка диагностики AAPT2: "
                                + error.getClass().getName()
                                + ": "
                                + error.getMessage()
                        );
                    }

                    // ----------------------------
                    // 3. Prepare AndroidManifest
                    // ----------------------------

                    if (format ==
                            CodeBoxBuildManager.OutputFormat.APK) {

                        manager.setStage(
                                "Подготовка AndroidManifest.xml"
                        );

                        try {

                            File manifestWork = new File(
                                    getCacheDir(),
                                    "apk-manifest-"
                                            + projectName
                            );

                            CodeBoxApkManifestPreparer.Result
                                    manifestResult =
                                    CodeBoxApkManifestPreparer.prepare(
                                            project,
                                            manifestWork,
                                            "com.example.myapp"
                                    );

                            manager.info(
                                    manifestResult.report
                            );

                            if (manifestResult.manifestFile.isFile()
                                    && manifestResult.manifestFile.length() > 0) {

                                manager.info(
                                        "MANIFEST_PREPARED: OK"
                                );

                                manager.info(
                                        "Размер XML: "
                                        + manifestResult.manifestFile.length()
                                        + " байт"
                                );

                            } else {

                                throw new IOException(
                                        "Подготовленный манифест отсутствует"
                                );
                            }

                        } catch (Exception e) {

                            manager.fail(
                                    "Ошибка подготовки манифеста: "
                                    + e.getClass().getName()
                                    + ": "
                                    + e.getMessage()
                            );

                            return;
                        }
                    }

                    // ----------------------------
                    // 4. Java preparation
                    // ----------------------------

                    manager.setStage(
                            "Подготовка Java"
                    );

                    CodeBoxJavaCompiler.Result preparation =
                            CodeBoxJavaCompiler.inspect(
                                    getApplicationContext(),
                                    project
                            );

                    manager.info(
                            preparation.report
                    );

                    // ----------------------------
                    // 5. ECJ compilation
                    // ----------------------------

                    manager.setStage(
                            "Компиляция Java (ECJ)"
                    );

                    CodeBoxEcjCompiler.Result compiled =
                            CodeBoxEcjCompiler.compile(
                                    getApplicationContext(),
                                    project
                            );

                    manager.info(
                            compiled.report
                    );

                    if (!compiled.compiled) {

                        manager.fail(
                                "ECJ не создал классы Java. "
                                + "См. диагностику выше."
                        );

                    } else {

                        manager.info(
                                "Java успешно скомпилирована. "
                                + "Каталог: "
                                + compiled.classesDirectory
                                        .getAbsolutePath()
                        );

                        verifyClassFilesAndRunD8(
                                compiled.classesDirectory,
                                format
                        );
                    }
                }

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

                    installing = false;
                    refreshReport();
                });
            }

        }, "CodeBoxAapt2EcjD8Build").start();
    }

    private void verifyClassFilesAndRunD8(
            File classesDirectory,
            CodeBoxBuildManager.OutputFormat format
    ) throws IOException {

        manager.setStage(
                "Проверка CLASS"
        );

        if (classesDirectory == null
                || !classesDirectory.isDirectory()) {

            manager.fail(
                    "Каталог CLASS файлов не существует"
            );

            return;
        }

        File root =
                classesDirectory.getCanonicalFile();

        manager.info(
                "Каталог CLASS: "
                + root.getAbsolutePath()
        );

        ClassScan scan = new ClassScan();

        scanClassFiles(
                root,
                root,
                scan,
                0
        );

        manager.info(
                "Найдено CLASS файлов: "
                + scan.count
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

            manager.info(
                    "MainActivity.class: OK"
            );

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

        // ----------------------------
        // 6. D8 inspection
        // ----------------------------

        manager.setStage(
                "Проверка D8"
        );

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

        // ----------------------------
        // 7. D8 compilation
        // ----------------------------

        manager.setStage(
                "Компиляция DEX (D8)"
        );

        CodeBoxD8Compiler.Result dex =
                CodeBoxD8Compiler.compile(
                        getApplicationContext(),
                        root,
                        projectName
                );

        manager.info(
                dex.report
        );

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

        manager.info(
                "classes.dex: OK"
        );

        manager.info(
                "Путь: "
                + dex.dexFile.getAbsolutePath()
        );

        manager.info(
                "Размер: "
                + dex.dexFile.length()
                + " байт"
        );

        // Final state is intentionally FAILED.
        // APK/AAB is not packaged yet.

        manager.fail(
                "ECJ и D8 успешно завершены. "
                + "classes.dex создан. "
                + "AAPT2 проверен отдельно. "
                + "Компиляция ресурсов, упаковка "
                + "и подпись "
                + format.name()
                + " пока не подключены. "
                + "APK/AAB не создан."
        );
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

        File[] children =
                directory.listFiles();

        if (children == null) {

            throw new IOException(
                    "Не удалось прочитать каталог CLASS: "
                    + directory
            );
        }

        for (File child : children) {

            File canonical =
                    child.getCanonicalFile();

            String rootPath =
                    root.getCanonicalPath();

            String childPath =
                    canonical.getCanonicalPath();

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

            } else if (
                    canonical.isFile()
                    && canonical.getName()
                            .endsWith(".class")
            ) {

                result.count++;

                if (result.count > MAX_CLASS_FILES) {

                    throw new IOException(
                            "Слишком много CLASS файлов"
                    );
                }

                if ("MainActivity.class".equals(
                        canonical.getName()
                )) {

                    result.mainActivity =
                            canonical;
                }
            }
        }
    }

    private void requestAndroidJar() {

        if (installing) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "Установка Android API"
                )
                .setMessage(
                        "Выберите настоящий android.jar "
                        + "из Android SDK, например "
                        + "platforms/android-35/android.jar.\n\n"
                        + "CodeBox проверит структуру JAR, "
                        + "основные классы Android "
                        + "и рассчитает SHA-256.\n\n"
                        + "Используйте только доверенный SDK."
                )
                .setNegativeButton(
                        "Отмена",
                        null
                )
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

            try (InputStream stream =
                         getContentResolver()
                                 .openInputStream(uri)) {

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
                        + "Ошибка: "
                        + e
                        + "\n"
                        + "Установка не завершена.";
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
                .setTitle(
                        "Установка инструментов"
                )
                .setMessage(
                        "Нужен заранее проверенный ZIP "
                        + "с шестью файлами в корне:\n\n"
                        + "aapt2, android.jar, d8.jar, "
                        + "ecj.jar, apksigner.jar, "
                        + "bundletool.jar.\n\n"
                        + "Также потребуется доверенный "
                        + "SHA-256 из независимого источника.\n\n"
                        + "Наличие файлов не означает "
                        + "совместимость с Android."
                )
                .setNegativeButton(
                        "Отмена",
                        null
                )
                .setPositiveButton(
                        "Выбрать ZIP",
                        (d, w) -> {

                            Intent intent = new Intent(
                                    Intent.ACTION_OPEN_DOCUMENT
                            );

                            intent.addCategory(
                                    Intent.CATEGORY_OPENABLE
                            );

                            intent.setType(
                                    "application/zip"
                            );

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

        final Uri uri =
                data.getData();

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

        hash.setHint(
                "64 символа SHA-256"
        );

        hash.setPadding(
                dp(16),
                dp(12),
                dp(16),
                dp(12)
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Доверенный SHA-256"
                )
                .setMessage(
                        "Вставьте контрольную сумму "
                        + "из независимого доверенного источника.\n\n"
                        + "Не копируйте её из того же "
                        + "непроверенного архива."
                )
                .setView(hash)
                .setNegativeButton(
                        "Отмена",
                        null
                )
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

                            installZip(
                                    uri,
                                    expected
                            );
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

            try (InputStream stream =
                         getContentResolver()
                                 .openInputStream(uri)) {

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
                        "CodeBox 5 — установка инструментов\n"
                        + "Статус: файлы распакованы\n"
                        + installed.report
                        + "\n\n"
                        + inventory.report;

            } catch (Exception e) {

                result =
                        "CodeBox 5 — установка инструментов\n"
                        + "Статус: FAILED\n"
                        + "Ошибка: "
                        + e
                        + "\n"
                        + "Прежний комплект мог сохраниться; "
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

            reportView.setText(
                    installationReport
            );

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
                          + "диагностики. Если Android API "
                          + "отсутствует, установите "
                          + "доверенный android.jar."
                        : manager.getReport()
        );
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

    private void saveLog() {

        if (installing) {

            toast(
                    "Дождитесь завершения текущей операции"
            );

            return;
        }

        String report =
                currentReport();

        if (report == null) {

            toast(
                    "Сначала запустите диагностику "
                    + "или установку"
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

                String stamp = new SimpleDateFormat(
                        "yyyyMMdd_HHmmss_SSS",
                        Locale.US
                ).format(new Date());

                file = new File(
                        dir,
                        "toolchain_"
                                + stamp
                                + ".txt"
                );

                try (FileOutputStream out =
                             new FileOutputStream(file)) {

                    out.write(
                            report.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

                    out.getFD().sync();
                }
            }

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Журнал сохранён"
                    )
                    .setMessage(
                            "Внутренний файл приложения:\n"
                            + file.getAbsolutePath()
                            + "\n\nЧтобы передать текст журнала, "
                            + "нажмите «Поделиться»."
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

        } catch (IOException e) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            "Ошибка сохранения"
                    )
                    .setMessage(
                            e.toString()
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();
        }
    }

    private void shareLog() {

        if (installing) {

            toast(
                    "Дождитесь завершения текущей операции"
            );

            return;
        }

        String report =
                currentReport();

        if (report == null) {

            toast(
                    "Сначала запустите диагностику "
                    + "или установку"
            );

            return;
        }

        Intent send = new Intent(
                Intent.ACTION_SEND
        );

        send.setType(
                "text/plain"
        );

        send.putExtra(
                Intent.EXTRA_SUBJECT,
                "CodeBox 5 — журнал "
                        + projectName
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

        TextView view =
                new TextView(this);

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

        view.setGravity(
                Gravity.CENTER
        );

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
