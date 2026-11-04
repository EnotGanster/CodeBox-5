package ru.codebox.app;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.ArrayList;

public final class EditorHistoryManager {

    private static final int MAX_HISTORY = 100;

    private final EditText editor;

    private final ArrayList<String> history =
            new ArrayList<>();

    private int position = 0;
    private boolean restoring = false;

    public EditorHistoryManager(EditText editor) {
        if (editor == null) {
            throw new IllegalArgumentException(
                    "Editor cannot be null"
            );
        }

        this.editor = editor;

        history.add(
                editor.getText().toString()
        );

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
                    record(s.toString());
                }
            }
        });
    }

    private void record(String text) {

        if (history.get(position).equals(text)) {
            return;
        }

        // Удаляем ветку Redo после нового ввода.
        while (history.size() > position + 1) {
            history.remove(history.size() - 1);
        }

        history.add(text);
        position++;

        // Ограничиваем размер истории.
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            position--;
        }
    }

    public boolean canUndo() {
        return position > 0;
    }

    public boolean canRedo() {
        return position < history.size() - 1;
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }

        position--;
        restore(history.get(position));
    }

    public void redo() {
        if (!canRedo()) {
            return;
        }

        position++;
        restore(history.get(position));
    }

    private void restore(String text) {

        restoring = true;

        try {
            int cursor = editor.getSelectionStart();

            editor.setText(text);

            int safeCursor = Math.max(
                    0,
                    Math.min(cursor, text.length())
            );

            editor.setSelection(safeCursor);

        } finally {
            restoring = false;
        }
    }

    public void clear() {
        history.clear();

        history.add(
                editor.getText().toString()
        );

        position = 0;
    }
}
