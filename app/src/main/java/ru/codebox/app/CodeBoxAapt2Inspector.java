package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * CodeBox 5 - AAPT2 Inspector.
 *
 * Проверяет:
 * - AAPT2 в nativeLibraryDir;
 * - резервный путь files/build-tools/aapt2;
 * - ELF и архитектуру;
 * - права выполнения;
 * - запуск команды version (по запросу).
 *
 * Не выполняет сборку ресурсов или APK.
 */
public final class CodeBoxAapt2Inspector {

    private static final int ELF_HEADER_SIZE = 20;
    private static final long TIMEOUT_SECONDS = 5;
    private static final int MAX_OUTPUT_BYTES = 16384;

    private CodeBoxAapt2Inspector() {
    }

    public static final class Result {

        public final boolean found;
        public final boolean elfValid;
        public final boolean architectureCompatible;
        public final boolean executable;
        public final boolean launchSuccessful;
        public final String report;

        private Result(
                boolean found,
                boolean elfValid,
                boolean architectureCompatible,
                boolean executable,
                boolean launchSuccessful,
                String report
        ) {
            this.found = found;
            this.elfValid = elfValid;
            this.architectureCompatible = architectureCompatible;
            this.executable = executable;
            this.launchSuccessful = launchSuccessful;
            this.report = report;
        }
    }

    /**
     * Безопасная проверка без запуска AAPT2.
     */
    public static Result inspect(Context context) {
        return inspect(context, false);
    }

    /**
     * При testLaunch=true выполняет команду:
     *
     * libaapt2.so version
     *
     * Вызывать из фонового потока.
     * Запускать только проверенный бинарник.
     */
    public static Result inspect(
            Context context,
            boolean testLaunch
    ) {
        StringBuilder log = new StringBuilder();

        log.append("CodeBox 5 - AAPT2 Inspector\n");
        log.append("Android API: ")
                .append(Build.VERSION.SDK_INT)
                .append('\n');

        log.append("Поддерживаемые ABI:\n");

        for (String abi : Build.SUPPORTED_ABIS) {
            log.append(" - ").append(abi).append('\n');
        }

        if (context == null) {
            log.append("ERROR: Context отсутствует\n");

            return result(
                    false, false, false,
                    false, false, log
            );
        }

        File nativeBinary = null;

        String nativeDirectory =
                context.getApplicationInfo().nativeLibraryDir;

        log.append("\nКаталог nativeLibraryDir:\n")
                .append(nativeDirectory)
                .append('\n');

        if (nativeDirectory != null
                && !nativeDirectory.isEmpty()) {

            nativeBinary = new File(
                    nativeDirectory,
                    "libaapt2.so"
            );
        }

        File legacyBinary = new File(
                new File(
                        context.getFilesDir(),
                        "build-tools"
                ),
                "aapt2"
        );

        log.append("Ожидаемый нативный файл:\n")
                .append(
                        nativeBinary == null
                                ? "UNAVAILABLE"
                                : nativeBinary.getAbsolutePath()
                )
                .append('\n');

        log.append("Резервный путь:\n")
                .append(legacyBinary.getAbsolutePath())
                .append('\n');

        File aapt2;

        if (nativeBinary != null
                && nativeBinary.isFile()) {

            aapt2 = nativeBinary;
            log.append("\nИсточник: NATIVE_LIBRARY\n");

        } else if (legacyBinary.isFile()) {

            aapt2 = legacyBinary;
            log.append("\nИсточник: LEGACY_BUILD_TOOLS\n");

        } else {

            log.append("\nAAPT2: NOT_FOUND\n");
            log.append(
                    "libaapt2.so не найден в nativeLibraryDir.\n"
            );
            log.append(
                    "aapt2 не найден в files/build-tools.\n"
            );

            return result(
                    false, false, false,
                    false, false, log
            );
        }

        log.append("AAPT2: FOUND\n");
        log.append("Путь: ")
                .append(aapt2.getAbsolutePath())
                .append('\n');

        log.append("Размер: ")
                .append(aapt2.length())
                .append(" байт\n");

        boolean executable = aapt2.canExecute();

        byte[] header = new byte[ELF_HEADER_SIZE];

        try (InputStream input =
                     new FileInputStream(aapt2)) {

            int total = 0;

            while (total < header.length) {

                int count = input.read(
                        header,
                        total,
                        header.length - total
                );

                if (count < 0) {
                    break;
                }

                if (count == 0) {
                    continue;
                }

                total += count;
            }

            if (total != header.length) {

                log.append(
                        "ELF: INVALID (короткий заголовок)\n"
                );

                return result(
                        true, false, false,
                        executable, false, log
                );
            }

        } catch (IOException e) {

            log.append("Ошибка чтения: ")
                    .append(e.getMessage())
                    .append('\n');

            return result(
                    true, false, false,
                    executable, false, log
            );
        }

        boolean validElf =
                (header[0] & 0xff) == 0x7f
                && header[1] == 'E'
                && header[2] == 'L'
                && header[3] == 'F';

        if (!validElf) {

            log.append("ELF: INVALID\n");

            return result(
                    true, false, false,
                    executable, false, log
            );
        }

        int elfClass = header[4] & 0xff;
        int endian = header[5] & 0xff;

        if ((elfClass != 1 && elfClass != 2)
                || (endian != 1 && endian != 2)) {

            log.append("ELF: INVALID CLASS/ENDIAN\n");

            return result(
                    true, false, false,
                    executable, false, log
            );
        }

        log.append("ELF: OK\n");
        log.append("Разрядность: ")
                .append(elfClass == 2 ? "64-bit" : "32-bit")
                .append('\n');

        int machine;

        if (endian == 1) {

            machine =
                    (header[18] & 0xff)
                    | ((header[19] & 0xff) << 8);

        } else {

            machine =
                    ((header[18] & 0xff) << 8)
                    | (header[19] & 0xff);
        }

        String architecture;

        switch (machine) {

            case 3:
                architecture = "x86";
                break;

            case 40:
                architecture = "ARM";
                break;

            case 62:
                architecture = "x86_64";
                break;

            case 183:
                architecture = "AArch64";
                break;

            default:
                architecture = "UNKNOWN (" + machine + ")";
                break;
        }

        log.append("Архитектура ELF: ")
                .append(architecture)
                .append('\n');

        boolean compatible = false;

        for (String abi : Build.SUPPORTED_ABIS) {

            if (machine == 183
                    && elfClass == 2
                    && "arm64-v8a".equals(abi)) {

                compatible = true;
            }

            if (machine == 40
                    && elfClass == 1
                    && abi.startsWith("armeabi")) {

                compatible = true;
            }

            if (machine == 3
                    && elfClass == 1
                    && "x86".equals(abi)) {

                compatible = true;
            }

            if (machine == 62
                    && elfClass == 2
                    && "x86_64".equals(abi)) {

                compatible = true;
            }
        }

        log.append("Архитектура совместима: ")
                .append(compatible ? "YES" : "NO")
                .append('\n');

        log.append("Права на выполнение: ")
                .append(executable ? "YES" : "NO")
                .append('\n');

        if (!executable) {

            log.append(
                    "Нет разрешения на выполнение AAPT2.\n"
            );
        }

        boolean launched = false;

        if (testLaunch) {

            if (!compatible) {

                log.append(
                        "Запуск пропущен: несовместимая архитектура.\n"
                );

            } else if (!executable) {

                log.append(
                        "Запуск пропущен: нет прав выполнения.\n"
                );

            } else {

                launched = testVersion(aapt2, log);
            }

        } else {

            log.append(
                    "\nЗапуск AAPT2 пока не выполнялся.\n"
            );
        }

        log.append("\n--- Итог ---\n");

        if (launched) {

            log.append("AAPT2 VERSION: OK\n");

        } else if (testLaunch) {

            log.append("AAPT2 VERSION: FAILED\n");

        } else if (compatible && executable) {

            log.append("AAPT2: NEEDS_LAUNCH_TEST\n");

        } else {

            log.append("AAPT2: NOT_READY\n");
        }

        log.append(
                "Проверка ELF и ABI не гарантирует "
                + "успешный запуск на Android.\n"
        );

        return result(
                true,
                true,
                compatible,
                executable,
                launched,
                log
        );
    }

