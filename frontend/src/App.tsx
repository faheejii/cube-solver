import { startTransition, useEffect, useRef, useState } from "react";
import { randomScrambleForEvent } from "cubing/scramble";
import { Save, X } from "lucide-react";
import ActiveSolutionsView from "./ActiveSolutionsView";
import DashboardSidebar, { type DashboardView } from "./DashboardSidebar";
import HistoryView from "./HistoryView";
import SolutionResultBody from "./SolutionResultBody";
import StatisticsRail from "./StatisticsRail";
import TimerWorkspace from "./TimerWorkspace";
import {
  createSolveAttempt,
  deleteSolve,
  cancelSolveJob,
  fetchSolveJob,
  fetchSolveHistory,
  fetchSolveHistoryDetail,
  fetchSolveStatistics,
  saveSolveSolution,
  startSolveJob,
} from "./api";
import {
  crossFaceLabel,
  f2lModeLabel,
  formatHistoryTime,
  formatSolveTime,
} from "./format";
import type {
  SolutionProcess,
  SolutionProcessSource,
  SolveJob,
  SolveJobRequest,
  SaveSolutionRequest,
  SavedSolution,
  SolveHistoryDetail,
  SolveHistoryEntry,
  SolveResponse,
  SolveStatistics,
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
  return "dark";
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
  const [timerSolutionOpen, setTimerSolutionOpen] = useState(false);
  const [activeView, setActiveView] = useState<DashboardView>("timer");
  const [processes, setProcesses] = useState<SolutionProcess[]>([]);
  const [processPreview, setProcessPreview] = useState<SolutionProcess | null>(null);
  const [historyStatus, setHistoryStatus] = useState<HistoryStatus>("idle");
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyEntries, setHistoryEntries] = useState<SolveHistoryEntry[]>([]);
  const [historyCursor, setHistoryCursor] = useState<string | null>(null);
  const [historyLoadingMore, setHistoryLoadingMore] = useState(false);
  const [deletingSolveId, setDeletingSolveId] = useState<number | null>(null);
  const [statistics, setStatistics] = useState<SolveStatistics | null>(null);
  const [statisticsLoading, setStatisticsLoading] = useState(true);
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
  const [modalJobStatus, setModalJobStatus] = useState<"queued" | "running" | null>(null);
  const [modalStatesExplored, setModalStatesExplored] = useState(0);
  const [modalStatesPruned, setModalStatesPruned] = useState(0);
  const [modalDuplicateStates, setModalDuplicateStates] = useState(0);
  const [modalBestMoves, setModalBestMoves] = useState(-1);
  const [modalCompletedCandidates, setModalCompletedCandidates] = useState(0);
  const [modalCandidatesEvaluated, setModalCandidatesEvaluated] = useState(0);
  const [modalBestTotalMoves, setModalBestTotalMoves] = useState(-1);
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
  const modalJobRequestIdRef = useRef(0);

  const inspectionElapsedMs =
    inspectionStartedAt === null ? 0 : Math.max(0, clockMs - inspectionStartedAt);
  const inspectionPenalty = penaltyForInspectionElapsed(inspectionElapsedMs);
  const runningElapsedMs = runStartedAt === null ? 0 : Math.max(0, clockMs - runStartedAt);
  const attemptLocked = attemptSaveStatus === "saving" && timerPhase === "stopped";

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    window.localStorage.setItem("cube-solver-theme", theme);
  }, [theme]);

  useEffect(() => {
    const overlayOpen = modalStatus !== "idle" || processPreview !== null || timerSolutionOpen;
    document.body.style.overflow = activeView === "timer" || overlayOpen ? "hidden" : "";
    if (activeView === "timer") {
      window.scrollTo({ top: 0, left: 0 });
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [activeView, modalStatus, processPreview, timerSolutionOpen]);

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
    void loadStatistics();
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
    setTimerSolutionOpen(false);

    const solveRequest: SolveJobRequest = {
      scramble: committedScramble,
      crossFace,
      f2lMode,
    };

    void runTrackedSolve(solveRequest, "timer")
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
    if (!processPreview) {
      return;
    }
    const closePreview = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setProcessPreview(null);
      }
    };
    window.addEventListener("keydown", closePreview);
    return () => window.removeEventListener("keydown", closePreview);
  }, [processPreview]);

  useEffect(() => {
    if (!timerSolutionOpen) {
      return;
    }
    const closeTimerSolution = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setTimerSolutionOpen(false);
      }
    };
    window.addEventListener("keydown", closeTimerSolution);
    return () => window.removeEventListener("keydown", closeTimerSolution);
  }, [timerSolutionOpen]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const overlayOpen = modalStatus !== "idle" || processPreview !== null || timerSolutionOpen;
      if (overlayOpen) {
        if (event.code === "Space") {
          event.preventDefault();
        }
        return;
      }
      if (
        activeView !== "timer"
        || isEditingScramble
      ) {
        return;
      }
      if (attemptSaveStatus === "saving" || attemptSaveStatus === "error") {
        return;
      }
      if (isTextEntryTarget(event.target)) {
        return;
      }

      if (event.code === "Space") {
        if (event.repeat) {
          return;
        }

        event.preventDefault();
        blurFocusedButton();
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
      const overlayOpen = modalStatus !== "idle" || processPreview !== null || timerSolutionOpen;
      if (overlayOpen) {
        if (event.code === "Space") {
          event.preventDefault();
        }
        return;
      }
      if (
        activeView !== "timer"
        || isEditingScramble
      ) {
        return;
      }
      if (event.code !== "Space" || isTextEntryTarget(event.target)) {
        return;
      }

      event.preventDefault();
      blurFocusedButton();
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
    activeView,
    modalStatus,
    processPreview,
    timerSolutionOpen,
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
      const page = await fetchSolveHistory(userId, 25);
      setHistoryEntries(page.items);
      setHistoryCursor(page.nextCursor);
      setHistoryStatus("ready");
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "History request failed";
      setHistoryError(message);
      setHistoryStatus("error");
    }
  }

  async function loadMoreHistory() {
    if (!historyCursor || historyLoadingMore) {
      return;
    }
    setHistoryLoadingMore(true);
    try {
      const page = await fetchSolveHistory(userId, 25, historyCursor);
      setHistoryEntries((current) => [
        ...current,
        ...page.items.filter((entry) => current.every((existing) => existing.id !== entry.id)),
      ]);
      setHistoryCursor(page.nextCursor);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "History request failed";
      setHistoryError(message);
    } finally {
      setHistoryLoadingMore(false);
    }
  }

  async function loadStatistics() {
    setStatisticsLoading(true);
    try {
      setStatistics(await fetchSolveStatistics(userId));
    } catch {
      setStatistics(null);
    } finally {
      setStatisticsLoading(false);
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

  async function handleDeleteSolve(entry: SolveHistoryEntry) {
    if (deletingSolveId !== null) {
      return;
    }
    const displayedTime = formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf);
    if (!window.confirm(
      `Delete the ${displayedTime} solve permanently?\n\nThis also deletes all saved Fast and Optimized solutions.`
    )) {
      return;
    }

    setDeletingSolveId(entry.id);
    setHistoryError(null);
    try {
      await deleteSolve(userId, entry.id);
      setHistoryEntries((current) => current.filter((solve) => solve.id !== entry.id));
      setSaveNotice("Solve deleted");
      await Promise.all([loadHistory(), loadStatistics()]);
    } catch (deleteError) {
      const message = deleteError instanceof Error ? deleteError.message : "Solve deletion failed";
      setHistoryError(message);
    } finally {
      setDeletingSolveId(null);
    }
  }

  async function computeModalSolution(
    detail: SolveHistoryDetail,
    mode: F2LMode,
    requestedCross: string,
    autoSave: boolean,
  ) {
    const requestId = modalJobRequestIdRef.current + 1;
    modalJobRequestIdRef.current = requestId;
    setModalComputing(true);
    setModalJobStatus(mode === "optimized" ? "queued" : null);
    setModalStatesExplored(0);
    setModalStatesPruned(0);
    setModalDuplicateStates(0);
    setModalBestMoves(-1);
    setModalCompletedCandidates(0);
    setModalCandidatesEvaluated(0);
    setModalBestTotalMoves(-1);
    setModalError(null);
    setModalResult(null);
    try {
      const computed = await runTrackedSolve({
        scramble: detail.scramble,
        crossFace: requestedCross,
        f2lMode: mode,
        userId: autoSave ? userId : undefined,
        solveId: autoSave ? detail.id : undefined,
        saveOnComplete: autoSave,
      }, "history", (progress) => {
        if (modalJobRequestIdRef.current !== requestId) {
          return;
        }
        setModalJobStatus(progress.status === "queued" ? "queued" : "running");
        setModalStatesExplored(progress.statesExplored);
        setModalStatesPruned(progress.statesPruned);
        setModalDuplicateStates(progress.duplicateStates);
        setModalBestMoves(progress.bestMoves);
        setModalCompletedCandidates(progress.completedCandidates);
        setModalCandidatesEvaluated(progress.candidatesEvaluated);
        setModalBestTotalMoves(progress.bestTotalMoves);
      });
      if (modalJobRequestIdRef.current !== requestId) {
        if (autoSave) {
          void loadHistory();
          void loadStatistics();
        }
        return;
      }
      setModalResult(computed);
      if (autoSave) {
        const refreshedDetail = await fetchSolveHistoryDetail(userId, detail.id);
        setModalDetail(refreshedDetail);
        setModalDirty(false);
        await loadHistory();
        await loadStatistics();
      } else {
        setModalDirty(true);
      }
    } catch (computeError) {
      const message = computeError instanceof Error ? computeError.message : "Solution computation failed";
      setModalError(message);
    } finally {
      if (modalJobRequestIdRef.current === requestId) {
        setModalComputing(false);
        setModalJobStatus(null);
      }
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

  function retryModalSolution() {
    if (!modalDetail || modalComputing) {
      return;
    }
    const saved = modalDetail.solutions.find((solution) => solution.mode === modalMode);
    const autoSave = !saved;
    void computeModalSolution(modalDetail, modalMode, modalCrossFace, autoSave);
  }

  function closeSolutionModal() {
    if (!confirmDiscardModalPreview()) {
      return;
    }
    setModalStatus("idle");
    modalJobRequestIdRef.current++;
    setModalDetail(null);
    setModalResult(null);
    setModalDirty(false);
    setModalError(null);
    setModalComputing(false);
    setModalJobStatus(null);
    setModalStatesExplored(0);
    setModalStatesPruned(0);
    setModalDuplicateStates(0);
    setModalBestMoves(-1);
    setModalCompletedCandidates(0);
    setModalCandidatesEvaluated(0);
    setModalBestTotalMoves(-1);
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
    setTimerSolutionOpen(false);
    setAttemptSaveStatus("idle");
    setAttemptSaveError(null);
    attemptSavingRef.current = null;
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
    if (timerPhase === "stopped") {
      void handleGenerateScramble();
    }

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
      void loadStatistics();

      const solutionPromise = snapshot.result
        ? saveSolveSolution(
            savedAttempt.id,
            snapshot.f2lMode,
            buildSaveSolutionRequest(userId, snapshot.crossFace, snapshot.result),
          )
        : runTrackedSolve({
            scramble: snapshot.scramble,
            crossFace: snapshot.crossFace,
            f2lMode: snapshot.f2lMode,
            userId,
            solveId: savedAttempt.id,
            saveOnComplete: true,
          }, "background");
      void solutionPromise
        .then(() => loadHistory())
        .catch((solutionError) => {
          const message =
            solutionError instanceof Error ? solutionError.message : "Solution save failed";
          setHistoryError(message);
        });
      if (completedAttemptRef.current?.clientAttemptId === snapshot.clientAttemptId) {
        completedAttemptRef.current = null;
      }
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
    setTimerSolutionOpen(true);
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
    setTimerSolutionOpen(false);
    resetTimer();
  }

  function handleF2LModeChange(nextMode: F2LMode) {
    setF2LMode(nextMode);
    setTimerSolutionOpen(false);
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

  function runTrackedSolve(
    request: SolveJobRequest,
    source: SolutionProcessSource,
    onProgress?: (job: SolveJob) => void,
  ): Promise<SolveResponse> {
    const processId = crypto.randomUUID();
    const now = Date.now();
    const initial: SolutionProcess = {
      id: processId,
      jobId: null,
      source,
      request,
      status: "queued",
      statesExplored: 0,
      statesPruned: 0,
      duplicateStates: 0,
      bestMoves: -1,
      completedCandidates: 0,
      candidatesEvaluated: 0,
      bestTotalMoves: -1,
      createdAt: now,
      updatedAt: now,
      result: null,
      error: null,
      cancelling: false,
    };
    setProcesses((current) => [initial, ...current]);

    return startSolveJob(request)
      .then((job) => {
        updateProcessFromJob(processId, job);
        return waitForSolveJob(job, (progress) => {
          updateProcessFromJob(processId, progress);
          onProgress?.(progress);
        });
      })
      .catch((solveError) => {
        const message = solveError instanceof Error ? solveError.message : "Solve request failed";
        setProcesses((current) => trimFinishedProcesses(current.map((process) =>
          process.id === processId && !isTerminalProcess(process)
            ? {
                ...process,
                status: solveError instanceof SolveJobCancelledError ? "cancelled" : "failed",
                error: message,
                cancelling: false,
                updatedAt: Date.now(),
              }
            : process
        )));
        throw solveError;
      });
  }

  function updateProcessFromJob(processId: string, job: SolveJob) {
    setProcesses((current) => trimFinishedProcesses(current.map((process) =>
      process.id === processId
        ? {
            ...process,
            jobId: job.id,
            status: job.status,
            statesExplored: job.statesExplored,
            statesPruned: job.statesPruned,
            duplicateStates: job.duplicateStates,
            bestMoves: job.bestMoves,
            completedCandidates: job.completedCandidates,
            candidatesEvaluated: job.candidatesEvaluated,
            bestTotalMoves: job.bestTotalMoves,
            result: job.result,
            error: job.error,
            cancelling: isTerminalProcess({ ...process, status: job.status })
              ? false
              : process.cancelling,
            updatedAt: Date.now(),
          }
        : process
    )));
  }

  async function terminateProcess(process: SolutionProcess) {
    if (!process.jobId || isTerminalProcess(process)) {
      return;
    }
    setProcesses((current) => current.map((entry) =>
      entry.id === process.id ? { ...entry, cancelling: true } : entry
    ));
    try {
      updateProcessFromJob(process.id, await cancelSolveJob(process.jobId));
    } catch (cancelError) {
      const message = cancelError instanceof Error ? cancelError.message : "Cancellation failed";
      setProcesses((current) => current.map((entry) =>
        entry.id === process.id ? { ...entry, cancelling: false, error: message } : entry
      ));
    }
  }

  function retryProcess(process: SolutionProcess) {
    void runTrackedSolve(process.request, process.source);
  }

  function dismissProcess(processId: string) {
    setProcesses((current) => current.filter((process) => process.id !== processId));
  }

  function clearFinishedProcesses() {
    setProcesses((current) => current.filter((process) => !isTerminalProcess(process)));
  }

  return (
    <main className={`dashboard-shell view-${activeView}`}>
      <DashboardSidebar
        activeView={activeView}
        theme={theme}
        activeProcessCount={processes.filter((process) => !isTerminalProcess(process)).length}
        onViewChange={setActiveView}
        onToggleTheme={toggleTheme}
      />

      <div className="dashboard-center">
        {activeView === "timer" ? (
          <>
            <TimerWorkspace
              scramble={committedScramble}
              draftScramble={draftScramble}
              crossFace={crossFace}
              f2lMode={f2lMode}
              faceOptions={FACE_OPTIONS}
              isEditingScramble={isEditingScramble}
              generatingScramble={generatingScramble}
              attemptLocked={attemptLocked}
              solutionStatus={solutionStatus}
              result={result}
              statistics={statistics}
              timerPhase={timerPhase}
              timerValue={timerText(
                timerPhase,
                inspectionElapsedMs,
                runningElapsedMs,
                stoppedElapsedMs,
                finalPenalty,
              )}
              timerHint={timerHint(timerPhase, inspectionPenalty, finalPenalty)}
              timerDetail={
                timerPhase === "stopped"
                  ? timerResultLabel(stoppedElapsedMs, finalPenalty)
                  : isEditingScramble
                    ? "Editing locked"
                    : solveReadinessLabel(solutionStatus)
              }
              onDraftChange={setDraftScramble}
              onCrossFaceChange={handleCrossFaceChange}
              onF2LModeChange={handleF2LModeChange}
              onGenerateScramble={() => void handleGenerateScramble()}
              onEnterEdit={handleEnterEditMode}
              onCancelEdit={handleCancelEdit}
              onSaveEdit={handleSaveEdit}
              onShowSolution={handleShowSolution}
              onTimerPointerDown={handleTimerPointerDown}
              onTimerPointerUp={handleTimerPointerUp}
            />

            {error || attemptSaveStatus === "error" || saveNotice ? (
              <div className="timer-notices">
                {error ? <div className="dashboard-alert error"><strong>Request failed.</strong> {error}</div> : null}
                {attemptSaveStatus === "error" ? (
                  <div className="dashboard-alert error save-status">
                    <span><strong>Could not save solve.</strong> {attemptSaveError}</span>
                    <button className="dashboard-secondary-button" type="button" onClick={() => void persistCompletedAttempt()}>
                      Retry save
                    </button>
                  </div>
                ) : null}
                {saveNotice ? (
                  <button className="dashboard-toast" type="button" onClick={() => setSaveNotice(null)}>
                    {saveNotice}
                  </button>
                ) : null}
              </div>
            ) : null}

          </>
        ) : activeView === "history" ? (
          <HistoryView
            entries={historyEntries}
            loading={historyStatus === "loading"}
            loadingMore={historyLoadingMore}
            error={historyError}
            hasMore={historyCursor !== null}
            deletingSolveId={deletingSolveId}
            onRefresh={() => void loadHistory()}
            onLoadMore={() => void loadMoreHistory()}
            onOpenSolve={(entry) => void openHistorySolution(entry)}
            onDeleteSolve={(entry) => void handleDeleteSolve(entry)}
          />
        ) : (
          <ActiveSolutionsView
            processes={processes}
            onCancel={(process) => void terminateProcess(process)}
            onRetry={retryProcess}
            onPreview={setProcessPreview}
            onDismiss={dismissProcess}
            onClearFinished={clearFinishedProcesses}
          />
        )}
      </div>

      {activeView === "timer" ? (
        <StatisticsRail
          statistics={statistics}
          loading={statisticsLoading}
          onOpenHistory={() => setActiveView("history")}
          onOpenSolve={(entry) => void openHistorySolution(entry)}
        />
      ) : null}

      {activeView !== "timer" && saveNotice ? (
        <button className="dashboard-toast global-toast" type="button" onClick={() => setSaveNotice(null)}>
          {saveNotice}
        </button>
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
                {modalComputing ? (
                  <p className="modal-message">
                    {modalMode === "optimized"
                      ? modalJobStatus === "queued"
                        ? "Optimized solve queued..."
                        : modalCandidatesEvaluated > 0
                          ? `Evaluating last layer... ${modalCandidatesEvaluated}/${modalCompletedCandidates} · Best total: ${modalBestTotalMoves < 0 ? "pending" : `${modalBestTotalMoves} moves`}`
                          : `Searching F2L... States: ${modalStatesExplored.toLocaleString()} · Pruned: ${modalStatesPruned.toLocaleString()} · Duplicates: ${modalDuplicateStates.toLocaleString()} · Best F2L: ${modalBestMoves < 0 ? "pending" : `${modalBestMoves} moves`}`
                      : `Computing ${f2lModeLabel(modalMode)} solution...`}
                  </p>
                ) : null}
                {modalError ? (
                  <div className="modal-message error-text">
                    <span>{modalError}</span>
                    <button
                      className="dashboard-secondary-button compact"
                      type="button"
                      onClick={retryModalSolution}
                      disabled={modalComputing}
                    >
                      Retry
                    </button>
                  </div>
                ) : null}

                {modalResult ? (
                  <SolutionResultBody result={modalResult} requestedCross={modalCrossFace} />
                ) : null}
              </>
            ) : null}
          </section>
        </div>
      ) : null}

      {timerSolutionOpen && result ? (
        <div className="solution-modal-backdrop" role="presentation" onMouseDown={() => setTimerSolutionOpen(false)}>
          <section
            className="solution-modal timer-solution-modal"
            role="dialog"
            aria-modal="true"
            aria-label="Timer solution"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="solution-modal-header">
              <div>
                <p className="section-label">Current scramble</p>
                <h2>Solution and summary</h2>
                <span className="solution-modal-context">
                  {f2lModeLabel(result.f2lMode)} · Cross {crossFaceLabel(crossFace)}
                </span>
              </div>
              <button className="icon-button" type="button" onClick={() => setTimerSolutionOpen(false)} aria-label="Close solution">
                <X size={18} />
              </button>
            </div>
            <p className="modal-scramble">{result.scramble}</p>
            <SolutionResultBody result={result} requestedCross={crossFace} />
          </section>
        </div>
      ) : null}

      {processPreview?.result ? (
        <div className="solution-modal-backdrop" role="presentation" onMouseDown={() => setProcessPreview(null)}>
          <section
            className="solution-modal process-preview-modal"
            role="dialog"
            aria-modal="true"
            aria-label="Solution process result"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="solution-modal-header">
              <div>
                <p className="section-label">Completed process</p>
                <h2>{f2lModeLabel(processPreview.request.f2lMode)} solution</h2>
                <span className="solution-modal-context">
                  Cross {crossFaceLabel(processPreview.request.crossFace)}
                </span>
              </div>
              <button className="icon-button" type="button" onClick={() => setProcessPreview(null)} aria-label="Close solution">
                <X size={18} />
              </button>
            </div>
            <p className="modal-scramble">{processPreview.request.scramble}</p>
            <SolutionResultBody
              result={processPreview.result}
              requestedCross={processPreview.request.crossFace}
            />
          </section>
        </div>
      ) : null}
    </main>
  );
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

async function waitForSolveJob(
  initialJob: SolveJob,
  onProgress?: (job: SolveJob) => void,
): Promise<SolveResponse> {
  let job = initialJob;
  while (true) {
    onProgress?.(job);
    if (job.status === "completed") {
      if (!job.result) {
        throw new Error("Optimized solve completed without a result");
      }
      return job.result;
    }
    if (job.status === "failed") {
      throw new Error(job.error ?? "Solve failed");
    }
    if (job.status === "cancelled") {
      throw new SolveJobCancelledError();
    }
    await new Promise<void>((resolve) => window.setTimeout(resolve, 250));
    job = await fetchSolveJob(job.id);
  }
}

class SolveJobCancelledError extends Error {
  constructor() {
    super("Solve cancelled");
  }
}

function isTerminalProcess(process: SolutionProcess): boolean {
  return process.status === "completed"
    || process.status === "failed"
    || process.status === "cancelled";
}

function trimFinishedProcesses(processes: SolutionProcess[]): SolutionProcess[] {
  const active = processes.filter((process) => !isTerminalProcess(process));
  const finished = processes
    .filter(isTerminalProcess)
    .sort((left, right) => right.updatedAt - left.updatedAt)
    .slice(0, 20);
  return [...active, ...finished];
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

function penaltyForInspectionElapsed(inspectionElapsedMs: number): TimerPenalty {
  if (inspectionElapsedMs > INSPECTION_DNF_MS) {
    return "dnf";
  }
  if (inspectionElapsedMs > INSPECTION_PLUS_TWO_MS) {
    return "+2";
  }
  return "none";
}

function isTextEntryTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return Boolean(target.closest("input, textarea, select, [contenteditable='true']"));
}

function blurFocusedButton() {
  if (document.activeElement instanceof HTMLButtonElement) {
    document.activeElement.blur();
  }
}
