package ru.codebox.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.security.auth.x500.X500Principal;

/**
 * CodeBox 5 - AAB Signer.
 *
 * Creates a test signing key in Android Keystore.
 *
 * Signs Android App Bundles using JAR signing
 * (SHA256withRSA).
 *
 * This is intended for development/testing.
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

    private static final int BUFFER_SIZE = 8192;

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

    public static Result sign(
            Context context,
            File unsignedAab,
            String projectName
    ) {

        StringBuilder log = new StringBuilder(
                "CodeBox 5 - AAB Signer\n"
        );

        File output = null;

        try {

            if (context == null) {
                throw new IOException(
                        "Context отсутствует"
                );
            }

            if (projectName == null ||
                    !projectName.matches(
                            "[A-Za-z][A-Za-z0-9_]{0,49}"
                    )) {
                throw new IOException(
                        "Недопустимое имя проекта"
                );
            }

            if (unsignedAab == null ||
                    !unsignedAab.isFile() ||
                    unsignedAab.length() == 0) {
                throw new IOException(
                        "Исходный AAB отсутствует"
                );
            }

            File outputDir = new File(
                    context.getCacheDir(),
                    "aab-signed-" + projectName
            );

            if (!outputDir.isDirectory() &&
                    !outputDir.mkdirs()) {
                throw new IOException(
                        "Не удалось создать каталог"
                );
            }

            output = new File(
                    outputDir,
                    projectName + "-signed.aab"
            );

            log.append("Исходный AAB: ")
                    .append(unsignedAab.getAbsolutePath())
                    .append('\n');

            log.append("Размер: ")
                    .append(unsignedAab.length())
                    .append(" байт\n");

            log.append("\n--- Android Keystore ---\n");

            KeyStore keyStore = KeyStore.getInstance(
                    KEYSTORE_TYPE
            );

            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_ALIAS)) {

                log.append(
                        "Создание нового тестового RSA-ключа\n"
                );

                KeyPairGenerator generator =
                        KeyPairGenerator.getInstance(
                                KeyProperties.KEY_ALGORITHM_RSA,
                                KEYSTORE_TYPE
                        );

                KeyGenParameterSpec spec =
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

                generator.initialize(spec);

                KeyPair keyPair =
                        generator.generateKeyPair();

                log.append(
                        "RSA-ключ создан: OK\n"
                );

            } else {

                log.append(
                        "Существующий тестовый ключ найден\n"
                );
            }

            PrivateKey privateKey =
                    (PrivateKey) keyStore.getKey(
                            KEY_ALIAS,
                            null
                    );

            Certificate certificate =
                    keyStore.getCertificate(KEY_ALIAS);

            if (privateKey == null ||
                    certificate == null) {
                throw new IOException(
                        "Ключ или сертификат недоступен"
                );
            }

            log.append("Алгоритм: ")
                    .append(SIGNATURE_ALGORITHM)
                    .append('\n');

            log.append("Сертификат: ")
                    .append(certificate.getType())
                    .append('\n');

            log.append("\n--- Подготовка JAR-подписи ---\n");

            Manifest manifest = new Manifest();

            Attributes mainAttributes =
                    manifest.getMainAttributes();

            mainAttributes.putValue(
                    "Manifest-Version",
                    "1.0"
            );

            mainAttributes.putValue(
                    "Created-By",
                    "CodeBox 5"
            );

            List<String> entryNames =
                    new ArrayList<>();

            try (ZipFile zip = new ZipFile(unsignedAab)) {

                java.util.Enumeration<? extends ZipEntry>
                        entries = zip.entries();

                while (entries.hasMoreElements()) {

                    ZipEntry entry =
                            entries.nextElement();

                    if (entry.isDirectory()) {
                        continue;
                    }

                    String name = entry.getName();

                    if (name.startsWith("META-INF/")) {
                        continue;
                    }

                    entryNames.add(name);

                    MessageDigest digest =
                            MessageDigest.getInstance(
                                    DIGEST_ALGORITHM
                            );

                    try (InputStream input =
                                 zip.getInputStream(entry)) {

                        byte[] buffer =
                                new byte[BUFFER_SIZE];

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

                    Attributes attributes =
                            new Attributes();

                    attributes.putValue(
                            "SHA-256-Digest",
                            Base64.getEncoder()
                                    .encodeToString(
                                            digest.digest()
                                    )
                    );

                    manifest.getEntries().put(
                            name,
                            attributes
                    );
                }
            }

            Collections.sort(entryNames);

            java.io.ByteArrayOutputStream
                    manifestBuffer =
                    new java.io.ByteArrayOutputStream();

            manifest.write(manifestBuffer);

            byte[] manifestBytes =
                    manifestBuffer.toByteArray();

            MessageDigest manifestDigest =
                    MessageDigest.getInstance(
                            DIGEST_ALGORITHM
                    );

            byte[] manifestHash =
                    manifestDigest.digest(
                            manifestBytes
                    );

            String manifestHashBase64 =
                    Base64.getEncoder()
                            .encodeToString(
                                    manifestHash
                            );

            String signatureFileText =
                    "Signature-Version: 1.0\r\n"
                            + "Created-By: CodeBox 5\r\n"
                            + "SHA-256-Digest-Manifest: "
                            + manifestHashBase64
                            + "\r\n\r\n";

            byte[] signatureFileBytes =
                    signatureFileText.getBytes(
                            StandardCharsets.UTF_8
                    );

            Signature signature =
                    Signature.getInstance(
                            SIGNATURE_ALGORITHM
                    );

            signature.initSign(privateKey);

            signature.update(
                    signatureFileBytes
            );

            byte[] signatureBytes =
                    signature.sign();

            log.append("Manifest: создан\n");
            log.append("Signature file: создан\n");
            log.append("RSA signature: создана\n");

            log.append("\n--- Формирование AAB ---\n");

            if (output.exists() &&
                    !output.delete()) {
                throw new IOException(
                        "Не удалось удалить старый AAB"
                );
            }

            try (
                    ZipFile source =
                            new ZipFile(unsignedAab);

                    JarOutputStream target =
                            new JarOutputStream(
                                    new FileOutputStream(output)
                            )
            ) {

                addBytes(
                        target,
                        "META-INF/MANIFEST.MF",
                        manifestBytes
                );

                addBytes(
                        target,
                        "META-INF/CODEBOX.SF",
                        signatureFileBytes
                );

                addBytes(
                        target,
                        "META-INF/CODEBOX.RSA",
                        signatureBytes
                );

                byte[] buffer =
                        new byte[BUFFER_SIZE];

                for (String name : entryNames) {

                    ZipEntry sourceEntry =
                            source.getEntry(name);

                    JarEntry targetEntry =
                            new JarEntry(name);

                    target.putNextEntry(
                            targetEntry
                    );

                    try (InputStream input =
                                 source.getInputStream(
                                         sourceEntry
                                 )) {

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

            if (!output.isFile() ||
                    output.length() == 0) {
                throw new IOException(
                        "Подписанный AAB не создан"
                );
            }

            log.append("Выходной AAB: ")
                    .append(output.getAbsolutePath())
                    .append('\n');

            log.append("Размер: ")
                    .append(output.length())
                    .append(" байт\n");

            log.append(
                    "Результат: SIGNED_AAB_CREATED\n"
            );

            log.append(
                    "ВНИМАНИЕ: тестовая подпись.\n"
            );

            return new Result(
                    true,
                    output,
                    log.toString()
            );

        } catch (Throwable error) {

            if (output != null &&
                    output.exists()) {
                output.delete();
            }

            log.append("\nРезультат: FAILED\n");

            log.append("Ошибка: ")
                    .append(error.getClass().getName())
                    .append(": ")
                    .append(error.getMessage())
                    .append('\n');

            return new Result(
                    false,
                    null,
                    log.toString()
            );
        }
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
}
