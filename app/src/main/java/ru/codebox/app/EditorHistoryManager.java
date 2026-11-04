package ru.codebox.app;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.ArrayList;

public final class EditorHistoryManager {

    private static final int MAX_HISTORY = 100;

    private final EditText editor;

    private final ArrayList<HistoryState> history =
            new ArrayList<>();

    private int position = 0;

    private boolean restoring = false;
    private boolean recordingScheduled = false;

    private final Runnable recordTask = new Runnable() {
        @Override
        public void run() {
            recordingScheduled = false;

            if (!restoring) {
                recordCurrentState();
            }
        }
    };

    private static final class HistoryState {

        final String text;
        final int cursorStart;
        final int cursorEnd;

        HistoryState(
                String text,
                int cursorStart,
                int cursorEnd) {

            this.text = text;
            this.cursorStart = cursorStart;
            this.cursorEnd = cursorEnd;
        }
    }

    public EditorHistoryManager(EditText editor) {

        if (editor == null) {
            throw new IllegalArgumentException(
                    "Editor cannot be null"
            );
        }

        this.editor = editor;

        history.add(captureState());

        editor.addTextChangedListener(
                new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count) {
            }

            @Override
            public void afterTextChanged(
                    Editable s) {

                if (!restoring) {
                    scheduleRecord();
                }
            }
        });
    }

    private HistoryState captureState() {

        return new HistoryState(
                editor.getText().toString(),
                editor.getSelectionStart(),
                editor.getSelectionEnd()
        );
    }

    private void scheduleRecord() {

        if (recordingScheduled) {
            editor.removeCallbacks(recordTask);
        }

        recordingScheduled = true;

        editor.post(recordTask);
    }

    private void flushPendingRecord() {

        if (recordingScheduled) {
            editor.removeCallbacks(recordTask);
            recordingScheduled = false;
            recordCurrentState();
        }
    }

    private void recordCurrentState() {

        if (restoring || history.isEmpty()) {
            return;
        }

        HistoryState current = captureState();

        HistoryState previous = history.get(position);

        if (previous.text.equals(current.text)) {
            return;
        }

        while (history.size() > position + 1) {
            history.remove(history.size() - 1);
        }

        history.add(current);
        position++;

        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            position--;
        }
    }

    public boolean canUndo() {
        flushPendingRecord();
        return position > 0;
    }

    public boolean canRedo() {
        flushPendingRecord();
        return position < history.size() - 1;
    }

    public void undo() {

        flushPendingRecord();

        if (position <= 0) {
            return;
        }

        position--;

        restore(history.get(position));
    }

    public void redo() {

        flushPendingRecord();

        if (position >= history.size() - 1) {
            return;
        }

        position++;

        restore(history.get(position));
    }

    private void restore(HistoryState state) {

        editor.removeCallbacks(recordTask);
        recordingScheduled = false;

        restoring = true;

        try {
            editor.setText(state.text);

            int length = editor.length();

            int start = Math.max(
                    0,
                    Math.min(state.cursorStart, length)
            );

            int end = Math.max(
                    0,
                    Math.min(state.cursorEnd, length)
            );

            editor.setSelection(
                    Math.min(start, end),
                    Math.max(start, end)
            );

        } finally {
            restoring = false;
        }
    }

    public void clear() {

        editor.removeCallbacks(recordTask);
        recordingScheduled = false;

        history.clear();
        history.add(captureState());

        position = 0;
    }
}
