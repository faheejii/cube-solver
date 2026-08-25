import {formatMetricTime, formatRollingAverage} from "./format";
import type {SolveStatistics} from "./types";

type Props = {
    statistics: SolveStatistics | null;
    loading: boolean;
};

export default function StatisticsSummary({statistics, loading}: Props) {
    return (
        <div className="statistics-grid">
            <Stat label="Best" value={formatMetricTime(statistics?.bestMs ?? null)} accent="blue"/>
            <Stat label="Ao5" value={formatRollingAverage(statistics?.ao5 ?? null)} accent="violet"/>
            <Stat label="Ao12" value={formatRollingAverage(statistics?.ao12 ?? null)} accent="cyan"/>
            <Stat label="Average" value={formatMetricTime(statistics?.averageMs ?? null)}/>
            <Stat label="Solves" value={loading ? "…" : String(statistics?.solveCount ?? 0)}/>
            <Stat label="DNFs" value={loading ? "…" : String(statistics?.dnfCount ?? 0)} accent="amber"/>
        </div>
    );
}

function Stat({label, value, accent = ""}: {label: string; value: string; accent?: string}) {
    return (
        <div className={`rail-stat ${accent ? `accent-${accent}` : ""}`}>
            <span>{label}</span>
            <strong>{value}</strong>
        </div>
    );
}