    private static Result result(
            boolean found,
            boolean elfValid,
            boolean compatible,
            boolean executable,
            boolean launched,
            StringBuilder log
    ) {
        return new Result(
                found,
                elfValid,
                compatible,
                executable,
                launched,
                log.toString()
        );
    }

    private static boolean testVersion(
            File binary,
            StringBuilder log
    ) {
        Process process = null;

        try {

            log.append("\n--- AAPT2 version ---\n");

            ProcessBuilder builder = new ProcessBuilder(
                    binary.getAbsolutePath(),
                    "version"
            );

            builder.redirectErrorStream(true);

            process = builder.start();

            final Process runningProcess = process;

            final ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            Thread reader = new Thread(() -> {

                try (InputStream input =
                             runningProcess.getInputStream()) {

                    byte[] buffer = new byte[1024];
                    int total = 0;
                    int count;

                    while ((count = input.read(buffer)) != -1) {

                        int remaining =
                                MAX_OUTPUT_BYTES - total;

                        if (remaining > 0) {

                            int writeCount =
                                    Math.min(count, remaining);

                            output.write(
                                    buffer,
                                    0,
                                    writeCount
                            );

                            total += writeCount;
                        }
                    }

                } catch (IOException ignored) {
                    // Поток может закрыться при завершении процесса.
                }

            }, "CodeBox-AAPT2-Output");

            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            if (!finished) {

                process.destroyForcibly();

                process.waitFor(
                        1,
                        TimeUnit.SECONDS
                );

                reader.join(1000);

                log.append(
                        "TIMEOUT: AAPT2 не завершился за 5 секунд\n"
                );

                return false;
            }

            reader.join(1000);

            int exitCode = process.exitValue();

            log.append("Exit code: ")
                    .append(exitCode)
                    .append('\n');

            String commandOutput;

            synchronized (output) {
                commandOutput = output.toString("UTF-8");
            }

            if (!commandOutput.isEmpty()) {

                log.append("Вывод команды:\n");
                log.append(commandOutput);

                if (!commandOutput.endsWith("\n")) {
                    log.append('\n');
                }
            }

            if (exitCode == 0) {

                log.append(
                        "Команда version выполнена успешно.\n"
                );

                return true;
            }

            log.append(
                    "Команда version завершилась с ошибкой.\n"
            );

            return false;

        } catch (Exception e) {

            log.append("Ошибка запуска: ")
                    .append(e.getClass().getName())
                    .append(": ")
                    .append(e.getMessage())
                    .append('\n');

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return false;

        } finally {

            if (process != null) {
                process.destroy();
            }
        }
    }
}
