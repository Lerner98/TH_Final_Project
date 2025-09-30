package com.example.asltranslator.network;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.example.asltranslator.utils.Constants;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Manages cryptographic operations for secure data handling in the ASL translator app.
 * Provides password hashing, salt generation, AES-256-GCM encryption/decryption, and key management.
 * Data Flow: Raw data → Hashing/Encryption → Secure Storage/Transmission → Decryption/Verification.
 */
public class CryptoManager {
    private static CryptoManager instance;
    private KeyStore keyStore;
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Returns the singleton instance of CryptoManager.
     * @return The single instance of this class.
     */
    public static synchronized CryptoManager getInstance() {
        if (instance == null) {
            instance = new CryptoManager();
        }
        return instance;
    }

    private CryptoManager() {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Hashes a password with SHA-256 using a provided salt for security.
     * @param password The password to hash.
     * @param salt The salt value to combine with the password.
     * @return The hashed password as a hexadecimal string, or null if hashing fails.
     */
    public String hashPasswordSHA256(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String passwordWithSalt = password + salt;
            byte[] hash = digest.digest(passwordWithSalt.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates a random salt for password hashing.
     * @return A Base64-encoded salt string.
     */
    public String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    /**
     * Initializes AES-256-GCM keys in the Android KeyStore if not already present.
     * @throws Exception If key generation or keystore access fails.
     */
    public void initializeKeys() throws Exception {
        if (!keyStore.containsAlias(Constants.KEYSTORE_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                    Constants.KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            keyGenerator.init(keyGenParameterSpec);
            keyGenerator.generateKey();
        }
    }

    /**
     * Encrypts frame data using AES-256-GCM with integrity check via SHA-256 hash.
     * @param data The byte array to encrypt.
     * @return A Base64-encoded string containing IV, encrypted data, and hash, or null on failure.
     */
    public String encryptFrame(byte[] data) {
        try {
            SecretKey secretKey = (SecretKey) keyStore.getKey(Constants.KEYSTORE_ALIAS, null);
            Cipher cipher = Cipher.getInstance(Constants.AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data);

            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

            // Add SHA-256 hash for integrity
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined);

            // Final format: IV + encrypted + hash
            byte[] result = new byte[combined.length + hash.length];
            System.arraycopy(combined, 0, result, 0, combined.length);
            System.arraycopy(hash, 0, result, combined.length, hash.length);

            return Base64.encodeToString(result, Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decrypts frame data using AES-256-GCM and verifies integrity with SHA-256 hash.
     * @param encryptedData The Base64-encoded string containing IV, encrypted data, and hash.
     * @return The decrypted byte array, or null on failure or integrity check failure.
     */
    public byte[] decryptFrame(String encryptedData) {
        try {
            byte[] data = Base64.decode(encryptedData, Base64.NO_WRAP);
            byte[] iv = new byte[12]; // GCM IV is 12 bytes
            byte[] encrypted = new byte[data.length - 12 - 32]; // minus IV and SHA-256 hash
            byte[] hash = new byte[32]; // SHA-256 hash is 32 bytes

            System.arraycopy(data, 0, iv, 0, 12);
            System.arraycopy(data, 12, encrypted, 0, encrypted.length);
            System.arraycopy(data, data.length - 32, hash, 0, 32);

            // Verify integrity
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            byte[] computedHash = digest.digest(combined);

            if (!MessageDigest.isEqual(hash, computedHash)) {
                throw new SecurityException("Data integrity check failed");
            }

            SecretKey secretKey = (SecretKey) keyStore.getKey(Constants.KEYSTORE_ALIAS, null);
            Cipher cipher = Cipher.getInstance(Constants.AES_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}