
package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class GraphicsEditorActivity extends Activity {

    private static final int IMAGE_SIZE = 1024;

    private File projectDirectory;
    private File targetDirectory;

    private DrawingView drawingView;
    private int selectedColor = Color.BLACK;
    private float brushSize = 8f;

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String projectName = getIntent()
                .getStringExtra("project_name");

        String folderPath = getIntent()
                .getStringExtra("folder_path");

        if (projectName == null ||
                !projectName.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {
            showError("Неверное имя проекта");
            finish();
            return;
        }

        try {
            File projects = new File(
                    getFilesDir(), "projects"
            ).getCanonicalFile();

            projectDirectory = new File(
                    projects, projectName
            ).getCanonicalFile();

            if (!projectDirectory.getParentFile()
                    .equals(projects)
                    || !projectDirectory.isDirectory()) {
                throw new IOException(
                        "Проект не найден"
                );
            }

            if (folderPath == null) {
                folderPath = "";
            }

            if (new File(folderPath).isAbsolute()) {
                throw new IOException(
                        "Недопустимый путь папки"
                );
            }

            targetDirectory = new File(
                    projectDirectory, folderPath
            ).getCanonicalFile();

            String root = projectDirectory
                    .getCanonicalPath();

            String target = targetDirectory
                    .getCanonicalPath();

            if (!target.equals(root)
                    && !target.startsWith(
                            root + File.separator
                    )) {
                throw new IOException(
                        "Папка вне проекта"
                );
            }

            if (!targetDirectory.isDirectory()) {
                throw new IOException(
                        "Папка не существует"
                );
            }

        } catch (IOException e) {
            showError(e.getMessage());
            finish();
            return;
        }

        buildScreen();
    }

    private TextView button(
            String label, int color) {

        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(14);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);

        CodeBoxTheme.styleColoredButton(
                view, color
        );

        return view;
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
                dp(12), dp(12),
                dp(12), dp(12)
        );

        TextView back = button(
                "‹ Назад",
                CodeBoxTheme.BLUE
        );

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        0, dp(50), 1
                )
        );

        back.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText("🎨 Редактор");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(
                CodeBoxTheme.textColor(this)
        );

        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0, dp(50), 2
                )
        );

        TextView save = button(
                "Сохранить",
                CodeBoxTheme.GREEN
        );

        header.addView(
                save,
                new LinearLayout.LayoutParams(
                        0, dp(50), 2
                )
        );

        save.setOnClickListener(
                v -> showSaveDialog()
        );

        root.addView(header);

        drawingView = new DrawingView();

        LinearLayout.LayoutParams canvasParams =
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                );

        canvasParams.setMargins(
                dp(12), dp(6),
                dp(12), dp(6)
        );

        root.addView(
                drawingView, canvasParams
        );

        TextView hint = new TextView(this);
        hint.setText(
                "Рисуй пальцем на белом холсте"
        );
        hint.setGravity(Gravity.CENTER);
        hint.setTextSize(13);
        hint.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        root.addView(hint);

        createPalette(root);
        createTools(root);

        setContentView(root);
    }

    private void createPalette(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);

        LinearLayout palette = new LinearLayout(this);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.setGravity(Gravity.CENTER);
        palette.setPadding(
                dp(8), dp(10),
                dp(8), dp(10)
        );

        int[] colors = {
                Color.BLACK,
                Color.WHITE,
                Color.RED,
                Color.rgb(255, 140, 0),
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.GRAY
        };

        for (int color : colors) {
            TextView swatch = new TextView(this);
            swatch.setText("●");
            swatch.setTextSize(27);
            swatch.setTextColor(color);
            swatch.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            dp(42), dp(48)
                    );

            palette.addView(swatch, params);

            swatch.setOnClickListener(v -> {
                selectedColor = color;

                Toast.makeText(
                        this,
                        "Цвет выбран",
                        Toast.LENGTH_SHORT
                ).show();
            });
        }

        android.widget.HorizontalScrollView horizontal =
                new android.widget.HorizontalScrollView(this);

        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.addView(palette);

        root.addView(horizontal);
    }

    private void createTools(LinearLayout root) {
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(
                dp(8), dp(8),
                dp(8), dp(14)
        );

        TextView thin = button(
                "Тонкая",
                CodeBoxTheme.BLUE
        );

        TextView thick = button(
                "Толстая",
                CodeBoxTheme.PURPLE
        );

        TextView eraser = button(
                "Ластик",
                0xFF777777
        );

        TextView clear = button(
                "Очистить",
                0xFFD94444
        );

        addTool(tools, thin);
        addTool(tools, thick);
        addTool(tools, eraser);
        addTool(tools, clear);

        thin.setOnClickListener(v -> brushSize = 4f);

        thick.setOnClickListener(v -> brushSize = 22f);

        eraser.setOnClickListener(v -> {
            selectedColor = Color.WHITE;
            brushSize = 30f;
        });

        clear.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Очистить холст?")
                        .setMessage(
                                "Все нарисованные линии будут удалены."
                        )
                        .setNegativeButton("Отмена", null)
                        .setPositiveButton(
                                "Очистить",
                                (dialog, which) ->
                                        drawingView.clear()
                        )
                        .show()
        );

        root.addView(tools);
    }

    private void addTool(
            LinearLayout parent, TextView tool) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0, dp(48), 1
                );

        params.setMargins(
                dp(2), 0, dp(2), 0
        );

        parent.addView(tool, params);
    }

    private void showSaveDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText("drawing.png");
        input.selectAll();

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(
                dp(22), dp(10),
                dp(22), dp(8)
        );

        wrapper.addView(input);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Сохранить изображение")
                        .setView(wrapper)
                        .setPositiveButton("Сохранить", null)
                        .setNegativeButton("Отмена", null)
                        .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(v -> {
                    String name = input.getText()
                            .toString().trim();

                    if (!name.toLowerCase(
                            java.util.Locale.ROOT
                    ).endsWith(".png")) {
                        name += ".png";
                    }

                    if (!name.matches(
                            "[^/\\\\\\p{Cntrl}]+\\.png"
                    ) || name.equals(".png")
                            || name.equals("..png")) {
                        input.setError(
                                "Недопустимое имя файла"
                        );
                        return;
                    }

                    try {
                        saveImage(name);
                        dialog.dismiss();

                        Toast.makeText(
                                this,
                                "PNG сохранён в проект",
                                Toast.LENGTH_SHORT
                        ).show();

                        finish();

                    } catch (IOException e) {
                        input.setError(
                                e.getMessage()
                        );
                    }
                })
        );

        dialog.show();
    }

    private void saveImage(String name)
            throws IOException {

        File destination = new File(
                targetDirectory, name
        );

        if (!destination.getCanonicalFile()
                .getParentFile()
                .equals(targetDirectory)) {
            throw new IOException(
                    "Недопустимый путь файла"
            );
        }

        if (destination.exists()) {
            throw new IOException(
                    "Файл с таким именем уже существует"
            );
        }

        Bitmap bitmap = Bitmap.createBitmap(
                IMAGE_SIZE,
                IMAGE_SIZE,
                Bitmap.Config.ARGB_8888
        );

        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            drawingView.renderTo(canvas);

            try (FileOutputStream output =
                         new FileOutputStream(destination)) {

                if (!bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output
                )) {
                    throw new IOException(
                            "Не удалось сохранить PNG"
                    );
                }

                output.flush();
            } catch (IOException e) {
                destination.delete();
                throw e;
            }

        } finally {
            bitmap.recycle();
        }
    }

    private void showError(String message) {
        Toast.makeText(
                this,
                message == null
                        ? "Ошибка графического редактора"
                        : message,
                Toast.LENGTH_LONG
        ).show();
    }

    private final class DrawingView extends View {

        private Bitmap drawingBitmap;
        private Canvas drawingCanvas;
        private Paint paint;
        private Path currentPath;

        DrawingView() {
            super(GraphicsEditorActivity.this);

            setBackgroundColor(Color.WHITE);

            paint = new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );

            paint.setStyle(
                    Paint.Style.STROKE
            );

            paint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            paint.setStrokeJoin(
                    Paint.Join.ROUND
            );

            currentPath = new Path();
        }

        @Override
        protected void onSizeChanged(
                int w, int h,
                int oldw, int oldh) {

            super.onSizeChanged(
                    w, h, oldw, oldh
            );

            if (w <= 0 || h <= 0) {
                return;
            }

            Bitmap old = drawingBitmap;

            drawingBitmap = Bitmap.createBitmap(
                    w, h,
                    Bitmap.Config.ARGB_8888
            );

            drawingCanvas = new Canvas(
                    drawingBitmap
            );

            drawingCanvas.drawColor(
                    Color.WHITE
            );

            if (old != null) {
                drawingCanvas.drawBitmap(
                        old, 0, 0, null
                );
                old.recycle();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (drawingBitmap != null) {
                canvas.drawBitmap(
                        drawingBitmap, 0, 0, null
                );
            }

            paint.setColor(selectedColor);
            paint.setStrokeWidth(brushSize);

            canvas.drawPath(
                    currentPath, paint
            );
        }

        @Override
        public boolean onTouchEvent(
                MotionEvent event) {

            float x = event.getX();
            float y = event.getY();

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:
                    currentPath.reset();
                    currentPath.moveTo(x, y);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    currentPath.lineTo(x, y);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                    currentPath.lineTo(x, y);

                    if (drawingCanvas != null) {
                        paint.setColor(selectedColor);
                        paint.setStrokeWidth(brushSize);

                        drawingCanvas.drawPath(
                                currentPath, paint
                        );
                    }

                    currentPath.reset();
                    invalidate();
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    currentPath.reset();
                    invalidate();
                    return true;
            }

            return true;
        }

        void clear() {
            if (drawingCanvas != null) {
                drawingCanvas.drawColor(
                        Color.WHITE
                );
            }

            currentPath.reset();
            invalidate();
        }

        void renderTo(Canvas target) {
            if (drawingBitmap == null) {
                return;
            }

            target.save();

            float scale = Math.min(
                    (float) IMAGE_SIZE
                            / drawingBitmap.getWidth(),
                    (float) IMAGE_SIZE
                            / drawingBitmap.getHeight()
            );

            float width =
                    drawingBitmap.getWidth() * scale;

            float height =
                    drawingBitmap.getHeight() * scale;

            target.translate(
                    (IMAGE_SIZE - width) / 2f,
                    (IMAGE_SIZE - height) / 2f
            );

            target.scale(scale, scale);

            target.drawBitmap(
                    drawingBitmap,
                    0, 0, null
            );

            target.restore();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();

            if (drawingBitmap != null
                    && !drawingBitmap.isRecycled()) {
                drawingBitmap.recycle();
                drawingBitmap = null;
            }
        }
    }
}
