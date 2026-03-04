package org.hyvote.plugins.votifier.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Manages RSA key pair generation and storage for vote encryption/decryption.
 *
 * <p>Uses 2048-bit RSA keys as per the Votifier security model. Voting sites
 * encrypt vote data with the server's public key; this plugin decrypts with
 * the private key.</p>
 */
public class RSAKeyManager {

    private static final String RSA_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;

    /** File name for the private key in PEM format (PKCS8). */
    public static final String PRIVATE_KEY_FILE = "rsa.key";

    /** File name for the public key in PEM format (X509). */
    public static final String PUBLIC_KEY_FILE = "rsa.pub";

    private static final String PEM_PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
    private static final String PEM_PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_PUBLIC_KEY_END = "-----END PUBLIC KEY-----";

    private KeyPair keyPair;

    /**
     * Creates a new RSAKeyManager instance.
     */
    public RSAKeyManager() {
    }

    /**
     * Generates a new 2048-bit RSA key pair using SecureRandom.
     *
     * @return the generated key pair
     * @throws RuntimeException if RSA algorithm is not available (should not happen with standard JDK)
     */
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(KEY_SIZE, new SecureRandom());
            this.keyPair = generator.generateKeyPair();
            return this.keyPair;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA algorithm not available - this should not happen with standard JDK", e);
        }
    }

    /**
     * Returns the current key pair, or null if none has been generated or loaded.
     *
     * @return the current key pair, or null
     */
    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Sets the key pair (used when loading existing keys).
     *
     * @param keyPair the key pair to set
     */
    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * Saves the current key pair to PEM files in the specified directory.
     *
     * <p>Creates the directory if it does not exist. Writes private key as PKCS8 PEM
     * (rsa.key) and public key as X509 PEM (rsa.pub).</p>
     *
     * @param directory the directory to save the keys to
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if no key pair has been generated or loaded
     */
    public void saveKeyPair(Path directory) throws IOException {
        if (keyPair == null) {
            throw new IllegalStateException("No key pair to save - generate or load keys first");
        }

        Files.createDirectories(directory);

        Path privateKeyPath = directory.resolve(PRIVATE_KEY_FILE);
        Path publicKeyPath = directory.resolve(PUBLIC_KEY_FILE);

        writePemFile(privateKeyPath, keyPair.getPrivate().getEncoded(),
                PEM_PRIVATE_KEY_BEGIN, PEM_PRIVATE_KEY_END);
        writePemFile(publicKeyPath, keyPair.getPublic().getEncoded(),
                PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);
    }

    /**
     * Loads a key pair from PEM files in the specified directory.
     *
     * @param directory the directory containing rsa.key and rsa.pub files
     * @return the loaded key pair
     * @throws IOException if an I/O error occurs or files are missing
     */
    public KeyPair loadKeyPair(Path directory) throws IOException {
        Path privateKeyPath = directory.resolve(PRIVATE_KEY_FILE);
        Path publicKeyPath = directory.resolve(PUBLIC_KEY_FILE);

        byte[] privateKeyBytes = readPemFile(privateKeyPath, PEM_PRIVATE_KEY_BEGIN, PEM_PRIVATE_KEY_END);
        byte[] publicKeyBytes = readPemFile(publicKeyPath, PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            this.keyPair = new KeyPair(publicKey, privateKey);
            return this.keyPair;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IOException("Failed to load RSA keys", e);
        }
    }

    /**
     * Checks if key files exist in the specified directory.
     *
     * @param directory the directory to check
     * @return true if both rsa.key and rsa.pub exist
     */
    public boolean keysExist(Path directory) {
        return Files.exists(directory.resolve(PRIVATE_KEY_FILE))
                && Files.exists(directory.resolve(PUBLIC_KEY_FILE));
    }

    /**
     * Checks if a key pair has been loaded or generated.
     *
     * @return true if keys are available
     */
    public boolean hasKeys() {
        return keyPair != null;
    }

    /**
     * Returns the private key from the current key pair.
     *
     * @return the private key, or null if no keys are loaded
     */
    public PrivateKey getPrivateKey() {
        return keyPair != null ? keyPair.getPrivate() : null;
    }

    /**
     * Returns the public key from the current key pair.
     *
     * @return the public key, or null if no keys are loaded
     */
    public PublicKey getPublicKey() {
        return keyPair != null ? keyPair.getPublic() : null;
    }

    /**
     * Writes key bytes to a PEM file with BEGIN/END markers.
     */
    private void writePemFile(Path path, byte[] keyBytes, String beginMarker, String endMarker)
            throws IOException {
        // Base64 MIME encoder produces 76-char lines with CRLF; we want 64-char lines with LF
        Base64.Encoder encoder = Base64.getMimeEncoder(64, new byte[]{'\n'});
        String base64 = encoder.encodeToString(keyBytes);

        String pemContent = beginMarker + "\n" + base64 + "\n" + endMarker + "\n";
        Files.writeString(path, pemContent);
    }

    /**
     * Reads key bytes from a PEM file, stripping BEGIN/END markers.
     */
    private byte[] readPemFile(Path path, String beginMarker, String endMarker) throws IOException {
        String content = Files.readString(path);

        // Strip markers and whitespace
        String base64 = content
                .replace(beginMarker, "")
                .replace(endMarker, "")
                .replaceAll("\\s", "");

        return Base64.getDecoder().decode(base64);
    }
}
