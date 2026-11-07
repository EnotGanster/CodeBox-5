
package ru.codebox.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.security.auth.x500.X500Principal;

/**
 * CodeBox 5 - Android App Bundle JAR signer.
 *
 * Android Keystore RSA + SHA-256 + CMS/PKCS#7.
 *
 * Implements JAR signature files:
 *
 * META-INF/MANIFEST.MF
 * META-INF/CODEBOX.SF
 * META-INF/CODEBOX.RSA
 *
 * Includes SHA-256 digests of individual
 * manifest sections in CODEBOX.SF.
 *
 * Development/test signing only.
 *
 * Run on a background thread.
 */
public final class CodeBoxAabSigner {

    private static final String KEY_ALIAS =
            "codebox5_test_aab_signing";

    private static final String KEYSTORE_TYPE =
            "AndroidKeyStore";

    private static final String SIGNATURE_ALGORITHM =
            "SHA256withRSA";

    private static final String DIGEST_ALGORITHM =
            "SHA-256";

    private static final String MANIFEST_NAME =
            "META-INF/MANIFEST.MF";

    private static final String SF_NAME =
            "META-INF/CODEBOX.SF";

    private static final String RSA_NAME =
            "META-INF/CODEBOX.RSA";

    private static final int BUFFER_SIZE = 8192;

    private static final byte[] CRLF =
            new byte[]{13, 10};

    private CodeBoxAabSigner() {
    }

    public static final class Result {

        public final boolean signed;
        public final File aabFile;
        public final String report;

        private Result(
                boolean signed,
                File aabFile,
                String report
        ) {
            this.signed = signed;
            this.aabFile = aabFile;
            this.report = report;
        }
    }

    private static final class ManifestData {

        final byte[] fullBytes;
        final byte[] mainBytes;

        final Map<String, byte[]> sections;

        ManifestData(
                byte[] fullBytes,
                byte[] mainBytes,
                Map<String, byte[]> sections
        ) {
            this.fullBytes = fullBytes;
            this.mainBytes = mainBytes;
            this.sections = sections;
        }
    }

    public static Result sign(
            Context context,
            File unsignedAab,
            String projectName
    ) {

        StringBuilder log =
                new StringBuilder();

        log.append("CodeBox 5 - AAB Signer\n");
        log.append("========================\n");

        File output = null;

        try {

            validateInputs(
                    context,
                    unsignedAab,
                    projectName
            );

            File outputDir = new File(
                    context.getCacheDir(),
                    "aab-signed-" + projectName
            );

            if (!outputDir.isDirectory()
                    && !outputDir.mkdirs()) {

                throw new IOException(
                        "Не удалось создать каталог AAB"
                );
            }

            output = new File(
                    outputDir,
                    projectName + "-signed.aab"
            );

            log.append("\n--- Исходный AAB ---\n");

            log.append("Файл: ")
                    .append(unsignedAab.getAbsolutePath())
                    .append('\n');

            log.append("Размер: ")
                    .append(unsignedAab.length())
                    .append(" байт\n");

            log.append("\n--- Ключ подписи ---\n");

            KeyStore keyStore =
                    KeyStore.getInstance(
                            KEYSTORE_TYPE
                    );

            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_ALIAS)) {

                createTestKey();

                log.append(
                        "Создан новый тестовый RSA-ключ\n"
                );

                keyStore.load(null);

            } else {

                log.append(
                        "Используется существующий RSA-ключ\n"
                );
            }

            PrivateKey privateKey =
                    (PrivateKey) keyStore.getKey(
                            KEY_ALIAS,
                            null
                    );

            Certificate certificate =
                    keyStore.getCertificate(
                            KEY_ALIAS
                    );

            if (privateKey == null) {

                throw new IOException(
                        "Закрытый ключ недоступен"
                );
            }

            if (!(certificate
                    instanceof X509Certificate)) {

                throw new IOException(
                        "Отсутствует сертификат X.509"
                );
            }

            X509Certificate x509 =
                    (X509Certificate) certificate;

