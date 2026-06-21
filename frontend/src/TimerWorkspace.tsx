import {
  Check,
  Eye,
  Pencil,
  RefreshCw,
  Sparkles,
  X,
} from "lucide-react";
import ScrambleCube from "./ScrambleCube";
import { formatMetricTime, formatRollingAverage } from "./format";
import type { SolveResponse, SolveStatistics } from "./types";

type FaceOption = {
  value: string;
  label: string;
};

type Props = {
  scramble: string;
  draftScramble: string;
  crossFace: string;
  f2lMode: "greedy" | "optimized";
  faceOptions: readonly FaceOption[];
  isEditingScramble: boolean;
  generatingScramble: boolean;
  attemptLocked: boolean;
  solutionStatus: string;
  result: SolveResponse | null;
  statistics: SolveStatistics | null;
  timerPhase: string;
  timerValue: string;
  timerHint: string;
  timerDetail: string;
  onDraftChange: (value: string) => void;
  onCrossFaceChange: (value: string) => void;
  onF2LModeChange: (value: "greedy" | "optimized") => void;
  onGenerateScramble: () => void;
  onEnterEdit: () => void;
  onCancelEdit: () => void;
  onSaveEdit: () => void;
  onShowSolution: () => void;
  onTimerPointerDown: () => void;
  onTimerPointerUp: () => void;
};

export default function TimerWorkspace({
  scramble,
  draftScramble,
  crossFace,
  f2lMode,
  faceOptions,
  isEditingScramble,
  generatingScramble,
  attemptLocked,
  solutionStatus,
  result,
  statistics,
  timerPhase,
  timerValue,
  timerHint,
  timerDetail,
  onDraftChange,
  onCrossFaceChange,
  onF2LModeChange,
  onGenerateScramble,
  onEnterEdit,
  onCancelEdit,
  onSaveEdit,
  onShowSolution,
  onTimerPointerDown,
  onTimerPointerUp,
}: Props) {
  return (
    <section className="timer-workspace">
      <header className="workspace-toolbar">
        <div className="toolbar-group">
          <span className="toolbar-product">3×3</span>
          <span className="toolbar-separator" />
          <label>
            <span>Cross</span>
            <select
              value={crossFace}
              onChange={(event) => onCrossFaceChange(event.target.value)}
              disabled={attemptLocked}
            >
              {faceOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <span className="toolbar-separator" />
          <div className="workspace-mode-switch" aria-label="F2L mode">
            <button
              className={f2lMode === "greedy" ? "active" : ""}
              type="button"
              onClick={() => onF2LModeChange("greedy")}
              disabled={attemptLocked}
            >
              Fast
            </button>
            <button
              className={f2lMode === "optimized" ? "active" : ""}
              type="button"
              onClick={() => onF2LModeChange("optimized")}
              disabled={attemptLocked}
            >
              Optimized
            </button>
          </div>
        </div>
        <span className={`solver-state state-${solutionStatus}`}>
          <Sparkles size={14} />
          {solutionStatus === "loading" ? "Computing" : solutionStatus === "ready" ? "Solution ready" : "Solver ready"}
        </span>
      </header>

      <section className="dashboard-scramble-card">
        <span className="dashboard-kicker">{isEditingScramble ? "Edit scramble" : "Scramble"}</span>
        {isEditingScramble ? (
          <textarea
            rows={2}
            value={draftScramble}
            onChange={(event) => onDraftChange(event.target.value)}
            autoFocus
          />
        ) : (
          <p>{scramble || "Preparing scramble…"}</p>
        )}
        <div className="dashboard-scramble-actions">
          <button
            type="button"
            onClick={onGenerateScramble}
            disabled={generatingScramble || attemptLocked}
            aria-label="Generate new scramble"
            title="Generate new scramble"
          >
            <RefreshCw size={18} />
          </button>
          {isEditingScramble ? (
            <>
              <button type="button" onClick={onCancelEdit} aria-label="Cancel edit">
                <X size={18} />
              </button>
              <button type="button" onClick={onSaveEdit} aria-label="Save scramble">
                <Check size={18} />
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={onEnterEdit}
              disabled={attemptLocked}
              aria-label="Edit scramble"
            >
              <Pencil size={17} />
            </button>
          )}
        </div>
      </section>

      <section
        className={`dashboard-timer-stage phase-${timerPhase}`}
        onPointerDown={onTimerPointerDown}
        onPointerUp={onTimerPointerUp}
        onPointerCancel={onTimerPointerUp}
        aria-label="Solve timer"
      >
        <div className="dashboard-timer-number">{timerValue}</div>
        <div className="dashboard-inline-stats">
          <InlineStat label="Best" value={formatMetricTime(statistics?.bestMs ?? null)} accent="blue" />
          <InlineStat label="Ao5" value={formatRollingAverage(statistics?.ao5 ?? null)} accent="violet" />
          <InlineStat label="Ao12" value={formatRollingAverage(statistics?.ao12 ?? null)} accent="cyan" />
        </div>
        <ScrambleCube scramble={scramble} />
        <div className="timer-start-capsule">
          <strong>{timerHint}</strong>
          <span>{timerDetail}</span>
        </div>
      </section>

      <footer className="workspace-actions">
        <button
          className="dashboard-primary-button"
          type="button"
          onClick={onShowSolution}
          disabled={solutionStatus !== "ready" || result === null}
        >
          <Eye size={17} />
          Show solution
        </button>
      </footer>
    </section>
  );
}

function InlineStat({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent: string;
}) {
  return (
    <div className={`inline-stat accent-${accent}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
