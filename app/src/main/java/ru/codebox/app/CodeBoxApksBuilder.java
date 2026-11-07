package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import com.android.tools.build.bundletool.commands.BuildApksCommand;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import java.util.Arrays;
import java.util.Comparator;

/**
 * CodeBox 5 - APKS Builder.
 *
 * Diagnostic stage 2.
 *
 * Inspects:
 * - BuildApksCommand
 * - Aapt2Command
 * - SigningConfiguration
 * - SigningConfigurationProvider
 * - APK build modes
 * - AAPT2 executable availability
 *
 * Does not generate APKs.
 * Does not create or modify signing keys.
 * Does not change the existing AAB pipeline.
 *
 * Run on a background thread.
 */
public final class CodeBoxApksBuilder {

    private static final long MAX_AAB_BYTES =
            512L * 1024L * 1024L;

    private static final String AAPT2_CLASS =
            "com.android.tools.build.bundletool."
                    + "androidtools.Aapt2Command";

    private static final String SIGNING_CLASS =
            "com.android.tools.build.bundletool."
                    + "model.SigningConfiguration";

    private static final String SIGNING_PROVIDER_CLASS =
            "com.android.tools.build.bundletool."
                    + "model.SigningConfigurationProvider";

    private CodeBoxApksBuilder() {
    }

    public static final class Result {

        public final boolean built;
        public final File apksFile;
        public final File universalApk;
        public final String report;

        private Result(
                boolean built,
                File apksFile,
                File universalApk,
                String report
        ) {
            this.built = built;
            this.apksFile = apksFile;
            this.universalApk = universalApk;
            this.report = report;
        }
    }

    public static Result build(
            Context context,
            File aabFile,
            String projectName
    ) {

        StringBuilder log = new StringBuilder();

        log.append(
                "CodeBox 5 - APKS Builder\n"
        );

        log.append(
                "========================\n"
        );

        log.append(
                "Diagnostic stage: 2\n"
        );

        try {

            validateInputs(
                    context,
                    aabFile,
                    projectName
            );

            appendDeviceInfo(log);

            appendInputInfo(
                    log,
                    aabFile,
                    projectName
            );

            File outputDirectory =
                    prepareOutputDirectory(
                            context,
                            projectName
                    );

            File plannedOutput = new File(
                    outputDirectory,
                    projectName + ".apks"
            );

            log.append(
                    "\n--- Planned output ---\n"
            );

            log.append(
                    plannedOutput.getAbsolutePath()
            ).append('\n');

            /*
             * This version intentionally does not
             * delete existing APKS or APK files.
             */

            inspectBuildApksCommand(log);

            inspectAapt2Command(log);

            inspectSigningConfiguration(log);

            inspectSigningProvider(log);

            inspectBuildModes(log);

            inspectAapt2Executable(
                    context,
                    log
            );

            log.append(
                    "\n--- Final result ---\n"
            );

            log.append(
                    "AAPT2_API_DIAGNOSTICS: COMPLETE\n"
            );

            log.append(
                    "SIGNING_API_DIAGNOSTICS: COMPLETE\n"
            );

            log.append(
                    "APKS_GENERATION: NOT_STARTED\n"
            );

            log.append(
                    "APK_SIGNING: NOT_STARTED\n"
            );

            log.append(
                    "Status: DIAGNOSTICS_COMPLETE\n"
            );

            return new Result(
                    false,
                    null,
                    null,
                    log.toString()
            );

        } catch (Throwable error) {

            log.append(
                    "\n--- Diagnostic failure ---\n"
            );

            appendError(
                    log,
                    error
            );

            log.append(
                    "Status: FAILED\n"
            );

            return new Result(
                    false,
                    null,
                    null,
                    log.toString()
            );
        }
    }

