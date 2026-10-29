package solver.pruning;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CombinedPruningTableCodecTest {
    @Test
    void rejectsTruncatedAndIncorrectlyIdentifiedFilesBeforeReadingPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> CombinedPruningTableCodec.read(new ByteArrayInputStream(new byte[8])));
        assertThrows(IllegalArgumentException.class,
                () -> CombinedPruningTableCodec.read(new ByteArrayInputStream(new byte[20])));
    }

    @Test
    void rejectsUnexpectedTableKind() {
        assertThrows(IllegalArgumentException.class,
                () -> CombinedPruningTableCodec.read(new ByteArrayInputStream(header(2))));
    }

    private static byte[] header(int kind) {
        return ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
                .putInt(0x43504654)
                .putShort((short) 1)
                .put((byte) kind)
                .put((byte) 0xff)
                .putInt(CombinedPruningCoordinates.STATE_COUNT)
                .putInt(0)
                .putInt(CombinedPruningCoordinates.STATE_COUNT)
                .array();
    }
}
