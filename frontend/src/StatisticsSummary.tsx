import {useState} from "react";
import {LoaderCircle} from "lucide-react";
import {formatMetricTime} from "./format";
import RollingAverageStat from "./RollingAverageStat";
import type {SolveHistoryEntry, SolveStatistics} from "./types";

type Props = {
    statistics: SolveStatistics | null;
    loading: boolean;
    onOpenBestSolve?: (bestMs: number) => Promise<void> | void;
    onOpenSolve: (entry: SolveHistoryEntry, solveNumber?: number, filteredResult?: boolean, returnFocusTo?: HTMLElement | SVGElement) => void;
};

export default function StatisticsSummary({statistics, loading, onOpenBestSolve, onOpenSolve}: Props) {
    const [openingBest, setOpeningBest] = useState(false);
    const [openBestError, setOpenBestError] = useState<string | null>(null);
    const bestMs = statistics?.bestMs ?? null;

    async function handleOpenBest() {
        if (bestMs === null || !onOpenBestSolve || openingBest) return;
        setOpeningBest(true);
        setOpenBestError(null);
        try {
            await onOpenBestSolve(bestMs);
        } catch {
            setOpenBestError("Could not open the best solve. Try again.");
        } finally {
            setOpeningBest(false);
        }
    }

    return (
        <>
            <div className="statistics-grid">
                <Stat
                    label="Best"
                    value={formatMetricTime(bestMs)}
                    accent="blue"
                    onClick={onOpenBestSolve ? () => void handleOpenBest() : undefined}
                    disabled={loading || bestMs === null || openingBest}
                    busy={openingBest}
                />
                <RollingAverageStat
                    size={5}
                    value={statistics?.ao5 ?? null}
                    solveCount={statistics?.solveCount ?? null}
                    onOpenSolve={onOpenSolve}
                    className="rail-stat accent-violet"
                />
                <RollingAverageStat
                    size={12}
                    value={statistics?.ao12 ?? null}
                    solveCount={statistics?.solveCount ?? null}
                    onOpenSolve={onOpenSolve}
                    className="rail-stat accent-cyan"
                />
                <Stat label="Mean" value={formatMetricTime(statistics?.averageMs ?? null)}/>
                <Stat label="Solves" value={loading ? "…" : String(statistics?.solveCount ?? 0)}/>
                <Stat label="DNFs" value={loading ? "…" : String(statistics?.dnfCount ?? 0)} accent="amber"/>
            </div>
            {openBestError ? <p className="statistics-summary-error" role="alert">{openBestError}</p> : null}
        </>
    );
}

function Stat({
                  label,
                  value,
                  accent = "",
                  onClick,
                  disabled = false,
                  busy = false,
              }: {
    label: string;
    value: string;
    accent?: string;
    onClick?: () => void;
    disabled?: boolean;
    busy?: boolean;
}) {
    const className = `rail-stat${accent ? ` accent-${accent}` : ""}${onClick ? " rail-stat-action" : ""}`;
    const content = <><span>{label}</span><strong>{value}</strong>{busy ? <LoaderCircle className="best-solve-loading" size={13} aria-hidden="true"/> : null}</>;

    return onClick ? (
        <button
            className={className}
            type="button"
            onClick={onClick}
            disabled={disabled}
            aria-label={`Open best solve ${value}`}
            aria-busy={busy}
            title="Open solve with best time"
        >
            {content}
        </button>
    ) : <div className={className}>{content}</div>;
}
