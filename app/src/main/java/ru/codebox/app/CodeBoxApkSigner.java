package ru.codebox.app;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

/** Signs local APKs using a persistent, non-exportable Android Keystore key. */
public final class CodeBoxApkSigner {

    // Do not change this alias: existing installed applications use this certificate.
    private static final String KEYSTORE_NAME = "AndroidKeyStore";
    private static final String KEY_ALIAS = "CodeBox5_APK_Signing_Key_v1";

    private CodeBoxApkSigner() { }

    public static final class Result {
        public final boolean signed;
        public final File signedApk;
        public final String report;

        private Result(boolean signed, File signedApk, String report) {
            this.signed = signed;
            this.signedApk = signedApk;
            this.report = report;
        }
    }

    public static synchronized Result sign(Context context, File unsignedApk,
                                            String projectName) {
        StringBuilder log = new StringBuilder("CodeBox 5 - APK Signer\n");
        log.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n');
        File temporaryApk = null;
        try {
            if (context == null) {
                throw new IOException("Context отсутствует");
            }
            if (Build.VERSION.SDK_INT < 26) {
                throw new IOException("Требуется Android API 26+");
            }
            if (projectName == null
                    || !projectName.matches("[A-Za-z][A-Za-z0-9_]{0,49}")) {
                throw new IOException("Недопустимое имя проекта");
            }
            if (unsignedApk == null || !unsignedApk.isFile()
                    || unsignedApk.length() == 0) {
                throw new IOException("Неподписанный APK не найден");
            }

            log.append("Input: ").append(unsignedApk.getAbsolutePath()).append('\n');
            log.append("Input size: ").append(unsignedApk.length()).append(" bytes\n");

            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_NAME);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                log.append("Ключ отсутствует: создаётся новый ключ подписи.\n");
                createSigningKey();
                keyStore.load(null);
            } else {
                log.append("Используется существующий ключ подписи.\n");
            }

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_ALIAS, null);
            if (privateKey == null) {
                throw new IOException("Не удалось получить закрытый ключ");
            }
            java.security.cert.Certificate certificate = keyStore.getCertificate(KEY_ALIAS);
            if (!(certificate instanceof X509Certificate)) {
                throw new IOException("Сертификат X.509 отсутствует");
            }
            X509Certificate x509 = (X509Certificate) certificate;
            x509.checkValidity();
            String fingerprint = sha256(x509.getEncoded());
            log.append("Key alias: ").append(KEY_ALIAS).append('\n');
            log.append("Key algorithm: ").append(privateKey.getAlgorithm()).append('\n');
            log.append("Certificate SHA-256: ").append(fingerprint).append('\n');
            log.append("Важно: сохраните этот отпечаток для проверки обновлений.\n");

            File outputDirectory = new File(context.getCacheDir(),
                    "apk-signed-" + projectName);
            if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
                throw new IOException("Не удалось создать каталог APK");
            }
            File signedApk = new File(outputDirectory, projectName + ".apk");
            temporaryApk = new File(outputDirectory, projectName + ".tmp.apk");
            if (temporaryApk.exists() && !temporaryApk.delete()) {
                throw new IOException("Не удалось удалить временный APK");
            }
            if (unsignedApk.getCanonicalFile().equals(signedApk.getCanonicalFile())
                    || unsignedApk.getCanonicalFile().equals(temporaryApk.getCanonicalFile())) {
                throw new IOException("Входной APK совпадает с выходным файлом");
            }

            log.append("\n--- APK Signing ---\n");
            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                    "CodeBox5", privateKey, Arrays.asList(x509)).build();
            ApkSigner signer = new ApkSigner.Builder(Arrays.asList(signerConfig))
                    .setInputApk(unsignedApk)
                    .setOutputApk(temporaryApk)
                    .setMinSdkVersion(26)
                    .setV1SigningEnabled(false)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(false)
                    .build();
            signer.sign();
            if (!temporaryApk.isFile() || temporaryApk.length() == 0) {
                throw new IOException("Подписанный APK не создан");
            }

            log.append("\n--- APK Verification ---\n");
            ApkVerifier.Result verification = new ApkVerifier.Builder(temporaryApk)
                    .setMinCheckedPlatformVersion(26)
                    .build().verify();
            if (!verification.isVerified()) {
                for (ApkVerifier.IssueWithParams issue : verification.getErrors()) {
                    log.append("ERROR: ").append(issue).append('\n');
                }
                throw new IOException("Проверка подписи APK не пройдена");
            }
            log.append("Verification: OK\n");

            // Replace the previous output only after signing and verification succeed.
            // The temporary file and final file are in the same directory.
            Files.move(temporaryApk.toPath(), signedApk.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            temporaryApk = null;

            log.append("\nРезультат: SIGNED_APK_CREATED\n");
            log.append("APK: ").append(signedApk.getAbsolutePath()).append('\n');
            log.append("Размер: ").append(signedApk.length()).append(" байт\n");
            log.append("Схема подписи: APK Signature Scheme v2\n");
            log.append("Ключ Android Keystore не экспортируется. ")
                    .append("Не удаляйте данные CodeBox 5.\n");
            return new Result(true, signedApk, log.toString());
        } catch (Exception e) {
            log.append("\nРезультат: FAILED\nОшибка: ")
                    .append(e.getClass().getName()).append(": ")
                    .append(e.getMessage()).append('\n');
            return new Result(false, null, log.toString());
        } finally {
            if (temporaryApk != null && temporaryApk.exists()) {
                // A failed signing operation must not leave a misleading APK.
                temporaryApk.delete();
            }
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static void createSigningKey() throws Exception {
        Calendar calendar = Calendar.getInstance();
        Date startDate = calendar.getTime();
        calendar.add(Calendar.YEAR, 25);
        Date endDate = calendar.getTime();
        KeyGenParameterSpec specification = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=CodeBox5, O=CodeBox"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(startDate)
                .setCertificateNotAfter(endDate)
                .setUserAuthenticationRequired(false)
                .build();
        KeyPairGenerator generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_NAME);
        generator.initialize(specification);
        generator.generateKeyPair();
    }
}
