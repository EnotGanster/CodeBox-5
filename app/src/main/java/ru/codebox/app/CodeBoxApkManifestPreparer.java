package ru.codebox.app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Prepares a text manifest for a future AAPT2 link step. Does not create an APK. */
public final class CodeBoxApkManifestPreparer {
    private static final Pattern PACKAGE = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

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

    /** Caller supplies applicationId from a trusted project configuration. */
    public static Result prepare(File projectDirectory, File workDirectory,
                                 String applicationId) throws IOException {
        if (projectDirectory == null || workDirectory == null || applicationId == null
                || !PACKAGE.matcher(applicationId).matches()) {
            throw new IOException("Неверный проект, рабочая папка или applicationId");
        }
        File project = projectDirectory.getCanonicalFile();
        File source = new File(project, "app/src/main/AndroidManifest.xml").getCanonicalFile();
        if (!source.toPath().startsWith(project.toPath()) || !source.isFile()) {
            throw new IOException("AndroidManifest.xml не найден в проекте");
        }
        if (source.length() > 1024 * 1024) {
            throw new IOException("AndroidManifest.xml превышает 1 МиБ");
        }
        File work = workDirectory.getCanonicalFile();
        if (work.toPath().startsWith(project.toPath())) {
            throw new IOException("Рабочая папка должна находиться вне исходного проекта");
        }
        if (!work.isDirectory() && !work.mkdirs()) {
            throw new IOException("Не удалось создать рабочую папку");
        }
        File output = new File(work, "AndroidManifest.xml").getCanonicalFile();
        if (!output.getParentFile().equals(work)) {
            throw new IOException("Недопустимый выходной путь");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document;
            try (InputStream input = Files.newInputStream(source.toPath())) {
                document = factory.newDocumentBuilder().parse(input);
            }
            Element manifest = document.getDocumentElement();
            if (manifest == null || !"manifest".equals(manifest.getTagName())) {
                throw new IOException("Корневой элемент XML должен быть manifest");
            }
            String existing = manifest.getAttribute("package").trim();
            if (!existing.isEmpty() && !applicationId.equals(existing)) {
                throw new IOException("package манифеста не совпадает с applicationId: " + existing);
            }
            manifest.setAttribute("package", applicationId);
            TransformerFactory transformers = TransformerFactory.newInstance();
            transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = transformers.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            try (OutputStream stream = Files.newOutputStream(output.toPath())) {
                transformer.transform(new DOMSource(document), new StreamResult(stream));
            }
            return new Result(output, applicationId,
                    "Манифест подготовлен: " + output.getAbsolutePath()
                            + "\napplicationId: " + applicationId
                            + "\nЭто текстовый XML для AAPT2, не бинарный манифест и не APK.");
        } catch (ParserConfigurationException | SAXException | TransformerException e) {
            throw new IOException("Ошибка обработки AndroidManifest.xml: " + e.getMessage(), e);
        }
    }
}
