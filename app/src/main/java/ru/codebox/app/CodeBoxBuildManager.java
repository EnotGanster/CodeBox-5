package ru.codebox.app;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Tracks local APK/AAB build state, diagnostics, and produced artifacts. */
public final class CodeBoxBuildManager {

    public enum OutputFormat { APK, AAB }
    public enum State { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED }
    public enum Level { INFO, WARNING, ERROR }

    public static final class Entry {
        public final long timestamp;
        public final Level level;
        public final String stage;
        public final String message;

        private Entry(long timestamp, Level level, String stage, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.stage = stage;
            this.message = message;
        }
    }

    private final File logsDirectory;
    private final List<Entry> entries = new ArrayList<>();
    private String projectName = "";
    private OutputFormat outputFormat = OutputFormat.APK;
    private State state = State.IDLE;
    private String stage = "Ожидание";
    private long startedAt;
    private long finishedAt;
    private int errors;
    private int warnings;
    private File outputFile;

    public CodeBoxBuildManager(Context context) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context отсутствует");
        }
        logsDirectory = new File(context.getFilesDir(), "build-logs");
        if (!logsDirectory.isDirectory() && !logsDirectory.mkdirs()) {
            throw new IOException("Не удалось создать папку журналов");
        }
    }

    public synchronized void start(String name, OutputFormat format) {
        if (state == State.RUNNING) {
            throw new IllegalStateException("Сборка уже выполняется");
        }
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
            throw new IllegalArgumentException("Недопустимое имя проекта");
        }
        if (format == null) {
            throw new IllegalArgumentException("Не выбран формат сборки");
        }
        entries.clear();
        projectName = name;
        outputFormat = format;
        state = State.RUNNING;
        stage = "Подготовка";
        startedAt = System.currentTimeMillis();
        finishedAt = 0;
        errors = 0;
        warnings = 0;
        outputFile = null;
        append(Level.INFO, "Система", "Начата локальная сборка " + format.name());
        append(Level.INFO, "Система", "Конвейер сборки: AAPT2, ECJ, D8; упаковка и подпись по формату результата");
    }

    public synchronized void setStage(String newStage) {
        ensureRunning();
        if (newStage == null || newStage.trim().isEmpty()) {
            throw new IllegalArgumentException("Этап не указан");
        }
        stage = newStage.trim();
        append(Level.INFO, stage, "Этап начат");
    }

    public synchronized void info(String message) {
        ensureRunning();
        append(Level.INFO, stage, message);
    }

    public synchronized void warning(String message) {
        ensureRunning();
        warnings++;
        append(Level.WARNING, stage, message);
    }

    public synchronized void error(String message) {
        ensureRunning();
        errors++;
        append(Level.ERROR, stage, message);
    }

    public synchronized void fail(String message) {
        ensureRunning();
        error(message);
        state = State.FAILED;
        finishedAt = System.currentTimeMillis();
        append(Level.INFO, "Система", "Сборка завершилась с ошибкой");
    }

    public synchronized void cancel() {
        ensureRunning();
        state = State.CANCELLED;
        finishedAt = System.currentTimeMillis();
        append(Level.INFO, "Система", "Сборка отменена");
    }

    /** The caller must separately validate the artifact's format and signature. */
    public synchronized void succeed(File artifact) throws IOException {
        ensureRunning();
        if (errors != 0) {
            throw new IOException("В журнале есть ошибки");
        }
        if (artifact == null || !artifact.isFile() || artifact.length() == 0) {
            throw new IOException("Выходной файл не создан");
        }
        String expected = outputFormat == OutputFormat.APK ? ".apk" : ".aab";
        if (!artifact.getName().toLowerCase(Locale.ROOT).endsWith(expected)) {
            throw new IOException("Неверный формат выходного файла");
        }
        outputFile = artifact.getCanonicalFile();
        state = State.SUCCEEDED;
        finishedAt = System.currentTimeMillis();
        append(Level.INFO, "Система", "Артефакт: " + outputFile.getName());
        append(Level.INFO, "Система", "Сборка завершена");
    }

    public synchronized State getState() { return state; }
    public synchronized String getStage() { return stage; }
    public synchronized int getErrorCount() { return errors; }
    public synchronized int getWarningCount() { return warnings; }
    public synchronized File getOutputFile() { return outputFile; }
    public synchronized List<Entry> getEntries() { return new ArrayList<>(entries); }

    public synchronized String getReport() {
        StringBuilder result = new StringBuilder();
        result.append("CodeBox 5 - Build Log\n");
        result.append("Project: ").append(projectName).append('\n');
        result.append("Output: ").append(outputFormat.name()).append('\n');
        result.append("Status: ").append(state.name()).append('\n');
        result.append("Stage: ").append(stage).append('\n');
        result.append("Errors: ").append(errors).append('\n');
        result.append("Warnings: ").append(warnings).append('\n');
        if (startedAt > 0) {
            result.append("Started: ").append(formatTime(startedAt)).append('\n');
        }
        if (finishedAt > 0) {
            result.append("Finished: ").append(formatTime(finishedAt)).append('\n');
            result.append("Duration ms: ").append(finishedAt - startedAt).append('\n');
        }
        result.append("\n--- Diagnostics ---\n");
        for (Entry entry : entries) {
            result.append('[').append(formatTime(entry.timestamp)).append("] ");
            result.append('[').append(entry.level.name()).append("] ");
            result.append('[').append(entry.stage).append("] ");
            result.append(entry.message).append('\n');
        }
        return result.toString();
    }

    public synchronized File saveReport() throws IOException {
        if (state == State.IDLE) {
            throw new IOException("Журнал ещё не создан");
        }
        String safeName = projectName.isEmpty() ? "project" : projectName;
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new Date());
        File destination = new File(logsDirectory, safeName + "_" + stamp + ".txt");
        Path temporary = Files.createTempFile(logsDirectory.toPath(), ".codebox-log-", ".tmp");
        try {
            Files.write(temporary, getReport().getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return destination;
    }

    private void ensureRunning() {
        if (state != State.RUNNING) {
            throw new IllegalStateException("Сборка не запущена");
        }
    }

    private void append(Level level, String section, String message) {
        String clean = message == null ? "(нет сообщения)" : message;
        entries.add(new Entry(System.currentTimeMillis(), level, section, clean));
    }

    private static String formatTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date(time));
    }
}
