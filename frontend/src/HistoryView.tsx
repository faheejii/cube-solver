import { Eye, LoaderCircle, RefreshCw, Trash2 } from "lucide-react";
import { crossFaceLabel, formatHistoryTime } from "./format";
import type { SolveHistoryEntry } from "./types";

type Props = {
  entries: SolveHistoryEntry[];
  loading: boolean;
  loadingMore: boolean;
  error: string | null;
  hasMore: boolean;
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
  deletingSolveId,
  onRefresh,
  onLoadMore,
  onOpenSolve,
  onDeleteSolve,
}: Props) {
  return (
    <section className="dashboard-history-view">
      <header className="history-view-header">
        <div>
          <span className="dashboard-kicker">Solve archive</span>
          <h1>History</h1>
          <p>Review times, scrambles, and saved CFOP solution variants.</p>
        </div>
        <button className="dashboard-secondary-button" type="button" onClick={onRefresh}>
          <RefreshCw size={16} />
          Refresh
        </button>
      </header>

      {error ? <div className="dashboard-alert error">{error}</div> : null}
      {loading ? <div className="history-loading"><LoaderCircle size={22} /> Loading history</div> : null}
      {!loading && entries.length === 0 ? (
        <div className="history-empty-state">
          <strong>No solves yet</strong>
          <span>Complete a timed solve and it will appear here.</span>
        </div>
      ) : null}

      <div className="history-table">
        {entries.map((entry, index) => (
          <article className="history-table-row" key={entry.id}>
            <span className="history-index">{String(index + 1).padStart(2, "0")}</span>
            <div className="history-time-cell">
              <strong>{formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}</strong>
              <small>{new Date(entry.createdAt).toLocaleString()}</small>
            </div>
            <p>{entry.scramble}</p>
            <div className="history-variants">
              <span>Cross {crossFaceLabel(entry.crossFaceRequested)}</span>
              <span>{entry.fastCrossFaceRequested ? "Fast saved" : "Fast missing"}</span>
              <span>{entry.optimizedCrossFaceRequested ? "Optimized saved" : "Optimized missing"}</span>
            </div>
            <div className="history-row-actions">
              <button
                className="dashboard-secondary-button compact"
                type="button"
                onClick={() => onOpenSolve(entry)}
                disabled={deletingSolveId === entry.id}
              >
                <Eye size={15} />
                Solution
              </button>
              <button
                className="history-delete-button"
                type="button"
                onClick={() => onDeleteSolve(entry)}
                disabled={deletingSolveId !== null}
                aria-label={`Delete solve ${formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}`}
                title="Delete solve"
              >
                {deletingSolveId === entry.id ? <LoaderCircle size={15} /> : <Trash2 size={15} />}
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
