import {lazy, Suspense, useRef, useState} from "react";
import {ArrowUpRight, LoaderCircle, RefreshCw, Trash2} from "lucide-react";
import DeferredCubePreview from "./DeferredCubePreview";
import {formatHistoryTime} from "./format";
import StatisticsSummary from "./StatisticsSummary";
import type {SolveHistoryEntry, SolveStatistics} from "./types";

const StatisticsModal = lazy(() => import("./StatisticsModal"));

type Props = {
    entries: SolveHistoryEntry[];
    loading: boolean;
    loadingMore: boolean;
    error: string | null;
    hasMore: boolean;
    solveCount: number | null;
    statistics: SolveStatistics | null;
    statisticsLoading: boolean;
    deletingSolveId: number | null;
    onRefresh: () => void;
    onLoadMore: () => void;
    onOpenSolve: (entry: SolveHistoryEntry) => void;
    onDeleteSolve: (entry: SolveHistoryEntry) => void;
};

export default function HistoryView({
                                        entries,
                                        loading,
                                        loadingMore,
                                        error,
                                        hasMore,
                                        solveCount,
                                        statistics,
                                        statisticsLoading,
                                        deletingSolveId,
                                        onRefresh,
                                        onLoadMore,
                                        onOpenSolve,
                                        onDeleteSolve,
                                    }: Props) {
    const [statisticsOpen, setStatisticsOpen] = useState(false);
    const statisticsMoreButtonRef = useRef<HTMLButtonElement>(null);

    function closeStatistics() {
        setStatisticsOpen(false);
        window.requestAnimationFrame(() => statisticsMoreButtonRef.current?.focus());
    }

    return (
        <section className="dashboard-history-view">
            <header className="history-view-header">
                <div>
                    <h1>History</h1>
                    <p>Review times, scrambles, and saved CFOP solution variants.</p>
                </div>
                <button className="dashboard-secondary-button" type="button" onClick={onRefresh}>
                    <RefreshCw size={16}/>
                    Refresh
                </button>
            </header>

            <section className="history-statistics" aria-label="Solve statistics">
                <div className="history-statistics-header">
                    <span>Statistics</span>
                    <button
                        ref={statisticsMoreButtonRef}
                        type="button"
                        aria-label="More statistics"
                        onClick={() => setStatisticsOpen(true)}
                    >
                        More <ArrowUpRight size={14}/>
                    </button>
                </div>
                <StatisticsSummary statistics={statistics} loading={statisticsLoading}/>
            </section>

            {statisticsOpen ? (
                <Suspense fallback={null}>
                    <StatisticsModal
                        statistics={statistics}
                        onClose={closeStatistics}
                        onOpenSolve={onOpenSolve}
                    />
                </Suspense>
            ) : null}

            {error ? <div className="dashboard-alert error">{error}</div> : null}
            {loading ? <div className="history-loading"><LoaderCircle size={22}/> Loading history</div> : null}
            {!loading && entries.length === 0 ? (
                <div className="history-empty-state">
                    <strong>No solves yet</strong>
                    <span>Complete a timed solve and it will appear here.</span>
                </div>
            ) : null}

            <div className="history-table">
                {entries.map((entry, index) => (
                    <article className="history-table-row" key={entry.id}>
                        <button
                            className="history-row-open"
                            type="button"
                            onClick={() => onOpenSolve(entry)}
                            disabled={deletingSolveId === entry.id}
                            aria-label={`Open solution for solve ${formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}`}
                        >
                            <span className="history-index">
                                {String(solveCount === null ? index + 1 : solveCount - index).padStart(2, "0")}
                            </span>
                            <span className="history-scramble-preview" aria-hidden="true">
                                <DeferredCubePreview
                                    displayMode="3d"
                                    setupAlgorithm={entry.scramble}
                                    compact
                                />
                            </span>
                            <span className="history-time-cell">
                                <strong>{formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}</strong>
                                <small>{new Date(entry.createdAt).toLocaleString()}</small>
                            </span>
                            <span className="history-scramble">{entry.scramble}</span>
                        </button>
                        <div className="history-row-actions">
                            <button
                                className="history-delete-button"
                                type="button"
                                onClick={(event) => {
                                    event.stopPropagation();
                                    onDeleteSolve(entry);
                                }}
                                disabled={deletingSolveId !== null}
                                aria-label={`Delete solve ${formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}`}
                                title="Delete solve"
                            >
                                {deletingSolveId === entry.id ? <LoaderCircle size={15}/> : <Trash2 size={15}/>}
                            </button>
                        </div>
                    </article>
                ))}
            </div>

            {hasMore ? (
                <button
                    className="history-load-more"
                    type="button"
                    onClick={onLoadMore}
                    disabled={loadingMore}
                >
                    {loadingMore ? "Loading…" : "Load more"}
                </button>
            ) : null}
        </section>
    );
}
