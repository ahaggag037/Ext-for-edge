package com.edgeflow.browser;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureStateCodec {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "edgeflow_session_state_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFIX = "v1:";

    static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.encodeToString(iv, Base64.NO_WRAP)
                    + "."
                    + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        } catch (Throwable error) {
            return "";
        }
    }

    static String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        if (!encoded.startsWith(PREFIX)) {
            // One-time compatibility with the Genesis plaintext state format.
            return encoded;
        }
        try {
            int split = encoded.indexOf('.', PREFIX.length());
            if (split <= PREFIX.length()) return "";
            byte[] iv = Base64.decode(encoded.substring(PREFIX.length(), split), Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(encoded.substring(split + 1), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Throwable error) {
            return "";
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        java.security.Key key = keyStore.getKey(ALIAS, null);
        if (key instanceof SecretKey) return (SecretKey) key;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private SecureStateCodec() {}
}
