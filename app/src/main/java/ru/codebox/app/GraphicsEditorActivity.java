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
    private static final int MAX_SOURCE_SIZE = 2048;

    private static final int PURPLE = 0xFF7650E8;
    private static final int BLUE = 0xFF487DDE;
    private static final int RED = 0xFFD94B5B;

    private File projectDirectory;
    private File targetDirectory;
    private File sourceFile;

    private Bitmap sourceBitmap;
    private DrawingView drawingView;

    private int selectedColor = Color.BLACK;
    private float brushSize = 8f;
    private boolean eraserMode = false;

    private TextView toolStatus;
    private TextView undoButton;
    private TextView redoButton;

    private final List<TextView> swatches =
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

    private GradientDrawable gradient() {
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
            String value, int size, boolean bold) {

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

        TextView view = text(value, 12, true);
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

        String imagePath = getIntent()
                .getStringExtra("image_path");

        if (projectName == null
                || !projectName.matches(
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

            checkInsideProject(targetDirectory);

            if (!targetDirectory.isDirectory()) {
                throw new IOException(
                        "Папка не существует"
                );
            }

            if (imagePath != null
                    && !imagePath.isEmpty()) {

                if (new File(imagePath).isAbsolute()) {
                    throw new IOException(
                            "Недопустимый путь изображения"
                    );
                }

                sourceFile = new File(
                        projectDirectory, imagePath
                ).getCanonicalFile();

                checkInsideProject(sourceFile);

                if (!sourceFile.isFile()) {
                    throw new IOException(
                            "Изображение не найдено"
                    );
                }

                loadSourceImage();
            }

        } catch (Exception e) {
            showError(e.getMessage());
            finish();
            return;
        }

        buildScreen();
    }

    private void checkInsideProject(File file)
            throws IOException {

        String root = projectDirectory
                .getCanonicalPath();

        String target = file.getCanonicalPath();

        if (!target.equals(root)
                && !target.startsWith(
                        root + File.separator
                )) {

            throw new IOException(
                    "Путь вне проекта"
            );
        }
    }

    private void loadSourceImage()
            throws IOException {

        String name = sourceFile.getName()
                .toLowerCase(Locale.ROOT);

        if (!name.endsWith(".png")
                && !name.endsWith(".jpg")
                && !name.endsWith(".jpeg")
                && !name.endsWith(".webp")) {

            throw new IOException(
                    "Неподдерживаемый формат"
            );
        }

        if (sourceFile.length()
                > 30L * 1024 * 1024) {

            throw new IOException(
                    "Изображение слишком большое"
            );
        }

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(
                sourceFile.getAbsolutePath(),
                options
        );

        if (options.outWidth <= 0
                || options.outHeight <= 0) {

            throw new IOException(
                    "Не удалось прочитать изображение"
            );
        }

        int sampleSize = 1;

        while (options.outWidth / sampleSize
                > MAX_SOURCE_SIZE
                || options.outHeight / sampleSize
                > MAX_SOURCE_SIZE) {

            sampleSize *= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;

        sourceBitmap = BitmapFactory.decodeFile(
                sourceFile.getAbsolutePath(),
                options
        );

        if (sourceBitmap == null) {
            throw new IOException(
                    "Не удалось открыть изображение"
            );
        }
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
        createCanvas(root);
        createPalette(root);
        createTools(root);

        setContentView(root);

        updateToolStatus();
        updatePalette();
        updateHistoryButtons();
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
                dp(12), dp(10),
                dp(12), dp(10)
        );

        header.setBackground(gradient());

        TextView back = text("‹", 28, true);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);

        back.setBackground(
                rounded(0x33FFFFFF, 12)
        );

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(40), dp(40)
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
                dp(10), 0, dp(6), 0
        );

        TextView title = text(
                sourceFile == null
                        ? "Новый рисунок"
                        : "Редактирование",
                17,
                true
        );

        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        labels.addView(title);

        TextView subtitle = text(
                "PNG • 1024 × 1024",
                10,
                false
        );

        subtitle.setTextColor(0xFFE8DDFF);
        labels.addView(subtitle);

        header.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        TextView save = text(
                "✓ PNG", 12, true
        );

        save.setTextColor(Color.WHITE);
        save.setGravity(Gravity.CENTER);

        save.setBackground(
                rounded(0x33FFFFFF, 12)
        );

        header.addView(
                save,
                new LinearLayout.LayoutParams(
                        dp(70), dp(40)
                )
        );

        save.setOnClickListener(
                v -> showSaveDialog()
        );

        root.addView(header);
    }

    private void createCanvas(
            LinearLayout root) {

        LinearLayout section =
                new LinearLayout(this);

        section.setOrientation(
                LinearLayout.VERTICAL
        );

        section.setPadding(
                dp(10), dp(8),
                dp(10), dp(6)
        );

        LinearLayout info =
                new LinearLayout(this);

        info.setOrientation(
                LinearLayout.HORIZONTAL
        );

        info.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = text(
                "ХОЛСТ", 11, true
        );

        label.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        info.addView(
                label,
                new LinearLayout.LayoutParams(
                        0, -2, 1
                )
        );

        toolStatus = text(
                "Кисть", 11, true
        );

        info.addView(toolStatus);
        section.addView(info);

        drawingView = new DrawingView();

        LinearLayout.LayoutParams canvasParams =
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                );

        canvasParams.topMargin = dp(6);

        section.addView(
                drawingView, canvasParams
        );

        root.addView(
                section,
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
                dp(10), dp(5),
                dp(10), dp(5)
        );

        TextView label = text(
                "ЦВЕТА", 11, true
        );

        label.setTextColor(
                CodeBoxTheme.secondaryText(this)
        );

        section.addView(label);

        HorizontalScrollView horizontal =
                new HorizontalScrollView(this);

        horizontal.setHorizontalScrollBarEnabled(
                false
        );

        LinearLayout palette =
                new LinearLayout(this);

        palette.setOrientation(
                LinearLayout.HORIZONTAL
        );

        palette.setPadding(
                0, dp(6), 0, dp(4)
        );

        swatches.clear();

        for (int color : colors) {

            TextView swatch =
                    text("●", 24, true);

            swatch.setTextColor(color);
            swatch.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            dp(44), dp(44)
                    );

            params.rightMargin = dp(5);

            palette.addView(swatch, params);
            swatches.add(swatch);

            swatch.setOnClickListener(v -> {
                selectedColor = color;
                eraserMode = false;

                updatePalette();
                updateToolStatus();
            });
        }

        horizontal.addView(palette);
        section.addView(horizontal);
        root.addView(section);
    }

    private void updatePalette() {

        for (int i = 0;
                i < swatches.size(); i++) {

            boolean selected =
                    !eraserMode
                    && selectedColor == colors[i];

            GradientDrawable background =
                    rounded(
                            selected
                                    ? 0xFFE6DDFB
                                    : CodeBoxTheme.surface(this),
                            12
                    );

            background.setStroke(
                    dp(selected ? 2 : 1),
                    selected
                            ? PURPLE
                            : 0xFF9999AA
            );

            swatches.get(i).setBackground(
                    background
            );
        }
    }

    private void createTools(
            LinearLayout root) {

        LinearLayout tools =
                new LinearLayout(this);

        tools.setOrientation(
                LinearLayout.VERTICAL
        );

        tools.setPadding(
                dp(8), dp(5),
                dp(8), dp(10)
        );

        LinearLayout sizes =
                new LinearLayout(this);

        sizes.setOrientation(
                LinearLayout.HORIZONTAL
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

        addTool(sizes, thin);
        addTool(sizes, medium);
        addTool(sizes, thick);

        tools.addView(
                sizes,
                new LinearLayout.LayoutParams(
                        -1, dp(42)
                )
        );

        thin.setOnClickListener(v ->
                chooseBrush(4f)
        );

        medium.setOnClickListener(v ->
                chooseBrush(8f)
        );

        thick.setOnClickListener(v ->
                chooseBrush(22f)
        );

        LinearLayout actions =
                new LinearLayout(this);

        actions.setOrientation(
                LinearLayout.HORIZONTAL
        );

        TextView eraser = button(
                "Ластик", 0xFF777788
        );

        undoButton = button(
                "↶", BLUE
        );

        redoButton = button(
                "↷", BLUE
        );

        TextView clear = button(
                "Очистить", RED
        );

        addTool(actions, eraser);
        addTool(actions, undoButton);
        addTool(actions, redoButton);
        addTool(actions, clear);

        LinearLayout.LayoutParams actionParams =
                new LinearLayout.LayoutParams(
                        -1, dp(42)
                );

        actionParams.topMargin = dp(7);

        tools.addView(actions, actionParams);

        eraser.setOnClickListener(v -> {
            eraserMode = true;
            brushSize = 30f;
            updateToolStatus();
            updatePalette();
        });

        undoButton.setOnClickListener(v -> {
            drawingView.undo();
            updateHistoryButtons();
        });

        redoButton.setOnClickListener(v -> {
            drawingView.redo();
            updateHistoryButtons();
        });

        clear.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Очистить рисунок?")
                        .setMessage(
                                "Нарисованные линии будут "
                                        + "удалены. Исходное "
                                        + "изображение сохранится."
                        )
                        .setNegativeButton(
                                "Отмена", null
                        )
                        .setPositiveButton(
                                "Очистить",
                                (dialog, which) -> {
                                    drawingView.clear();
                                    updateHistoryButtons();
                                }
                        )
                        .show()
        );

        root.addView(tools);
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

    private void chooseBrush(float size) {
        brushSize = size;
        eraserMode = false;
        updateToolStatus();
        updatePalette();
    }

    private void updateToolStatus() {

        if (toolStatus == null) {
            return;
        }

        if (eraserMode) {
            toolStatus.setText("Ластик • 30");
        } else {
            toolStatus.setText(
                    "Кисть • " + (int) brushSize
            );
        }

        toolStatus.setTextColor(
                CodeBoxTheme.PURPLE
        );
    }

    private void updateHistoryButtons() {

        if (drawingView == null
                || undoButton == null
                || redoButton == null) {
            return;
        }

        undoButton.setAlpha(
                drawingView.canUndo()
                        ? 1f : 0.45f
        );

        redoButton.setAlpha(
                drawingView.canRedo()
                        ? 1f : 0.45f
        );
    }

    private void attemptExit() {

        if (drawingView == null
                || !drawingView.hasChanges()) {
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Выйти из редактора?")
                .setMessage(
                        "Несохранённые изменения "
                                + "будут потеряны."
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

        String defaultName = "drawing.png";

        if (sourceFile != null) {
            String name = sourceFile.getName();

            int dot = name.lastIndexOf('.');

            if (dot > 0) {
                name = name.substring(0, dot);
            }

            defaultName = name + "_edited.png";
        }

        input.setText(defaultName);
        input.selectAll();

        LinearLayout wrapper =
                new LinearLayout(this);

        wrapper.setPadding(
                dp(20), dp(8),
                dp(20), dp(8)
        );

        wrapper.addView(input);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Сохранить PNG"
                        )
                        .setMessage(
                                "Оригинал не изменится."
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
                                "Изображение сохранено",
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
        ).getCanonicalFile();

        if (!destination.getParentFile()
                .equals(targetDirectory)) {

            throw new IOException(
                    "Недопустимый путь файла"
            );
        }

        if (destination.exists()) {
            throw new IOException(
                    "Файл уже существует"
            );
        }

        Bitmap outputBitmap =
                Bitmap.createBitmap(
                        IMAGE_SIZE,
                        IMAGE_SIZE,
                        Bitmap.Config.ARGB_8888
                );

        try {
            Canvas canvas =
                    new Canvas(outputBitmap);

            canvas.drawColor(Color.WHITE);

            drawingView.renderTo(canvas);

            try (FileOutputStream output =
                         new FileOutputStream(
                                 destination
                         )) {

                if (!outputBitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output
                )) {

                    throw new IOException(
                            "Ошибка сохранения PNG"
                    );
                }

                output.flush();

            } catch (IOException e) {
                destination.delete();
                throw e;
            }

        } finally {
            outputBitmap.recycle();
        }
    }

    private void showError(String message) {
        Toast.makeText(
                this,
                message == null
                        ? "Ошибка редактора"
                        : message,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onDestroy() {

        if (sourceBitmap != null
                && !sourceBitmap.isRecycled()) {

            sourceBitmap.recycle();
            sourceBitmap = null;
        }

        super.onDestroy();
    }

    private final class DrawingView extends View {

        private final Paint imagePaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                                | Paint.FILTER_BITMAP_FLAG
                );

        private final Paint strokePaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path activePath =
                new Path();

        private final List<Stroke> strokes =
                new ArrayList<>();

        private final List<Stroke> undone =
                new ArrayList<>();

        private boolean drawing = false;
        private int activeColor;
        private float activeWidth;

        DrawingView() {
            super(GraphicsEditorActivity.this);

            setBackgroundColor(Color.WHITE);

            strokePaint.setStyle(
                    Paint.Style.STROKE
            );

            strokePaint.setStrokeCap(
                    Paint.Cap.ROUND
            );

            strokePaint.setStrokeJoin(
                    Paint.Join.ROUND
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawContent(canvas, getWidth(), getHeight());
        }

        private void drawContent(
                Canvas canvas,
                int width,
                int height) {

            if (width <= 0 || height <= 0) {
                return;
            }

            canvas.drawColor(Color.WHITE);

            if (sourceBitmap != null
                    && !sourceBitmap.isRecycled()) {

                RectF destination = imageBounds(
                        width, height
                );

                canvas.drawBitmap(
                        sourceBitmap,
                        null,
                        destination,
                        imagePaint
                );
            }

            for (Stroke stroke : strokes) {
                drawStroke(canvas, stroke);
            }

            if (drawing) {
                strokePaint.setColor(activeColor);
                strokePaint.setStrokeWidth(
                        activeWidth
                );

                canvas.drawPath(
                        activePath,
                        strokePaint
                );
            }
        }

        private RectF imageBounds(
                int width,
                int height) {

            float imageWidth = sourceBitmap.getWidth();
            float imageHeight = sourceBitmap.getHeight();

            float factor = Math.min(
                    (float) width / imageWidth,
                    (float) height / imageHeight
            );

            float w = imageWidth * factor;
            float h = imageHeight * factor;

            return new RectF(
                    (width - w) / 2f,
                    (height - h) / 2f,
                    (width + w) / 2f,
                    (height + h) / 2f
            );
        }

        private void drawStroke(
                Canvas canvas,
                Stroke stroke) {

            strokePaint.setColor(stroke.color);
            strokePaint.setStrokeWidth(stroke.width);

            canvas.drawPath(
                    stroke.path,
                    strokePaint
            );
        }

        @Override
        public boolean onTouchEvent(
                MotionEvent event) {

            float x = event.getX();
            float y = event.getY();

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:

                    activePath.reset();
                    activePath.moveTo(x, y);

                    activeColor = eraserMode
                            ? Color.WHITE
                            : selectedColor;

                    activeWidth = brushSize;
                    drawing = true;

                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:

                    if (!drawing) {
                        return true;
                    }

                    activePath.lineTo(x, y);
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP:

                    if (!drawing) {
                        return true;
                    }

                    activePath.lineTo(x, y);

                    strokes.add(
                            new Stroke(
                                    new Path(activePath),
                                    activeColor,
                                    activeWidth
                            )
                    );

                    undone.clear();

                    activePath.reset();
                    drawing = false;

                    invalidate();
                    updateHistoryButtons();
                    return true;

                case MotionEvent.ACTION_CANCEL:

                    activePath.reset();
                    drawing = false;
                    invalidate();
                    return true;
            }

            return true;
        }

        boolean canUndo() {
            return !strokes.isEmpty();
        }

        boolean canRedo() {
            return !undone.isEmpty();
        }

        void undo() {

            if (strokes.isEmpty()) {
                return;
            }

            undone.add(
                    strokes.remove(
                            strokes.size() - 1
                    )
            );

            invalidate();
        }

        void redo() {

            if (undone.isEmpty()) {
                return;
            }

            strokes.add(
                    undone.remove(
                            undone.size() - 1
                    )
            );

            invalidate();
        }

        void clear() {
            strokes.clear();
            undone.clear();
            activePath.reset();
            drawing = false;
            invalidate();
        }

        boolean hasChanges() {
            return !strokes.isEmpty() || drawing;
        }

        void renderTo(Canvas canvas) {

            if (getWidth() <= 0
                    || getHeight() <= 0) {
                return;
            }

            canvas.save();

            float factor = Math.min(
                    (float) IMAGE_SIZE / getWidth(),
                    (float) IMAGE_SIZE / getHeight()
            );

            float scaledWidth =
                    getWidth() * factor;

            float scaledHeight =
                    getHeight() * factor;

            canvas.translate(
                    (IMAGE_SIZE - scaledWidth) / 2f,
                    (IMAGE_SIZE - scaledHeight) / 2f
            );

            canvas.scale(factor, factor);

            drawContent(
                    canvas,
                    getWidth(),
                    getHeight()
            );

            canvas.restore();
        }

        private final class Stroke {

            final Path path;
            final int color;
            final float width;

            Stroke(
                    Path path,
                    int color,
                    float width) {

                this.path = path;
                this.color = color;
                this.width = width;
            }
        }
    }
}
