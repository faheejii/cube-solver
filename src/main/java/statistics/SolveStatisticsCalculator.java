package statistics;

import database.SolveHistoryEntry;
import database.TimedSolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SolveStatisticsCalculator {
    private SolveStatisticsCalculator() {
    }

    public static SolveStatistics calculate(
            List<TimedSolve> solves,
            List<SolveHistoryEntry> recentSolves
    ) {
        var ordered = solves.stream()
                .sorted(Comparator.comparing(TimedSolve::createdAt).reversed()
                        .thenComparing(Comparator.comparingLong(TimedSolve::id).reversed()))
                .toList();
        var validTimes = ordered.stream()
                .filter(solve -> !solve.dnf() && solve.officialMs() != null)
                .map(TimedSolve::officialMs)
                .toList();

        Integer best = validTimes.stream().min(Integer::compareTo).orElse(null);
        Integer average = validTimes.isEmpty()
                ? null
                : (int) Math.round(validTimes.stream().mapToLong(Integer::longValue).average().orElseThrow());

        return new SolveStatistics(
                ordered.size(),
                (int) ordered.stream().filter(TimedSolve::dnf).count(),
                best,
                average,
                rollingAverage(ordered, 5),
                rollingAverage(ordered, 12),
                List.copyOf(recentSolves)
        );
    }

    static RollingAverage rollingAverage(List<TimedSolve> ordered, int size) {
        if (ordered.size() < size) {
            return RollingAverage.insufficient();
        }

        var window = ordered.subList(0, size);
        var dnfCount = window.stream().filter(TimedSolve::dnf).count();
        if (dnfCount >= 2) {
            return RollingAverage.dnf();
        }

        var values = new ArrayList<Integer>();
        for (var solve : window) {
            if (!solve.dnf() && solve.officialMs() != null) {
                values.add(solve.officialMs());
            }
        }
        values.sort(Integer::compareTo);

        if (dnfCount == 0) {
            values.remove(values.size() - 1);
        }
        values.remove(0);

        var average = (int) Math.round(values.stream().mapToLong(Integer::longValue).average().orElseThrow());
        return RollingAverage.value(average);
    }
}
