
package ru.codebox.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class ProjectTreeActivity extends Activity {

    private File projectDirectory;
    private TextView treeText;

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    private TextView label(
            String value, int size, boolean bold) {

        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(
                CodeBoxTheme.textColor(this)
        );

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String name = getIntent()
                .getStringExtra("project_name");

        if (name == null ||
                !name.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {
            showMessage("Неверное имя проекта");
            finish();
            return;
        }

        try {
            File projectsRoot = new File(
                    getFilesDir(), "projects"
            ).getCanonicalFile();

            projectDirectory = new File(
                    projectsRoot, name
            ).getCanonicalFile();

            if (!projectDirectory.getParentFile()
                    .equals(projectsRoot)
                    || !projectDirectory.isDirectory()) {
                throw new IOException(
                        "Проект не найден"
                );
            }

        } catch (IOException e) {
            showMessage(e.getMessage());
            finish();
            return;
        }

        buildScreen();
        refreshTree();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(
                CodeBoxTheme.background(this)
        );

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(
                dp(14), dp(18), dp(14), dp(18)
        );
        header.setBackgroundColor(
                CodeBoxTheme.PURPLE
        );

        TextView back = label("‹", 36, true);
        back.setTextColor(0xFFFFFFFF);
        back.setGravity(Gravity.CENTER);
        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(48), dp(48)
                )
        );
        back.setOnClickListener(v -> finish());

        TextView title = label(
                projectDirectory.getName(),
                20, true
        );
        title.setTextColor(0xFFFFFFFF);
        title.setSingleLine(true);
        title.setEllipsize(
                android.text.TextUtils.TruncateAt.END
        );

        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView copy = label("⧉", 27, true);
        copy.setTextColor(0xFFFFFFFF);
        copy.setGravity(Gravity.CENTER);
        header.addView(
                copy,
                new LinearLayout.LayoutParams(
                        dp(48), dp(48)
                )
        );
        copy.setOnClickListener(v -> copyTree());

        TextView reload = label("↻", 29, true);
        reload.setTextColor(0xFFFFFFFF);
        reload.setGravity(Gravity.CENTER);
        header.addView(
                reload,
                new LinearLayout.LayoutParams(
                        dp(48), dp(48)
                )
        );
        reload.setOnClickListener(v -> refreshTree());

        root.addView(header);

        TextView subtitle = label(
                "🌳 Структура проекта", 17, true
        );
        subtitle.setPadding(
                dp(18), dp(20), dp(18), dp(12)
        );
        root.addView(subtitle);

        ScrollView vertical = new ScrollView(this);
        HorizontalScrollView horizontal =
                new HorizontalScrollView(this);

        treeText = label("", 14, false);
        treeText.setTypeface(Typeface.MONOSPACE);
        treeText.setTextIsSelectable(true);
        treeText.setPadding(
                dp(18), dp(20), dp(18), dp(20)
        );
        treeText.setBackground(
                CodeBoxTheme.cardBackground(this)
        );

        horizontal.addView(treeText);
        vertical.addView(horizontal);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                );
        scrollParams.setMargins(
                dp(14), 0, dp(14), dp(16)
        );

        root.addView(vertical, scrollParams);
        setContentView(root);
    }

    private void refreshTree() {
        try {
            String tree = ProjectTreeManager
                    .buildTree(projectDirectory);

            treeText.setText(tree);

        } catch (IOException e) {
            showMessage(
                    "Ошибка построения дерева: "
                            + e.getMessage()
            );
        }
    }

    private void copyTree() {
        String tree = treeText.getText().toString();

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(
                        Context.CLIPBOARD_SERVICE
                );

        if (clipboard == null) {
            showMessage("Буфер обмена недоступен");
            return;
        }

        clipboard.setPrimaryClip(
                ClipData.newPlainText(
                        "CodeBox Project Tree", tree
                )
        );

        showMessage("Дерево скопировано");
    }

    private void showMessage(String message) {
        Toast.makeText(
                this, message, Toast.LENGTH_SHORT
        ).show();
    }
}
