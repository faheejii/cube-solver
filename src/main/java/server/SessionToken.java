package server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

final class SessionToken {
    private static final int TOKEN_BYTES = 32;
    private final SecureRandom random;

    SessionToken() {
        this(new SecureRandom());
    }

    SessionToken(SecureRandom random) {
        this.random = random;
    }

    String generate() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static boolean isValid(String token) {
        return token != null && token.matches("^[A-Za-z0-9_-]{43}$");
    }
}
