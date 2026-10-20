package solver.pruning;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Versioned, deterministic header and high-nibble-first packed-distance payload for the combined PDB. */
public final class CombinedPruningTableCodec {
    private static final int MAGIC = 0x43504654; // CPFT
    private static final int VERSION = 1;
    private static final int KIND_CROSS_FR = 1;
    private static final int HEADER_BYTES = 20;

    private CombinedPruningTableCodec() {
    }

    public static byte[] encode(byte[] distances) {
        var packed = pack(distances);
        var buffer = ByteBuffer.allocate(HEADER_BYTES + packed.length).order(ByteOrder.BIG_ENDIAN);
        putHeader(buffer, distances.length, checksum(packed));
        buffer.put(packed);
        return buffer.array();
    }

    public static void write(OutputStream output, byte[] distances) throws IOException {
        var packed = pack(distances);
        var header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        putHeader(header, distances.length, checksum(packed));
        output.write(header.array());
        output.write(packed);
    }

    public static byte[] read(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        input.transferTo(output);
        var bytes = output.toByteArray();
        if (bytes.length < HEADER_BYTES) throw new IllegalArgumentException("Truncated combined pruning table header");
        var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (buffer.getInt() != MAGIC) throw new IllegalArgumentException("Invalid combined pruning table magic");
        if (Short.toUnsignedInt(buffer.getShort()) != VERSION) throw new IllegalArgumentException("Unsupported combined pruning table version");
        if (Byte.toUnsignedInt(buffer.get()) != KIND_CROSS_FR) throw new IllegalArgumentException("Unexpected combined pruning table kind");
        if (buffer.get() != (byte) 0xff) throw new IllegalArgumentException("Unexpected combined pruning table slot marker");
        var count = buffer.getInt();
        var expectedChecksum = Integer.toUnsignedLong(buffer.getInt());
        if (buffer.getInt() != CombinedPruningCoordinates.STATE_COUNT) throw new IllegalArgumentException("Unexpected combined coordinate format");
        var payloadBytes = (count + 1) / 2;
        if (count != CombinedPruningCoordinates.STATE_COUNT || bytes.length != HEADER_BYTES + payloadBytes) {
            throw new IllegalArgumentException("Combined pruning table payload length mismatch");
        }
        var packed = Arrays.copyOfRange(bytes, HEADER_BYTES, bytes.length);
        if (checksum(packed) != expectedChecksum) throw new IllegalArgumentException("Combined pruning table checksum mismatch");
        validatePacked(packed, count);
        return packed;
    }

    public static int distanceAt(byte[] packed, int coordinateIndex) {
        if (packed == null || coordinateIndex < 0 || coordinateIndex >= CombinedPruningCoordinates.STATE_COUNT
                || packed.length != (CombinedPruningCoordinates.STATE_COUNT + 1) / 2) {
            throw new IllegalArgumentException("Invalid packed combined-table lookup");
        }
        var value = Byte.toUnsignedInt(packed[coordinateIndex >>> 1]);
        return (coordinateIndex & 1) == 0 ? value >>> 4 : value & 0x0f;
    }

    private static byte[] pack(byte[] distances) {
        validateDistances(distances);
        var packed = new byte[(distances.length + 1) / 2];
        for (var i = 0; i < distances.length; i++) {
            var distance = Byte.toUnsignedInt(distances[i]);
            if (distance > 15) throw new IllegalArgumentException("Combined pruning table maximum distance exceeds nibble format (15): " + distance);
            if ((i & 1) == 0) packed[i >>> 1] = (byte) (distance << 4);
            else packed[i >>> 1] |= (byte) distance;
        }
        return packed;
    }

    private static void putHeader(ByteBuffer buffer, int count, long checksum) {
        buffer.putInt(MAGIC).putShort((short) VERSION).put((byte) KIND_CROSS_FR).put((byte) 0xff)
                .putInt(count).putInt((int) checksum).putInt(CombinedPruningCoordinates.STATE_COUNT);
    }

    private static long checksum(byte[] distances) {
        var crc = new CRC32();
        crc.update(distances);
        return crc.getValue();
    }

    private static void validateDistances(byte[] distances) {
        if (distances == null || distances.length != CombinedPruningCoordinates.STATE_COUNT) {
            throw new IllegalArgumentException("Combined pruning table must contain " + CombinedPruningCoordinates.STATE_COUNT + " distances");
        }
        for (byte distance : distances) if (distance == (byte) 0xff) throw new IllegalArgumentException("Combined pruning table contains unreachable coordinates");
    }

    private static void validatePacked(byte[] packed, int coordinateCount) {
        if (packed.length != (coordinateCount + 1) / 2) throw new IllegalArgumentException("Packed pruning table length mismatch");
        if ((coordinateCount & 1) != 0 && (packed[packed.length - 1] & 0x0f) != 0) {
            throw new IllegalArgumentException("Packed pruning table has nonzero padding bits");
        }
    }
}
