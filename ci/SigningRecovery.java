import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * Writes a strongly encrypted recovery envelope for the Android release signing key.
 *
 * <p>Usage: {@code java ci/SigningRecovery.java <output.json>} or {@code --self-test}.
 * All secret material is read from the environment and kept in memory; the only file the
 * program creates is the encrypted envelope. See ci/signing-recovery.md for the format.
 */
public final class SigningRecovery {
    private static final String KEYSTORE_ENV = "ANDROID_RELEASE_KEYSTORE_B64";
    private static final String PASSWORD_ENV = "ANDROID_RELEASE_PASSWORD";
    private static final String RECIPIENT_ENV = "SIGNING_RECOVERY_PUBLIC_KEY";

    private static final int SCHEMA_VERSION = 1;
    private static final String ALGORITHM = "RSA-OAEP-256+A256GCM";
    private static final int MIN_RSA_BITS = 3072;
    private static final int AES_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private SigningRecovery() {
    }

    public static void main(String[] args) {
        int status;
        try {
            status = run(args);
        } catch (RecoveryException e) {
            System.err.println("error: " + e.getMessage());
            status = e.status;
        } catch (Throwable ignored) {
            // Never print exception details: they can contain key material or environment data.
            System.err.println("error: signing recovery failed");
            status = 1;
        }
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            selfTest();
            System.out.println("self-test: ok");
            return 0;
        }
        if (args.length != 1) {
            System.err.println("usage: java ci/SigningRecovery.java <output.json> | --self-test");
            return 2;
        }

