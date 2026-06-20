package test;

import config.Dotenv;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DotenvTest {
    @Test
    void load_shouldParseQuotedAndUnquotedValues() throws IOException {
        var path = Files.createTempFile("cube-solver-dotenv", ".env");
        Files.writeString(path, """
                # comment
                DATABASE_URL='postgresql://example'
                DATABASE_USER="alice"
                DATABASE_PASSWORD=secret
                INVALID_LINE
                """);

        var dotenv = Dotenv.load(path);

        assertEquals("postgresql://example", dotenv.get("DATABASE_URL"));
        assertEquals("alice", dotenv.get("DATABASE_USER"));
        assertEquals("secret", dotenv.get("DATABASE_PASSWORD"));
        assertNull(dotenv.get("INVALID_LINE"));
    }

    @Test
    void load_shouldReturnEmptyValuesWhenFileMissing() {
        var dotenv = Dotenv.load(java.nio.file.Path.of("does-not-exist.env"));

        assertNull(dotenv.get("DATABASE_URL"));
    }
}
