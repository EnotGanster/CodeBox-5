package ru.codebox.app;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Syntax highlighting for Java and XML without changing the source text. */
public final class CodeSyntaxHighlighter {

    private static final int KEYWORD = Color.rgb(184, 145, 255);
    private static final int STRING = Color.rgb(126, 220, 156);
    private static final int COMMENT = Color.rgb(142, 153, 172);
    private static final int NUMBER = Color.rgb(255, 183, 113);
    private static final int ANNOTATION = Color.rgb(116, 195, 255);
    private static final int XML_TAG = Color.rgb(126, 179, 255);
    private static final int XML_ATTRIBUTE = Color.rgb(255, 160, 205);

    private static final Pattern JAVA_KEYWORDS = Pattern.compile(
            "\\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|exports|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|module|native|new|non-sealed|open|opens|package|permits|private|protected|provides|public|record|requires|return|sealed|short|static|strictfp|super|switch|synchronized|this|throw|throws|to|transient|transitive|try|uses|var|void|volatile|while|with|yield|true|false|null)\\b"
    );
    private static final Pattern JAVA_NUMBERS = Pattern.compile(
            "(?<![\\w$])(?:0[xX][0-9a-fA-F_]+(?:\\.[0-9a-fA-F_]*)?(?:[pP][+-]?[0-9_]+)?|0[bB][01_]+|(?:[0-9][0-9_]*)(?:\\.[0-9_]*)?(?:[eE][+-]?[0-9_]+)?)[fFdDlL]?(?![\\w$])"
    );
    private static final Pattern JAVA_ANNOTATIONS = Pattern.compile(
            "@[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
    );
    private static final Pattern XML_TAGS = Pattern.compile(
            "</?[A-Za-z_][A-Za-z0-9_.:-]*"
    );
    private static final Pattern XML_ATTRIBUTES = Pattern.compile(
            "[A-Za-z_:][A-Za-z0-9_.:-]*(?=\\s*=)"
    );

    private CodeSyntaxHighlighter() { }

    private static final class Token {
        final int start;
        final int end;
        final int color;

        Token(int start, int end, int color) {
            this.start = start;
            this.end = end;
            this.color = color;
        }
    }

    public static void highlight(EditText editor, String fileName) {
        if (editor == null || fileName == null) return;
        Editable editable = editor.getText();
        if (editable == null) return;

        String name = fileName.toLowerCase(Locale.ROOT);
        boolean javaMode = name.endsWith(".java");
        boolean xmlMode = name.endsWith(".xml");
        if (!javaMode && !xmlMode) {
            clearSpans(editable);
            return;
        }

        String source = editable.toString();
        boolean[] protectedChars = new boolean[source.length()];
        List<Token> tokens = new ArrayList<>();

        // Mark comments and quoted strings first so keywords inside them are ignored.
        if (javaMode) {
            scanJavaProtected(source, protectedChars, tokens);
            scanPattern(source, JAVA_KEYWORDS, KEYWORD, protectedChars, tokens);
            scanPattern(source, JAVA_NUMBERS, NUMBER, protectedChars, tokens);
            scanPattern(source, JAVA_ANNOTATIONS, ANNOTATION, protectedChars, tokens);
        } else {
            scanXmlProtected(source, protectedChars, tokens);
            scanPattern(source, XML_TAGS, XML_TAG, protectedChars, tokens);
            scanPattern(source, XML_ATTRIBUTES, XML_ATTRIBUTE, protectedChars, tokens);
        }

        clearSpans(editable);
        for (Token token : tokens) {
            if (token.start < token.end && token.end <= editable.length()) {
                editable.setSpan(new ForegroundColorSpan(token.color),
                        token.start, token.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void clearSpans(Editable text) {
        ForegroundColorSpan[] spans = text.getSpans(0, text.length(),
                ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) text.removeSpan(span);
    }

    private static void scanPattern(String source, Pattern pattern, int color,
                                    boolean[] protectedChars, List<Token> tokens) {
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
            if (!blocked && start < end) tokens.add(new Token(start, end, color));
        }
    }

    private static void protect(boolean[] protectedChars, List<Token> tokens,
                                int start, int end, int color) {
        for (int i = start; i < end; i++) protectedChars[i] = true;
        tokens.add(new Token(start, end, color));
    }

    private static void scanJavaProtected(String s, boolean[] mask,
                                          List<Token> tokens) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            int start = i;
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                i += 2;
                while (i < n && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++;
                protect(mask, tokens, start, i, COMMENT);
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i = i + 1 < n ? i + 2 : n;
                protect(mask, tokens, start, i, COMMENT);
            } else if (c == '"' || c == '\'') {
                boolean textBlock = c == '"' && i + 2 < n
                        && s.charAt(i + 1) == '"' && s.charAt(i + 2) == '"';
                i += textBlock ? 3 : 1;
                while (i < n) {
                    if (s.charAt(i) == '\\') {
                        i = Math.min(n, i + 2);
                    } else if (textBlock && i + 2 < n && s.charAt(i) == '"'
                            && s.charAt(i + 1) == '"' && s.charAt(i + 2) == '"') {
                        i += 3;
                        break;
                    } else if (!textBlock && s.charAt(i) == c) {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
                protect(mask, tokens, start, i, STRING);
            } else {
                i++;
            }
        }
    }

    private static void scanXmlProtected(String s, boolean[] mask,
                                         List<Token> tokens) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            int start = i;
            if (s.startsWith("<!--", i)) {
                int end = s.indexOf("-->", i + 4);
                i = end < 0 ? n : end + 3;
                protect(mask, tokens, start, i, COMMENT);
            } else if (s.startsWith("<![CDATA[", i)) {
                int end = s.indexOf("]]>", i + 9);
                i = end < 0 ? n : end + 3;
                protect(mask, tokens, start, i, STRING);
            } else if (s.charAt(i) == '"' || s.charAt(i) == '\'') {
                char quote = s.charAt(i++);
                while (i < n && s.charAt(i) != quote) i++;
                if (i < n) i++;
                protect(mask, tokens, start, i, STRING);
            } else {
                i++;
            }
        }
    }
}
