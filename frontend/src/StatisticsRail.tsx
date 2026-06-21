import { ArrowUpRight } from "lucide-react";
import { formatHistoryTime, formatMetricTime, formatRollingAverage } from "./format";
import type { SolveHistoryEntry, SolveStatistics } from "./types";

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
            More <ArrowUpRight size={14} />
          </button>
        </div>
        <div className="statistics-grid">
          <Stat label="Best" value={formatMetricTime(statistics?.bestMs ?? null)} accent="blue" />
          <Stat label="Ao5" value={formatRollingAverage(statistics?.ao5 ?? null)} accent="violet" />
          <Stat label="Ao12" value={formatRollingAverage(statistics?.ao12 ?? null)} accent="cyan" />
          <Stat label="Average" value={formatMetricTime(statistics?.averageMs ?? null)} />
          <Stat label="Solves" value={loading ? "…" : String(statistics?.solveCount ?? 0)} />
          <Stat label="DNFs" value={loading ? "…" : String(statistics?.dnfCount ?? 0)} accent="amber" />
        </div>
      </section>

      <section className="rail-card recent-card">
        <div className="rail-card-header">
          <span>Recent solves</span>
          <button type="button" onClick={onOpenHistory}>
            More <ArrowUpRight size={14} />
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

function Stat({
  label,
  value,
  accent = "",
}: {
  label: string;
  value: string;
  accent?: string;
}) {
  return (
    <div className={`rail-stat ${accent ? `accent-${accent}` : ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
