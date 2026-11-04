package ru.codebox.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

public class EditorToolbar extends HorizontalScrollView {

    private final EditText editor;
    private final LinearLayout buttons;

    public EditorToolbar(Context context, EditText editor) {
        super(context);

        this.editor = editor;

        setHorizontalScrollBarEnabled(false);
        setBackgroundColor(Color.rgb(235, 238, 245));

        buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(dp(4), dp(3), dp(4), dp(3));

        addView(buttons);

        String[] symbols = {
                "{", "}", "(", ")", "[", "]",
                ";", "=", "\"", "'", ".", ",",
                "/", "_", "Tab", "←", "→"
        };

        for (String symbol : symbols) {
            addSymbolButton(symbol);
        }
    }

    private int dp(int value) {
        return (int) (value *
                getResources().getDisplayMetrics().density
                + 0.5f);
    }

    private void addSymbolButton(String symbol) {
        Button button = new Button(getContext());
        button.setText(symbol);
        button.setTextSize(14);
        button.setTypeface(Typeface.MONOSPACE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(5), 0, dp(5), 0);

        // Кнопки не забирают фокус у редактора.
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(symbol.equals("Tab") ? 58 : 44),
                        dp(44)
                );

        params.setMargins(dp(2), 0, dp(2), 0);
        buttons.addView(button, params);

        button.setOnClickListener(v -> {
            switch (symbol) {
                case "Tab":
                    insertText("    ");
                    break;
                case "←":
                    moveCursor(-1);
                    break;
                case "→":
                    moveCursor(1);
                    break;
                default:
                    insertText(symbol);
                    break;
            }
        });
    }

    private void insertText(String text) {
        if (editor == null) {
            return;
        }

        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();

        if (start < 0 || end < 0) {
            return;
        }

        int from = Math.min(start, end);
        int to = Math.max(start, end);

        editor.getText().replace(from, to, text);
        editor.setSelection(from + text.length());
    }

    private void moveCursor(int direction) {
        if (editor == null) {
            return;
        }

        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();

        if (start < 0 || end < 0) {
            return;
        }

        int position;

        if (start != end) {
            position = direction < 0
                    ? Math.min(start, end)
                    : Math.max(start, end);
        } else {
            position = start + direction;
        }

        position = Math.max(
                0,
                Math.min(position, editor.length())
        );

        editor.setSelection(position);
    }
}
