package server;

import database.AuthUser;
import database.StoredCredential;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hash_shouldUseUniqueSaltAndVerifyOnlyMatchingPassword() {
        var first = hasher.hash("correct horse battery staple");
        var second = hasher.hash("correct horse battery staple");

        assertNotEquals(first.salt(), second.salt());
        assertNotEquals(first.hash(), second.hash());
        assertTrue(hasher.verify("correct horse battery staple", stored(first)));
        assertFalse(hasher.verify("incorrect password", stored(first)));
    }

    @Test
    void verify_shouldRejectMalformedStoredCredential() {
        var malformed = new StoredCredential(
                new AuthUser(1, "external", "user@example.com", null),
                "not-base64!",
                "not-base64!",
                PasswordHasher.ITERATIONS
        );

        assertFalse(hasher.verify("correct horse battery staple", malformed));
    }

    private static StoredCredential stored(PasswordHasher.PasswordCredential credential) {
        return new StoredCredential(
                new AuthUser(1, "external", "user@example.com", null),
                credential.hash(),
                credential.salt(),
                credential.iterations()
        );
    }
}
