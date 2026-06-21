package test;

import database.TimedSolve;
import org.junit.jupiter.api.Test;
import statistics.SolveStatisticsCalculator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SolveStatisticsCalculatorTest {
    @Test
    void calculate_shouldComputeBestAverageAndTrimmedAo5() {
        var statistics = SolveStatisticsCalculator.calculate(
                solves(10_000, 11_000, 12_000, 13_000, 20_000),
                List.of()
        );

        assertEquals(10_000, statistics.bestMs());
        assertEquals(13_200, statistics.averageMs());
        assertEquals("value", statistics.ao5().status());
        assertEquals(12_000, statistics.ao5().valueMs());
        assertEquals("insufficient", statistics.ao12().status());
    }

    @Test
    void calculate_shouldDropOneDnfAsWorstResult() {
        var solves = new ArrayList<>(solves(10_000, 11_000, 12_000, 13_000));
        solves.add(timedSolve(5, null, true));

        var statistics = SolveStatisticsCalculator.calculate(solves, List.of());

        assertEquals("value", statistics.ao5().status());
        assertEquals(12_000, statistics.ao5().valueMs());
    }

    @Test
    void calculate_shouldMarkAverageDnfWhenWindowContainsTwoDnfs() {
        var solves = new ArrayList<>(solves(10_000, 11_000, 12_000));
        solves.add(timedSolve(4, null, true));
        solves.add(timedSolve(5, null, true));

        var statistics = SolveStatisticsCalculator.calculate(solves, List.of());

        assertEquals("dnf", statistics.ao5().status());
        assertNull(statistics.ao5().valueMs());
    }

    @Test
    void calculate_shouldIgnoreDnfsForBestAndOverallAverage() {
        var solves = new ArrayList<>(solves(10_000, 14_000));
        solves.add(timedSolve(3, null, true));

        var statistics = SolveStatisticsCalculator.calculate(solves, List.of());

        assertEquals(10_000, statistics.bestMs());
        assertEquals(12_000, statistics.averageMs());
        assertEquals(1, statistics.dnfCount());
    }

    private static List<TimedSolve> solves(int... times) {
        var solves = new ArrayList<TimedSolve>();
        for (int i = 0; i < times.length; i++) {
            solves.add(timedSolve(i + 1, times[i], false));
        }
        return solves;
    }

    private static TimedSolve timedSolve(long id, Integer time, boolean dnf) {
        return new TimedSolve(id, time, dnf, OffsetDateTime.parse("2026-06-20T12:00:00Z").minusMinutes(id));
    }
}
