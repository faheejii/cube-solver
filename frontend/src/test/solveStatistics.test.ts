import {describe, expect, it} from "vitest";
import {calculateRollingMetricSeries, calculateRollingWindowBreakdown, calculateWindowStatistics} from "../solveStatistics";
import type {SolveHistoryEntry} from "../types";

function entry(id: number, time: number | null, penalty = "none"): SolveHistoryEntry {
    return {
        id,
        clientAttemptId: `attempt-${id}`,
        scramble: "R U",
        crossFaceRequested: "U",
        timerMs: time,
        officialMs: time,
        penalty,
        dnf: penalty === "dnf",
        fastCrossFaceRequested: "U",
        optimizedCrossFaceRequested: null,
        createdAt: new Date(Date.UTC(2026, 0, id)).toISOString(),
    };
}

describe("calculateWindowStatistics", () => {
    it("calculates count, DNF count, best, and mean from only the selected solves", () => {
        const result = calculateWindowStatistics([
            entry(4, null, "dnf"),
            entry(3, 14_000, "+2"),
            entry(2, 12_000),
        ]);

        expect(result.solveCount).toBe(3);
        expect(result.dnfCount).toBe(1);
        expect(result.bestMs).toBe(12_000);
        expect(result.averageMs).toBe(13_000);
        expect(result.ao5.status).toBe("insufficient");
    });

    it("matches backend Ao5 trimming with one DNF and includes official +2 times", () => {
        const result = calculateWindowStatistics([
            entry(5, null, "dnf"),
            entry(4, 14_000, "+2"),
            entry(3, 13_000),
            entry(2, 12_000),
            entry(1, 11_000),
        ]);

        expect(result.ao5).toEqual({status: "value", valueMs: 13_000});
    });

    it("marks Ao5 or Ao12 as DNF when its newest rolling window has at least two DNFs", () => {
        const ao5 = calculateWindowStatistics([
            entry(5, null, "dnf"), entry(4, null, "dnf"), entry(3, 13_000), entry(2, 12_000), entry(1, 11_000),
        ]);
        expect(ao5.ao5).toEqual({status: "dnf", valueMs: null});

        const ao12 = calculateWindowStatistics(Array.from({length: 12}, (_, index) =>
            index < 2 ? entry(12 - index, null, "dnf") : entry(12 - index, 10_000 + index * 1000)
        ));
        expect(ao12.ao12).toEqual({status: "dnf", valueMs: null});
    });

    it("computes Ao5 and Ao12 from only the newest five and twelve selected solves", () => {
        const newestFirst = Array.from({length: 12}, (_, index) => entry(12 - index, 10_000 + index * 1000));
        const result = calculateWindowStatistics(newestFirst);

        expect(result.ao5).toEqual({status: "value", valueMs: 12_000});
        expect(result.ao12).toEqual({status: "value", valueMs: 15_500});
    });
});

describe("calculateRollingMetricSeries", () => {
    it("associates each Ao5 value with its ending solve and follows WCA DNF windows", () => {
        const solves = [
            entry(5, 50_000),
            entry(4, 40_000),
            entry(3, 30_000),
            entry(2, 20_000),
            entry(1, 10_000),
            entry(0, null, "dnf"),
            entry(-1, null, "dnf"),
        ];

        const series = calculateRollingMetricSeries(solves, 5);

        expect(series.slice(3).every((point) => point.status === "insufficient")).toBe(true);
        expect(series[0]).toEqual({status: "value", valueMs: 30_000});
        expect(series[1]).toEqual({status: "value", valueMs: 30_000});
        expect(series[2]).toEqual({status: "dnf", valueMs: null});
    });

    it("treats a single DNF as the removed high result in Ao12", () => {
        const solves = Array.from({length: 12}, (_, index) =>
            entry(12 - index, index === 0 ? null : 10_000 + index * 1_000, index === 0 ? "dnf" : "none"));

        expect(calculateRollingMetricSeries(solves, 12)[0]).toEqual({status: "value", valueMs: 16_500});
    });
});

describe("calculateRollingWindowBreakdown", () => {
    it("marks fastest and slowest as dropped and uses official +2 times", () => {
        const result = calculateRollingWindowBreakdown([
            entry(5, 12_000, "+2"),
            entry(4, 9_000),
            entry(3, 11_000),
            entry(2, 14_000),
            entry(1, 13_000),
        ], 5);

        expect(result.average).toEqual({status: "value", valueMs: 12_000});
        expect(result.solves.map(({disposition}) => disposition)).toEqual([
            "counted", "dropped-fastest", "counted", "dropped-slowest", "counted",
        ]);
    });

    it("drops a single DNF as slowest and marks multiple-DNF windows without a numeric average", () => {
        const oneDnf = calculateRollingWindowBreakdown([
            entry(5, null, "dnf"), entry(4, 9_000), entry(3, 11_000), entry(2, 14_000), entry(1, 13_000),
        ], 5);
        expect(oneDnf.average).toEqual({status: "value", valueMs: 12_667});
        expect(oneDnf.solves[0].disposition).toBe("dropped-slowest");

        const multipleDnfs = calculateRollingWindowBreakdown([
            entry(5, null, "dnf"), entry(4, null, "dnf"), entry(3, 11_000), entry(2, 14_000), entry(1, 13_000),
        ], 5);
        expect(multipleDnfs.average).toEqual({status: "dnf", valueMs: null});
        expect(multipleDnfs.solves.map(({disposition}) => disposition)).toEqual([
            "dnf", "dnf", "not-counted", "not-counted", "not-counted",
        ]);
    });

    it("keeps tied exclusions deterministic and identifies partial windows", () => {
        const tied = calculateRollingWindowBreakdown([
            entry(5, 10_000), entry(4, 10_000), entry(3, 10_000), entry(2, 10_000), entry(1, 10_000),
        ], 5);
        expect(tied.solves.map(({disposition}) => disposition)).toEqual([
            "dropped-fastest", "dropped-slowest", "counted", "counted", "counted",
        ]);

        const partial = calculateRollingWindowBreakdown([entry(2, 12_000), entry(1, 11_000)], 5);
        expect(partial.average).toEqual({status: "insufficient", valueMs: null});
        expect(partial.solves.map(({disposition}) => disposition)).toEqual(["not-counted", "not-counted"]);
    });
});