            log.append("Алгоритм: ")
                    .append(SIGNATURE_ALGORITHM)
                    .append('\n');

            log.append("Сертификат: ")
                    .append(
                            x509.getSubjectX500Principal()
                                    .getName()
                    )
                    .append('\n');

            List<String> entryNames =
                    collectEntryNames(
                            unsignedAab
                    );

            if (entryNames.isEmpty()) {

                throw new IOException(
                        "В AAB отсутствуют файлы"
                );
            }

            log.append("\n--- JAR Manifest ---\n");

            ManifestData manifest =
                    buildManifest(
                            unsignedAab,
                            entryNames
                    );

            log.append("Записей: ")
                    .append(entryNames.size())
                    .append('\n');

            log.append("MANIFEST.MF: подготовлен\n");

            log.append("Размер: ")
                    .append(manifest.fullBytes.length)
                    .append(" байт\n");

            log.append("\n--- Signature File ---\n");

            byte[] signatureFileBytes =
                    buildSignatureFile(
                            manifest,
                            entryNames
                    );

            log.append("CODEBOX.SF: подготовлен\n");

            log.append("Подписанных секций: ")
                    .append(entryNames.size())
                    .append('\n');

            log.append("Размер: ")
                    .append(signatureFileBytes.length)
                    .append(" байт\n");

            log.append("\n--- CMS / PKCS#7 ---\n");

            byte[] cmsBytes =
                    createCmsSignature(
                            signatureFileBytes,
                            privateKey,
                            x509
                    );

            if (cmsBytes.length == 0) {

                throw new IOException(
                        "CMS-подпись пуста"
                );
            }

            log.append("CMS SignedData: создан\n");

            log.append("Размер блока: ")
                    .append(cmsBytes.length)
                    .append(" байт\n");

            log.append("\n--- Упаковка AAB ---\n");

            if (output.exists()
                    && !output.delete()) {

                throw new IOException(
                        "Не удалось удалить старый AAB"
                );
            }

            writeSignedAab(
                    unsignedAab,
                    output,
                    entryNames,
                    manifest.fullBytes,
                    signatureFileBytes,
                    cmsBytes
            );

            log.append("AAB сформирован\n");

            log.append("Размер: ")
                    .append(output.length())
                    .append(" байт\n");

            log.append("\n--- Проверка подписи ---\n");

            verifySignedJar(
                    output,
                    entryNames,
                    x509
            );

            log.append(
                    "JAR verification: PASSED\n"
            );

            log.append(
                    "Все файлы подтверждены сертификатом\n"
            );

            log.append("\n--- Результат ---\n");

            log.append("Статус: SUCCEEDED\n");

            log.append("Файл: ")
                    .append(output.getAbsolutePath())
                    .append('\n');

            log.append("Размер: ")
                    .append(output.length())
                    .append(" байт\n");

            log.append(
                    "Подпись: JAR SHA256withRSA / CMS\n"
            );

            log.append(
                    "Ключ: TEST ONLY\n"
            );

