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

/** Prepares a text AndroidManifest.xml for AAPT2 without changing the project. */
public final class CodeBoxApkManifestPreparer {
    private static final Pattern PACKAGE = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    private static final String ANDROID_NS =
            "http://schemas.android.com/apk/res/android";
    private static final long MAX_XML_BYTES = 1024 * 1024;

    private CodeBoxApkManifestPreparer() { }

    public static final class Result {
        public final File manifestFile;
        public final String applicationId;
        public final String report;

        private Result(File manifestFile, String applicationId, String report) {
            this.manifestFile = manifestFile;
            this.applicationId = applicationId;
            this.report = report;
        }
    }

    /** Uses the package declared in the project's manifest. */
    public static Result prepare(File projectDirectory, File workDirectory)
            throws IOException {
        return prepare(projectDirectory, workDirectory, null);
    }

    /**
     * An explicit applicationId is supported for existing callers. When null,
     * the package attribute of the source manifest is used instead.
     */
    public static Result prepare(File projectDirectory, File workDirectory,
                                 String applicationId) throws IOException {
        if (projectDirectory == null || workDirectory == null) {
            throw new IOException("Не указан проект или рабочий каталог");
        }
        if (applicationId != null && !PACKAGE.matcher(applicationId).matches()) {
            throw new IOException("Недопустимый applicationId: " + applicationId);
        }

        File project = projectDirectory.getCanonicalFile();
        if (!project.isDirectory()) {
            throw new IOException("Каталог проекта отсутствует");
        }
        File source = new File(project, "app/src/main/AndroidManifest.xml")
                .getCanonicalFile();
        if (!source.toPath().startsWith(project.toPath()) || !source.isFile()) {
            throw new IOException("AndroidManifest.xml не найден");
        }
        if (source.length() == 0 || source.length() > MAX_XML_BYTES) {
            throw new IOException("Недопустимый размер AndroidManifest.xml");
        }

        File work = workDirectory.getCanonicalFile();
        if (work.toPath().startsWith(project.toPath())) {
            throw new IOException("Рабочий каталог должен быть вне проекта");
        }
        if (!work.isDirectory() && !work.mkdirs()) {
            throw new IOException("Не удалось создать рабочий каталог");
        }
        File output = new File(work, "AndroidManifest.xml").getCanonicalFile();
        File temporary = new File(work, "AndroidManifest.xml.tmp").getCanonicalFile();
        if (!work.equals(output.getParentFile())
                || !work.equals(temporary.getParentFile())) {
            throw new IOException("Недопустимый выходной путь");
        }
        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Не удалось очистить временный файл");
        }

        boolean completed = false;
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            XmlSerializer serializer = Xml.newSerializer();
            String resolvedId = applicationId;
            boolean rootFound = false;
            boolean applicationFound = false;
            boolean launcherFound = false;
            int depth = 0;
            String activityName = null;
            boolean inIntentFilter = false;
            boolean mainAction = false;
            boolean launcherCategory = false;

