package server;

import database.StoredCredential;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

final class PasswordHasher {
    static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private final SecureRandom random;

    PasswordHasher() {
        this(new SecureRandom());
    }

    PasswordHasher(SecureRandom random) {
        this.random = random;
    }

    PasswordCredential hash(String password) {
        var salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        var derived = derive(password, salt, ITERATIONS);
        return new PasswordCredential(
                Base64.getEncoder().encodeToString(derived),
                Base64.getEncoder().encodeToString(salt),
                ITERATIONS
        );
    }

    boolean verify(String password, StoredCredential credential) {
        try {
            var salt = Base64.getDecoder().decode(credential.passwordSalt());
            var expected = Base64.getDecoder().decode(credential.passwordHash());
            var actual = derive(password, salt, credential.passwordIterations());
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("Invalid password iteration count");
        }
        var chars = password.toCharArray();
        var spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        java.util.Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2 is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }

    record PasswordCredential(String hash, String salt, int iterations) {
    }
}
