package test;

import database.HistoryCursor;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HistoryCursorTest {
    @Test
    void encodeAndParse_shouldRoundTripStableHistoryPosition() {
        var cursor = new HistoryCursor(OffsetDateTime.parse("2026-06-20T12:34:56.123Z"), 42);

        assertEquals(cursor, HistoryCursor.parse(cursor.encode()));
    }

    @Test
    void parse_shouldRejectMalformedCursor() {
        assertThrows(IllegalArgumentException.class, () -> HistoryCursor.parse("bad-cursor"));
    }
}
