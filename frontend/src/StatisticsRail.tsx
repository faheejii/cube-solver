import {ArrowUpRight} from "lucide-react";
import {formatHistoryTime} from "./format";
import StatisticsSummary from "./StatisticsSummary";
import type {SolveHistoryEntry, SolveStatistics} from "./types";

type Props = {
    statistics: SolveStatistics | null;
    loading: boolean;
    onOpenHistory: () => void;
    onOpenSolve: (entry: SolveHistoryEntry) => void;
};

export default function StatisticsRail({
                                           statistics,
                                           loading,
                                           onOpenHistory,
                                           onOpenSolve,
                                       }: Props) {
    const recent = statistics?.recentSolves ?? [];

    return (
        <aside className="statistics-rail">
            <section className="rail-card statistics-card">
                <div className="rail-card-header">
                    <span>Statistics</span>
                    <button type="button" onClick={onOpenHistory}>
                        More <ArrowUpRight size={14}/>
                    </button>
                </div>
                <StatisticsSummary statistics={statistics} loading={loading}/>
            </section>

            <section className="rail-card recent-card">
                <div className="rail-card-header">
                    <span>Recent solves</span>
                    <button type="button" onClick={onOpenHistory}>
                        More <ArrowUpRight size={14}/>
                    </button>
                </div>
                <div className="rail-recent-list">
                    {recent.length === 0 ? <p>No solves yet</p> : null}
                    {recent.map((entry, index) => (
                        <button
                            className="rail-recent-row"
                            type="button"
                            key={entry.id}
                            onClick={() => onOpenSolve(entry)}
                        >
                            <span>#{statistics ? statistics.solveCount - index : entry.id}</span>
                            <strong>{formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}</strong>
                            <small>{new Date(entry.createdAt).toLocaleDateString()}</small>
                        </button>
                    ))}
                </div>
            </section>
        </aside>
    );
}
