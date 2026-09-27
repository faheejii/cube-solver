import type {RollingAverage, SolveHistoryEntry, SolveStatistics} from "./types";

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
        ao5: calculateRollingAverage(newestFirst, 5),
        ao12: calculateRollingAverage(newestFirst, 12),
        recentSolves: newestFirst.slice(0, 5),
    };
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
