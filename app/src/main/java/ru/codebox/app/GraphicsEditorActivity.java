package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GraphicsEditorActivity extends Activity {

    private static final int IMAGE_SIZE = 1024;

    private static final int PURPLE = 0xFF7650E8;
    private static final int BLUE = 0xFF487DDE;
    private static final int RED = 0xFFD94B5B;

    private File projectDirectory;
    private File targetDirectory;

    private DrawingView drawingView;

    private int selectedColor = Color.BLACK;
    private float brushSize = 8f;
    private boolean eraserMode = false;

    private TextView toolStatus;
    private LinearLayout paletteLayout;

    private final List<TextView> colorSwatches =
            new ArrayList<>();

    private final int[] colors = {
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

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
    }

    private GradientDrawable rounded(
            int color, int radius) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));

        return drawable;
    }

    private GradientDrawable headerGradient() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        0xFF8657F5,
                        0xFF6535D5,
                        0xFF4522A3
                }
        );
    }

    private TextView text(
            String value,
            int size,
            boolean bold) {

        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);

        view.setTextColor(
                CodeBoxTheme.textColor(this)
        );

        if (bold) {
            view.setTypeface(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
            );
        }

        return view;
    }

    private TextView button(
            String value, int color) {

        TextView view = text(value, 13, true);
        view.setGravity(Gravity.CENTER);

        CodeBoxTheme.styleColoredButton(
                view, color
        );

        return view;
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

    private void buildScreen() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(
                CodeBoxTheme.background(this)
        );

        createHeader(root);
        createCanvasArea(root);
        createPalette(root);
        createTools(root);

        setContentView(root);
        updateToolStatus();
        updatePaletteSelection();
    }

    private void createHeader(
            LinearLayout root) {

        LinearLayout header =
                new LinearLayout(this);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(Gravity.CENTER_VERTICAL);

        header.setPadding(
                dp(14), dp(14),
                dp(14), dp(14)
        );

        header.setBackground(
                headerGradient()
        );

        TextView back = text(
                "‹", 32, true
        );

        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);

        back.setBackground(
                rounded(0x33FFFFFF, 15)
        );

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(44), dp(44)
                )
        );

        back.setOnClickListener(
                v -> attemptExit()
        );

        LinearLayout labels =
                new LinearLayout(this);

        labels.setOrientation(
                LinearLayout.VERTICAL
        );

        labels.setPadding(
                dp(12), 0,
                dp(8), 0
        );

        TextView title = text(
                "Графический редактор",
                17,
                true
        );

        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);

        labels.addView(title);

        TextView subtitle = text(
                "PNG • 1024 × 1024",
                11,
                false
        );

        subtitle.setTextColor(
                0xFFE8DDFF
        );

        labels.addView(subtitle);

        header.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView save = text(
                "✓ PNG", 13, true
        );

        save.setTextColor(Color.WHITE);
        save.setGravity(Gravity.CENTER);

        save.setBackground(
                rounded(0x33FFFFFF, 14)
        );

        header.addView(
                save,
                new LinearLayout.LayoutParams(
                        dp(74), dp(44)
                )
        );

        save.setOnClickListener(
                v -> showSaveDialog()
        );

        root.addView(header);
    }

    private void createCanvasArea(
            LinearLayout root) {

        LinearLayout canvasContainer =
                new LinearLayout(this);

        canvasContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        canvasContainer.setPadding(
                dp(12), dp(12),
                dp(12), dp(8)
        );

        LinearLayout info =
                new LinearLayout(this);

        info.setOrientation(
                LinearLayout.HORIZONTAL
        );

        info.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView label = text(
                "ХОЛСТ",
                12,
                true
        );

        label.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        info.addView(
                label,
                new LinearLayout.LayoutParams(
                        0, dp(30), 1
                )
        );

        label.setGravity(
                Gravity.CENTER_VERTICAL
        );

        toolStatus = text(
                "● Кисть",
                12,
                true
        );

        toolStatus.setGravity(
                Gravity.CENTER_VERTICAL
                        | Gravity.END
        );

        info.addView(toolStatus);

        canvasContainer.addView(info);

        drawingView = new DrawingView();

        LinearLayout.LayoutParams canvasParams =
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                );

        canvasParams.topMargin = dp(6);
        canvasParams.bottomMargin = dp(8);

        canvasContainer.addView(
                drawingView,
                canvasParams
        );

        TextView hint = text(
                "Рисуй пальцем на белом холсте",
                12,
                false
        );

        hint.setGravity(Gravity.CENTER);

        hint.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        canvasContainer.addView(hint);

        root.addView(
                canvasContainer,
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                )
        );
    }

    private void createPalette(
            LinearLayout root) {

        LinearLayout section =
                new LinearLayout(this);

        section.setOrientation(
                LinearLayout.VERTICAL
        );

        section.setPadding(
                dp(12), dp(8),
                dp(12), dp(6)
        );

        TextView title = text(
                "ПАЛИТРА ЦВЕТОВ",
                11,
                true
        );

        title.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        section.addView(title);

        HorizontalScrollView horizontal =
                new HorizontalScrollView(this);

        horizontal.setHorizontalScrollBarEnabled(
                false
        );

        paletteLayout =
                new LinearLayout(this);

        paletteLayout.setOrientation(
                LinearLayout.HORIZONTAL
        );

        paletteLayout.setGravity(
                Gravity.CENTER_VERTICAL
        );

        paletteLayout.setPadding(
                0, dp(8),
                0, dp(6)
        );

        colorSwatches.clear();

        for (int color : colors) {

            TextView swatch =
                    new TextView(this);

            swatch.setGravity(Gravity.CENTER);
            swatch.setText("●");
            swatch.setTextSize(26);
            swatch.setTextColor(color);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            dp(48), dp(48)
                    );

            params.rightMargin = dp(6);

            paletteLayout.addView(
                    swatch, params
            );

            colorSwatches.add(swatch);

            swatch.setContentDescription(
                    "Выбрать цвет"
            );

            swatch.setOnClickListener(v -> {
                selectedColor = color;
                eraserMode = false;

                updatePaletteSelection();
                updateToolStatus();
            });
        }

        horizontal.addView(paletteLayout);
        section.addView(horizontal);
        root.addView(section);
    }

    private void updatePaletteSelection() {

        for (int i = 0;
                i < colorSwatches.size();
                i++) {

            TextView swatch =
                    colorSwatches.get(i);

            boolean selected =
                    !eraserMode
                    && colors[i] == selectedColor;

            GradientDrawable background =
                    rounded(
                            selected
                                    ? (CodeBoxTheme.isDark(this)
                                            ? 0xFF46366D
                                            : 0xFFE6DDFB)
                                    : CodeBoxTheme.surface(this),
                            14
                    );

            background.setStroke(
                    dp(selected ? 2 : 1),
                    selected
                            ? PURPLE
                            : (CodeBoxTheme.isDark(this)
                                    ? 0xFF454555
                                    : 0xFFE2E2EA)
            );

            swatch.setBackground(background);
        }
    }

    private void createTools(
            LinearLayout root) {

        LinearLayout section =
                new LinearLayout(this);

        section.setOrientation(
                LinearLayout.VERTICAL
        );

        section.setPadding(
                dp(12), dp(6),
                dp(12), dp(14)
        );

        TextView title = text(
                "ИНСТРУМЕНТЫ",
                11,
                true
        );

        title.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        section.addView(title);

        LinearLayout firstRow =
                new LinearLayout(this);

        firstRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        -1, dp(46)
                );

        rowParams.topMargin = dp(8);

        section.addView(
                firstRow, rowParams
        );

        TextView thin = button(
                "Тонкая", BLUE
        );

        TextView medium = button(
                "Средняя", PURPLE
        );

        TextView thick = button(
                "Толстая", PURPLE
        );

        addTool(firstRow, thin);
        addTool(firstRow, medium);
        addTool(firstRow, thick);

        thin.setOnClickListener(v -> {
            brushSize = 4f;
            eraserMode = false;
            updateToolStatus();
            updatePaletteSelection();
        });

        medium.setOnClickListener(v -> {
            brushSize = 8f;
            eraserMode = false;
            updateToolStatus();
            updatePaletteSelection();
        });

        thick.setOnClickListener(v -> {
            brushSize = 22f;
            eraserMode = false;
            updateToolStatus();
            updatePaletteSelection();
        });

        LinearLayout secondRow =
                new LinearLayout(this);

        secondRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams secondParams =
                new LinearLayout.LayoutParams(
                        -1, dp(46)
                );

        secondParams.topMargin = dp(8);

        section.addView(
                secondRow, secondParams
        );

        TextView eraser = button(
                "⌫ Ластик",
                0xFF777788
        );

        TextView clear = button(
                "✕ Очистить",
                RED
        );

        addTool(secondRow, eraser);
        addTool(secondRow, clear);

        eraser.setOnClickListener(v -> {
            eraserMode = true;
            brushSize = 30f;

            updateToolStatus();
            updatePaletteSelection();
        });

        clear.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Очистить холст?")
                        .setMessage(
                                "Все нарисованные линии "
                                        + "будут удалены."
                        )
                        .setNegativeButton(
                                "Отмена", null
                        )
                        .setPositiveButton(
                                "Очистить",
                                (dialog, which) -> {
                                    drawingView.clear();
                                    updateToolStatus();
                                }
                        )
                        .show()
        );

        root.addView(section);
    }

    private void addTool(
            LinearLayout parent,
            TextView tool) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0, -1, 1
                );

        params.setMargins(
                dp(2), 0,
                dp(2), 0
        );

        parent.addView(tool, params);
    }

    private void updateToolStatus() {

        if (toolStatus == null) {
            return;
        }

        if (eraserMode) {
            toolStatus.setText(
                    "● Ластик • 30"
            );

            toolStatus.setTextColor(
                    0xFF9999AA
            );

        } else {
            toolStatus.setText(
                    "● Кисть • "
                            + (int) brushSize
            );

            toolStatus.setTextColor(
                    CodeBoxTheme.PURPLE
            );
        }
    }

    private void attemptExit() {

        if (drawingView == null
                || !drawingView.hasDrawing()) {
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Выйти из редактора?")
                .setMessage(
                        "Несохранённый рисунок "
                                + "будет потерян."
                )
                .setPositiveButton(
                        "Сохранить",
                        (dialog, which) ->
                                showSaveDialog()
                )
                .setNegativeButton(
                        "Не сохранять",
                        (dialog, which) -> finish()
                )
                .setNeutralButton(
                        "Отмена", null
                )
                .show();
    }

    @Override
    public void onBackPressed() {
        attemptExit();
    }

    private void showSaveDialog() {

        EditText input = new EditText(this);

        input.setSingleLine(true);
        input.setText("drawing.png");
        input.selectAll();

        LinearLayout wrapper =
                new LinearLayout(this);

        wrapper.setPadding(
                dp(22), dp(10),
                dp(22), dp(8)
        );

        wrapper.addView(input);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Сохранить изображение"
                        )
                        .setMessage(
                                "PNG • 1024 × 1024"
                        )
                        .setView(wrapper)
                        .setPositiveButton(
                                "Сохранить", null
                        )
                        .setNegativeButton(
                                "Отмена", null
                        )
                        .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(v -> {

                    String name = input.getText()
                            .toString().trim();

                    if (!name.toLowerCase(
                            Locale.ROOT
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
                    "Файл с таким именем "
                            + "уже существует"
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
                         new FileOutputStream(
                                 destination
                         )) {

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

        private final Paint paint;
        private final Path currentPath;

        private final Paint bitmapPaint;

        private int strokeColor = Color.BLACK;
        private float strokeWidth = 8f;

        private boolean drawingStarted = false;
        private boolean hasDrawing = false;

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

            bitmapPaint = new Paint(
                    Paint.ANTI_ALIAS_FLAG
                            | Paint.FILTER_BITMAP_FLAG
            );
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

            drawingCanvas.drawColor(Color.WHITE);

            if (old != null
                    && !old.isRecycled()) {

                RectF destination = new RectF(
                        0, 0, w, h
                );

                drawingCanvas.drawBitmap(
                        old,
                        null,
                        destination,
                        bitmapPaint
                );

                old.recycle();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (drawingBitmap != null) {
                canvas.drawBitmap(
                        drawingBitmap,
                        0, 0, bitmapPaint
                );
            }

            if (drawingStarted) {
                paint.setColor(strokeColor);
                paint.setStrokeWidth(strokeWidth);

                canvas.drawPath(
                        currentPath, paint
                );
            }
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

                    strokeColor = eraserMode
                            ? Color.WHITE
                            : selectedColor;

                    strokeWidth = brushSize;
                    drawingStarted = true;

                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:

                    if (!drawingStarted) {
                        return true;
                    }

                    currentPath.lineTo(x, y);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:

                    if (!drawingStarted) {
                        return true;
                    }

                    currentPath.lineTo(x, y);

                    if (drawingCanvas != null) {
                        paint.setColor(strokeColor);
                        paint.setStrokeWidth(strokeWidth);

                        drawingCanvas.drawPath(
                                currentPath, paint
                        );

                        hasDrawing = true;
                    }

                    currentPath.reset();
                    drawingStarted = false;

                    invalidate();
                    return true;

                case MotionEvent.ACTION_CANCEL:

                    currentPath.reset();
                    drawingStarted = false;

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
            drawingStarted = false;
            hasDrawing = false;

            invalidate();
        }

        boolean hasDrawing() {
            return hasDrawing || drawingStarted;
        }

        void renderTo(Canvas target) {

            if (drawingBitmap == null
                    || drawingBitmap.isRecycled()) {
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
                    0, 0, bitmapPaint
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
