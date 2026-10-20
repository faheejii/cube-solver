package solver.pruning;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Versioned, checksummed binary format for the six face-specific cross PDBs. */
public final class CrossPruningTableCodec {
    private static final int MAGIC = 0x43525054; // CRPT
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 20;
    private static final int DISTANCE_COUNT = CrossPruningCoordinates.FACE_COUNT * CrossPruningCoordinates.STATE_COUNT;

    private CrossPruningTableCodec() {
    }

    public static byte[] encode(byte[] distances) {
        validate(distances);
        var header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        putHeader(header, checksum(distances));
        var encoded = Arrays.copyOf(header.array(), HEADER_BYTES + distances.length);
        System.arraycopy(distances, 0, encoded, HEADER_BYTES, distances.length);
        return encoded;
    }

    public static void write(OutputStream output, byte[] distances) throws IOException {
        validate(distances);
        var header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        putHeader(header, checksum(distances));
        output.write(header.array());
        output.write(distances);
    }

    public static byte[] read(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        input.transferTo(output);
        var encoded = output.toByteArray();
        if (encoded.length < HEADER_BYTES) throw new IllegalArgumentException("Truncated cross pruning table header");
        var header = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        if (header.getInt() != MAGIC) throw new IllegalArgumentException("Invalid cross pruning table magic");
        if (Short.toUnsignedInt(header.getShort()) != VERSION) throw new IllegalArgumentException("Unsupported cross pruning table version");
        if (Byte.toUnsignedInt(header.get()) != CrossPruningCoordinates.FACE_COUNT) {
            throw new IllegalArgumentException("Unexpected cross pruning table face count");
        }
        if (header.get() != 0) throw new IllegalArgumentException("Unexpected cross pruning table format marker");
        if (header.getInt() != DISTANCE_COUNT) throw new IllegalArgumentException("Unexpected cross pruning coordinate count");
        var expectedChecksum = Integer.toUnsignedLong(header.getInt());
        if (header.getInt() != CrossPruningCoordinates.STATE_COUNT) {
            throw new IllegalArgumentException("Unexpected cross pruning states-per-face count");
        }
        if (encoded.length != HEADER_BYTES + DISTANCE_COUNT) {
            throw new IllegalArgumentException("Cross pruning table payload length mismatch");
        }
        var distances = Arrays.copyOfRange(encoded, HEADER_BYTES, encoded.length);
        validate(distances);
        if (checksum(distances) != expectedChecksum) throw new IllegalArgumentException("Cross pruning table checksum mismatch");
        return distances;
    }

    private static void putHeader(ByteBuffer header, long checksum) {
        header.putInt(MAGIC).putShort((short) VERSION).put((byte) CrossPruningCoordinates.FACE_COUNT).put((byte) 0)
                .putInt(DISTANCE_COUNT).putInt((int) checksum).putInt(CrossPruningCoordinates.STATE_COUNT);
    }

    private static long checksum(byte[] distances) {
        var crc = new CRC32();
        crc.update(distances);
        return crc.getValue();
    }

    private static void validate(byte[] distances) {
        if (distances == null || distances.length != DISTANCE_COUNT) {
            throw new IllegalArgumentException("Cross pruning table must contain " + DISTANCE_COUNT + " distances");
        }
        for (byte distance : distances) {
            var value = Byte.toUnsignedInt(distance);
            if (value == 0xff || value > 15) throw new IllegalArgumentException("Invalid cross pruning distance: " + value);
        }
    }
}
