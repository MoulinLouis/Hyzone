package org.hyvote.plugins.votifier.crypto;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;

/**
 * Cryptographic utilities for vote payload handling.
 *
 * <p>Provides RSA decryption compatible with the standard Votifier protocol.
 * Voting sites encrypt vote data using RSA/ECB/PKCS1Padding with the server's
 * public key; this utility decrypts using the corresponding private key.</p>
 */
public final class CryptoUtil {

    /**
     * The cipher transformation used by Votifier protocol.
     * Must be RSA/ECB/PKCS1Padding for compatibility with existing Votifier clients.
     */
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    private CryptoUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Decrypts RSA-encrypted data using the provided private key.
     *
     * <p>Uses RSA/ECB/PKCS1Padding cipher transformation as per the Votifier protocol.
     * This is intentionally not OAEP to maintain compatibility with existing vote sites.</p>
     *
     * @param encryptedData the encrypted bytes to decrypt
     * @param privateKey the RSA private key for decryption
     * @return the decrypted bytes
     * @throws VoteDecryptionException if decryption fails due to invalid key or corrupted data
     */
    public static byte[] decrypt(byte[] encryptedData, PrivateKey privateKey) throws VoteDecryptionException {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(encryptedData);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            // Should not happen with standard JDK
            throw new VoteDecryptionException("RSA cipher not available", e);
        } catch (InvalidKeyException e) {
            throw new VoteDecryptionException("Invalid RSA private key", e);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new VoteDecryptionException("Failed to decrypt vote data - corrupted or tampered payload", e);
        }
    }
}