    private static void validateInputs(
            Context context,
            File aabFile,
            String projectName
    ) throws IOException {

        if (context == null) {
            throw new IOException(
                    "Context отсутствует"
            );
        }

        if (Build.VERSION.SDK_INT < 26) {
            throw new IOException(
                    "Требуется Android API 26+"
            );
        }

        if (projectName == null
                || !projectName.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {

            throw new IOException(
                    "Недопустимое имя проекта"
            );
        }

        if (aabFile == null
                || !aabFile.isFile()
                || aabFile.length() == 0) {

            throw new IOException(
                    "Исходный AAB отсутствует"
            );
        }

        if (aabFile.length() > MAX_AAB_BYTES) {
            throw new IOException(
                    "Исходный AAB слишком большой"
            );
        }
    }

    private static File prepareOutputDirectory(
            Context context,
            String projectName
    ) throws IOException {

        File directory = new File(
                context.getCacheDir(),
                "apks-output-" + projectName
        );

        if (!directory.isDirectory()
                && !directory.mkdirs()) {

            throw new IOException(
                    "Не удалось создать каталог APKS"
            );
        }

        return directory;
    }

    private static void appendDeviceInfo(
            StringBuilder log
    ) {

        log.append(
                "\n--- Device ---\n"
        );

        log.append("Android API: ")
                .append(Build.VERSION.SDK_INT)
                .append('\n');

        log.append("Device: ")
                .append(Build.MANUFACTURER)
                .append(' ')
                .append(Build.MODEL)
                .append('\n');

        log.append("Supported ABI:\n");

        for (String abi : Build.SUPPORTED_ABIS) {

            log.append(" - ")
                    .append(abi)
                    .append('\n');
        }
    }

    private static void appendInputInfo(
            StringBuilder log,
            File aabFile,
            String projectName
    ) {

        log.append(
                "\n--- Input ---\n"
        );

        log.append("Project: ")
                .append(projectName)
                .append('\n');

        log.append("AAB: ")
                .append(aabFile.getAbsolutePath())
                .append('\n');

        log.append("AAB size: ")
                .append(aabFile.length())
                .append(" bytes\n");
    }

    private static void inspectBuildApksCommand(
            StringBuilder log
    ) {

        log.append(
                "\n--- BuildApksCommand ---\n"
        );

        try {

            Class<?> commandClass =
                    BuildApksCommand.class;

            appendClassInfo(
                    log,
                    commandClass
            );

            Class<?> builderClass =
                    Class.forName(
                            "com.android.tools.build."
                                    + "bundletool.commands."
                                    + "BuildApksCommand$Builder"
                    );

            log.append(
                    "\nBuilder:\n"
            );

            appendClassInfo(
                    log,
                    builderClass
            );

            log.append(
                    "\nRelevant builder methods:\n"
            );

            Method[] methods =
                    builderClass.getMethods();

            sortMethods(methods);

            for (Method method : methods) {

                String name = method.getName();

                if (name.equals("build")
                        || name.equals("setAapt2Command")
                        || name.equals("setSigningConfiguration")
                        || name.equals("setSigningConfigurationProvider")
                        || name.equals("setBundlePath")
                        || name.equals("setOutputFile")
                        || name.equals("setApkBuildMode")
                        || name.equals("setOverwriteOutput")
                        || name.equals("setOutputFormat")
                        || name.equals("setCreateApkSetArchive")) {

                    appendMethod(
                            log,
                            method
                    );
                }
            }

            log.append(
                    "\nBuilder factory methods:\n"
            );

            for (Method method :
                    commandClass.getDeclaredMethods()) {

                if (Modifier.isStatic(
                        method.getModifiers()
                )) {

                    if (method.getName()
                            .toLowerCase()
                            .contains("builder")) {

                        appendMethod(
                                log,
                                method
                        );
                    }
                }
            }

        } catch (Throwable error) {

            appendSectionError(
                    log,
                    "BuildApksCommand",
                    error
            );
        }
    }

    private static void inspectAapt2Command(
            StringBuilder log
    ) {

        log.append(
                "\n--- Aapt2Command API ---\n"
        );

        try {

            Class<?> type =
                    Class.forName(
                            AAPT2_CLASS
                    );

            appendClassInfo(
                    log,
                    type
            );

            log.append(
                    "\nConstructors:\n"
            );

            Constructor<?>[] constructors =
                    type.getDeclaredConstructors();

            for (Constructor<?> constructor :
                    constructors) {

                log.append(
                        constructor.toGenericString()
                ).append('\n');
            }

            log.append(
                    "\nStatic factory methods:\n"
            );

            Method[] methods =
                    type.getDeclaredMethods();

            sortMethods(methods);

            int factories = 0;

            for (Method method : methods) {

                if (Modifier.isStatic(
                        method.getModifiers()
                )) {

                    appendMethod(
                            log,
                            method
                    );

                    factories++;
                }
            }

            log.append(
                    "Static methods found: "
            ).append(factories).append('\n');

            log.append(
                    "\nInstance methods:\n"
            );

            for (Method method : methods) {

                if (!Modifier.isStatic(
                        method.getModifiers()
                )) {

                    appendMethod(
                            log,
                            method
                    );
                }
            }

            log.append(
                    "\nFields:\n"
            );

            appendFields(
                    log,
                    type
            );

            log.append(
                    "\nAAPT2_CLASS_FOUND: true\n"
            );

        } catch (Throwable error) {

            appendSectionError(
                    log,
                    "Aapt2Command",
                    error
            );
        }
    }

    private static void inspectSigningConfiguration(
            StringBuilder log
    ) {

        log.append(
                "\n--- SigningConfiguration API ---\n"
        );

        try {

            Class<?> type =
                    Class.forName(
                            SIGNING_CLASS
                    );

            appendClassInfo(
                    log,
                    type
            );

            log.append(
                    "\nConstructors:\n"
            );

            for (Constructor<?> constructor :
                    type.getDeclaredConstructors()) {

                log.append(
                        constructor.toGenericString()
                ).append('\n');
            }

            log.append(
                    "\nStatic methods:\n"
            );

            Method[] methods =
                    type.getDeclaredMethods();

            sortMethods(methods);

            for (Method method : methods) {

                if (Modifier.isStatic(
                        method.getModifiers()
                )) {

                    appendMethod(
                            log,
                            method
                    );
                }
            }

            log.append(
                    "\nInstance methods:\n"
            );

            for (Method method : methods) {

                if (!Modifier.isStatic(
                        method.getModifiers()
                )) {

                    appendMethod(
                            log,
                            method
                    );
                }
            }

            log.append(
                    "\nFields:\n"
            );

            appendFields(
                    log,
                    type
            );

            log.append(
                    "\nSIGNING_CONFIGURATION_FOUND: true\n"
            );

        } catch (Throwable error) {

            appendSectionError(
                    log,
                    "SigningConfiguration",
                    error
            );
        }
    }

    private static void inspectSigningProvider(
            StringBuilder log
    ) {

        log.append(
                "\n--- SigningConfigurationProvider API ---\n"
        );

        try {

            Class<?> type =
                    Class.forName(
                            SIGNING_PROVIDER_CLASS
                    );

            appendClassInfo(
                    log,
                    type
            );

            log.append(
                    "\nInterface: "
            ).append(type.isInterface())
                    .append('\n');

            log.append(
                    "\nMethods:\n"
            );

            Method[] methods =
                    type.getDeclaredMethods();

            sortMethods(methods);

            for (Method method : methods) {

                appendMethod(
                        log,
                        method
                );
            }

            log.append(
                    "\nFields:\n"
            );

            appendFields(
                    log,
                    type
            );

            log.append(
                    "\nSIGNING_PROVIDER_FOUND: true\n"
            );

        } catch (Throwable error) {

            appendSectionError(
                    log,
                    "SigningConfigurationProvider",
                    error
            );
        }
    }

    private static void inspectBuildModes(
            StringBuilder log
    ) {

        log.append(
                "\n--- APK build modes ---\n"
        );

        try {

            Class<?> modeClass =
                    Class.forName(
                            "com.android.tools.build."
                                    + "bundletool.commands."
                                    + "BuildApksCommand$ApkBuildMode"
                    );

            Object[] values =
                    modeClass.getEnumConstants();

            if (values == null) {

                log.append(
                        "No enum constants\n"
                );

                return;
            }

            for (Object value : values) {

                log.append("Mode: ")
                        .append(value)
                        .append('\n');
            }

        } catch (Throwable error) {

            appendSectionError(
                    log,
                    "ApkBuildMode",
                    error
            );
        }
    }

    private static void inspectAapt2Executable(
            Context context,
            StringBuilder log
    ) {

        log.append(
                "\n--- AAPT2 executable ---\n"
        );

        try {

            File nativeAapt2 = new File(
                    context.getApplicationInfo()
                            .nativeLibraryDir,
                    "libaapt2.so"
            );

            File externalAapt2 = new File(
                    context.getFilesDir(),
                    "build-tools/aapt2"
            );

            appendFileInfo(
                    log,
                    "Native AAPT2",
                    nativeAapt2
            );

            appendFileInfo(
                    log,
                    "External AAPT2",
                    externalAapt2
            );

            File selected = null;

            if (nativeAapt2.isFile()
                    && nativeAapt2.canExecute()) {

                selected = nativeAapt2;

            } else if (externalAapt2.isFile()
                    && externalAapt2.canExecute()) {

                selected = externalAapt2;
            }

            if (selected != null) {

                log.append(
                        "Selected AAPT2: "
                ).append(
                        selected.getAbsolutePath()
                ).append('\n');

                log.append(
                        "AAPT2_EXECUTABLE_FOUND: true\n"
                );

            } else {

                log.append(
                        "AAPT2_EXECUTABLE_FOUND: false\n"
                );
            }

        } catch (Throwable error) {

            appendSectionError(
                    log,
                    "AAPT2 executable",
                    error
            );
        }
    }

    private static void appendClassInfo(
            StringBuilder log,
            Class<?> type
    ) {

        log.append("Class: ")
                .append(type.getName())
                .append('\n');

        log.append("Modifiers: ")
                .append(
                        Modifier.toString(
                                type.getModifiers()
                        )
                ).append('\n');

        log.append("Interface: ")
                .append(type.isInterface())
                .append('\n');

        log.append("Enum: ")
                .append(type.isEnum())
                .append('\n');

        ClassLoader loader =
                type.getClassLoader();

        log.append("ClassLoader: ")
                .append(
                        loader == null
                                ? "BOOT"
                                : loader.getClass().getName()
                ).append('\n');

        Class<?> parent =
                type.getSuperclass();

        log.append("Superclass: ")
                .append(
                        parent == null
                                ? "none"
                                : parent.getName()
                ).append('\n');

        Class<?>[] interfaces =
                type.getInterfaces();

        for (Class<?> item : interfaces) {

            log.append("Implements: ")
                    .append(item.getName())
                    .append('\n');
        }
    }

    private static void appendMethod(
            StringBuilder log,
            Method method
    ) {

        log.append(
                method.toGenericString()
        ).append('\n');

        Type[] parameters =
                method.getGenericParameterTypes();

        for (int i = 0;
                i < parameters.length;
                i++) {

            log.append("  Parameter ")
                    .append(i)
                    .append(": ")
                    .append(
                            parameters[i].getTypeName()
                    )
                    .append('\n');
        }

        log.append("  Returns: ")
                .append(
                        method.getGenericReturnType()
                                .getTypeName()
                )
                .append('\n');
    }

    private static void appendFields(
            StringBuilder log,
            Class<?> type
    ) {

        Field[] fields =
                type.getDeclaredFields();

        Arrays.sort(
                fields,
                Comparator.comparing(
                        Field::getName
                )
        );

        if (fields.length == 0) {

            log.append(
                    "No declared fields\n"
            );

            return;
        }

        for (Field field : fields) {

            /*
             * Print metadata only.
             *
             * Never read private field values
             * or signing secrets.
             */

            log.append(
                    field.toGenericString()
            ).append('\n');
        }
    }

    private static void appendFileInfo(
            StringBuilder log,
            String label,
            File file
    ) {

        log.append(label)
                .append(": ")
                .append(file.getAbsolutePath())
                .append('\n');

        log.append("  Exists: ")
                .append(file.isFile())
                .append('\n');

        log.append("  Executable: ")
                .append(file.canExecute())
                .append('\n');

        if (file.isFile()) {

            log.append("  Size: ")
                    .append(file.length())
                    .append(" bytes\n");
        }
    }

    private static void sortMethods(
            Method[] methods
    ) {

        Arrays.sort(
                methods,
                Comparator.comparing(
                        Method::getName
                ).thenComparing(
                        Method::toGenericString
                )
        );
    }

    private static void appendSectionError(
            StringBuilder log,
            String section,
            Throwable error
    ) {

        log.append(section)
                .append(" diagnostic error: ")
                .append(
                        error.getClass().getName()
                )
                .append(": ")
                .append(error.getMessage())
                .append('\n');
    }

    private static void appendError(
            StringBuilder log,
            Throwable error
    ) {

        log.append(
                error.getClass().getName()
        ).append(": ")
                .append(error.getMessage())
                .append('\n');

        StringWriter writer =
                new StringWriter();

        PrintWriter printer =
                new PrintWriter(writer);

        error.printStackTrace(printer);
        printer.flush();

        log.append(
                "\n--- Stack trace ---\n"
        );

        log.append(
                writer.toString()
        );
    }
}
