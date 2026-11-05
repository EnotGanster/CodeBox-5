package ru.codebox.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class ImageViewerActivity extends Activity {

    private static final int MAX_IMAGE_SIZE = 2048;

    private Bitmap bitmap;
    private ZoomImageView imageView;
    private TextView scaleLabel;

    private float scale = 1.0f;

    private String projectName;
    private String relativePath;
    private File imageFile;

    private int dp(float value) {
        return CodeBoxTheme.dp(this, value);
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
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        projectName = getIntent()
                .getStringExtra("project_name");

        relativePath = getIntent()
                .getStringExtra("file_path");

        if (projectName == null
                || !projectName.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )
                || relativePath == null
                || relativePath.isEmpty()) {

            showError("Неверный путь изображения");
            finish();
            return;
        }

        try {
            File projects = new File(
                    getFilesDir(), "projects"
            ).getCanonicalFile();

            File project = new File(
                    projects, projectName
            ).getCanonicalFile();

            if (!project.getParentFile()
                    .equals(projects)
                    || !project.isDirectory()) {

                throw new IOException(
                        "Проект не найден"
                );
            }

            if (new File(relativePath).isAbsolute()) {
                throw new IOException(
                        "Недопустимый путь изображения"
                );
            }

            imageFile = new File(
                    project, relativePath
            ).getCanonicalFile();

            if (!imageFile.getPath().startsWith(
                    project.getPath()
                            + File.separator
            ) || !imageFile.isFile()) {

                throw new IOException(
                        "Файл недоступен"
                );
            }

            String lower = imageFile.getName()
                    .toLowerCase(Locale.ROOT);

            if (!lower.endsWith(".png")
                    && !lower.endsWith(".jpg")
                    && !lower.endsWith(".jpeg")
                    && !lower.endsWith(".webp")) {

                throw new IOException(
                        "Неподдерживаемый формат"
                );
            }

            if (imageFile.length()
                    > 30L * 1024 * 1024) {

                throw new IOException(
                        "Изображение слишком большое"
                );
            }

            BitmapFactory.Options options =
                    new BitmapFactory.Options();

            options.inJustDecodeBounds = true;

            BitmapFactory.decodeFile(
                    imageFile.getAbsolutePath(),
                    options
            );

            if (options.outWidth <= 0
                    || options.outHeight <= 0) {

                throw new IOException(
                        "Неверный формат изображения"
                );
            }

            int sampleSize = 1;

            while (options.outWidth / sampleSize
                    > MAX_IMAGE_SIZE
                    || options.outHeight / sampleSize
                    > MAX_IMAGE_SIZE) {

                sampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;

            bitmap = BitmapFactory.decodeFile(
                    imageFile.getAbsolutePath(),
                    options
            );

            if (bitmap == null) {
                throw new IOException(
                        "Не удалось открыть изображение"
                );
            }

            buildScreen();

        } catch (Exception e) {
            showError(
                    "Ошибка: " + e.getMessage()
            );
            finish();
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
        createImageArea(root);
        createBottomControls(root);

        setContentView(root);
    }

    private void createHeader(
            LinearLayout root) {

        LinearLayout header =
                new LinearLayout(this);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        header.setPadding(
                dp(12), dp(10),
                dp(12), dp(10)
        );

        header.setBackground(
                headerGradient()
        );

        TextView back = text(
                "‹", 28, true
        );

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
                v -> finish()
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
                imageFile.getName(),
                16,
                true
        );

        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);

        title.setEllipsize(
                TextUtils.TruncateAt.MIDDLE
        );

        labels.addView(title);

        TextView subtitle = text(
                bitmap.getWidth()
                        + " × "
                        + bitmap.getHeight()
                        + " пикс.",
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

        root.addView(header);
    }

    private void createImageArea(
            LinearLayout root) {

        FrameLayout frame =
                new FrameLayout(this);

        frame.setBackgroundColor(
                0xFF222228
        );

        imageView = new ZoomImageView();

        frame.addView(
                imageView,
                new FrameLayout.LayoutParams(
                        -1, -1
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -1, 0, 1
                );

        params.topMargin = dp(8);
        params.bottomMargin = dp(8);

        root.addView(frame, params);
    }

    private void createBottomControls(
            LinearLayout root) {

        LinearLayout bottom =
                new LinearLayout(this);

        bottom.setOrientation(
                LinearLayout.VERTICAL
        );

        bottom.setPadding(
                dp(12), dp(8),
                dp(12), dp(12)
        );

        bottom.setBackgroundColor(
                CodeBoxTheme.surface(this)
        );

        LinearLayout zoomRow =
                new LinearLayout(this);

        zoomRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        zoomRow.setGravity(Gravity.CENTER);

        TextView minus = controlButton("−");

        scaleLabel = controlButton("100%");

        TextView plus = controlButton("+");

        addZoomControl(zoomRow, minus);
        addZoomControl(zoomRow, scaleLabel);
        addZoomControl(zoomRow, plus);

        bottom.addView(
                zoomRow,
                new LinearLayout.LayoutParams(
                        -1, dp(44)
                )
        );

        minus.setOnClickListener(
                v -> changeScale(0.8f)
        );

        plus.setOnClickListener(
                v -> changeScale(1.25f)
        );

        scaleLabel.setOnClickListener(v -> {
            scale = 1.0f;
            imageView.invalidate();
            updateScaleLabel();
        });

        TextView edit = text(
                "✏ Редактировать изображение",
                13,
                true
        );

        edit.setGravity(Gravity.CENTER);

        CodeBoxTheme.styleColoredButton(
                edit,
                CodeBoxTheme.PURPLE
        );

        LinearLayout.LayoutParams editParams =
                new LinearLayout.LayoutParams(
                        -1, dp(44)
                );

        editParams.topMargin = dp(10);

        bottom.addView(edit, editParams);

        edit.setOnClickListener(
                v -> openEditor()
        );

        root.addView(bottom);
    }

    private TextView controlButton(
            String value) {

        TextView button = text(
                value, 16, true
        );

        button.setGravity(Gravity.CENTER);

        button.setBackground(
                rounded(
                        CodeBoxTheme.isDark(this)
                                ? 0xFF373443
                                : 0xFFEDEAF4,
                        12
                )
        );

        return button;
    }

    private void addZoomControl(
            LinearLayout parent,
            TextView button) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0, -1, 1
                );

        params.leftMargin = dp(3);
        params.rightMargin = dp(3);

        parent.addView(button, params);
    }

    private void changeScale(float factor) {

        scale = Math.max(
                0.5f,
                Math.min(4.0f, scale * factor)
        );

        imageView.invalidate();
        updateScaleLabel();
    }

    private void updateScaleLabel() {

        if (scaleLabel != null) {
            scaleLabel.setText(
                    Math.round(scale * 100) + "%"
            );
        }
    }

    private void openEditor() {

        Intent intent = new Intent(
                this,
                GraphicsEditorActivity.class
        );

        intent.putExtra(
                "project_name",
                projectName
        );

        intent.putExtra(
                "folder_path",
                new File(relativePath)
                        .getParent() == null
                        ? ""
                        : new File(relativePath)
                                .getParent()
        );

        intent.putExtra(
                "image_path",
                relativePath
        );

        startActivity(intent);
    }

    private void showError(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onDestroy() {

        if (bitmap != null
                && !bitmap.isRecycled()) {

            bitmap.recycle();
            bitmap = null;
        }

        super.onDestroy();
    }

    private final class ZoomImageView
            extends View {

        private final Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                                | Paint.FILTER_BITMAP_FLAG
                );

        ZoomImageView() {
            super(ImageViewerActivity.this);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (bitmap == null
                    || bitmap.isRecycled()) {
                return;
            }

            float width = getWidth();
            float height = getHeight();

            if (width <= 0 || height <= 0) {
                return;
            }

            float fitScale = Math.min(
                    width / bitmap.getWidth(),
                    height / bitmap.getHeight()
            );

            float drawWidth =
                    bitmap.getWidth()
                            * fitScale * scale;

            float drawHeight =
                    bitmap.getHeight()
                            * fitScale * scale;

            RectF destination = new RectF(
                    (width - drawWidth) / 2f,
                    (height - drawHeight) / 2f,
                    (width + drawWidth) / 2f,
                    (height + drawHeight) / 2f
            );

            canvas.save();

            canvas.clipRect(
                    0, 0,
                    getWidth(),
                    getHeight()
            );

            canvas.drawBitmap(
                    bitmap,
                    null,
                    destination,
                    paint
            );

            canvas.restore();
        }
    }
}
