
package ru.codebox.app;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;

/**
 * CodeBox 5 - Android manifest preparation.
 *
 * Uses Android's native XML Pull Parser.
 * Does not create binary XML or APK.
 */
public final class CodeBoxApkManifestPreparer {

    private static final Pattern PACKAGE = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"
    );

    private static final String ANDROID_NS =
            "http://schemas.android.com/apk/res/android";

    private static final long MAX_XML_BYTES = 1024 * 1024;

    private CodeBoxApkManifestPreparer() { }

    public static final class Result {
        public final File manifestFile;
        public final String applicationId;
        public final String report;

        private Result(
                File manifestFile,
                String applicationId,
                String report
        ) {
            this.manifestFile = manifestFile;
            this.applicationId = applicationId;
            this.report = report;
        }
    }

    public static Result prepare(
            File projectDirectory,
            File workDirectory,
            String applicationId
    ) throws IOException {

        if (projectDirectory == null
                || workDirectory == null
                || applicationId == null
                || !PACKAGE.matcher(applicationId).matches()) {
            throw new IOException(
                    "Неверный проект, рабочая папка или applicationId"
            );
        }

        File project = projectDirectory.getCanonicalFile();

        if (!project.isDirectory()) {
            throw new IOException("Каталог проекта отсутствует");
        }

        File source = new File(
                project,
                "app/src/main/AndroidManifest.xml"
        ).getCanonicalFile();

        if (!source.toPath().startsWith(project.toPath())
                || !source.isFile()) {
            throw new IOException(
                    "AndroidManifest.xml не найден"
            );
        }

        if (source.length() == 0
                || source.length() > MAX_XML_BYTES) {
            throw new IOException(
                    "Недопустимый размер AndroidManifest.xml"
            );
        }

        File work = workDirectory.getCanonicalFile();

        if (work.toPath().startsWith(project.toPath())) {
            throw new IOException(
                    "Рабочий каталог должен быть вне проекта"
            );
        }

        if (!work.isDirectory() && !work.mkdirs()) {
            throw new IOException(
                    "Не удалось создать рабочий каталог"
            );
        }

        File output = new File(
                work,
                "AndroidManifest.xml"
        ).getCanonicalFile();

        if (!work.equals(output.getParentFile())) {
            throw new IOException("Недопустимый выходной путь");
        }

        File temporary = new File(
                work,
                "AndroidManifest.xml.tmp"
        ).getCanonicalFile();

        if (!work.equals(temporary.getParentFile())) {
            throw new IOException("Недопустимый временный путь");
        }

        if (temporary.exists() && !temporary.delete()) {
            throw new IOException(
                    "Не удалось очистить временный файл"
            );
        }

        boolean completed = false;

        try {
            XmlPullParser parser = Xml.newPullParser();

            parser.setFeature(
                    XmlPullParser.FEATURE_PROCESS_NAMESPACES,
                    true
            );

            XmlSerializer serializer = Xml.newSerializer();

            try (InputStream input = new FileInputStream(source);
                 OutputStream stream = new FileOutputStream(temporary)) {

                parser.setInput(input, "UTF-8");
                serializer.setOutput(stream, "UTF-8");
                serializer.startDocument("UTF-8", true);

                int event = parser.getEventType();
                int depth = 0;
                boolean rootFound = false;
                boolean applicationFound = false;

                while (event != XmlPullParser.END_DOCUMENT) {

                    if (event == XmlPullParser.DOCDECL) {
                        throw new IOException(
                                "DOCTYPE запрещён в AndroidManifest.xml"
                        );
                    }

                    if (event == XmlPullParser.START_TAG) {
                        String name = parser.getName();
                        String namespace = parser.getNamespace();

                        if (depth == 0) {
                            if (!"manifest".equals(name)) {
                                throw new IOException(
                                        "Корневой элемент должен быть manifest"
                                );
                            }
                            rootFound = true;

                            String existing =
                                    parser.getAttributeValue(null, "package");

                            if (existing != null
                                    && !existing.isEmpty()
                                    && !applicationId.equals(existing)) {
                                throw new IOException(
                                        "package не совпадает с applicationId: "
                                        + existing
                                );
                            }
                        }

                        if (depth == 1
                                && "application".equals(name)) {
                            applicationFound = true;
                        }

                        for (int i = 0;
                             i < parser.getNamespaceCount(depth + 1);
                             i++) {

                            if (i < parser.getNamespaceCount(depth)) {
                                continue;
                            }

                            String prefix = parser.getNamespacePrefix(i);
                            String uri = parser.getNamespaceUri(i);

                            serializer.setPrefix(
                                    prefix == null ? "" : prefix,
                                    uri == null ? "" : uri
                            );
                        }

                        serializer.startTag(
                                namespace == null ? "" : namespace,
                                name
                        );

                        for (int i = 0;
                             i < parser.getAttributeCount();
                             i++) {

                            String attrName = parser.getAttributeName(i);
                            String attrNamespace =
                                    parser.getAttributeNamespace(i);

                            if (depth == 0
                                    && "package".equals(attrName)
                                    && (attrNamespace == null
                                    || attrNamespace.isEmpty())) {
                                continue;
                            }

                            serializer.attribute(
                                    attrNamespace == null
                                            ? "" : attrNamespace,
                                    attrName,
                                    parser.getAttributeValue(i)
                            );
                        }

                        if (depth == 0) {
                            serializer.attribute(
                                    "",
                                    "package",
                                    applicationId
                            );
                        }

                        depth++;

                    } else if (event == XmlPullParser.END_TAG) {

                        depth--;

                        serializer.endTag(
                                parser.getNamespace() == null
                                        ? "" : parser.getNamespace(),
                                parser.getName()
                        );

                    } else if (event == XmlPullParser.TEXT) {

                        serializer.text(parser.getText());

                    } else if (event == XmlPullParser.CDSECT) {

                        serializer.cdsect(parser.getText());

                    } else if (event == XmlPullParser.ENTITY_REF) {

                        throw new IOException(
                                "XML-сущности не поддерживаются"
                        );
                    }

                    event = parser.nextToken();
                }

                if (!rootFound || !applicationFound || depth != 0) {
                    throw new IOException(
                            "Неполная структура AndroidManifest.xml"
                    );
                }

                serializer.endDocument();
                serializer.flush();
            }

            if (!temporary.isFile()
                    || temporary.length() == 0) {
                throw new IOException(
                        "Подготовленный XML пуст"
                );
            }

            if (output.exists() && !output.delete()) {
                throw new IOException(
                        "Не удалось заменить прежний XML"
                );
            }

            if (!temporary.renameTo(output)) {
                throw new IOException(
                        "Не удалось сохранить подготовленный XML"
                );
            }

            completed = true;

            return new Result(
                    output,
                    applicationId,
                    "MANIFEST_PREPARED: OK\n"
                    + "applicationId: " + applicationId
                    + "\nФайл: " + output.getAbsolutePath()
                    + "\nРазмер: " + output.length() + " байт"
                    + "\nФормат: текстовый XML для AAPT2"
            );

        } catch (XmlPullParserException e) {
            throw new IOException(
                    "Ошибка XML-парсера: " + e.getMessage(),
                    e
            );
        } finally {
            if (!completed && temporary.exists()) {
                temporary.delete();
            }
        }
    }
}
