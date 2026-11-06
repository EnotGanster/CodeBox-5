package ru.codebox.app;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * CodeBox 5 - AAPT2 Inspector.
 *
 * Checks:
 * - AAPT2 file presence
 * - ELF header
 * - CPU architecture
 * - executable permissions
 * - optional execution test
 *
 * Does not compile resources or create APK.
 */
public final class CodeBoxAapt2Inspector {

    private static final int ELF_HEADER_SIZE = 20;
    private static final long TIMEOUT_SECONDS = 5;

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
            this.architectureCompatible =
                    architectureCompatible;
            this.executable = executable;
            this.launchSuccessful = launchSuccessful;
            this.report = report;
        }
    }

    /**
     * Performs file and architecture checks only.
     * Does not launch the executable.
     */
    public static Result inspect(Context context) {
        return inspect(context, false);
    }

    /**
     * Set testLaunch=true to attempt "aapt2 version".
     *
     * Call from a background thread when testLaunch=true.
     * Only run trusted AAPT2 binaries.
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

            return new Result(
                    false, false, false,
                    false, false, log.toString()
            );
        }

        File toolDirectory = new File(
                context.getFilesDir(),
                "build-tools"
        );

        File aapt2 = new File(
                toolDirectory,
                "aapt2"
        );

        log.append("\nКаталог инструментов:\n");
        log.append(toolDirectory.getAbsolutePath());
        log.append('\n');

        if (!aapt2.isFile()) {
            log.append("\nAAPT2: NOT_FOUND\n");
            log.append("Файл aapt2 отсутствует.\n");
            log.append(
                    "Нужна сборка AAPT2 для Android "
                    + "и архитектуры устройства.\n"
            );

            return new Result(
                    false, false, false,
                    false, false, log.toString()
            );
        }

        log.append("\nAAPT2: FOUND\n");
        log.append("Путь: ")
                .append(aapt2.getAbsolutePath())
                .append('\n');

        log.append("Размер: ")
                .append(aapt2.length())
                .append(" байт\n");

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

                total += count;
            }

            if (total < ELF_HEADER_SIZE) {
                log.append(
                        "ELF: INVALID (заголовок слишком короткий)\n"
                );

                return new Result(
                        true, false, false,
                        aapt2.canExecute(),
                        false, log.toString()
                );
            }

        } catch (IOException e) {
            log.append("Ошибка чтения: ")
                    .append(e.getMessage())
                    .append('\n');

            return new Result(
                    true, false, false,
                    aapt2.canExecute(),
                    false, log.toString()
            );
        }

        boolean validElf =
                (header[0] & 0xff) == 0x7f
                && header[1] == 'E'
                && header[2] == 'L'
                && header[3] == 'F';

        if (!validElf) {
            log.append("ELF: INVALID\n");
            log.append(
                    "Файл не является обычным ELF executable.\n"
            );

            return new Result(
                    true, false, false,
                    aapt2.canExecute(),
                    false, log.toString()
            );
        }

        log.append("ELF: OK\n");

        int elfClass = header[4] & 0xff;
        int endian = header[5] & 0xff;

        String bitness;

        if (elfClass == 1) {
            bitness = "32-bit";
        } else if (elfClass == 2) {
            bitness = "64-bit";
        } else {
            bitness = "UNKNOWN";
        }

        log.append("Разрядность: ")
                .append(bitness)
                .append('\n');

        if (endian != 1 && endian != 2) {
            log.append("ELF byte order: INVALID\n");

            return new Result(
                    true, false, false,
                    aapt2.canExecute(),
                    false, log.toString()
            );
        }

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

        boolean executable = aapt2.canExecute();

        log.append("Права на выполнение: ")
                .append(executable ? "YES" : "NO")
                .append('\n');

        if (!executable) {
            log.append(
                    "Файл не имеет разрешения на выполнение.\n"
            );
        }

        boolean launched = false;

        if (testLaunch) {

            if (!compatible) {
                log.append(
                        "Запуск пропущен: архитектура несовместима.\n"
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
                    "\nЗапуск AAPT2 не выполнялся.\n"
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
                + "работу AAPT2 на Android.\n"
        );

        return new Result(
                true,
                true,
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

            boolean finished = process.waitFor(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            if (!finished) {
                process.destroyForcibly();

                log.append(
                        "TIMEOUT: AAPT2 не завершился за 5 секунд\n"
                );

                return false;
            }

            int exitCode = process.exitValue();

            log.append("Exit code: ")
                    .append(exitCode)
                    .append('\n');

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

            return false;

        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
