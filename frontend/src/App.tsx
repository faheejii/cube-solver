import { startTransition, useEffect, useRef, useState } from "react";
import { randomScrambleForEvent } from "cubing/scramble";
import { Check, Eye, EyeOff, ListTree, Moon, Pencil, RefreshCw, Sun, X } from "lucide-react";
import CubeAnimator from "./CubeAnimator";
import { solveCube } from "./api";
import type { SolveResponse, SolveStage } from "./types";

const DEFAULT_SCRAMBLE = "R D R' D2 R D' R'";
const FACE_OPTIONS = [
  { value: "U", label: "U" },
  { value: "D", label: "D" },
  { value: "F", label: "F" },
  { value: "B", label: "B" },
  { value: "L", label: "L" },
  { value: "R", label: "R" },
  { value: "CN", label: "Color Neutral" },
] as const;
const INSPECTION_PLUS_TWO_MS = 15_000;
const INSPECTION_DNF_MS = 17_000;

type Theme = "light" | "dark";
type SolutionStatus = "idle" | "loading" | "ready" | "error";
type TimerPhase = "idle" | "armed" | "inspection" | "running" | "stopped";
type ArmedSource = "idle" | "stopped" | "inspection" | null;
type TimerPenalty = "none" | "+2" | "dnf";
type F2LMode = "greedy" | "optimized";

