import { startTransition, useEffect, useRef, useState } from "react";
import { randomScrambleForEvent } from "cubing/scramble";
import { Check, Eye, EyeOff, History, ListTree, Moon, Pencil, RefreshCw, Save, Sun, X } from "lucide-react";
import CubeAnimator from "./CubeAnimator";
import {
  createSolveAttempt,
  fetchSolveHistory,
  fetchSolveHistoryDetail,
  saveSolveSolution,
  solveCube,
} from "./api";
import type {
  SaveSolutionRequest,
  SavedSolution,
  SolveHistoryDetail,
  SolveHistoryEntry,
  SolveResponse,
  SolveStage,
} from "./types";

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
type HistoryStatus = "idle" | "loading" | "ready" | "error";
type AttemptSaveStatus = "idle" | "saving" | "saved" | "error";
type ModalStatus = "idle" | "loading" | "ready" | "error";
type CompletedAttemptSnapshot = {
  clientAttemptId: string;
  scramble: string;
  crossFace: string;
  f2lMode: F2LMode;
  elapsedMs: number;
  penalty: TimerPenalty;
  result: SolveResponse | null;
};

function initialTheme(): Theme {
  const savedTheme = window.localStorage.getItem("cube-solver-theme");
  if (savedTheme === "light" || savedTheme === "dark") {
    return savedTheme;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export default function App() {
  const [userId] = useState(() => getOrCreateClientUserId());
  const [committedScramble, setCommittedScramble] = useState("");
  const [clientAttemptId, setClientAttemptId] = useState(() => crypto.randomUUID());
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
  const [historyVisible, setHistoryVisible] = useState(false);
  const [historyStatus, setHistoryStatus] = useState<HistoryStatus>("idle");
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyEntries, setHistoryEntries] = useState<SolveHistoryEntry[]>([]);
  const [attemptSaveStatus, setAttemptSaveStatus] = useState<AttemptSaveStatus>("idle");
  const [attemptSaveError, setAttemptSaveError] = useState<string | null>(null);
  const [saveNotice, setSaveNotice] = useState<string | null>(null);
  const [modalStatus, setModalStatus] = useState<ModalStatus>("idle");
  const [modalError, setModalError] = useState<string | null>(null);
  const [modalDetail, setModalDetail] = useState<SolveHistoryDetail | null>(null);
  const [modalMode, setModalMode] = useState<F2LMode>("greedy");
  const [modalCrossFace, setModalCrossFace] = useState("U");
  const [modalResult, setModalResult] = useState<SolveResponse | null>(null);
  const [modalDirty, setModalDirty] = useState(false);
  const [modalComputing, setModalComputing] = useState(false);
  const [modalSaving, setModalSaving] = useState(false);
  const [theme, setTheme] = useState<Theme>(initialTheme);
  const [timerPhase, setTimerPhase] = useState<TimerPhase>("idle");
  const [armedSource, setArmedSource] = useState<ArmedSource>(null);
  const [inspectionStartedAt, setInspectionStartedAt] = useState<number | null>(null);
  const [runStartedAt, setRunStartedAt] = useState<number | null>(null);
  const [stoppedElapsedMs, setStoppedElapsedMs] = useState<number | null>(null);
  const [finalPenalty, setFinalPenalty] = useState<TimerPenalty>("none");
  const [clockMs, setClockMs] = useState(0);
  const requestIdRef = useRef(0);
  const attemptSavingRef = useRef<string | null>(null);
  const completedAttemptRef = useRef<CompletedAttemptSnapshot | null>(null);

  const inspectionElapsedMs =
    inspectionStartedAt === null ? 0 : Math.max(0, clockMs - inspectionStartedAt);
  const inspectionPenalty = penaltyForInspectionElapsed(inspectionElapsedMs);
  const runningElapsedMs = runStartedAt === null ? 0 : Math.max(0, clockMs - runStartedAt);
  const attemptLocked = attemptSaveStatus === "saving" || attemptSaveStatus === "error";

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    window.localStorage.setItem("cube-solver-theme", theme);
  }, [theme]);

  useEffect(() => {
    document.body.style.overflow =
      modalStatus !== "idle" ? "hidden" : summaryVisible || historyVisible ? "" : "hidden";
    return () => {
      document.body.style.overflow = "";
    };
  }, [summaryVisible, historyVisible, modalStatus]);

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
    void loadHistory();
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
    if (timerPhase !== "stopped" || stoppedElapsedMs === null) {
      return;
    }
    void persistCompletedAttempt();
  }, [timerPhase, stoppedElapsedMs]);

  useEffect(() => {
    if (modalStatus === "idle") {
      return;
    }
    const handleModalKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeSolutionModal();
      }
    };
    window.addEventListener("keydown", handleModalKeyDown);
    return () => window.removeEventListener("keydown", handleModalKeyDown);
  }, [modalStatus, modalDirty]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (modalStatus !== "idle") {
        return;
      }
      if (attemptSaveStatus === "saving" || attemptSaveStatus === "error") {
        return;
      }
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

    };

    const handleKeyUp = (event: KeyboardEvent) => {
      if (modalStatus !== "idle") {
        return;
      }
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
  }, [
    timerPhase,
    armedSource,
    inspectionStartedAt,
    runStartedAt,
    generatingScramble,
    isEditingScramble,
    modalStatus,
    attemptSaveStatus,
    committedScramble,
    crossFace,
    f2lMode,
    solutionStatus,
    result,
    clientAttemptId,
    finalPenalty,
  ]);

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

  async function loadHistory() {
    setHistoryStatus("loading");
    setHistoryError(null);
    try {
      const entries = await fetchSolveHistory(userId, 20);
      setHistoryEntries(entries);
      setHistoryStatus("ready");
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "History request failed";
      setHistoryError(message);
      setHistoryStatus("error");
    }
  }

  async function openHistorySolution(entry: SolveHistoryEntry) {
    setModalStatus("loading");
    setModalError(null);
    setModalDirty(false);
    setModalDetail(null);
    setModalResult(null);
    try {
      const detail = await fetchSolveHistoryDetail(userId, entry.id);
      const saved = detail.solutions[0];
      const nextMode = (saved?.mode ?? f2lMode) as F2LMode;
      const nextCross = saved?.crossFaceRequested ?? crossFace;
      setModalDetail(detail);
      setModalMode(nextMode);
      setModalCrossFace(nextCross);
      setModalStatus("ready");
      if (saved) {
        setModalResult(savedSolutionResult(detail.scramble, saved));
      } else {
        await computeModalSolution(detail, nextMode, nextCross, true);
      }
    } catch (openError) {
      const message = openError instanceof Error ? openError.message : "Solve detail request failed";
      setModalError(message);
      setModalStatus("error");
    }
  }

  async function computeModalSolution(
    detail: SolveHistoryDetail,
    mode: F2LMode,
    requestedCross: string,
    autoSave: boolean,
  ) {
    setModalComputing(true);
    setModalError(null);
    try {
      const computed = await solveCube({
        scramble: detail.scramble,
        crossFace: requestedCross,
        f2lMode: mode,
      });
      setModalResult(computed);
      if (autoSave) {
        const saved = await saveSolveSolution(
          detail.id,
          mode,
          buildSaveSolutionRequest(userId, requestedCross, computed),
        );
        updateModalSavedSolution(saved);
        setModalDirty(false);
        await loadHistory();
      } else {
        setModalDirty(true);
      }
    } catch (computeError) {
      const message = computeError instanceof Error ? computeError.message : "Solution computation failed";
      setModalError(message);
    } finally {
      setModalComputing(false);
    }
  }

  function updateModalSavedSolution(saved: SavedSolution) {
    setModalDetail((current) => {
      if (!current) {
        return current;
      }
      return {
        ...current,
        solutions: [
          ...current.solutions.filter((solution) => solution.mode !== saved.mode),
          saved,
        ].sort((left, right) => (left.mode === "greedy" ? -1 : right.mode === "greedy" ? 1 : 0)),
      };
    });
  }

  function confirmDiscardModalPreview(): boolean {
    return !modalDirty || window.confirm("Discard the unsaved solution preview?");
  }

  async function handleModalModeChange(nextMode: F2LMode) {
    if (nextMode === modalMode || !modalDetail || !confirmDiscardModalPreview()) {
      return;
    }
    const saved = modalDetail.solutions.find((solution) => solution.mode === nextMode);
    setModalMode(nextMode);
    setModalDirty(false);
    setModalError(null);
    if (saved) {
      setModalCrossFace(saved.crossFaceRequested);
      setModalResult(savedSolutionResult(modalDetail.scramble, saved));
      return;
    }
    await computeModalSolution(modalDetail, nextMode, modalCrossFace, true);
  }

  async function handleModalCrossChange(nextCross: string) {
    if (nextCross === modalCrossFace || !modalDetail || !confirmDiscardModalPreview()) {
      return;
    }
    setModalCrossFace(nextCross);
    setModalDirty(false);
    setModalError(null);
    const saved = modalDetail.solutions.find((solution) => solution.mode === modalMode);
    if (saved?.crossFaceRequested === nextCross) {
      setModalResult(savedSolutionResult(modalDetail.scramble, saved));
      return;
    }
    await computeModalSolution(modalDetail, modalMode, nextCross, false);
  }

  async function saveModalReplacement() {
    if (!modalDetail || !modalResult || !modalDirty) {
      return;
    }
    setModalSaving(true);
    setModalError(null);
    try {
      const saved = await saveSolveSolution(
        modalDetail.id,
        modalMode,
        buildSaveSolutionRequest(userId, modalCrossFace, modalResult),
      );
      updateModalSavedSolution(saved);
      setModalResult(savedSolutionResult(modalDetail.scramble, saved));
      setModalDirty(false);
      await loadHistory();
    } catch (saveError) {
      const message = saveError instanceof Error ? saveError.message : "Solution save failed";
      setModalError(message);
    } finally {
      setModalSaving(false);
    }
  }

  function closeSolutionModal() {
    if (!confirmDiscardModalPreview()) {
      return;
    }
    setModalStatus("idle");
    setModalDetail(null);
    setModalResult(null);
    setModalDirty(false);
    setModalError(null);
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
    setClientAttemptId(crypto.randomUUID());
    setDraftScramble(normalized);
    setIsEditingScramble(false);
    setSolutionVisible(false);
    setSummaryVisible(false);
    setAttemptSaveStatus("idle");
    setAttemptSaveError(null);
    attemptSavingRef.current = null;
    completedAttemptRef.current = null;
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
    const elapsedMs = now - runStartedAt;
    completedAttemptRef.current = {
      clientAttemptId,
      scramble: committedScramble,
      crossFace,
      f2lMode,
      elapsedMs,
      penalty: finalPenalty,
      result: solutionStatus === "ready" && result?.scramble === committedScramble ? result : null,
    };
    setTimerPhase("stopped");
    setStoppedElapsedMs(elapsedMs);
    setRunStartedAt(null);
    setClockMs(now);
  }

  async function persistCompletedAttempt() {
    const snapshot = completedAttemptRef.current;
    if (!snapshot || attemptSavingRef.current === snapshot.clientAttemptId) {
      return;
    }

    attemptSavingRef.current = snapshot.clientAttemptId;
    setAttemptSaveStatus("saving");
    setAttemptSaveError(null);

    try {
      const savedAttempt = await createSolveAttempt({
        userId,
        clientAttemptId: snapshot.clientAttemptId,
        scramble: snapshot.scramble,
        crossFaceRequested: snapshot.crossFace,
        timerMs: Math.round(snapshot.elapsedMs),
        penalty: snapshot.penalty,
        officialMs: officialTimeMs(snapshot.elapsedMs, snapshot.penalty),
        dnf: snapshot.penalty === "dnf",
      });

      setHistoryEntries((current) => [
        savedAttempt,
        ...current.filter((entry) => entry.id !== savedAttempt.id),
      ].slice(0, 20));
      setHistoryStatus("ready");
      setAttemptSaveStatus("saved");
      setSaveNotice(`Solve saved · ${formatHistoryTime(savedAttempt.officialMs, savedAttempt.penalty, savedAttempt.dnf)}`);

      const solutionPromise = snapshot.result
        ? Promise.resolve(snapshot.result)
        : solveCube({
            scramble: snapshot.scramble,
            crossFace: snapshot.crossFace,
            f2lMode: snapshot.f2lMode,
          });
      void solutionPromise
        .then((completedResult) =>
          saveSolveSolution(
            savedAttempt.id,
            snapshot.f2lMode,
            buildSaveSolutionRequest(userId, snapshot.crossFace, completedResult),
          ),
        )
        .then(() => loadHistory())
        .catch((solutionError) => {
          const message =
            solutionError instanceof Error ? solutionError.message : "Solution save failed";
          setHistoryError(message);
        });

      await handleGenerateScramble();
    } catch (saveError) {
      const message = saveError instanceof Error ? saveError.message : "Attempt save failed";
      setAttemptSaveStatus("error");
      setAttemptSaveError(message);
      attemptSavingRef.current = null;
    }
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
    if (
      isEditingScramble
      || attemptSaveStatus === "saving"
      || attemptSaveStatus === "error"
    ) {
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
    <main className={summaryVisible || historyVisible ? "page-shell summary-open" : "page-shell"}>
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
            <select
              value={crossFace}
              onChange={(event) => handleCrossFaceChange(event.target.value)}
              disabled={attemptLocked}
            >
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
              disabled={attemptLocked}
            >
              Fast
            </button>
            <button
              type="button"
              className={f2lMode === "optimized" ? "mode-option active" : "mode-option"}
              onClick={() => handleF2LModeChange("optimized")}
              disabled={attemptLocked}
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
              disabled={generatingScramble || attemptLocked}
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
              <button
                className="icon-button"
                type="button"
                onClick={handleEnterEditMode}
                aria-label="Edit scramble"
                title="Edit scramble"
                disabled={attemptLocked}
              >
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
      {attemptSaveStatus === "error" ? (
        <section className="status-banner status-error save-status">
          <span><strong>Could not save solve.</strong> {attemptSaveError}</span>
          <button className="tool-button" type="button" onClick={() => void persistCompletedAttempt()}>
            Retry save
          </button>
        </section>
      ) : null}
      {saveNotice ? (
        <section className="status-banner status-success" onClick={() => setSaveNotice(null)}>
          {saveNotice}
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
        <button
          className="tool-button"
          type="button"
          onClick={() => setHistoryVisible((current) => !current)}
        >
          <History size={17} />
          <span>{historyVisible ? "Hide history" : "History"}</span>
        </button>
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

      {historyVisible ? (
        <section className="history-panel">
          <div className="history-panel-header">
            <div>
              <p className="section-label">Solve History</p>
              <h2>Recent solves</h2>
            </div>
            <button className="tool-button" type="button" onClick={() => void loadHistory()}>
              <RefreshCw size={16} />
              <span>Refresh</span>
            </button>
          </div>
          {historyStatus === "loading" ? <p className="history-empty">Loading history...</p> : null}
          {historyStatus === "error" ? <p className="history-empty">{historyError ?? "History unavailable"}</p> : null}
          {historyStatus !== "loading" && historyEntries.length === 0 ? (
            <p className="history-empty">No solves saved yet.</p>
          ) : null}
          {historyEntries.length > 0 ? (
            <div className="history-list">
              {historyEntries.map((entry) => (
                <article className="history-entry" key={entry.id}>
                  <div className="history-entry-top">
                    <strong>{formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}</strong>
                    <span>{new Date(entry.createdAt).toLocaleString()}</span>
                  </div>
                  <div className="history-entry-meta">
                    <span>Attempt cross {crossFaceLabel(entry.crossFaceRequested)}</span>
                    <span>{entry.fastCrossFaceRequested ? `Fast ${crossFaceLabel(entry.fastCrossFaceRequested)}` : "Fast not saved"}</span>
                    <span>{entry.optimizedCrossFaceRequested ? `Optimized ${crossFaceLabel(entry.optimizedCrossFaceRequested)}` : "Optimized not saved"}</span>
                  </div>
                  <p className="history-scramble">{entry.scramble}</p>
                  <button
                    className="tool-button history-solution-button"
                    type="button"
                    onClick={() => void openHistorySolution(entry)}
                  >
                    <Eye size={16} />
                    <span>Show solution</span>
                  </button>
                </article>
              ))}
            </div>
          ) : null}
        </section>
      ) : null}

      {modalStatus !== "idle" ? (
        <div className="solution-modal-backdrop" role="presentation" onMouseDown={closeSolutionModal}>
          <section
            className="solution-modal"
            role="dialog"
            aria-modal="true"
            aria-label="Solve solution"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="solution-modal-header">
              <div>
                <p className="section-label">History Solution</p>
                <h2>{modalDetail ? formatHistoryTime(modalDetail.officialMs, modalDetail.penalty, modalDetail.dnf) : "Loading"}</h2>
              </div>
              <button className="icon-button" type="button" onClick={closeSolutionModal} aria-label="Close solution">
                <X size={18} />
              </button>
            </div>

            {modalStatus === "loading" ? <p className="modal-message">Loading saved solution...</p> : null}
            {modalStatus === "error" ? <p className="modal-message error-text">{modalError}</p> : null}

            {modalDetail ? (
              <>
                <div className="solution-modal-controls">
                  <div className="mode-toggle" aria-label="History F2L mode">
                    <button
                      className={modalMode === "greedy" ? "mode-option active" : "mode-option"}
                      type="button"
                      onClick={() => void handleModalModeChange("greedy")}
                      disabled={modalComputing || modalSaving}
                    >
                      Fast
                    </button>
                    <button
                      className={modalMode === "optimized" ? "mode-option active" : "mode-option"}
                      type="button"
                      onClick={() => void handleModalModeChange("optimized")}
                      disabled={modalComputing || modalSaving}
                    >
                      Optimized
                    </button>
                  </div>
                  <label className="compact-control">
                    <span>Cross</span>
                    <select
                      value={modalCrossFace}
                      onChange={(event) => void handleModalCrossChange(event.target.value)}
                      disabled={modalComputing || modalSaving}
                    >
                      {FACE_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  {modalDirty ? (
                    <button
                      className="tool-button primary"
                      type="button"
                      onClick={() => void saveModalReplacement()}
                      disabled={modalSaving || modalComputing}
                      title={modalOverwriteMessage(modalDetail, modalMode, modalCrossFace)}
                    >
                      <Save size={16} />
                      <span>{modalSaving ? "Saving" : "Save replacement"}</span>
                    </button>
                  ) : null}
                </div>

                <p className="modal-scramble">{modalDetail.scramble}</p>
                {modalDirty ? (
                  <p className="modal-warning">{modalOverwriteMessage(modalDetail, modalMode, modalCrossFace)}</p>
                ) : null}
                {modalComputing ? <p className="modal-message">Computing {f2lModeLabel(modalMode)} solution...</p> : null}
                {modalError ? <p className="modal-message error-text">{modalError}</p> : null}

                {modalResult ? (
                  <div className="solution-modal-content">
                    <CubeAnimator result={modalResult} />
                    <div className="modal-summary">
                      <Metric label="Total moves" value={String(modalResult.totalMoveCount)} />
                      <Metric label="Cross requested" value={crossFaceLabel(modalCrossFace)} />
                      <Metric label="Cross chosen" value={modalResult.crossFace} />
                      <Metric label="F2L mode" value={f2lModeLabel(modalResult.f2lMode)} />
                      <StageCard stage={modalResult.cross} accent="amber" />
                      <StageCard stage={modalResult.f2l} accent="teal" />
                      <StageCard stage={modalResult.oll} accent="rust" />
                      <StageCard stage={modalResult.pll} accent="ink" />
                    </div>
                  </div>
                ) : null}
              </>
            ) : null}
          </section>
        </div>
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

function getOrCreateClientUserId(): string {
  const existing = window.localStorage.getItem("cube-solver-user-id");
  if (existing && existing.trim().length > 0) {
    return existing;
  }
  const generated = `anon-${crypto.randomUUID()}`;
  window.localStorage.setItem("cube-solver-user-id", generated);
  return generated;
}

function buildSaveSolutionRequest(
  userId: string,
  crossFaceRequested: string,
  result: SolveResponse,
): SaveSolutionRequest {
  return {
    userId,
    crossFaceRequested,
    crossFaceChosen: result.crossFace,
    f2lMode: result.f2lMode,
    f2lSetupCaseCount: result.f2lSetupCaseCount,
    f2lInsertCaseCount: result.f2lInsertCaseCount,
    solvedF2LSlots: result.solvedF2LSlots,
    totalMoves: result.totalMoveCount,
    fullySolved: result.fullySolved,
    solveElapsedMs: result.elapsedMs,
    crossAlgorithm: result.cross.algorithm,
    crossMoves: result.cross.moveCount,
    crossSolved: result.cross.solved,
    crossStatus: result.cross.status,
    f2lAlgorithm: result.f2l.algorithm,
    f2lMoves: result.f2l.moveCount,
    f2lSolved: result.f2l.solved,
    f2lStatus: result.f2l.status,
    ollAlgorithm: result.oll.algorithm,
    ollMoves: result.oll.moveCount,
    ollSolved: result.oll.solved,
    ollStatus: result.oll.status,
    pllAlgorithm: result.pll.algorithm,
    pllMoves: result.pll.moveCount,
    pllSolved: result.pll.solved,
    pllStatus: result.pll.status,
  };
}

function savedSolutionResult(scramble: string, saved: SavedSolution): SolveResponse {
  return {
    scramble,
    crossFace: saved.crossFace,
    f2lMode: saved.f2lMode,
    f2lSetupCaseCount: saved.f2lSetupCaseCount,
    f2lInsertCaseCount: saved.f2lInsertCaseCount,
    cross: saved.cross,
    f2l: saved.f2l,
    oll: saved.oll,
    pll: saved.pll,
    solvedF2LSlots: saved.solvedF2LSlots,
    fullySolved: saved.fullySolved,
    totalMoveCount: saved.totalMoveCount,
    elapsedMs: saved.elapsedMs,
  };
}

function crossFaceLabel(face: string): string {
  return face === "CN" ? "Color Neutral" : face;
}

function modalOverwriteMessage(
  detail: SolveHistoryDetail,
  mode: F2LMode,
  nextCross: string,
): string {
  const saved = detail.solutions.find((solution) => solution.mode === mode);
  const oldCross = saved ? crossFaceLabel(saved.crossFaceRequested) : "none";
  return `Saving will replace the saved ${f2lModeLabel(mode)} solution (${oldCross}) with this ${crossFaceLabel(nextCross)} solution.`;
}

function officialTimeMs(stoppedElapsedMs: number, penalty: TimerPenalty): number | null {
  if (penalty === "dnf") {
    return null;
  }
  return penalty === "+2" ? Math.round(stoppedElapsedMs + 2_000) : Math.round(stoppedElapsedMs);
}

function formatHistoryTime(officialMs: number | null, penalty: string, dnf: boolean): string {
  if (dnf || penalty === "dnf" || officialMs === null) {
    return "DNF";
  }
  if (penalty === "+2") {
    return `${formatSolveTime(officialMs)}+`;
  }
  return formatSolveTime(officialMs);
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
