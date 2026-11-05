package ru.codebox.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GraphicsEditorActivity extends Activity {
    private static final int SIZE = 1024;
    private static final int PURPLE = 0xFF7650E8;
    private static final int BLUE = 0xFF487DDE;
    private static final int RED = 0xFFD94B5B;
    private File projectDirectory, targetDirectory, sourceFile;
    private Bitmap sourceBitmap;
    private DrawingView drawingView;
    private TextView status, undoButton, redoButton, cropButton;
    private LinearLayout cropPanel;
    private int color = Color.BLACK;
    private float brush = 8f;
    private boolean eraser = false, cropMode = false;
    private int shape = 0; // 0 none, 1 circle, 2 square, 3 rounded square
    private float cropFraction = 0.75f, cropX = 0.5f, cropY = 0.5f;
    private final int[] palette = {Color.BLACK, Color.WHITE, Color.RED, 0xFFFF8C00,
            Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.GRAY};
    private final List<TextView> swatches = new ArrayList<>();

    private int dp(float value) { return CodeBoxTheme.dp(this, value); }
    private GradientDrawable bg(int c, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(c); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView label(String s, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(CodeBoxTheme.textColor(this));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private TextView button(String s, int c) {
        TextView t = label(s, 12, true); t.setGravity(Gravity.CENTER);
        CodeBoxTheme.styleColoredButton(t, c); t.setSingleLine(true); return t;
    }
    private void addButton(LinearLayout row, TextView t) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1);
        p.setMargins(dp(2), 0, dp(2), 0); row.addView(t, p);
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String projectName = getIntent().getStringExtra("project_name");
        String folderPath = getIntent().getStringExtra("folder_path");
        String imagePath = getIntent().getStringExtra("image_path");
        if (projectName == null || !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
            error("Неверное имя проекта"); finish(); return;
        }
        try {
            File projects = new File(getFilesDir(), "projects").getCanonicalFile();
            projectDirectory = new File(projects, projectName).getCanonicalFile();
            if (!projectDirectory.getParentFile().equals(projects)
                    || !projectDirectory.isDirectory()) throw new IOException("Проект не найден");
            if (folderPath == null) folderPath = "";
            if (new File(folderPath).isAbsolute()) throw new IOException("Неверная папка");
            targetDirectory = new File(projectDirectory, folderPath).getCanonicalFile();
            insideProject(targetDirectory);
            if (!targetDirectory.isDirectory()) throw new IOException("Папка не существует");
            if (imagePath != null && !imagePath.isEmpty()) {
                if (new File(imagePath).isAbsolute()) throw new IOException("Неверный путь изображения");
                sourceFile = new File(projectDirectory, imagePath).getCanonicalFile();
                insideProject(sourceFile);
                if (!sourceFile.isFile()) throw new IOException("Изображение не найдено");
                loadImage();
            }
        } catch (Exception e) { error(e.getMessage()); finish(); return; }
        buildScreen();
    }
    private void insideProject(File f) throws IOException {
        String root = projectDirectory.getCanonicalPath();
        String path = f.getCanonicalPath();
        if (!path.equals(root) && !path.startsWith(root + File.separator))
            throw new IOException("Путь вне проекта");
    }
    private void loadImage() throws IOException {
        String n = sourceFile.getName().toLowerCase(Locale.ROOT);
        if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".webp"))) throw new IOException("Неподдерживаемый формат");
        if (sourceFile.length() > 30L * 1024 * 1024) throw new IOException("Файл слишком большой");
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("Ошибка чтения изображения");
        int sample = 1;
        while (o.outWidth / sample > 2048 || o.outHeight / sample > 2048) sample *= 2;
        o.inJustDecodeBounds = false; o.inSampleSize = sample;
        sourceBitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), o);
        if (sourceBitmap == null) throw new IOException("Не удалось открыть изображение");
    }
    private void buildScreen() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setBackgroundColor(CodeBoxTheme.background(this));
        LinearLayout header = row(); header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF8657F5, 0xFF6535D5, 0xFF4522A3}));
        TextView back = label("‹", 30, true); back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER); back.setBackground(bg(0x33FFFFFF, 12));
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        back.setOnClickListener(v -> attemptExit());
        TextView title = label(sourceFile == null ? "Новый рисунок" : "Редактирование", 17, true);
        title.setTextColor(Color.WHITE); title.setSingleLine(true);
        title.setPadding(dp(10), 0, dp(5), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        TextView save = label("✓ PNG", 13, true); save.setTextColor(Color.WHITE);
        save.setGravity(Gravity.CENTER); save.setBackground(bg(0x33FFFFFF, 12));
        header.addView(save, new LinearLayout.LayoutParams(dp(72), dp(40)));
        save.setOnClickListener(v -> saveDialog()); root.addView(header);

        LinearLayout area = new LinearLayout(this); area.setOrientation(1);
        area.setPadding(dp(10), dp(5), dp(10), dp(4));
        LinearLayout info = row(); TextView canvasLabel = label("ХОЛСТ", 11, true);
        canvasLabel.setTextColor(CodeBoxTheme.secondaryText(this));
        info.addView(canvasLabel, new LinearLayout.LayoutParams(0, dp(24), 1));
        status = label("Кисть", 11, true); info.addView(status); area.addView(info);
        drawingView = new DrawingView();
        LinearLayout.LayoutParams drawingParams = new LinearLayout.LayoutParams(-1, 0, 1);
        drawingParams.topMargin = dp(4); area.addView(drawingView, drawingParams);
        root.addView(area, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = new LinearLayout(this); controls.setOrientation(1);
        controls.setPadding(dp(8), dp(4), dp(8), dp(8));
        TextView paletteTitle = label("ЦВЕТА", 10, true);
        paletteTitle.setTextColor(CodeBoxTheme.secondaryText(this)); controls.addView(paletteTitle);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false); LinearLayout colorsRow = row();
        colorsRow.setPadding(0, dp(4), 0, dp(4));
        for (int c : palette) {
            TextView sw = label("●", 24, true); sw.setGravity(Gravity.CENTER);
            sw.setTextColor(c); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(42), dp(42));
            p.rightMargin = dp(4); colorsRow.addView(sw, p); swatches.add(sw);
            sw.setOnClickListener(v -> { color = c; eraser = false; cropMode = false;
                updateUI(); drawingView.invalidate(); });
        }
        scroll.addView(colorsRow); controls.addView(scroll);

        LinearLayout sizes = row();
        TextView thin = button("Тонкая", BLUE), medium = button("Средняя", PURPLE),
                thick = button("Толстая", PURPLE);
        addButton(sizes, thin); addButton(sizes, medium); addButton(sizes, thick);
        controls.addView(sizes, new LinearLayout.LayoutParams(-1, dp(40)));
        thin.setOnClickListener(v -> chooseBrush(4f));
        medium.setOnClickListener(v -> chooseBrush(8f));
        thick.setOnClickListener(v -> chooseBrush(22f));

        LinearLayout actions = row();
        TextView eraserButton = button("Ластик", 0xFF777788);
        undoButton = button("↶", BLUE); redoButton = button("↷", BLUE);
        TextView clear = button("Очистить", RED);
        addButton(actions, eraserButton); addButton(actions, undoButton);
        addButton(actions, redoButton); addButton(actions, clear);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(40)); ap.topMargin = dp(6);
        controls.addView(actions, ap);
        eraserButton.setOnClickListener(v -> { eraser = true; cropMode = false; brush = 30f; updateUI(); });
        undoButton.setOnClickListener(v -> { drawingView.undo(); updateUI(); });
        redoButton.setOnClickListener(v -> { drawingView.redo(); updateUI(); });
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Очистить рисунок?")
                .setMessage("Будут удалены нарисованные линии. Исходное изображение останется.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (d, w) -> { drawingView.clear(); updateUI(); })
                .show());

        cropButton = button("✂ Обрезка: выключена", PURPLE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(42)); cp.topMargin = dp(7);
        controls.addView(cropButton, cp);
        cropButton.setOnClickListener(v -> {
            cropMode = !cropMode;
            if (cropMode && shape == 0) shape = 1;
            cropPanel.setVisibility(cropMode ? View.VISIBLE : View.GONE);
            updateUI(); drawingView.invalidate();
        });
        cropPanel = new LinearLayout(this); cropPanel.setOrientation(1);
        cropPanel.setVisibility(View.GONE);
        LinearLayout shapes = row();
        TextView circle = button("Круг", BLUE), square = button("Квадрат", BLUE),
                round = button("Скругл.", BLUE);
        addButton(shapes, circle); addButton(shapes, square); addButton(shapes, round);
        cropPanel.addView(shapes, new LinearLayout.LayoutParams(-1, dp(40)));
        circle.setOnClickListener(v -> chooseShape(1));
        square.setOnClickListener(v -> chooseShape(2));
        round.setOnClickListener(v -> chooseShape(3));
        TextView sizeLabel = label("Размер области обрезки", 11, false);
        sizeLabel.setPadding(dp(4), dp(5), 0, 0); cropPanel.addView(sizeLabel);
        SeekBar seek = new SeekBar(this); seek.setMax(85); seek.setProgress(60);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                cropFraction = 0.15f + progress / 100f;
                drawingView.invalidate();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        cropPanel.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
        TextView cropHint = label("Перемещай область пальцем. Сохрани PNG для обрезки.", 11, false);
        cropHint.setTextColor(CodeBoxTheme.secondaryText(this));
        cropPanel.addView(cropHint);
        controls.addView(cropPanel);
        root.addView(controls); setContentView(root); updateUI();
    }
    private void chooseBrush(float size) {
        brush = size; eraser = false; cropMode = false;
        cropPanel.setVisibility(View.GONE); updateUI(); drawingView.invalidate();
    }
    private void chooseShape(int selected) {
        shape = selected; cropMode = true; updateUI(); drawingView.invalidate();
    }
    private void updateUI() {
        if (status != null) status.setText(cropMode ? "✂ Обрезка" :
                (eraser ? "Ластик • 30" : "Кисть • " + (int) brush));
        if (cropButton != null) cropButton.setText(cropMode ?
                "✂ Обрезка: " + (shape == 1 ? "круг" : shape == 2 ? "квадрат" : "скруглённый") :
                "✂ Обрезка: выключена");
        if (undoButton != null && drawingView != null)
            undoButton.setAlpha(drawingView.canUndo() ? 1f : 0.45f);
        if (redoButton != null && drawingView != null)
            redoButton.setAlpha(drawingView.canRedo() ? 1f : 0.45f);
        for (int i = 0; i < swatches.size(); i++) {
            boolean active = !eraser && !cropMode && palette[i] == color;
            GradientDrawable d = bg(active ? 0xFFE6DDFB : CodeBoxTheme.surface(this), 12);
            d.setStroke(dp(active ? 2 : 1), active ? PURPLE : 0xFF9999AA);
            swatches.get(i).setBackground(d);
        }
    }
    private void attemptExit() {
        if (drawingView == null || !drawingView.hasChanges()) { finish(); return; }
        new AlertDialog.Builder(this).setTitle("Выйти из редактора?")
                .setMessage("Несохранённые изменения будут потеряны.")
                .setPositiveButton("Сохранить", (d, w) -> saveDialog())
                .setNegativeButton("Не сохранять", (d, w) -> finish())
                .setNeutralButton("Отмена", null).show();
    }
    @Override public void onBackPressed() { attemptExit(); }
    private void saveDialog() {
        EditText input = new EditText(this); input.setSingleLine(true);
        String defaultName = "drawing.png";
        if (sourceFile != null) {
            String name = sourceFile.getName(); int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            defaultName = name + "_edited.png";
        }
        input.setText(defaultName); input.selectAll();
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), dp(8)); wrap.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Сохранить PNG")
                .setMessage("Оригинал не изменится. При обрезке углы будут прозрачными.")
                .setView(wrap).setPositiveButton("Сохранить", null)
                .setNegativeButton("Отмена", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) name += ".png";
                    if (!name.matches("[^/\\\\\\p{Cntrl}]+\\.png")
                            || name.equals(".png") || name.equals("..png")) {
                        input.setError("Недопустимое имя файла"); return;
                    }
                    try {
                        saveImage(name); dialog.dismiss();
                        Toast.makeText(this, "PNG сохранён в проект", Toast.LENGTH_SHORT).show();
                        finish();
                    } catch (IOException | RuntimeException e) { input.setError(e.getMessage()); }
                }));
        dialog.show();
    }
    private void saveImage(String name) throws IOException {
        File destination = new File(targetDirectory, name).getCanonicalFile();
        if (!destination.getParentFile().equals(targetDirectory))
            throw new IOException("Недопустимый путь файла");
        if (destination.exists()) throw new IOException("Файл уже существует");
        if (drawingView.getWidth() <= 0 || drawingView.getHeight() <= 0)
            throw new IOException("Холст ещё не готов");
        Bitmap result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(result);
            if (cropMode && shape != 0) {
                RectF rect = drawingView.cropRect();
                if (rect.width() <= 0) throw new IOException("Область обрезки пуста");
                canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
                Path mask = new Path();
                if (shape == 1) mask.addOval(new RectF(0, 0, SIZE, SIZE), Path.Direction.CW);
                else if (shape == 3) mask.addRoundRect(new RectF(0, 0, SIZE, SIZE),
                        SIZE * 0.18f, SIZE * 0.18f, Path.Direction.CW);
                else mask.addRect(new RectF(0, 0, SIZE, SIZE), Path.Direction.CW);
                canvas.save(); canvas.clipPath(mask);
                canvas.scale(SIZE / rect.width(), SIZE / rect.height());
                canvas.translate(-rect.left, -rect.top);
                drawingView.renderContent(canvas); canvas.restore();
            } else {
                canvas.drawColor(Color.WHITE);
                drawingView.renderToSquare(canvas);
            }
            try (FileOutputStream output = new FileOutputStream(destination)) {
                if (!result.compress(Bitmap.CompressFormat.PNG, 100, output))
                    throw new IOException("Ошибка сохранения PNG");
                output.flush();
            } catch (IOException e) { destination.delete(); throw e; }
        } finally { result.recycle(); }
    }
    private void error(String message) {
        Toast.makeText(this, message == null ? "Ошибка редактора" : message,
                Toast.LENGTH_LONG).show();
    }
    @Override protected void onDestroy() {
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) sourceBitmap.recycle();
        super.onDestroy();
    }
    private static final class Stroke {
        final Path path; final int color; final float width;
        Stroke(Path path, int color, float width) {
            this.path = path; this.color = color; this.width = width;
        }
    }
    private final class DrawingView extends View {
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path activePath = new Path();
        private final List<Stroke> strokes = new ArrayList<>();
        private final List<Stroke> undone = new ArrayList<>();
        private boolean drawing = false, draggingCrop = false;
        private float lastX, lastY;
        private int activeColor; private float activeWidth;
        DrawingView() {
            super(GraphicsEditorActivity.this); setBackgroundColor(Color.WHITE);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
        }
        RectF cropRect() {
            float size = Math.min(getWidth(), getHeight()) * cropFraction;
            float cx = cropX * getWidth(), cy = cropY * getHeight();
            cx = Math.max(size / 2f, Math.min(getWidth() - size / 2f, cx));
            cy = Math.max(size / 2f, Math.min(getHeight() - size / 2f, cy));
            return new RectF(cx - size / 2f, cy - size / 2f,
                    cx + size / 2f, cy + size / 2f);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); renderContent(canvas);
            if (cropMode) drawCropOverlay(canvas);
        }
        private void drawCropOverlay(Canvas canvas) {
            RectF rect = cropRect();
            overlayPaint.setStyle(Paint.Style.FILL);
            overlayPaint.setColor(0x77000000);
            Path outer = new Path(); outer.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            Path hole = new Path();
            if (shape == 1) hole.addOval(rect, Path.Direction.CW);
            else if (shape == 3) hole.addRoundRect(rect, rect.width() * 0.18f,
                    rect.height() * 0.18f, Path.Direction.CW);
            else hole.addRect(rect, Path.Direction.CW);
            outer.addPath(hole); outer.setFillType(Path.FillType.EVEN_ODD);
            canvas.drawPath(outer, overlayPaint);
            overlayPaint.setStyle(Paint.Style.STROKE);
            overlayPaint.setStrokeWidth(dp(2)); overlayPaint.setColor(Color.WHITE);
            canvas.drawPath(hole, overlayPaint);
            overlayPaint.setStyle(Paint.Style.FILL);
        }
        void renderContent(Canvas canvas) {
            canvas.drawColor(Color.WHITE);
            if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
                float factor = Math.min((float)getWidth() / sourceBitmap.getWidth(),
                        (float)getHeight() / sourceBitmap.getHeight());
                float w = sourceBitmap.getWidth() * factor;
                float h = sourceBitmap.getHeight() * factor;
                RectF rect = new RectF((getWidth() - w) / 2f, (getHeight() - h) / 2f,
                        (getWidth() + w) / 2f, (getHeight() + h) / 2f);
                canvas.drawBitmap(sourceBitmap, null, rect, imagePaint);
            }
            for (Stroke s : strokes) drawStroke(canvas, s.path, s.color, s.width);
            if (drawing) drawStroke(canvas, activePath, activeColor, activeWidth);
        }
        private void drawStroke(Canvas c, Path p, int col, float width) {
            strokePaint.setColor(col); strokePaint.setStrokeWidth(width);
            c.drawPath(p, strokePaint);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (cropMode) {
                        draggingCrop = true; lastX = x; lastY = y;
                    } else {
                        activePath.reset(); activePath.moveTo(x, y);
                        activeColor = eraser ? Color.WHITE : color;
                        activeWidth = brush; drawing = true; lastX = x; lastY = y;
                    }
                    invalidate(); return true;
                case MotionEvent.ACTION_MOVE:
                    if (cropMode && draggingCrop) {
                        cropX += (x - lastX) / Math.max(1f, getWidth());
                        cropY += (y - lastY) / Math.max(1f, getHeight());
                        cropX = Math.max(0f, Math.min(1f, cropX));
                        cropY = Math.max(0f, Math.min(1f, cropY));
                        lastX = x; lastY = y;
                    } else if (drawing) activePath.lineTo(x, y);
                    invalidate(); return true;
                case MotionEvent.ACTION_UP:
                    if (cropMode) draggingCrop = false;
                    else if (drawing) {
                        activePath.lineTo(x, y);
                        if (Math.abs(x - lastX) < 0.01f && Math.abs(y - lastY) < 0.01f)
                            activePath.lineTo(x + 0.1f, y + 0.1f);
                        strokes.add(new Stroke(new Path(activePath), activeColor, activeWidth));
                        undone.clear(); activePath.reset(); drawing = false;
                        updateUI();
                    }
                    invalidate(); return true;
                case MotionEvent.ACTION_CANCEL:
                    draggingCrop = false; drawing = false; activePath.reset();
                    invalidate(); return true;
            }
            return true;
        }
        boolean canUndo() { return !strokes.isEmpty(); }
        boolean canRedo() { return !undone.isEmpty(); }
        void undo() { if (canUndo()) undone.add(strokes.remove(strokes.size() - 1)); invalidate(); }
        void redo() { if (canRedo()) strokes.add(undone.remove(undone.size() - 1)); invalidate(); }
        void clear() { strokes.clear(); undone.clear(); activePath.reset(); drawing = false; invalidate(); }
        boolean hasChanges() { return !strokes.isEmpty() || drawing || cropMode; }
        void renderToSquare(Canvas canvas) {
            float factor = Math.min((float)SIZE / getWidth(), (float)SIZE / getHeight());
            float w = getWidth() * factor, h = getHeight() * factor;
            canvas.save(); canvas.translate((SIZE - w) / 2f, (SIZE - h) / 2f);
            canvas.scale(factor, factor); renderContent(canvas); canvas.restore();
        }
    }
}