function initialTheme(): Theme {
  const savedTheme = window.localStorage.getItem("cube-solver-theme");
  if (savedTheme === "light" || savedTheme === "dark") {
    return savedTheme;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export default function App() {
  const [committedScramble, setCommittedScramble] = useState("");
  const [draftScramble, setDraftScramble] = useState("");
  const [crossFace, setCrossFace] = useState("U");
  const [f2lMode, setF2LMode] = useState<F2LMode>("greedy");
  const [isEditingScramble, setIsEditingScramble] = useState(false);
  const [generatingScramble, setGeneratingScramble] = useState(false);
  const [solutionStatus, setSolutionStatus] = useState<SolutionStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<SolveResponse | null>(null);
  const [solutionVisible, setSolutionVisible] = useState(false);
  const [summaryVisible, setSummaryVisible] = useState(false);
  const [theme, setTheme] = useState<Theme>(initialTheme);
  const [timerPhase, setTimerPhase] = useState<TimerPhase>("idle");
  const [armedSource, setArmedSource] = useState<ArmedSource>(null);
  const [inspectionStartedAt, setInspectionStartedAt] = useState<number | null>(null);
  const [runStartedAt, setRunStartedAt] = useState<number | null>(null);
  const [stoppedElapsedMs, setStoppedElapsedMs] = useState<number | null>(null);
  const [finalPenalty, setFinalPenalty] = useState<TimerPenalty>("none");
  const [clockMs, setClockMs] = useState(0);
  const requestIdRef = useRef(0);

  const inspectionElapsedMs =
    inspectionStartedAt === null ? 0 : Math.max(0, clockMs - inspectionStartedAt);
  const inspectionPenalty = penaltyForInspectionElapsed(inspectionElapsedMs);
  const runningElapsedMs = runStartedAt === null ? 0 : Math.max(0, clockMs - runStartedAt);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    window.localStorage.setItem("cube-solver-theme", theme);
  }, [theme]);

  useEffect(() => {
    document.body.style.overflow = summaryVisible ? "" : "hidden";
    return () => {
      document.body.style.overflow = "";
    };
  }, [summaryVisible]);

  useEffect(() => {
    if (timerPhase !== "inspection" && timerPhase !== "running") {
      return;
    }

    let frameId = 0;
    const tick = (timestamp: number) => {
      setClockMs(timestamp);
      frameId = window.requestAnimationFrame(tick);
    };

    frameId = window.requestAnimationFrame(tick);
    return () => window.cancelAnimationFrame(frameId);
  }, [timerPhase]);

  useEffect(() => {
    void initializeScramble();
  }, []);

  useEffect(() => {
    if (!committedScramble) {
      return;
    }

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setSolutionStatus("loading");
    setError(null);
    setResult(null);
    setSolutionVisible(false);
    setSummaryVisible(false);

    void solveCube({
      scramble: committedScramble,
      crossFace,
      f2lMode,
    })
      .then((nextResult) => {
        if (requestIdRef.current !== requestId) {
          return;
        }
        startTransition(() => {
          setResult(nextResult);
          setSolutionStatus("ready");
        });
      })
      .catch((solveError) => {
        if (requestIdRef.current !== requestId) {
          return;
        }
        const message = solveError instanceof Error ? solveError.message : "Solve request failed";
        setError(message);
        setResult(null);
        setSolutionStatus("error");
      });
  }, [committedScramble, crossFace, f2lMode]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (isInteractiveTarget(event.target)) {
        return;
      }

      if (event.code === "Space") {
        if (event.repeat) {
          return;
        }

        event.preventDefault();
        if (timerPhase === "idle" || timerPhase === "stopped") {
          armTimer(timerPhase);
          return;
        }
        if (timerPhase === "inspection") {
          armTimer("inspection");
          return;
        }
        if (timerPhase === "running") {
          stopTimer();
        }
        return;
      }

      if (
        event.code === "Enter" &&
        timerPhase === "stopped" &&
        !generatingScramble &&
        !isEditingScramble
      ) {
        event.preventDefault();
        void handleGenerateScramble();
      }
    };

    const handleKeyUp = (event: KeyboardEvent) => {
      if (event.code !== "Space" || isInteractiveTarget(event.target)) {
        return;
      }

      event.preventDefault();
      if (timerPhase === "armed") {
        releaseArmedTimer();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("keyup", handleKeyUp);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("keyup", handleKeyUp);
    };
  }, [timerPhase, armedSource, inspectionStartedAt, runStartedAt, generatingScramble, isEditingScramble]);

  async function initializeScramble() {
    setGeneratingScramble(true);
    try {
      const nextScramble = await randomScrambleForEvent("333");
      commitScramble(nextScramble.toString());
    } catch (scrambleError) {
      const message =
        scrambleError instanceof Error ? scrambleError.message : "Scramble generation failed";
      setError(message);
      commitScramble(DEFAULT_SCRAMBLE);
    } finally {
      setGeneratingScramble(false);
    }
  }

  function resetTimer() {
    setTimerPhase("idle");
    setArmedSource(null);
    setInspectionStartedAt(null);
    setRunStartedAt(null);
    setStoppedElapsedMs(null);
    setFinalPenalty("none");
    setClockMs(window.performance.now());
  }

  function commitScramble(nextScramble: string) {
    const normalized = nextScramble.trim();
    setCommittedScramble(normalized);
    setDraftScramble(normalized);
    setIsEditingScramble(false);
    setSolutionVisible(false);
    setSummaryVisible(false);
    resetTimer();
  }

  function armTimer(source: Exclude<ArmedSource, null>) {
    setTimerPhase("armed");
    setArmedSource(source);
    setClockMs(window.performance.now());
  }

  function beginInspection() {
    const now = window.performance.now();
    setTimerPhase("inspection");
    setArmedSource(null);
    setInspectionStartedAt(now);
    setRunStartedAt(null);
    setStoppedElapsedMs(null);
    setFinalPenalty("none");
    setClockMs(now);
  }

  function beginRunning() {
    const now = window.performance.now();
    const nextPenalty = penaltyForInspectionElapsed(now - (inspectionStartedAt ?? now));
    setTimerPhase("running");
    setArmedSource(null);
    setRunStartedAt(now);
    setStoppedElapsedMs(null);
    setFinalPenalty(nextPenalty);
    setClockMs(now);
  }

  function releaseArmedTimer() {
    if (armedSource === "inspection") {
      beginRunning();
      return;
    }
    beginInspection();
  }

  function stopTimer() {
    if (runStartedAt === null) {
      return;
    }
    const now = window.performance.now();
    setTimerPhase("stopped");
    setStoppedElapsedMs(now - runStartedAt);
    setRunStartedAt(null);
    setClockMs(now);
  }

  async function handleGenerateScramble() {
    setGeneratingScramble(true);
    setError(null);
    try {
      const nextScramble = await randomScrambleForEvent("333");
      commitScramble(nextScramble.toString());
    } catch (scrambleError) {
      const message =
        scrambleError instanceof Error ? scrambleError.message : "Scramble generation failed";
      setError(message);
    } finally {
      setGeneratingScramble(false);
    }
  }

  function handleShowSolution() {
    if (solutionStatus !== "ready" || !result) {
      return;
    }
    setSolutionVisible((current) => {
      const next = !current;
      if (!next) {
        setSummaryVisible(false);
      }
      return next;
    });
  }

  function handleEnterEditMode() {
    setDraftScramble(committedScramble);
    setIsEditingScramble(true);
  }

  function handleCancelEdit() {
    setDraftScramble(committedScramble);
    setIsEditingScramble(false);
  }

  function handleSaveEdit() {
    commitScramble(draftScramble);
  }

  function handleCrossFaceChange(nextFace: string) {
    setCrossFace(nextFace);
    setSolutionVisible(false);
    setSummaryVisible(false);
    resetTimer();
  }

  function handleF2LModeChange(nextMode: F2LMode) {
    setF2LMode(nextMode);
    setSolutionVisible(false);
    setSummaryVisible(false);
    resetTimer();
  }

  function handleTimerPointerDown() {
    if (isEditingScramble) {
      return;
    }
    if (timerPhase === "idle" || timerPhase === "stopped") {
      armTimer(timerPhase);
      return;
    }
    if (timerPhase === "inspection") {
      armTimer("inspection");
      return;
    }
    if (timerPhase === "running") {
      stopTimer();
    }
  }

  function handleTimerPointerUp() {
    if (timerPhase === "armed") {
      releaseArmedTimer();
    }
  }

  function toggleTheme() {
    setTheme((currentTheme) => (currentTheme === "dark" ? "light" : "dark"));
  }

  return (
    <main className={summaryVisible ? "page-shell summary-open" : "page-shell"}>
      <header className="app-header">
        <div className="brand-lockup">
          <span className="brand-mark" aria-hidden="true">
            CF
          </span>
          <h1>Cube Solver</h1>
          <span className={result?.fullySolved ? "status-chip solved" : "status-chip"}>
            {solutionStatus === "loading" ? "Solving" : result?.fullySolved ? "Solved" : "Ready"}
          </span>
        </div>
        <div className="top-controls">
          <label className="compact-control">
            <span>Cross</span>
            <select value={crossFace} onChange={(event) => handleCrossFaceChange(event.target.value)}>
              {FACE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <div className="mode-toggle" aria-label="F2L mode">
            <button
              type="button"
              className={f2lMode === "greedy" ? "mode-option active" : "mode-option"}
              onClick={() => handleF2LModeChange("greedy")}
            >
              Fast
            </button>
            <button
              type="button"
              className={f2lMode === "optimized" ? "mode-option active" : "mode-option"}
              onClick={() => handleF2LModeChange("optimized")}
            >
              Optimized
            </button>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
            title={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
          >
            {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </div>
      </header>

      <section className="scramble-header">
        <div className="scramble-panel">
          <span className="section-label">{isEditingScramble ? "Edit scramble" : "Scramble"}</span>
          {isEditingScramble ? (
            <label className="field scramble-edit-field">
              <textarea
                rows={3}
                value={draftScramble}
                onChange={(event) => setDraftScramble(event.target.value)}
                placeholder="Enter a scramble like R U R' U'"
                autoFocus
              />
            </label>
          ) : (
            <p className="scramble-text">{committedScramble || "Preparing scramble..."}</p>
          )}

          <div className="scramble-actions">
            <button
              className="tool-button"
              type="button"
              onClick={handleGenerateScramble}
              disabled={generatingScramble}
            >
              <RefreshCw size={16} />
              <span>{generatingScramble ? "Generating" : "New"}</span>
            </button>
            {isEditingScramble ? (
              <>
                <button className="icon-button" type="button" onClick={handleCancelEdit} aria-label="Cancel edit" title="Cancel edit">
                  <X size={17} />
                </button>
                <button className="icon-button primary" type="button" onClick={handleSaveEdit} aria-label="Save scramble" title="Save scramble">
                  <Check size={17} />
                </button>
              </>
            ) : (
              <button className="icon-button" type="button" onClick={handleEnterEditMode} aria-label="Edit scramble" title="Edit scramble">
                <Pencil size={17} />
              </button>
            )}
          </div>
        </div>
      </section>

      <section className="workspace">
        <section className="main-viewport">
          {solutionVisible && result ? (
            <CubeAnimator result={result} />
          ) : (
            <section
              className={`timer-panel viewport phase-${timerPhase}`}
              aria-label="Solve timer"
              onPointerDown={handleTimerPointerDown}
              onPointerUp={handleTimerPointerUp}
              onPointerCancel={handleTimerPointerUp}
            >
              <div className="timer-panel-header">
                <p className="section-label">Timer</p>
                <span className="timer-status">{solutionStatusLabel(solutionStatus)}</span>
              </div>
              <div className="timer-readout">
                {timerText(timerPhase, inspectionElapsedMs, runningElapsedMs, stoppedElapsedMs, finalPenalty)}
              </div>
              <div className="timer-subline">
                <span>{timerHint(timerPhase, inspectionPenalty, finalPenalty)}</span>
                {timerPhase === "stopped" ? (
                  <span>{timerResultLabel(stoppedElapsedMs, finalPenalty)}</span>
                ) : (
                  <span>{isEditingScramble ? "Editing locked" : solveReadinessLabel(solutionStatus)}</span>
                )}
              </div>
            </section>
          )}
        </section>
      </section>

      {error ? (
        <section className="status-banner status-error">
          <strong>Request failed.</strong> {error}
        </section>
      ) : null}

      <section className="action-row">
        <button
          className="tool-button primary"
          type="button"
          onClick={handleShowSolution}
          disabled={solutionStatus !== "ready" || result === null}
        >
          {solutionVisible ? <EyeOff size={17} /> : <Eye size={17} />}
          <span>{solutionVisible ? "Hide solution" : "Show solution"}</span>
        </button>
        {solutionVisible ? (
          <button
            className="tool-button"
            type="button"
            onClick={() => setSummaryVisible((current) => !current)}
          >
            <ListTree size={17} />
            <span>{summaryVisible ? "Hide summary" : "Show summary"}</span>
          </button>
        ) : null}
      </section>

      {solutionVisible && summaryVisible && result ? (
        <section className="results-grid">
          <article className="summary-card">
            <p className="section-label">Solve Summary</p>
            <h2>{result.fullySolved ? "Solved in current frame" : "Partial solve result"}</h2>
            <div className="summary-metrics">
              <Metric label="Total moves" value={String(result.totalMoveCount)} />
              <Metric label="Elapsed" value={`${result.elapsedMs.toFixed(3)} ms`} />
              <Metric label="Solved slots" value={result.solvedF2LSlots} />
              <Metric label="Cross face" value={result.crossFace} />
            </div>
          </article>

          <article className="database-card">
            <p className="section-label">F2L Case Coverage</p>
            <div className="summary-metrics">
              <Metric label="Setup cases" value={String(result.f2lSetupCaseCount)} />
              <Metric label="Insert cases" value={String(result.f2lInsertCaseCount)} />
              <Metric label="F2L mode" value={f2lModeLabel(result.f2lMode || f2lMode)} />
              <Metric label="F2L strategy" value="two-phase DB + fallback" />
            </div>
          </article>

          <StageCard stage={result.cross} accent="amber" />
          <StageCard stage={result.f2l} accent="teal" />
          <StageCard stage={result.oll} accent="rust" />
          <StageCard stage={result.pll} accent="ink" />
        </section>
      ) : null}
    </main>
  );
}

function StageCard({ stage, accent }: { stage: SolveStage; accent: string }) {
  return (
    <article className={`stage-card accent-${accent}`}>
      <div className="stage-header">
        <p className="section-label">{stage.name.toUpperCase()}</p>
        <span className={stage.solved ? "stage-pill solved" : "stage-pill pending"}>
          {stage.solved ? "Solved" : "Pending"}
        </span>
      </div>
      <p className="stage-algorithm">{stage.algorithm || "No algorithm returned"}</p>
      <div className="stage-footer">
        <span>{stage.moveCount} moves</span>
        <span>{stage.status}</span>
      </div>
    </article>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function f2lModeLabel(mode: string): string {
  return mode === "optimized" ? "Optimized" : "Fast";
}

function solutionStatusLabel(status: SolutionStatus): string {
  switch (status) {
    case "loading":
      return "Computing";
    case "ready":
      return "Ready";
    case "error":
      return "Error";
    default:
      return "Waiting";
  }
}

function solveReadinessLabel(status: SolutionStatus): string {
  switch (status) {
    case "loading":
      return "Solving";
    case "ready":
      return "Solution ready";
    case "error":
      return "Solve unavailable";
    default:
      return "Waiting";
  }
}

function timerText(
  timerPhase: TimerPhase,
  inspectionElapsedMs: number,
  runningElapsedMs: number,
  stoppedElapsedMs: number | null,
  finalPenalty: TimerPenalty,
): string {
  if (timerPhase === "inspection") {
    return inspectionText(inspectionElapsedMs);
  }
  if (timerPhase === "running") {
    return formatSolveTime(runningElapsedMs);
  }
  if (timerPhase === "stopped") {
    return formatStoppedTime(stoppedElapsedMs ?? 0, finalPenalty);
  }
  return "0.00";
}

function timerHint(
  timerPhase: TimerPhase,
  inspectionPenalty: TimerPenalty,
  finalPenalty: TimerPenalty,
): string {
  if (timerPhase === "armed") {
    return "Release to continue";
  }
  if (timerPhase === "inspection") {
    if (inspectionPenalty === "dnf") {
      return "Inspection DNF";
    }
    if (inspectionPenalty === "+2") {
      return "Inspection +2";
    }
    return "Inspection";
  }
  if (timerPhase === "running") {
    return "Running";
  }
  if (timerPhase === "stopped") {
    if (finalPenalty === "dnf") {
      return "Inspection penalty: DNF";
    }
    if (finalPenalty === "+2") {
      return "Inspection penalty: +2";
    }
    return "Stopped";
  }
  return "Ready";
}

function timerResultLabel(stoppedElapsedMs: number | null, finalPenalty: TimerPenalty): string {
  if (stoppedElapsedMs === null) {
    return "";
  }
  if (finalPenalty === "dnf") {
    return `Raw ${formatSolveTime(stoppedElapsedMs)}`;
  }
  if (finalPenalty === "+2") {
    return `Official ${formatSolveTime(stoppedElapsedMs + 2_000)}`;
  }
  return `Official ${formatSolveTime(stoppedElapsedMs)}`;
}

function inspectionText(inspectionElapsedMs: number): string {
  const remainingMs = INSPECTION_PLUS_TWO_MS - inspectionElapsedMs;
  if (remainingMs >= 0) {
    return String(Math.ceil(remainingMs / 1_000));
  }
  if (inspectionElapsedMs <= INSPECTION_DNF_MS) {
    return "+2";
  }
  return "DNF";
}

function formatStoppedTime(elapsedMs: number, penalty: TimerPenalty): string {
  if (penalty === "dnf") {
    return "DNF";
  }
  if (penalty === "+2") {
    return `${formatSolveTime(elapsedMs + 2_000)}+`;
  }
  return formatSolveTime(elapsedMs);
}

function formatSolveTime(elapsedMs: number): string {
  const totalCentiseconds = Math.max(0, Math.floor(elapsedMs / 10));
  const minutes = Math.floor(totalCentiseconds / 6_000);
  const seconds = Math.floor((totalCentiseconds % 6_000) / 100);
  const centiseconds = totalCentiseconds % 100;

  if (minutes > 0) {
    return `${minutes}:${String(seconds).padStart(2, "0")}.${String(centiseconds).padStart(2, "0")}`;
  }
  return `${seconds}.${String(centiseconds).padStart(2, "0")}`;
}

function penaltyForInspectionElapsed(inspectionElapsedMs: number): TimerPenalty {
  if (inspectionElapsedMs > INSPECTION_DNF_MS) {
    return "dnf";
  }
  if (inspectionElapsedMs > INSPECTION_PLUS_TWO_MS) {
    return "+2";
  }
  return "none";
}

function isInteractiveTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return Boolean(target.closest("input, textarea, select, button, [contenteditable='true']"));
}
