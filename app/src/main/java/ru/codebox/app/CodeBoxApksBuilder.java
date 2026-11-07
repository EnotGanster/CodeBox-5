
package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import com.android.tools.build.bundletool.commands.BuildApksCommand;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * CodeBox 5 - APKS Builder.
 *
 * Diagnostic version for the patched
 * bundletool 1.18.2 runtime.
 *
 * Inspects the available BuildApksCommand API
 * without guessing unavailable classes.
 *
 * Does not modify the existing AAB pipeline.
 */
public final class CodeBoxApksBuilder {

    private static final long MAX_AAB_BYTES =
            512L * 1024L * 1024L;

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

        log.append("CodeBox 5 - APKS Builder\n");
        log.append("========================\n");

        try {

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

            log.append("\n--- Device ---\n");

            log.append("Android API: ")
                    .append(Build.VERSION.SDK_INT)
                    .append('\n');

            log.append("\n--- Input ---\n");

            log.append("Project: ")
                    .append(projectName)
                    .append('\n');

            log.append("AAB: ")
                    .append(aabFile.getAbsolutePath())
                    .append('\n');

            log.append("AAB size: ")
                    .append(aabFile.length())
                    .append(" bytes\n");

            File outputDir = new File(
                    context.getCacheDir(),
                    "apks-output-" + projectName
            );

            if (!outputDir.isDirectory()
                    && !outputDir.mkdirs()) {
                throw new IOException(
                        "Не удалось создать каталог APKS"
                );
            }

            File output = new File(
                    outputDir,
                    projectName + ".apks"
            );

            log.append("Planned output: ")
                    .append(output.getAbsolutePath())
                    .append('\n');

            log.append("\n--- Bundletool classes ---\n");

            appendClassInfo(
                    log,
                    "BuildApksCommand",
                    BuildApksCommand.class
            );

            Class<?> builderClass =
                    findBuilderClass();

            appendClassInfo(
                    log,
                    "BuildApksCommand.Builder",
                    builderClass
            );

            log.append("\n--- Builder methods ---\n");

            Method[] methods =
                    builderClass.getMethods();

            Arrays.sort(
                    methods,
                    Comparator.comparing(Method::getName)
                            .thenComparing(Method::toString)
            );

            for (Method method : methods) {

                String name = method.getName();

                if (name.startsWith("set")
                        || "build".equals(name)) {

                    log.append(
                            Modifier.toString(
                                    method.getModifiers()
                            )
                    );

                    log.append(' ');

                    log.append(
                            method.getReturnType().getName()
                    );

                    log.append(' ');
                    log.append(name);
                    log.append('(');

                    Class<?>[] parameters =
                            method.getParameterTypes();

                    for (int i = 0;
                            i < parameters.length;
                            i++) {

                        if (i > 0) {
                            log.append(", ");
                        }

                        log.append(
                                parameters[i].getName()
                        );
                    }

                    log.append(")\n");
                }
            }

            log.append("\n--- Signing methods ---\n");

            int signingMethods = 0;

            for (Method method : methods) {

                String name =
                        method.getName().toLowerCase();

                if (name.contains("sign")
                        || name.contains("key")
                        || name.contains("password")
                        || name.contains("aapt")) {

                    log.append(method.toString())
                            .append('\n');

                    signingMethods++;
                }
            }

            log.append("Matching methods: ")
                    .append(signingMethods)
                    .append('\n');

            log.append("\n--- APK build modes ---\n");

            try {

                Class<?> modeClass =
                        Class.forName(
                                "com.android.tools.build."
                                        + "bundletool.commands."
                                        + "BuildApksCommand$ApkBuildMode"
                        );

                Object[] values =
                        modeClass.getEnumConstants();

                if (values != null) {

                    for (Object value : values) {

                        log.append("Mode: ")
                                .append(value)
                                .append('\n');
                    }

                } else {

                    log.append(
                            "ApkBuildMode is not an enum\n"
                    );
                }

            } catch (Throwable error) {

                log.append("Mode diagnostic: ")
                        .append(error.getClass().getName())
                        .append(": ")
                        .append(error.getMessage())
                        .append('\n');
            }

            log.append("\n--- AAPT2 discovery ---\n");

            File nativeAapt2 = new File(
                    context.getApplicationInfo()
                            .nativeLibraryDir,
                    "libaapt2.so"
            );

            File externalAapt2 = new File(
                    context.getFilesDir(),
                    "build-tools/aapt2"
            );

            log.append("Native AAPT2: ")
                    .append(nativeAapt2.getAbsolutePath())
                    .append('\n');

            log.append("Native exists: ")
                    .append(nativeAapt2.isFile())
                    .append('\n');

            log.append("Native executable: ")
                    .append(nativeAapt2.canExecute())
                    .append('\n');

            log.append("External AAPT2: ")
                    .append(externalAapt2.getAbsolutePath())
                    .append('\n');

            log.append("External exists: ")
                    .append(externalAapt2.isFile())
                    .append('\n');

            log.append("External executable: ")
                    .append(externalAapt2.canExecute())
                    .append('\n');

            log.append("\n--- Result ---\n");

            log.append(
                    "Status: DIAGNOSTICS_COMPLETE\n"
            );

            log.append(
                    "APKS generation not started.\n"
            );

            log.append(
                    "The available bundletool API "
                            + "has been inspected.\n"
            );

            log.append(
                    "The universal APK is not yet "
                            + "generated or signed.\n"
            );

            return new Result(
                    false,
                    null,
                    null,
                    log.toString()
            );

        } catch (Throwable error) {

            log.append("\n--- Error ---\n");

            log.append(
                    error.getClass().getName()
            );

            log.append(": ")
                    .append(error.getMessage())
                    .append('\n');

            StringWriter writer =
                    new StringWriter();

            PrintWriter printer =
                    new PrintWriter(writer);

            error.printStackTrace(printer);
            printer.flush();

            log.append(writer.toString());

            log.append(
                    "\nStatus: FAILED\n"
            );

            return new Result(
                    false,
                    null,
                    null,
                    log.toString()
            );
        }
    }

    private static Class<?> findBuilderClass()
            throws ClassNotFoundException {

        return Class.forName(
                "com.android.tools.build."
                        + "bundletool.commands."
                        + "BuildApksCommand$Builder"
        );
    }

    private static void appendClassInfo(
            StringBuilder log,
            String label,
            Class<?> type
    ) {

        log.append(label)
                .append(": ")
                .append(type.getName())
                .append('\n');

        ClassLoader loader =
                type.getClassLoader();

        log.append("ClassLoader: ")
                .append(
                        loader == null
                                ? "BOOT"
                                : loader.getClass().getName()
                )
                .append('\n');

        Package pkg = type.getPackage();

        if (pkg != null) {

            log.append("Version: ")
                    .append(
                            pkg.getImplementationVersion()
                    )
                    .append('\n');
        }
    }
}
