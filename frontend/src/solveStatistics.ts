import type {RollingAverage, SolveHistoryEntry, SolveStatistics} from "./types";

export type RollingMetricPoint = {
    status: RollingAverage["status"];
    valueMs: number | null;
};

/** Calculates the same summary fields as the API, scoped to a newest-first history selection. */
export function calculateWindowStatistics(newestFirst: SolveHistoryEntry[]): SolveStatistics {
    const times = newestFirst
        .filter((entry) => !isDnf(entry))
        .map((entry) => entry.officialMs!)
        .filter((time): time is number => time !== null);

    return {
        solveCount: newestFirst.length,
        dnfCount: newestFirst.filter(isDnf).length,
        bestMs: times.length > 0 ? times.reduce((best, time) => Math.min(best, time)) : null,
        averageMs: times.length > 0
            ? Math.round(times.reduce((total, time) => total + time, 0) / times.length)
            : null,
        ao5: calculateRollingAverage(newestFirst.slice(0, 5), 5),
        ao12: calculateRollingAverage(newestFirst.slice(0, 12), 12),
        recentSolves: newestFirst.slice(0, 5),
    };
}

/** Returns one WCA-style rolling average for every solve, indexed newest-first by its ending solve. */
export function calculateRollingMetricSeries(
    newestFirst: SolveHistoryEntry[],
    size: 5 | 12,
): RollingMetricPoint[] {
    return newestFirst.map((_, index) => index + size > newestFirst.length
        ? {status: "insufficient", valueMs: null}
        : calculateRollingAverage(newestFirst.slice(index, index + size), size));
}

function calculateRollingAverage(newestFirst: SolveHistoryEntry[], size: number): RollingAverage {
    if (newestFirst.length < size) return {status: "insufficient", valueMs: null};

    const window = newestFirst.slice(0, size);
    const dnfCount = window.filter(isDnf).length;
    if (dnfCount >= 2) return {status: "dnf", valueMs: null};

    const times = window
        .filter((entry) => !isDnf(entry))
        .map((entry) => entry.officialMs!)
        .filter((time): time is number => time !== null)
        .sort((left, right) => left - right);

    if (dnfCount === 0) times.pop();
    times.shift();

    return {
        status: "value",
        valueMs: Math.round(times.reduce((total, time) => total + time, 0) / times.length),
    };
}

function isDnf(entry: SolveHistoryEntry): boolean {
    return entry.dnf || entry.penalty === "dnf" || entry.officialMs === null;
}
