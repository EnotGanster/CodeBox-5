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

/** Extended syntax highlighting for Java and XML. */
public final class CodeSyntaxHighlighter {

    private static final int KEYWORD = Color.rgb(184, 145, 255);
    private static final int STRING = Color.rgb(126, 220, 156);
    private static final int COMMENT = Color.rgb(142, 153, 172);
    private static final int NUMBER = Color.rgb(255, 183, 113);
    private static final int ANNOTATION = Color.rgb(116, 195, 255);
    private static final int TYPE = Color.rgb(100, 205, 220);
    private static final int CLASS_NAME = Color.rgb(255, 210, 125);
    private static final int METHOD = Color.rgb(130, 190, 255);
    private static final int CONSTANT = Color.rgb(255, 160, 190);
    private static final int XML_TAG = Color.rgb(126, 179, 255);
    private static final int XML_ATTRIBUTE = Color.rgb(255, 160, 205);
    private static final int XML_SPECIAL = Color.rgb(190, 160, 255);

    private static final Pattern JAVA_KEYWORDS = Pattern.compile(
            "\\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|exports|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|module|native|new|non-sealed|open|opens|package|permits|private|protected|provides|public|record|requires|return|sealed|short|static|strictfp|super|switch|synchronized|this|throw|throws|to|transient|transitive|try|uses|var|void|volatile|while|with|yield|true|false|null)\\b"
    );

    private static final Pattern JAVA_NUMBERS = Pattern.compile(
            "(?<![\\w$])(?:0[xX][0-9a-fA-F_]+(?:\\.[0-9a-fA-F_]*)?(?:[pP][+-]?[0-9_]+)?|0[bB][01_]+|(?:[0-9][0-9_]*)(?:\\.[0-9_]*)?(?:[eE][+-]?[0-9_]+)?)[fFdDlL]?(?![\\w$])"
    );

    private static final Pattern JAVA_ANNOTATIONS = Pattern.compile(
            "@[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
    );

    private static final Pattern JAVA_TYPES = Pattern.compile(
            "\\b(?:String|Object|Class|Void|Integer|Long|Short|Byte|Double|Float|Boolean|Character|Number|"
                    + "StringBuilder|StringBuffer|Math|System|Thread|Exception|RuntimeException|"
                    + "IOException|File|Path|List|ArrayList|Map|HashMap|Set|HashSet|Collection|"
                    + "Iterator|Optional|Date|Locale|Pattern|Matcher|"
                    + "Activity|Bundle|Intent|Context|View|TextView|EditText|Button|"
                    + "LinearLayout|RelativeLayout|FrameLayout|ScrollView|Toast|"
                    + "Canvas|Paint|Color|Bitmap|Drawable|"
                    + "OnClickListener|TextWatcher|Editable|"
                    + "RecyclerView|Adapter|Fragment|"
                    + "R)\\b"
    );

    private static final Pattern JAVA_CLASS_DECLARATION = Pattern.compile(
            "\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
    );

    private static final Pattern JAVA_METHODS = Pattern.compile(
            "(?<![\\w$])([A-Za-z_$][A-Za-z0-9_$]*)(?=\\s*\\()"
    );

    private static final Pattern JAVA_CONSTANTS = Pattern.compile(
            "\\b[A-Z][A-Z0-9_]{2,}\\b"
    );

    private static final Pattern XML_TAGS = Pattern.compile(
            "</?([A-Za-z_][A-Za-z0-9_.:-]*)"
    );

    private static final Pattern XML_ATTRIBUTES = Pattern.compile(
            "([A-Za-z_:][A-Za-z0-9_.:-]*)(?=\\s*=)"
    );

    private static final Pattern XML_ENTITIES = Pattern.compile(
            "&(?:[A-Za-z][A-Za-z0-9]+|#[0-9]+|#x[0-9a-fA-F]+);"
    );

