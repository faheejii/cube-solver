package database;

import java.util.List;

public record SolveHistoryPage(
        List<SolveHistoryEntry> items,
        String nextCursor
) {
}
