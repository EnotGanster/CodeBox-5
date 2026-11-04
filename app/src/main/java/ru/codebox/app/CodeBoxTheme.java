package ru.codebox.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

public final class CodeBoxTheme {

    private static final String PREFS = "codebox_theme";
    private static final String DARK_MODE = "dark_mode";

    public static final int PURPLE =
            Color.rgb(123, 91, 178);

    public static final int BLUE =
            Color.rgb(72, 142, 187);

    public static final int GREEN =
            Color.rgb(104, 160, 112);

    public static final int RED =
            Color.rgb(210, 58, 62);

    public static final int TEXT_DARK =
            Color.rgb(42, 40, 52);

    public static final int TEXT_LIGHT =
            Color.rgb(245, 244, 250);

    private CodeBoxTheme() {
    }

    public static boolean isDark(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS, Context.MODE_PRIVATE
                );

        return prefs.getBoolean(DARK_MODE, false);
    }

    public static void toggleTheme(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS, Context.MODE_PRIVATE
                );

        prefs.edit()
                .putBoolean(
                        DARK_MODE,
                        !isDark(context)
                )
                .apply();
    }

    public static int background(Context context) {
        return isDark(context)
                ? Color.rgb(24, 24, 31)
                : Color.rgb(247, 247, 251);
    }

    public static int surface(Context context) {
        return isDark(context)
                ? Color.rgb(40, 40, 51)
                : Color.WHITE;
    }

    public static int textColor(Context context) {
        return isDark(context)
                ? TEXT_LIGHT
                : TEXT_DARK;
    }

    public static int secondaryText(Context context) {
        return isDark(context)
                ? Color.rgb(180, 178, 193)
                : Color.rgb(125, 124, 134);
    }

    public static int borderColor(Context context) {
        return isDark(context)
                ? Color.rgb(65, 64, 78)
                : Color.rgb(231, 230, 237);
    }

    public static int dp(
            Context context, float value) {

        return (int) (
                value * context.getResources()
                        .getDisplayMetrics().density
                        + 0.5f
        );
    }

    public static GradientDrawable rounded(
            int color,
            float radius,
            Context context) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);
        drawable.setCornerRadius(
                dp(context, radius)
        );

        return drawable;
    }

    public static GradientDrawable cardBackground(
            Context context) {

        GradientDrawable drawable = rounded(
                surface(context), 24, context
        );

        drawable.setStroke(
                dp(context, 1),
                borderColor(context)
        );

        return drawable;
    }

    public static void styleCard(View view) {
        Context context = view.getContext();

        view.setBackground(
                cardBackground(context)
        );

        view.setElevation(dp(context, 2));
    }

    public static void styleText(
            TextView view,
            float size,
            boolean bold) {

        view.setTextSize(size);
        view.setTextColor(
                textColor(view.getContext())
        );

        if (bold) {
            view.setTypeface(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
            );
        }
    }

    public static void styleColoredButton(
            TextView view,
            int color) {

        Context context = view.getContext();

        view.setTextColor(Color.WHITE);

        view.setBackground(
                rounded(color, 18, context)
        );

        view.setGravity(
                android.view.Gravity.CENTER
        );

        view.setPadding(
                dp(context, 16),
                dp(context, 14),
                dp(context, 16),
                dp(context, 14)
        );
    }
}
