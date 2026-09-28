import {lazy, Suspense, useRef, useState} from "react";
import {ArrowUpRight, LoaderCircle} from "lucide-react";
import type {UIEvent} from "react";
import {formatHistoryTime} from "./format";
import StatisticsSummary from "./StatisticsSummary";
import type {SolveHistoryEntry, SolveStatistics} from "./types";

const StatisticsModal = lazy(() => import("./StatisticsModal"));

type HistoryStatus = "idle" | "loading" | "ready" | "error";

type Props = {
    statistics: SolveStatistics | null;
    loading: boolean;
    entries: SolveHistoryEntry[];
    historyStatus: HistoryStatus;
    historyError: string | null;
    loadingMore: boolean;
    hasMore: boolean;
    solveCount: number | null;
    onLoadMore: () => void;
    onRetry: () => void;
    onOpenHistory: () => void;
    onOpenSolve: (entry: SolveHistoryEntry) => void;
};

export default function StatisticsRail({
                                           statistics,
                                           loading,
                                           entries,
                                           historyStatus,
                                           historyError,
                                           loadingMore,
                                           hasMore,
                                           solveCount,
                                           onLoadMore,
                                           onRetry,
                                           onOpenHistory,
                                           onOpenSolve,
                                       }: Props) {
    const [statisticsOpen, setStatisticsOpen] = useState(false);
    const statisticsMoreButtonRef = useRef<HTMLButtonElement>(null);

    function handleListScroll(event: UIEvent<HTMLDivElement>) {
        const list = event.currentTarget;
        if (hasMore && !loadingMore && list.scrollHeight - list.scrollTop - list.clientHeight < 64) {
            onLoadMore();
        }
    }

    function closeStatistics() {
        setStatisticsOpen(false);
        const restoreFocus = () => statisticsMoreButtonRef.current?.focus();
        if (typeof window.requestAnimationFrame === "function") {
            window.requestAnimationFrame(restoreFocus);
        } else {
            queueMicrotask(restoreFocus);
        }
    }

    return (
        <aside className="statistics-rail">
            <section className="rail-card statistics-card">
                <div className="rail-card-header">
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
                <StatisticsSummary statistics={statistics} loading={loading}/>
            </section>

            <section className="rail-card recent-card">
                <div className="rail-card-header">
                    <span>Solves</span>
                    <button type="button" onClick={onOpenHistory}>
                        More <ArrowUpRight size={14}/>
                    </button>
                </div>
                <div className="rail-recent-list" role="region" aria-label="Solves list" onScroll={handleListScroll}>
                    {historyStatus === "loading" && entries.length === 0 ? (
                        <p className="rail-history-status"><LoaderCircle size={15}/> Loading solves…</p>
                    ) : null}
                    {historyStatus === "error" && entries.length === 0 ? (
                        <div className="rail-history-status error">
                            <p>{historyError ?? "Could not load solves."}</p>
                            <button type="button" onClick={onRetry}>Retry</button>
                        </div>
                    ) : null}
                    {historyStatus === "ready" && entries.length === 0 ? <p>No solves yet</p> : null}
                    {entries.map((entry, index) => (
                        <button
                            className="rail-recent-row"
                            type="button"
                            key={entry.id}
                            onClick={() => onOpenSolve(entry)}
                        >
                            <span>#{solveCount !== null ? solveCount - index : entry.id}</span>
                            <strong>{formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}</strong>
                            <small>{new Date(entry.createdAt).toLocaleDateString()}</small>
                        </button>
                    ))}
                    {historyError && entries.length > 0 ? (
                        <div className="rail-history-status error">
                            <p>{historyError}</p>
                            <button type="button" onClick={onRetry}>Retry</button>
                        </div>
                    ) : null}
                    {loadingMore ? <p className="rail-history-status"><LoaderCircle size={15}/> Loading more…</p> : null}
                    {hasMore && entries.length > 0 && !historyError ? (
                        <button className="rail-load-more" type="button" onClick={onLoadMore} disabled={loadingMore}>
                            Load more
                        </button>
                    ) : null}
                </div>
            </section>
            {statisticsOpen ? (
                <Suspense fallback={null}>
                    <StatisticsModal statistics={statistics} onClose={closeStatistics} onOpenSolve={onOpenSolve}/>
                </Suspense>
            ) : null}
        </aside>
    );
}
