package solver.pruning;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CombinedPruningTableCodecTest {
    @Test
    void rejectsTruncatedAndIncorrectlyIdentifiedFilesBeforeReadingPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> CombinedPruningTableCodec.read(new ByteArrayInputStream(new byte[8])));
        assertThrows(IllegalArgumentException.class,
                () -> CombinedPruningTableCodec.read(new ByteArrayInputStream(new byte[20])));
    }
}
