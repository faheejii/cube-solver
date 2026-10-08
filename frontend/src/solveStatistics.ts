import type {RollingAverage, SolveHistoryEntry, SolveStatistics} from "./types";

export type RollingMetricPoint = {
    status: RollingAverage["status"];
    valueMs: number | null;
};

export type RollingWindowDisposition = "counted" | "dropped-fastest" | "dropped-slowest" | "dnf" | "not-counted";

export type RollingWindowBreakdown = {
    average: RollingAverage;
    required: 5 | 12;
    dnfCount: number;
    solves: Array<{entry: SolveHistoryEntry; disposition: RollingWindowDisposition}>;
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

/** Calculates the newest Ao5/Ao12 and identifies the exact rows contributing to it. */
export function calculateRollingWindowBreakdown(
    newestFirst: SolveHistoryEntry[],
    size: 5 | 12,
): RollingWindowBreakdown {
    const window = newestFirst.slice(0, size);
    const dnfs = window.filter(isDnf);
    const average = calculateRollingAverage(window, size);
    const solves: RollingWindowBreakdown["solves"] = window.map((entry) => ({
        entry,
        disposition: isDnf(entry) ? "dnf" as const : "counted" as const,
    }));

    if (window.length < size) {
        return {
            average,
            required: size,
            dnfCount: dnfs.length,
            solves: solves.map((solve) => ({
                ...solve,
                disposition: (isDnf(solve.entry) ? "dnf" : "not-counted") as RollingWindowDisposition,
            })),
        };
    }

    if (dnfs.length >= 2) {
        return {
            average,
            required: size,
            dnfCount: dnfs.length,
            solves: solves.map((solve) => ({
                ...solve,
                disposition: (isDnf(solve.entry) ? "dnf" : "not-counted") as RollingWindowDisposition,
            })),
        };
    }

    if (dnfs.length === 1) {
        const dnfIndex = solves.findIndex((solve) => isDnf(solve.entry));
        solves[dnfIndex] = {...solves[dnfIndex], disposition: "dropped-slowest"};
    }

    const valid = solves
        .map((solve, index) => ({solve, index}))
        .filter(({solve}) => !isDnf(solve.entry));
    const fastest = valid.reduce((best, current) =>
        current.solve.entry.officialMs! < best.solve.entry.officialMs! ? current : best,
    );
    solves[fastest.index] = {...solves[fastest.index], disposition: "dropped-fastest"};

    if (dnfs.length === 0) {
        const slowest = valid
            .filter(({index}) => index !== fastest.index)
            .reduce((worst, current) =>
                current.solve.entry.officialMs! > worst.solve.entry.officialMs! ? current : worst,
            );
        solves[slowest.index] = {...solves[slowest.index], disposition: "dropped-slowest"};
    }

    return {average, required: size, dnfCount: dnfs.length, solves};
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
