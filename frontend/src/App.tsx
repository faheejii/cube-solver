import { startTransition, useEffect, useRef, useState } from "react";
import { randomScrambleForEvent } from "cubing/scramble";
import CubeAnimator from "./CubeAnimator";
import { solveCube } from "./api";
import type { SolveResponse, SolveStage } from "./types";

const DEFAULT_SCRAMBLE = "R D R' D2 R D' R'";
const FACE_OPTIONS = ["U", "D", "F", "B", "L", "R"] as const;
const INSPECTION_PLUS_TWO_MS = 15_000;
const INSPECTION_DNF_MS = 17_000;

type Theme = "light" | "dark";
type SolutionStatus = "idle" | "loading" | "ready" | "error";
type TimerPhase = "idle" | "armed" | "inspection" | "running" | "stopped";
type ArmedSource = "idle" | "stopped" | "inspection" | null;
type TimerPenalty = "none" | "+2" | "dnf";

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
  }, [committedScramble, crossFace]);

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
        <div>
          <p className="eyebrow">CFOP Timer</p>
          <h1>Cube Solver</h1>
        </div>
        <div className="header-actions">
          <button
            className="theme-toggle"
            type="button"
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
          >
            <span className="theme-toggle-track">
              <span className="theme-toggle-thumb" />
            </span>
            <span>{theme === "dark" ? "Dark" : "Light"}</span>
          </button>
          <div className={result?.fullySolved ? "system-pill ready" : "system-pill"}>
            {solutionStatus === "loading" ? "Solving" : result?.fullySolved ? "Solved" : "Ready"}
          </div>
        </div>
      </header>

      <section className="scramble-header">
        <div className="scramble-panel compact">
          <div className="scramble-panel-header">
            <div>
              <p className="eyebrow">Scramble</p>
              <h2>{isEditingScramble ? "Edit scramble" : "Current scramble"}</h2>
            </div>
            <div className="scramble-actions">
              <button
                className="generate-button"
                type="button"
                onClick={handleGenerateScramble}
                disabled={generatingScramble}
              >
                {generatingScramble ? "Generating..." : "Generate WCA scramble"}
              </button>
              {isEditingScramble ? (
                <>
                  <button className="utility-button" type="button" onClick={handleCancelEdit}>
                    Cancel
                  </button>
                  <button className="solve-button compact" type="button" onClick={handleSaveEdit}>
                    Save
                  </button>
                </>
              ) : (
                <button className="utility-button" type="button" onClick={handleEnterEditMode}>
                  Edit
                </button>
              )}
            </div>
          </div>

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
            <p className="scramble-text compact">{committedScramble || "Preparing scramble..."}</p>
          )}

          <div className="scramble-meta">
            <label className="field compact-field">
              <span>Cross face</span>
              <select value={crossFace} onChange={(event) => handleCrossFaceChange(event.target.value)}>
                {FACE_OPTIONS.map((face) => (
                  <option key={face} value={face}>
                    {face}
                  </option>
                ))}
              </select>
            </label>
            <div className="solve-status">
              <span className="eyebrow">Hidden solution</span>
              <strong>{solutionStatusLabel(solutionStatus)}</strong>
            </div>
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
                <p className="eyebrow">Timer</p>
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
                  <span>
                    {isEditingScramble
                      ? "Finish editing to use the timer"
                      : "Space or hold to control. Press Enter after stopping for next scramble."}
                  </span>
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
          className="summary-button"
          type="button"
          onClick={handleShowSolution}
          disabled={solutionStatus !== "ready" || result === null}
        >
          {solutionVisible ? "Hide solution" : "Show solution"}
        </button>
        {solutionVisible ? (
          <button
            className="summary-button secondary"
            type="button"
            onClick={() => setSummaryVisible((current) => !current)}
          >
            {summaryVisible ? "Hide solve summary" : "Show solve summary"}
          </button>
        ) : null}
        <p className="summary-note">{revealStatusMessage(solutionStatus, solutionVisible, summaryVisible)}</p>
      </section>

      {solutionVisible && summaryVisible && result ? (
        <section className="results-grid">
          <article className="summary-card">
            <p className="eyebrow">Solve Summary</p>
            <h2>{result.fullySolved ? "Solved in current frame" : "Partial solve result"}</h2>
            <div className="summary-metrics">
              <Metric label="Total moves" value={String(result.totalMoveCount)} />
              <Metric label="Elapsed" value={`${result.elapsedMs.toFixed(3)} ms`} />
              <Metric label="Solved slots" value={result.solvedF2LSlots} />
              <Metric label="Cross face" value={result.crossFace} />
            </div>
          </article>

          <article className="database-card">
            <p className="eyebrow">F2L Case Coverage</p>
            <div className="summary-metrics">
              <Metric label="Setup cases" value={String(result.f2lSetupCaseCount)} />
              <Metric label="Insert cases" value={String(result.f2lInsertCaseCount)} />
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
        <p className="eyebrow">{stage.name.toUpperCase()}</p>
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

function revealStatusMessage(
  solutionStatus: SolutionStatus,
  solutionVisible: boolean,
  summaryVisible: boolean,
): string {
  if (summaryVisible) {
    return "Solve summary is expanded below. Hide it to return to the fixed viewport layout.";
  }
  if (solutionVisible) {
    return "The solution is shown in the main panel. Hide it to return to the timer.";
  }
  if (solutionStatus === "loading") {
    return "Computing the solution in the background.";
  }
  if (solutionStatus === "error") {
    return "A valid solution is not available yet.";
  }
  if (solutionStatus === "ready") {
    return "The solution is ready but stays hidden until you reveal it.";
  }
  return "Generate a scramble to prepare a hidden solution.";
}

function solutionStatusLabel(status: SolutionStatus): string {
  switch (status) {
    case "loading":
      return "Computing now";
    case "ready":
      return "Ready to reveal";
    case "error":
      return "Unavailable";
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
      return "Inspection over 17s. Result will be DNF.";
    }
    if (inspectionPenalty === "+2") {
      return "Inspection over 15s. Result will be +2.";
    }
    return "Inspection running. Hold and release to start the solve.";
  }
  if (timerPhase === "running") {
    return "Solve running. Press space or tap to stop.";
  }
  if (timerPhase === "stopped") {
    if (finalPenalty === "dnf") {
      return "Inspection penalty: DNF";
    }
    if (finalPenalty === "+2") {
      return "Inspection penalty: +2";
    }
    return "Ready for the next solve.";
  }
  return "Hold and release to begin inspection.";
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