    private static final Pattern XML_PUNCTUATION = Pattern.compile(
            "</|/>|>|<|="
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

        if (javaMode) {
            highlightJava(source, protectedChars, tokens);
        } else {
            highlightXml(source, protectedChars, tokens);
        }

        clearSpans(editable);

        for (Token token : tokens) {
            if (token.start < token.end
                    && token.end <= editable.length()) {

                editable.setSpan(
                        new ForegroundColorSpan(token.color),
                        token.start,
                        token.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }

    private static void highlightJava(
            String source,
            boolean[] mask,
            List<Token> tokens
    ) {
        scanJavaProtected(source, mask, tokens);

        scanPattern(source, JAVA_ANNOTATIONS,
                ANNOTATION, mask, tokens);

        scanPattern(source, JAVA_KEYWORDS,
                KEYWORD, mask, tokens);

        scanPattern(source, JAVA_NUMBERS,
                NUMBER, mask, tokens);

        scanPattern(source, JAVA_TYPES,
                TYPE, mask, tokens);

        scanGroupPattern(source, JAVA_CLASS_DECLARATION,
                1, CLASS_NAME, mask, tokens);

        scanPattern(source, JAVA_CONSTANTS,
                CONSTANT, mask, tokens);

        scanPattern(source, JAVA_METHODS,
                METHOD, mask, tokens);
    }

    private static void highlightXml(
            String source,
            boolean[] mask,
            List<Token> tokens
    ) {
        scanXmlProtected(source, mask, tokens);

        scanGroupPattern(source, XML_TAGS,
                1, XML_TAG, mask, tokens);

        scanGroupPattern(source, XML_ATTRIBUTES,
                1, XML_ATTRIBUTE, mask, tokens);

        scanPattern(source, XML_ENTITIES,
                XML_SPECIAL, mask, tokens);

        scanPattern(source, XML_PUNCTUATION,
                XML_SPECIAL, mask, tokens);
    }

    private static void clearSpans(Editable text) {
        ForegroundColorSpan[] spans =
                text.getSpans(
                        0,
                        text.length(),
                        ForegroundColorSpan.class
                );

        for (ForegroundColorSpan span : spans) {
            text.removeSpan(span);
        }
    }

    private static boolean isBlocked(
            boolean[] mask,
            int start,
            int end
    ) {
        for (int i = start; i < end; i++) {
            if (mask[i]) return true;
        }
        return false;
    }

    private static void mark(
            boolean[] mask,
            List<Token> tokens,
            int start,
            int end,
            int color
    ) {
        if (start < 0 || end > mask.length || start >= end) {
            return;
        }

        for (int i = start; i < end; i++) {
            mask[i] = true;
        }

        tokens.add(new Token(start, end, color));
    }

    private static void scanPattern(
            String source,
            Pattern pattern,
            int color,
            boolean[] mask,
            List<Token> tokens
    ) {
        Matcher matcher = pattern.matcher(source);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            if (!isBlocked(mask, start, end)) {
                mark(mask, tokens, start, end, color);
            }
        }
    }

    private static void scanGroupPattern(
            String source,
            Pattern pattern,
            int group,
            int color,
            boolean[] mask,
            List<Token> tokens
    ) {
        Matcher matcher = pattern.matcher(source);

        while (matcher.find()) {
            int start = matcher.start(group);
            int end = matcher.end(group);

            if (start >= 0 && !isBlocked(mask, start, end)) {
                mark(mask, tokens, start, end, color);
            }
        }
    }

    private static void scanJavaProtected(
            String source,
            boolean[] mask,
            List<Token> tokens
    ) {
        int length = source.length();
        int i = 0;

        while (i < length) {
            int start = i;
            char c = source.charAt(i);

            if (c == '/' && i + 1 < length
                    && source.charAt(i + 1) == '/') {

                i += 2;

                while (i < length
                        && source.charAt(i) != '\n'
                        && source.charAt(i) != '\r') {
                    i++;
                }

                mark(mask, tokens, start, i, COMMENT);

            } else if (c == '/' && i + 1 < length
                    && source.charAt(i + 1) == '*') {

                i += 2;

                while (i + 1 < length
                        && !(source.charAt(i) == '*'
                        && source.charAt(i + 1) == '/')) {
                    i++;
                }

                i = i + 1 < length ? i + 2 : length;

                mark(mask, tokens, start, i, COMMENT);

            } else if (c == '"' || c == '\'') {

                boolean textBlock = c == '"'
                        && i + 2 < length
                        && source.charAt(i + 1) == '"'
                        && source.charAt(i + 2) == '"';

                i += textBlock ? 3 : 1;

                while (i < length) {
                    if (source.charAt(i) == '\\') {
                        i = Math.min(length, i + 2);

                    } else if (textBlock
                            && i + 2 < length
                            && source.charAt(i) == '"'
                            && source.charAt(i + 1) == '"'
                            && source.charAt(i + 2) == '"') {

                        i += 3;
                        break;

                    } else if (!textBlock && source.charAt(i) == c) {
                        i++;
                        break;

                    } else {
                        i++;
                    }
                }

                mark(mask, tokens, start, i, STRING);

            } else {
                i++;
            }
        }
    }

    private static void scanXmlProtected(
            String source,
            boolean[] mask,
            List<Token> tokens
    ) {
        int length = source.length();
        int i = 0;

        while (i < length) {
            int start = i;

            if (source.startsWith("<!--", i)) {

                int end = source.indexOf("-->", i + 4);
                i = end < 0 ? length : end + 3;

                mark(mask, tokens, start, i, COMMENT);

            } else if (source.startsWith("<![CDATA[", i)) {

                int end = source.indexOf("]]>", i + 9);
                i = end < 0 ? length : end + 3;

                mark(mask, tokens, start, i, STRING);

            } else if (source.startsWith("<?", i)) {

                int end = source.indexOf("?>", i + 2);
                i = end < 0 ? length : end + 2;

                mark(mask, tokens, start, i, XML_SPECIAL);

            } else if (source.startsWith("<!DOCTYPE", i)
                    || source.startsWith("<!doctype", i)) {

                int end = source.indexOf(">", i + 9);
                i = end < 0 ? length : end + 1;

                mark(mask, tokens, start, i, COMMENT);

            } else if (source.charAt(i) == '"'
                    || source.charAt(i) == '\'') {

                char quote = source.charAt(i++);

                while (i < length
                        && source.charAt(i) != quote) {
                    i++;
                }

                if (i < length) i++;

                mark(mask, tokens, start, i, STRING);

            } else {
                i++;
            }
        }
    }
}
