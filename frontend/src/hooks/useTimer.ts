import {useCallback, useEffect, useRef, useState} from "react";
import type {SolveResponse} from "../types";

export const INSPECTION_PLUS_TWO_MS = 15_000;
export const INSPECTION_DNF_MS = 17_000;

export type TimerPhase = "idle" | "armed" | "inspection" | "running" | "stopped";
export type ArmedSource = "idle" | "stopped" | "inspection" | null;
export type TimerPenalty = "none" | "+2" | "dnf";
export type TimerF2LMode = "greedy" | "optimized";
export type TimerSolutionStatus = "idle" | "loading" | "ready" | "error";

export type CompletedAttemptSnapshot = {
    clientAttemptId: string;
    scramble: string;
    crossFace: string;
    f2lMode: TimerF2LMode;
    elapsedMs: number;
    penalty: TimerPenalty;
    result: SolveResponse | null;
};

type Options = {
    activeView: string;
    overlayOpen: boolean;
    isEditingScramble: boolean;
    attemptSaveStatus: "idle" | "saving" | "saved" | "error";
    committedScramble: string;
    crossFace: string;
    f2lMode: TimerF2LMode;
    clientAttemptId: string;
    solutionStatus: TimerSolutionStatus;
    result: SolveResponse | null;
    onStopped: (snapshot: CompletedAttemptSnapshot) => void;
};

export function useTimer({
    activeView,
    overlayOpen,
    isEditingScramble,
    attemptSaveStatus,
    committedScramble,
    crossFace,
    f2lMode,
    clientAttemptId,
    solutionStatus,
    result,
    onStopped,
}: Options) {
    const [timerPhase, setTimerPhase] = useState<TimerPhase>("idle");
    const [armedSource, setArmedSource] = useState<ArmedSource>(null);
    const [inspectionStartedAt, setInspectionStartedAt] = useState<number | null>(null);
    const [runStartedAt, setRunStartedAt] = useState<number | null>(null);
    const [stoppedElapsedMs, setStoppedElapsedMs] = useState<number | null>(null);
    const [finalPenalty, setFinalPenalty] = useState<TimerPenalty>("none");
    const [clockMs, setClockMs] = useState(0);
    const onStoppedRef = useRef(onStopped);
    onStoppedRef.current = onStopped;

    const inspectionElapsedMs =
        inspectionStartedAt === null ? 0 : Math.max(0, clockMs - inspectionStartedAt);
    const inspectionPenalty = penaltyForInspectionElapsed(inspectionElapsedMs);
    const runningElapsedMs = runStartedAt === null ? 0 : Math.max(0, clockMs - runStartedAt);
    const attemptLocked = attemptSaveStatus === "saving" && timerPhase === "stopped";

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

    const resetTimer = useCallback(() => {
        setTimerPhase("idle");
        setArmedSource(null);
        setInspectionStartedAt(null);
        setRunStartedAt(null);
        setStoppedElapsedMs(null);
        setFinalPenalty("none");
        setClockMs(window.performance.now());
    }, []);

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
        onStoppedRef.current({
            clientAttemptId,
            scramble: committedScramble,
            crossFace,
            f2lMode,
            elapsedMs,
            penalty: finalPenalty,
            result: solutionStatus === "ready" && result?.scramble === committedScramble ? result : null,
        });
        setTimerPhase("stopped");
        setStoppedElapsedMs(elapsedMs);
        setRunStartedAt(null);
        setClockMs(now);
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

    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (overlayOpen) {
                if (event.code === "Space") {
                    event.preventDefault();
                }
                return;
            }
            if (activeView !== "timer" || isEditingScramble) {
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
            }
        };

        const handleKeyUp = (event: KeyboardEvent) => {
            if (overlayOpen) {
                if (event.code === "Space") {
                    event.preventDefault();
                }
                return;
            }
            if (activeView !== "timer" || isEditingScramble) {
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
        activeView,
        armedSource,
        attemptSaveStatus,
        clientAttemptId,
        committedScramble,
        crossFace,
        f2lMode,
        finalPenalty,
        inspectionStartedAt,
        isEditingScramble,
        overlayOpen,
        result,
        runStartedAt,
        solutionStatus,
        timerPhase,
    ]);

    return {
        timerPhase,
        stoppedElapsedMs,
        finalPenalty,
        inspectionPenalty,
        inspectionElapsedMs,
        runningElapsedMs,
        attemptLocked,
        resetTimer,
        handleTimerPointerDown,
        handleTimerPointerUp,
    };
}

export function penaltyForInspectionElapsed(inspectionElapsedMs: number): TimerPenalty {
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