            try (InputStream input = new FileInputStream(source);
                 OutputStream stream = new FileOutputStream(temporary)) {
                parser.setInput(input, "UTF-8");
                serializer.setOutput(stream, "UTF-8");
                serializer.startDocument("UTF-8", true);

                int event = parser.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.DOCDECL) {
                        throw new IOException("DOCTYPE запрещён в AndroidManifest.xml");
                    }
                    if (event == XmlPullParser.START_TAG) {
                        String name = parser.getName();
                        String namespace = parser.getNamespace();
                        if (depth == 0) {
                            if (rootFound || !"manifest".equals(name)) {
                                throw new IOException("Корневой элемент должен быть manifest");
                            }
                            rootFound = true;
                            String declared = parser.getAttributeValue(null, "package");
                            if (resolvedId == null) {
                                resolvedId = declared;
                            } else if (declared != null && !declared.isEmpty()
                                    && !resolvedId.equals(declared)) {
                                throw new IOException("package не совпадает с applicationId: "
                                        + declared);
                            }
                            if (resolvedId == null || !PACKAGE.matcher(resolvedId).matches()) {
                                throw new IOException("Укажите корректный package в манифесте");
                            }
                        }
                        if (depth == 1 && "application".equals(name)) {
                            applicationFound = true;
                        }
                        if (depth == 2 && "activity".equals(name)) {
                            activityName = parser.getAttributeValue(ANDROID_NS, "name");
                            if (activityName == null || activityName.trim().isEmpty()) {
                                throw new IOException("У activity отсутствует android:name");
                            }
                        }
                        if (depth == 3 && "intent-filter".equals(name)
                                && activityName != null) {
                            inIntentFilter = true;
                            mainAction = false;
                            launcherCategory = false;
                        }
                        if (inIntentFilter && depth == 4 && "action".equals(name)
                                && "android.intent.action.MAIN".equals(
                                parser.getAttributeValue(ANDROID_NS, "name"))) {
                            mainAction = true;
                        }
                        if (inIntentFilter && depth == 4 && "category".equals(name)
                                && "android.intent.category.LAUNCHER".equals(
                                parser.getAttributeValue(ANDROID_NS, "name"))) {
                            launcherCategory = true;
                        }
                        for (int i = parser.getNamespaceCount(depth);
                             i < parser.getNamespaceCount(depth + 1); i++) {
                            String prefix = parser.getNamespacePrefix(i);
                            String uri = parser.getNamespaceUri(i);
                            serializer.setPrefix(prefix == null ? "" : prefix,
                                    uri == null ? "" : uri);
                        }
                        serializer.startTag(namespace == null ? "" : namespace, name);
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            String attrName = parser.getAttributeName(i);
                            String attrNamespace = parser.getAttributeNamespace(i);
                            if (depth == 0 && "package".equals(attrName)
                                    && (attrNamespace == null || attrNamespace.isEmpty())) {
                                continue;
                            }
                            serializer.attribute(attrNamespace == null ? "" : attrNamespace,
                                    attrName, parser.getAttributeValue(i));
                        }
                        if (depth == 0) {
                            serializer.attribute("", "package", resolvedId);
                        }
                        depth++;
                    } else if (event == XmlPullParser.END_TAG) {
                        depth--;
                        if (depth < 0) {
                            throw new IOException("Неверная структура XML");
                        }
                        String name = parser.getName();
                        if (depth == 3 && "intent-filter".equals(name)
                                && inIntentFilter) {
                            if (mainAction && launcherCategory) {
                                launcherFound = true;
                            }
                            inIntentFilter = false;
                        }
                        if (depth == 2 && "activity".equals(name)) {
                            activityName = null;
                        }
                        serializer.endTag(parser.getNamespace() == null ? ""
                                : parser.getNamespace(), name);
                    } else if (event == XmlPullParser.TEXT) {
                        serializer.text(parser.getText());
                    } else if (event == XmlPullParser.CDSECT) {
                        serializer.cdsect(parser.getText());
                    } else if (event == XmlPullParser.ENTITY_REF) {
                        throw new IOException("XML-сущности не поддерживаются");
                    }
                    event = parser.nextToken();
                }
                if (!rootFound || !applicationFound || depth != 0) {
                    throw new IOException("Неполная структура AndroidManifest.xml");
                }
                serializer.endDocument();
                serializer.flush();
            }
            if (!temporary.isFile() || temporary.length() == 0
                    || temporary.length() > MAX_XML_BYTES) {
                throw new IOException("Подготовленный XML пуст или слишком велик");
            }
            if (output.exists() && !output.delete()) {
                throw new IOException("Не удалось заменить прежний XML");
            }
            if (!temporary.renameTo(output)) {
                throw new IOException("Не удалось сохранить подготовленный XML");
            }
            completed = true;
            return new Result(output, resolvedId,
                    "MANIFEST_PREPARED: OK\napplicationId: " + resolvedId
                            + "\nФайл: " + output.getAbsolutePath()
                            + "\nРазмер: " + output.length() + " байт"
                            + "\nLauncher activity: " + (launcherFound ? "найдена" : "не найдена")
                            + "\nФормат: текстовый XML для AAPT2");
        } catch (XmlPullParserException e) {
            throw new IOException("Ошибка XML-парсера: " + e.getMessage(), e);
        } finally {
            if (!completed && temporary.exists()) {
                temporary.delete();
            }
        }
    }
}
