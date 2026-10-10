package com.photoexhibition.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;

/** Internal configuration encryption. The master key must be backed up separately from the DB. */
@Component
public class SecretConfigCodec {
    static final String PREFIX = "enc:v1:";
    private static final Set<String> KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "ai_search_api_key", "sms_access_key_secret", "email_password", "payment_private_key",
        "payment_webhook_secret", "payment_api_secret")));
    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();
    private boolean initialized;

    public SecretConfigCodec(@Value("${app.security.config-key-file:${user.home}/.photo-exhibition/config.key}") String path) {
        keyFile = Paths.get(path);
    }
    public static boolean isSecret(String key) { return KEYS.contains(key); }
    public static boolean isSecretField(String key) {
        return isSecret(key) || Arrays.asList("aiSearchApiKey", "smsAccessKeySecret", "emailPassword",
            "paymentPrivateKey", "paymentWebhookSecret", "paymentApiSecret").contains(key);
    }
    public String encode(String key, String value) {
        if (!isSecret(key) || value == null || value.isEmpty()) return value;
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(true), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(key.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) { throw new IllegalStateException("密钥配置加密失败，请检查加密主密钥文件权限", ex); }
    }
    public String decode(String key, String value) {
        if (!isSecret(key) || value == null || !value.startsWith(PREFIX)) return value;
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length < 28) throw new IllegalArgumentException("Invalid ciphertext");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey(false), new GCMParameterSpec(128, Arrays.copyOf(payload, 12)));
            cipher.updateAAD(key.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Arrays.copyOfRange(payload, 12, payload.length)), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("密钥配置无法解密，请恢复原加密主密钥文件", ex); }
    }
    private synchronized SecretKeySpec masterKey(boolean create) throws Exception {
        if (!Files.exists(keyFile) && create && !initialized) {
            Path parent = keyFile.toAbsolutePath().getParent();
            if (!Files.exists(parent)) {
                try { Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))); }
                catch (UnsupportedOperationException ex) { Files.createDirectories(parent); }
            }
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            // Publish a complete file, never a partially written master key.
            Path temporary = Files.createTempFile(parent, ".config-key-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                Files.write(temporary, Base64.getEncoder().encode(bytes));
                try { Files.createLink(keyFile, temporary); }
                catch (FileAlreadyExistsException ignored) { /* Another instance owns creation. */ }
            } finally { Files.deleteIfExists(temporary); }
        }
        byte[] bytes = Base64.getDecoder().decode(Files.readAllBytes(keyFile));
        if (bytes.length != 32) throw new IllegalStateException("Invalid master key");
        initialized = true;
        return new SecretKeySpec(bytes, "AES");
    }
}