            return new Result(
                    true,
                    output,
                    log.toString()
            );

        } catch (Throwable error) {

            if (output != null
                    && output.exists()) {

                output.delete();
            }

            log.append("\n--- Ошибка ---\n");

            log.append(
                    error.getClass().getName()
            );

            log.append(": ")
                    .append(error.getMessage())
                    .append('\n');

            log.append("Статус: FAILED\n");

            return new Result(
                    false,
                    null,
                    log.toString()
            );
        }
    }

    private static List<String> collectEntryNames(
            File sourceFile
    ) throws IOException {

        List<String> names =
                new ArrayList<>();

        Set<String> seen =
                new HashSet<>();

        try (ZipFile zip =
                     new ZipFile(sourceFile)) {

            Enumeration<? extends ZipEntry> entries =
                    zip.entries();

            while (entries.hasMoreElements()) {

                ZipEntry entry =
                        entries.nextElement();

                String name =
                        entry.getName();

                if (!isSafeEntryName(name)) {

                    throw new IOException(
                            "Недопустимое имя ZIP-записи: "
                                    + name
                    );
                }

                if (entry.isDirectory()) {
                    continue;
                }

                if (name.regionMatches(
                        true,
                        0,
                        "META-INF/",
                        0,
                        9
                )) {

                    // Remove previous signing metadata.
                    continue;
                }

                if (!seen.add(name)) {

                    throw new IOException(
                            "Повторяющаяся ZIP-запись: "
                                    + name
                    );
                }

                names.add(name);
            }
        }

        Collections.sort(names);

        return names;
    }

    /**
     * Build a manifest with exact CRLF bytes.
     *
     * Every file section is saved separately
     * so CODEBOX.SF can hash those exact bytes.
     */
    private static ManifestData buildManifest(
            File sourceFile,
            List<String> entryNames
    ) throws Exception {

        ByteArrayOutputStream full =
                new ByteArrayOutputStream();

        ByteArrayOutputStream main =
                new ByteArrayOutputStream();

        writeHeader(
                main,
                "Manifest-Version",
                "1.0"
        );

        writeHeader(
                main,
                "Created-By",
                "CodeBox 5"
        );

        writeBlankLine(main);

        byte[] mainBytes =
                main.toByteArray();

        full.write(mainBytes);

        Map<String, byte[]> sections =
                new HashMap<>();

        try (ZipFile zip =
                     new ZipFile(sourceFile)) {

            for (String name : entryNames) {

                ZipEntry entry =
                        zip.getEntry(name);

                if (entry == null) {

                    throw new IOException(
                            "ZIP-запись исчезла: "
                                    + name
                    );
                }

                byte[] contentDigest =
                        digestEntry(
                                zip,
                                entry
                        );

                ByteArrayOutputStream section =
                        new ByteArrayOutputStream();

                writeHeader(
                        section,
                        "Name",
                        name
                );

                writeHeader(
                        section,
                        "SHA-256-Digest",
                        base64(contentDigest)
                );

                writeBlankLine(section);

                byte[] sectionBytes =
                        section.toByteArray();

                sections.put(
                        name,
                        sectionBytes
                );

                full.write(sectionBytes);
            }
        }

        return new ManifestData(
                full.toByteArray(),
                mainBytes,
                sections
        );
    }

    /**
     * Build CODEBOX.SF with:
     *
     * SHA-256-Digest-Manifest
     * SHA-256-Digest-Manifest-Main-Attributes
     *
     * and a SHA-256 digest for every
     * individual manifest section.
     */
    private static byte[] buildSignatureFile(
            ManifestData manifest,
            List<String> entryNames
    ) throws Exception {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        writeHeader(
                output,
                "Signature-Version",
                "1.0"
        );

        writeHeader(
                output,
                "Created-By",
                "CodeBox 5"
        );

        writeHeader(
                output,
                "SHA-256-Digest-Manifest",
                base64(
                        sha256(
                                manifest.fullBytes
                        )
                )
        );

        writeHeader(
                output,
                "SHA-256-Digest-Manifest-Main-Attributes",
                base64(
                        sha256(
                                manifest.mainBytes
                        )
                )
        );

        writeBlankLine(output);

        for (String name : entryNames) {

            byte[] section =
                    manifest.sections.get(name);

            if (section == null) {

                throw new IOException(
                        "Отсутствует секция манифеста: "
                                + name
                );
            }

            writeHeader(
                    output,
                    "Name",
                    name
            );

            writeHeader(
                    output,
                    "SHA-256-Digest",
                    base64(
                            sha256(section)
                    )
            );

            writeBlankLine(output);
        }

        return output.toByteArray();
    }

    /**
     * Write a JAR manifest header using
     * UTF-8, CRLF and 72-byte physical lines.
     *
     * Continuation lines begin with a space.
     */
    private static void writeHeader(
            ByteArrayOutputStream output,
            String name,
            String value
    ) throws IOException {

        if (name == null
                || value == null
                || name.isEmpty()) {

            throw new IOException(
                    "Некорректный заголовок манифеста"
            );
        }

        if (name.indexOf('\r') >= 0
                || name.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\0') >= 0) {

            throw new IOException(
                    "Недопустимый символ в манифесте"
            );
        }

        byte[] bytes =
                (name + ": " + value).getBytes(
                        StandardCharsets.UTF_8
                );

        int offset = 0;
        int firstLimit = 72;

        while (offset < bytes.length) {

            int limit =
                    offset == 0
                            ? firstLimit
                            : 71;

            int count =
                    Math.min(
                            limit,
                            bytes.length - offset
                    );

            if (offset > 0) {

                output.write(' ');
            }

            output.write(
                    bytes,
                    offset,
                    count
            );

            output.write(CRLF);

            offset += count;
        }
    }

    private static void writeBlankLine(
            ByteArrayOutputStream output
    ) throws IOException {

        output.write(CRLF);
    }

    private static byte[] sha256(
            byte[] bytes
    ) throws Exception {

        return MessageDigest.getInstance(
                DIGEST_ALGORITHM
        ).digest(bytes);
    }

    private static void writeSignedAab(
            File sourceFile,
            File outputFile,
            List<String> entryNames,
            byte[] manifestBytes,
            byte[] signatureFileBytes,
            byte[] cmsBytes
    ) throws IOException {

        try (
                ZipFile source =
                        new ZipFile(sourceFile);

                JarOutputStream target =
                        new JarOutputStream(
                                new FileOutputStream(
                                        outputFile
                                )
                        )
        ) {

            addBytes(
                    target,
                    MANIFEST_NAME,
                    manifestBytes
            );

            addBytes(
                    target,
                    SF_NAME,
                    signatureFileBytes
            );

            addBytes(
                    target,
                    RSA_NAME,
                    cmsBytes
            );

            byte[] buffer =
                    new byte[BUFFER_SIZE];

            for (String name : entryNames) {

                ZipEntry original =
                        source.getEntry(name);

                if (original == null) {

                    throw new IOException(
                            "ZIP-запись исчезла: "
                                    + name
                    );
                }

                JarEntry destination =
                        new JarEntry(name);

                target.putNextEntry(
                        destination
                );

                try (
                        InputStream input =
                                source.getInputStream(
                                        original
                                )
                ) {

                    int count;

                    while ((count = input.read(buffer))
                            != -1) {

                        target.write(
                                buffer,
                                0,
                                count
                        );
                    }
                }

                target.closeEntry();
            }
        }
    }

    private static void validateInputs(
            Context context,
            File source,
            String projectName
    ) throws IOException {

        if (context == null) {

            throw new IOException(
                    "Context отсутствует"
            );
        }

        if (projectName == null
                || !projectName.matches(
                        "[A-Za-z][A-Za-z0-9_]{0,49}"
                )) {

            throw new IOException(
                    "Недопустимое имя проекта"
            );
        }

        if (source == null
                || !source.isFile()
                || source.length() == 0) {

            throw new IOException(
                    "Исходный AAB не найден"
            );
        }
    }

    private static void createTestKey()
            throws Exception {

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_RSA,
                        KEYSTORE_TYPE
                );

        KeyGenParameterSpec specification =
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN
                                | KeyProperties.PURPOSE_VERIFY
                )
                        .setKeySize(2048)
                        .setDigests(
                                KeyProperties.DIGEST_SHA256
                        )
                        .setSignaturePaddings(
                                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1
                        )
                        .setCertificateSubject(
                                new X500Principal(
                                        "CN=CodeBox 5 Test Key"
                                )
                        )
                        .build();

        generator.initialize(
                specification
        );

        generator.generateKeyPair();
    }

    /**
     * Create detached CMS SignedData
     * for CODEBOX.SF.
     */
    private static byte[] createCmsSignature(
            byte[] signatureFile,
            PrivateKey privateKey,
            X509Certificate certificate
    ) throws Exception {

        BouncyCastleProvider bcProvider =
                new BouncyCastleProvider();

        ContentSigner contentSigner =
                new JcaContentSignerBuilder(
                        SIGNATURE_ALGORITHM
                ).build(privateKey);

        DigestCalculatorProvider digestProvider =
                new JcaDigestCalculatorProviderBuilder()
                        .setProvider(
                                bcProvider
                        )
                        .build();

        JcaSignerInfoGeneratorBuilder signerBuilder =
                new JcaSignerInfoGeneratorBuilder(
                        digestProvider
                );

        CMSSignedDataGenerator generator =
                new CMSSignedDataGenerator();

        generator.addSignerInfoGenerator(
                signerBuilder.build(
                        contentSigner,
                        certificate
                )
        );

        generator.addCertificates(
                new JcaCertStore(
                        Collections.singletonList(
                                certificate
                        )
                )
        );

        CMSProcessableByteArray content =
                new CMSProcessableByteArray(
                        signatureFile
                );

        CMSSignedData signedData =
                generator.generate(
                        content,
                        false
                );

        return signedData.getEncoded();
    }

    /**
     * Verify every signed content entry.
     *
     * The platform JAR verifier must attach
     * the expected certificate after the
     * entire entry has been read.
     */
    private static void verifySignedJar(
            File signedAab,
            List<String> expectedNames,
            X509Certificate expectedCertificate
    ) throws Exception {

        if (!signedAab.isFile()
                || signedAab.length() == 0) {

            throw new IOException(
                    "Подписанный AAB отсутствует"
            );
        }

        try (
                JarFile jar =
                        new JarFile(
                                signedAab,
                                true
                        )
        ) {

            if (jar.getEntry(MANIFEST_NAME) == null
                    || jar.getEntry(SF_NAME) == null
                    || jar.getEntry(RSA_NAME) == null) {

                throw new IOException(
                        "Файлы JAR-подписи отсутствуют"
                );
            }

            byte[] buffer =
                    new byte[BUFFER_SIZE];

            for (String name : expectedNames) {

                JarEntry entry =
                        jar.getJarEntry(name);

                if (entry == null) {

                    throw new IOException(
                            "Отсутствует файл: "
                                    + name
                    );
                }

                try (
                        InputStream input =
                                jar.getInputStream(entry)
                ) {

                    while (input.read(buffer) != -1) {
                        // Reading to EOF triggers
                        // JAR verification.
                    }
                }

                Certificate[] certificates =
                        entry.getCertificates();

                if (certificates == null
                        || certificates.length == 0) {

                    throw new SecurityException(
                            "Файл не подтверждён подписью: "
                                    + name
                    );
                }

                boolean matchingCertificate =
                        false;

                for (Certificate certificate :
                        certificates) {

                    if (MessageDigest.isEqual(
                            certificate.getEncoded(),
                            expectedCertificate.getEncoded()
                    )) {

                        matchingCertificate = true;
                        break;
                    }
                }

                if (!matchingCertificate) {

                    throw new SecurityException(
                            "Неожиданный сертификат: "
                                    + name
                    );
                }
            }
        }
    }

    private static byte[] digestEntry(
            ZipFile zip,
            ZipEntry entry
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        DIGEST_ALGORITHM
                );

        byte[] buffer =
                new byte[BUFFER_SIZE];

        try (
                InputStream input =
                        zip.getInputStream(entry)
        ) {

            int count;

            while ((count = input.read(buffer))
                    != -1) {

                digest.update(
                        buffer,
                        0,
                        count
                );
            }
        }

        return digest.digest();
    }

    private static void addBytes(
            JarOutputStream output,
            String name,
            byte[] data
    ) throws IOException {

        JarEntry entry =
                new JarEntry(name);

        output.putNextEntry(entry);

        output.write(data);

        output.closeEntry();
    }

    private static String base64(
            byte[] data
    ) {

        return Base64.getEncoder()
                .encodeToString(data);
    }

    private static boolean isSafeEntryName(
            String name
    ) {

        if (name == null
                || name.isEmpty()
                || name.startsWith("/")
                || name.contains("\\")
                || name.indexOf('\0') >= 0) {

            return false;
        }

        String[] parts =
                name.split("/");

        for (String part : parts) {

            if ("..".equals(part)) {
                return false;
            }
        }

        return true;
    }
}
