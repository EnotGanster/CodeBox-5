package ru.codebox.app;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeSyntaxHighlighter {

    private static final int KEYWORD = Color.rgb(120, 65, 190);
    private static final int STRING = Color.rgb(35, 135, 75);
    private static final int COMMENT = Color.rgb(130, 135, 140);
    private static final int NUMBER = Color.rgb(190, 100, 35);
    private static final int ANNOTATION = Color.rgb(30, 120, 180);
    private static final int TAG = Color.rgb(65, 90, 190);
    private static final int ATTRIBUTE = Color.rgb(175, 75, 130);

    private static final Pattern JAVA_KEYWORDS =
            Pattern.compile(
                    "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null|record|sealed|permits|var|yield)\\b"
            );

    private static final Pattern JAVA_NUMBERS =
            Pattern.compile(
                    "\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?)[fFdDlL]?\\b"
            );

    private static final Pattern JAVA_ANNOTATIONS =
            Pattern.compile(
                    "@[A-Za-z_$][A-Za-z0-9_$]*"
            );

    private static final Pattern JAVA_STRINGS =
            Pattern.compile(
                    "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"
            );

    private static final Pattern JAVA_COMMENTS =
            Pattern.compile(
                    "//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/"
            );

    private static final Pattern XML_TAGS =
            Pattern.compile(
                    "</?[A-Za-z_][A-Za-z0-9_.:-]*"
            );

    private static final Pattern XML_ATTRIBUTES =
            Pattern.compile(
                    "\\b[A-Za-z_][A-Za-z0-9_.:-]*(?=\\s*=)"
            );

    private static final Pattern XML_STRINGS =
            Pattern.compile(
                    "\"[^\"]*\"|'[^']*'"
            );

    private static final Pattern XML_COMMENTS =
            Pattern.compile(
                    "<!--[\\s\\S]*?-->"
            );

    private CodeSyntaxHighlighter() {
    }

    public static void highlight(
            EditText editor,
            String fileName) {

        if (editor == null || fileName == null) {
            return;
        }

        Editable text = editor.getText();

        if (text == null) {
            return;
        }

        ForegroundColorSpan[] oldSpans =
                text.getSpans(
                        0,
                        text.length(),
                        ForegroundColorSpan.class
                );

        for (ForegroundColorSpan span : oldSpans) {
            text.removeSpan(span);
        }

        String source = text.toString();
        String name = fileName.toLowerCase(
                java.util.Locale.ROOT
        );

        if (name.endsWith(".java")) {
            apply(text, source, JAVA_KEYWORDS, KEYWORD);
            apply(text, source, JAVA_NUMBERS, NUMBER);
            apply(text, source, JAVA_ANNOTATIONS, ANNOTATION);
            apply(text, source, JAVA_STRINGS, STRING);
            apply(text, source, JAVA_COMMENTS, COMMENT);

        } else if (name.endsWith(".xml")) {
            apply(text, source, XML_TAGS, TAG);
            apply(text, source, XML_ATTRIBUTES, ATTRIBUTE);
            apply(text, source, XML_STRINGS, STRING);
            apply(text, source, XML_COMMENTS, COMMENT);
        }
    }

    private static void apply(
            Editable text,
            String source,
            Pattern pattern,
            int color) {

        Matcher matcher = pattern.matcher(source);

        while (matcher.find()) {
            if (matcher.start() < matcher.end()) {
                text.setSpan(
                        new ForegroundColorSpan(color),
                        matcher.start(),
                        matcher.end(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }
}