        Path output;
        try {
            output = Paths.get(args[0]);
        } catch (RuntimeException e) {
            throw new RecoveryException(1, "invalid output path");
        }
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new RecoveryException(1, "output file already exists");
        }

        String keystoreBase64 = requiredEnv(KEYSTORE_ENV);
        String password = requiredEnv(PASSWORD_ENV);
        String recipientPem = requiredEnv(RECIPIENT_ENV);

        byte[] keystore;
        try {
            keystore = Base64.getDecoder().decode(keystoreBase64.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            throw new RecoveryException(1, "environment keystore is not valid Base64");
        }
        if (keystore.length == 0) {
            throw new RecoveryException(1, "environment keystore is empty");
        }

        byte[] payload = payloadJson(
                Base64.getEncoder().encodeToString(keystore), password).getBytes(StandardCharsets.UTF_8);
        try {
            PublicKey recipient = parsePublicKey(recipientPem);
            Envelope envelope = encryptEnvelope(recipient, payload);
            writeNewFile(output, envelope.toJson().getBytes(StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(payload, (byte) 0);
            Arrays.fill(keystore, (byte) 0);
        }
        System.out.println("signing recovery envelope written");
        return 0;
    }

    private static String requiredEnv(String name) throws RecoveryException {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new RecoveryException(1, "missing environment variable " + name);
        }
        return value;
    }

    /** Payload is a UTF-8 JSON object with exactly these two fields, documented in the guide. */
    static String payloadJson(String keystoreBase64, String password) {
        return "{\"keystoreBase64\":\"" + jsonEscape(keystoreBase64)
                + "\",\"keystorePassword\":\"" + jsonEscape(password) + "\"}";
    }

    static PublicKey parsePublicKey(String pem) throws RecoveryException {
        String begin = "-----BEGIN PUBLIC KEY-----";
        String end = "-----END PUBLIC KEY-----";
        int start = pem.indexOf(begin);
        int finish = pem.indexOf(end);
        if (start < 0 || finish < 0 || finish < start) {
            throw new RecoveryException(1, "invalid recipient public key");
        }
        String body = pem.substring(start + begin.length(), finish).replaceAll("\\s+", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new RecoveryException(1, "invalid recipient public key");
        }
        PublicKey key;
        try {
            key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new RecoveryException(1, "invalid recipient public key");
        }
        if (!(key instanceof RSAPublicKey rsa)) {
            throw new RecoveryException(1, "invalid recipient public key");
        }
        if (rsa.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new RecoveryException(
                    1, "recipient public key must be RSA " + MIN_RSA_BITS + " bits or larger");
        }
        return key;
    }

    static Envelope encryptEnvelope(PublicKey recipient, byte[] payload) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        byte[] aesKey = new byte[AES_KEY_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(aesKey);
        random.nextBytes(nonce);

        byte[] ciphertext;
        byte[] wrappedKey;
        try {
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            ciphertext = aes.doFinal(payload);

            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            rsa.init(Cipher.ENCRYPT_MODE, recipient, OAEP_SHA256);
            wrappedKey = rsa.doFinal(aesKey);
        } finally {
            Arrays.fill(aesKey, (byte) 0);
        }

        String recipientSha256;
        try {
            recipientSha256 = sha256Hex(recipient.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralSecurityException("SHA-256 unavailable", e);
        }
        return new Envelope(recipientSha256, wrappedKey, nonce, ciphertext);
    }

    static byte[] decryptPayload(PrivateKey recipient, byte[] wrappedKey, byte[] nonce, byte[] ciphertext)
            throws GeneralSecurityException {
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.DECRYPT_MODE, recipient, OAEP_SHA256);
        byte[] aesKey = rsa.doFinal(wrappedKey);
        try {
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            return aes.doFinal(ciphertext);
        } finally {
            Arrays.fill(aesKey, (byte) 0);
        }
    }

    static String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format(Locale.ROOT, "%02x", value));
        }
        return hex.toString();
    }

    static void writeNewFile(Path output, byte[] data) throws RecoveryException {
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        boolean created = false;
        try {
            Set<StandardOpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            // Keep the CREATE_NEW descriptor for the entire write; reopening the path could
            // otherwise follow a replacement file or symlink and truncate somebody else's file.
            try (SeekableByteChannel channel = posix
                    ? Files.newByteChannel(output, options, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")))
                    : Files.newByteChannel(output, options)) {
                created = true;
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
        } catch (FileAlreadyExistsException e) {
            throw new RecoveryException(1, "output file already exists");
        } catch (IOException | UnsupportedOperationException e) {
            if (created) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                    // Best effort: the file contains only ciphertext.
                }
            }
            throw new RecoveryException(1, created ? "cannot write output file" : "cannot create output file");
        }
    }

    static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static void selfTest() throws Exception {
        String secretPassword = "self-test-password-never-print";
        String keystoreBase64 = Base64.getEncoder()
                .encodeToString("self-test-keystore".getBytes(StandardCharsets.UTF_8));
        byte[] payload = payloadJson(keystoreBase64, secretPassword)
                .getBytes(StandardCharsets.UTF_8);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(MIN_RSA_BITS, new SecureRandom());
        KeyPair recipient = generator.generateKeyPair();
        KeyPair wrongRecipient = generator.generateKeyPair();

        Envelope envelope = encryptEnvelope(recipient.getPublic(), payload);
        String serialized = envelope.toJson();
        if (serialized.contains(secretPassword) || serialized.contains(keystoreBase64)) {
            throw new RecoveryException(1, "self-test failed: envelope leaked plaintext");
        }
        if (envelope.schemaVersion != SCHEMA_VERSION || !ALGORITHM.equals(envelope.algorithm)) {
            throw new RecoveryException(1, "self-test failed: envelope metadata mismatch");
        }
        String expectedHash = sha256Hex(recipient.getPublic().getEncoded());
        if (!expectedHash.equals(envelope.recipientSha256)) {
            throw new RecoveryException(1, "self-test failed: recipient hash mismatch");
        }

        byte[] recovered = decryptPayload(
                recipient.getPrivate(), envelope.wrappedKey, envelope.nonce, envelope.ciphertext);
        if (!Arrays.equals(payload, recovered)) {
            throw new RecoveryException(1, "self-test failed: roundtrip mismatch");
        }

        if (decryptSucceeds(wrongRecipient.getPrivate(), envelope)) {
            throw new RecoveryException(1, "self-test failed: wrong recipient decrypted envelope");
        }

        byte[] tampered = envelope.ciphertext.clone();
        tampered[tampered.length - 1] ^= 1;
        if (decryptSucceeds(recipient.getPrivate(), new Envelope(
                envelope.recipientSha256, envelope.wrappedKey, envelope.nonce, tampered))) {
            throw new RecoveryException(1, "self-test failed: tampered ciphertext decrypted");
        }

        generator.initialize(2048, new SecureRandom());
        String weakPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'})
                        .encodeToString(generator.generateKeyPair().getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        boolean weakRejected = false;
        try {
            parsePublicKey(weakPem);
        } catch (RecoveryException e) {
            weakRejected = true;
        }
        if (!weakRejected) {
            throw new RecoveryException(1, "self-test failed: weak recipient accepted");
        }
    }

    private static boolean decryptSucceeds(PrivateKey recipient, Envelope envelope) {
        try {
            decryptPayload(recipient, envelope.wrappedKey, envelope.nonce, envelope.ciphertext);
            return true;
        } catch (GeneralSecurityException expected) {
            return false;
        }
    }

    static final class Envelope {
        final int schemaVersion = SCHEMA_VERSION;
        final String algorithm = ALGORITHM;
        final String recipientSha256;
        final byte[] wrappedKey;
        final byte[] nonce;
        final byte[] ciphertext;

        Envelope(String recipientSha256, byte[] wrappedKey, byte[] nonce, byte[] ciphertext) {
            this.recipientSha256 = recipientSha256;
            this.wrappedKey = wrappedKey;
            this.nonce = nonce;
            this.ciphertext = ciphertext;
        }

        String toJson() {
            Base64.Encoder encoder = Base64.getEncoder();
            return "{"
                    + "\"schemaVersion\":" + schemaVersion + ","
                    + "\"algorithm\":\"" + algorithm + "\","
                    + "\"recipientSha256\":\"" + recipientSha256 + "\","
                    + "\"wrappedKey\":\"" + encoder.encodeToString(wrappedKey) + "\","
                    + "\"nonce\":\"" + encoder.encodeToString(nonce) + "\","
                    + "\"ciphertext\":\"" + encoder.encodeToString(ciphertext) + "\""
                    + "}\n";
        }
    }

    static final class RecoveryException extends Exception {
        final int status;

        RecoveryException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
