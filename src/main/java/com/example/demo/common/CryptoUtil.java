package com.example.demo.common;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** 轻量对称加密：用于数据库存储用户 API Key（AES/ECB + 密钥派生） */
public final class CryptoUtil {

    private static final String DEFAULT_KEY = "cece-ai-key-encryption-secret";
    private static String keySource = DEFAULT_KEY;

    private CryptoUtil() {
    }

    public static void configure(String source) {
        if (source != null && !source.isEmpty()) keySource = source;
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key());
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** 脱敏展示：sk-a****1234 */
    public static String mask(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        if (plain.length() <= 8) return "******";
        return plain.substring(0, 4) + "****" + plain.substring(plain.length() - 4);
    }

    private static SecretKeySpec key() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(keySource.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(bytes, "AES");
    }
}
