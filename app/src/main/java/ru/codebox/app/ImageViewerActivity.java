package ru.codebox.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private Bitmap bitmap;
    private float scale = 1.0f;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String projectName = getIntent()
                .getStringExtra("project_name");
        String relativePath = getIntent()
                .getStringExtra("file_path");

        if (projectName == null ||
                !projectName.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}")
                || relativePath == null) {
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

            if (!project.getParentFile().equals(projects)
                    || !project.isDirectory()) {
                throw new IOException("Проект не найден");
            }

            File image = new File(
                    project, relativePath
            ).getCanonicalFile();

            if (!image.getPath().startsWith(
                    project.getPath() + File.separator)
                    || !image.isFile()) {
                throw new IOException("Файл недоступен");
            }

            if (image.length() > 30L * 1024 * 1024) {
                throw new IOException(
                        "Изображение слишком большое");
            }

            BitmapFactory.Options options =
                    new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            BitmapFactory.decodeFile(
                    image.getAbsolutePath(), options);

            if (options.outWidth <= 0 ||
                    options.outHeight <= 0) {
                throw new IOException(
                        "Неверный формат изображения");
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;

            while (options.outWidth / options.inSampleSize > 2048
                    || options.outHeight / options.inSampleSize > 2048) {
                options.inSampleSize *= 2;
            }

            bitmap = BitmapFactory.decodeFile(
                    image.getAbsolutePath(), options);

            if (bitmap == null) {
                throw new IOException(
                        "Не удалось открыть изображение");
            }

            buildScreen(image.getName());

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Ошибка: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
            finish();
        }
    }

    private void buildScreen(String name) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(25, 25, 30));

        Button back = new Button(this);
        back.setText("← НАЗАД");
        back.setOnClickListener(v -> finish());

        root.addView(back);

        imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setBackgroundColor(Color.rgb(35, 35, 40));
        imageView.setScaleType(
                ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0, 1);

        root.addView(imageView, imageParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);

        Button minus = new Button(this);
        minus.setText("−");
        minus.setOnClickListener(v -> changeScale(0.8f));

        Button reset = new Button(this);
        reset.setText("100%");
        reset.setOnClickListener(v -> {
            scale = 1.0f;
            imageView.setScaleX(1.0f);
            imageView.setScaleY(1.0f);
        });

        Button plus = new Button(this);
        plus.setText("+");
        plus.setOnClickListener(v -> changeScale(1.25f));

        controls.addView(minus);
        controls.addView(reset);
        controls.addView(plus);

        root.addView(controls);

        setContentView(root);
        setTitle(name);
    }

    private void changeScale(float factor) {
        scale = Math.max(
                0.5f, Math.min(4.0f, scale * factor));

        imageView.setScaleX(scale);
        imageView.setScaleY(scale);
    }

    @Override
    protected void onDestroy() {
        if (imageView != null) {
            imageView.setImageDrawable(null);
        }

        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }

        super.onDestroy();
    }
}
