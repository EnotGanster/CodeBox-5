package ru.codebox.app;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeSyntaxHighlighter {

    private static final int KEYWORD =
            Color.rgb(120, 65, 190);

    private static final int STRING =
            Color.rgb(30, 145, 75);

    private static final int COMMENT =
            Color.rgb(125, 130, 140);

    private static final int NUMBER =
            Color.rgb(200, 105, 35);

    private static final int ANNOTATION =
            Color.rgb(35, 115, 190);

    private static final int XML_TAG =
            Color.rgb(55, 90, 200);

    private static final int XML_ATTRIBUTE =
            Color.rgb(170, 65, 135);

    private static final Pattern JAVA_KEYWORDS =
            Pattern.compile(
                    "\\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null|record|sealed|permits|var|yield)\\b"
            );

    private static final Pattern JAVA_NUMBERS =
            Pattern.compile(
                    "\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?)[fFdDlL]?\\b"
            );

    private static final Pattern JAVA_ANNOTATIONS =
            Pattern.compile(
                    "@[A-Za-z_$][A-Za-z0-9_$]*"
            );

    private static final Pattern JAVA_PROTECTED =
            Pattern.compile(
                    "//[^\\r\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)|\"(?:\\\\.|[^\"\\\\])*(?:\"|$)|'(?:\\\\.|[^'\\\\])*(?:'|$)"
            );

    private static final Pattern XML_TAGS =
            Pattern.compile(
                    "</?[A-Za-z_][A-Za-z0-9_.:-]*"
            );

    private static final Pattern XML_ATTRIBUTES =
            Pattern.compile(
                    "\\b[A-Za-z_][A-Za-z0-9_.:-]*(?=\\s*=)"
            );

    private static final Pattern XML_PROTECTED =
            Pattern.compile(
                    "<!--[\\s\\S]*?(?:-->|$)|\"[^\"]*(?:\"|$)|'[^']*(?:'|$)"
            );

    private CodeSyntaxHighlighter() {
    }

    public static void highlight(
            EditText editor,
            String fileName) {

        if (editor == null || fileName == null) {
            return;
        }

        Editable editable = editor.getText();

        if (editable == null) {
            return;
        }

        ForegroundColorSpan[] spans =
                editable.getSpans(
                        0,
                        editable.length(),
                        ForegroundColorSpan.class
                );

        for (ForegroundColorSpan span : spans) {
            editable.removeSpan(span);
        }

        String source = editable.toString();
        String name = fileName.toLowerCase(
                Locale.ROOT
        );

        boolean[] protectedChars =
                new boolean[source.length()];

        if (name.endsWith(".java")) {

            applyUnprotected(
                    editable, source,
                    JAVA_KEYWORDS, KEYWORD,
                    protectedChars
            );

            applyUnprotected(
                    editable, source,
                    JAVA_NUMBERS, NUMBER,
                    protectedChars
            );

            applyUnprotected(
                    editable, source,
                    JAVA_ANNOTATIONS, ANNOTATION,
                    protectedChars
            );

            applyProtected(
                    editable, source,
                    JAVA_PROTECTED,
                    protectedChars,
                    true
            );

        } else if (name.endsWith(".xml")) {

            applyUnprotected(
                    editable, source,
                    XML_TAGS, XML_TAG,
                    protectedChars
            );

            applyUnprotected(
                    editable, source,
                    XML_ATTRIBUTES, XML_ATTRIBUTE,
                    protectedChars
            );

            applyProtected(
                    editable, source,
                    XML_PROTECTED,
                    protectedChars,
                    false
            );
        }
    }

    private static void applyUnprotected(
            Editable text,
            String source,
            Pattern pattern,
            int color,
            boolean[] protectedChars) {

        Matcher matcher = pattern.matcher(source);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            boolean blocked = false;

            for (int i = start; i < end; i++) {
                if (protectedChars[i]) {
                    blocked = true;
                    break;
                }
            }

            if (!blocked && start < end) {
                text.setSpan(
                        new ForegroundColorSpan(color),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }

    private static void applyProtected(
            Editable text,
            String source,
            Pattern pattern,
            boolean[] protectedChars,
            boolean javaMode) {

        Matcher matcher = pattern.matcher(source);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            if (start >= end) {
                continue;
            }

            char first = source.charAt(start);

            boolean isComment = javaMode
                    ? first == '/'
                    : source.startsWith("<!--", start);

            int color = isComment
                    ? COMMENT
                    : STRING;

            for (int i = start; i < end; i++) {
                protectedChars[i] = true;
            }

            ForegroundColorSpan[] old =
                    text.getSpans(
                            start,
                            end,
                            ForegroundColorSpan.class
                    );

            for (ForegroundColorSpan span : old) {
                int spanStart =
                        text.getSpanStart(span);

                int spanEnd =
                        text.getSpanEnd(span);

                text.removeSpan(span);

                if (spanStart < start) {
                    text.setSpan(
                            new ForegroundColorSpan(
                                    span.getForegroundColor()
                            ),
                            spanStart,
                            start,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }

                if (spanEnd > end) {
                    text.setSpan(
                            new ForegroundColorSpan(
                                    span.getForegroundColor()
                            ),
                            end,
                            spanEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }

            text.setSpan(
                    new ForegroundColorSpan(color),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }
}
