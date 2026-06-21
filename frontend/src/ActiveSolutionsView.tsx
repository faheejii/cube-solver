import {
  Ban,
  Eye,
  LoaderCircle,
  RefreshCw,
  Trash2,
  X,
} from "lucide-react";
import { crossFaceLabel, f2lModeLabel } from "./format";
import type { SolutionProcess } from "./types";

type Props = {
  processes: SolutionProcess[];
  onCancel: (process: SolutionProcess) => void;
  onRetry: (process: SolutionProcess) => void;
  onPreview: (process: SolutionProcess) => void;
  onDismiss: (processId: string) => void;
  onClearFinished: () => void;
};

export default function ActiveSolutionsView({
  processes,
  onCancel,
  onRetry,
  onPreview,
  onDismiss,
  onClearFinished,
}: Props) {
  const active = processes
    .filter((process) => process.status === "queued" || process.status === "running")
    .sort((left, right) => left.createdAt - right.createdAt);
  const finished = processes
    .filter((process) => process.status !== "queued" && process.status !== "running")
    .sort((left, right) => right.updatedAt - left.updatedAt);

  return (
    <section className="dashboard-process-view">
      <header className="history-view-header">
        <div>
          <span className="dashboard-kicker">Solver queue</span>
          <h1>Active Solutions</h1>
          <p>Monitor, terminate, and review solution work from this browser session.</p>
        </div>
        {finished.length > 0 ? (
          <button className="dashboard-secondary-button" type="button" onClick={onClearFinished}>
            <Trash2 size={16} />
            Clear finished
          </button>
        ) : null}
      </header>

      {processes.length === 0 ? (
        <div className="history-empty-state">
          <strong>No solution processes</strong>
          <span>Fast and Optimized solves will appear here while they run.</span>
        </div>
      ) : null}

      {active.length > 0 ? (
        <section className="process-section">
          <div className="process-section-heading">
            <span>Active</span>
            <strong>{active.length}</strong>
          </div>
          <div className="process-list">
            {active.map((process) => (
              <ProcessCard
                key={process.id}
                process={process}
                onCancel={onCancel}
                onRetry={onRetry}
                onPreview={onPreview}
                onDismiss={onDismiss}
              />
            ))}
          </div>
        </section>
      ) : null}

      {finished.length > 0 ? (
        <section className="process-section">
          <div className="process-section-heading">
            <span>Recent</span>
            <strong>{finished.length}</strong>
          </div>
          <div className="process-list">
            {finished.map((process) => (
              <ProcessCard
                key={process.id}
                process={process}
                onCancel={onCancel}
                onRetry={onRetry}
                onPreview={onPreview}
                onDismiss={onDismiss}
              />
            ))}
          </div>
        </section>
      ) : null}
    </section>
  );
}

function ProcessCard({
  process,
  onCancel,
  onRetry,
  onPreview,
  onDismiss,
}: {
  process: SolutionProcess;
  onCancel: (process: SolutionProcess) => void;
  onRetry: (process: SolutionProcess) => void;
  onPreview: (process: SolutionProcess) => void;
  onDismiss: (processId: string) => void;
}) {
  const active = process.status === "queued" || process.status === "running";
  const optimized = process.request.f2lMode === "optimized";

  return (
    <article className={`process-card status-${process.status}`}>
      <div className="process-card-topline">
        <div>
          <span className={`process-status status-${process.status}`}>
            {active ? <LoaderCircle size={14} /> : null}
            {process.cancelling ? "Cancelling" : statusLabel(process.status)}
          </span>
          <span className="process-source">{sourceLabel(process.source)}</span>
        </div>
        {!active ? (
          <button
            className="process-icon-button"
            type="button"
            onClick={() => onDismiss(process.id)}
            aria-label="Dismiss process"
          >
            <X size={16} />
          </button>
        ) : null}
      </div>

      <p className="process-scramble">{process.request.scramble}</p>

      <div className="process-metadata">
        <span>{f2lModeLabel(process.request.f2lMode)}</span>
        <span>Cross {crossFaceLabel(process.request.crossFace)}</span>
        <span>{formatElapsed(process.createdAt, process.updatedAt, active)}</span>
      </div>

      {active ? (
        <div className="process-progress">
          <div className="process-progress-bar"><i /></div>
          <p>
            {optimized
              ? process.candidatesEvaluated > 0
                ? `${process.candidatesEvaluated}/${process.completedCandidates} candidates · Best total ${moveLabel(process.bestTotalMoves)}`
                : `${process.statesExplored.toLocaleString()} states · ${process.statesPruned.toLocaleString()} pruned · Best F2L ${moveLabel(process.bestMoves)}`
              : process.status === "queued"
                ? "Waiting for a Fast worker"
                : "Computing Fast solution"}
          </p>
        </div>
      ) : null}

      {process.error && process.status !== "cancelled" ? (
        <p className="process-error">{process.error}</p>
      ) : null}

      <div className="process-actions">
        {active ? (
          <button
            className="dashboard-secondary-button danger compact"
            type="button"
            onClick={() => onCancel(process)}
            disabled={process.cancelling || process.jobId === null}
          >
            <Ban size={15} />
            {process.cancelling ? "Terminating" : "Terminate"}
          </button>
        ) : null}
        {process.status === "completed" && process.result ? (
          <button
            className="dashboard-secondary-button compact"
            type="button"
            onClick={() => onPreview(process)}
          >
            <Eye size={15} />
            View solution
          </button>
        ) : null}
        {(process.status === "failed" || process.status === "cancelled") ? (
          <button
            className="dashboard-secondary-button compact"
            type="button"
            onClick={() => onRetry(process)}
          >
            <RefreshCw size={15} />
            Retry
          </button>
        ) : null}
      </div>
    </article>
  );
}

function sourceLabel(source: SolutionProcess["source"]): string {
  switch (source) {
    case "history":
      return "History";
    case "background":
      return "Saved solve";
    default:
      return "Timer";
  }
}

function statusLabel(status: SolutionProcess["status"]): string {
  return status.charAt(0).toUpperCase() + status.slice(1);
}

function moveLabel(moves: number): string {
  return moves < 0 ? "pending" : `${moves} moves`;
}

function formatElapsed(createdAt: number, updatedAt: number, active: boolean): string {
  const end = active ? Date.now() : updatedAt;
  const seconds = Math.max(0, Math.round((end - createdAt) / 1_000));
  if (seconds < 60) {
    return `${seconds}s`;
  }
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
}
