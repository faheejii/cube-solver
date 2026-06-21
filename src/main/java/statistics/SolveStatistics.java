package statistics;

import database.SolveHistoryEntry;

import java.util.List;

public record SolveStatistics(
        int solveCount,
        int dnfCount,
        Integer bestMs,
        Integer averageMs,
        RollingAverage ao5,
        RollingAverage ao12,
        List<SolveHistoryEntry> recentSolves
) {
}
