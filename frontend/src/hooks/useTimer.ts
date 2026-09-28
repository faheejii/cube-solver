import {useCallback, useEffect, useRef, useState} from "react";
import type {SolveResponse} from "../types";

export const INSPECTION_PLUS_TWO_MS = 15_000;
export const INSPECTION_DNF_MS = 17_000;
export const TIMER_ARM_HOLD_MS = 500;

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
    inspectionEnabled: boolean;
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
    inspectionEnabled,
    clientAttemptId,
    solutionStatus,
    result,
    onStopped,
}: Options) {
    const [timerPhase, setTimerPhase] = useState<TimerPhase>("idle");
    const [armedSource, setArmedSource] = useState<ArmedSource>(null);
    const [armReady, setArmReady] = useState(false);
    const [inspectionStartedAt, setInspectionStartedAt] = useState<number | null>(null);
    const [runStartedAt, setRunStartedAt] = useState<number | null>(null);
    const [stoppedElapsedMs, setStoppedElapsedMs] = useState<number | null>(null);
    const [finalPenalty, setFinalPenalty] = useState<TimerPenalty>("none");
    const [clockMs, setClockMs] = useState(0);
    const onStoppedRef = useRef(onStopped);
    const armStartedAtRef = useRef<number | null>(null);
    const armedSourceRef = useRef<ArmedSource>(null);
    const armTimeoutRef = useRef<number | null>(null);
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
        clearArmHold();
        setTimerPhase("idle");
        setArmedSource(null);
        setInspectionStartedAt(null);
        setRunStartedAt(null);
        setStoppedElapsedMs(null);
        setFinalPenalty("none");
        setClockMs(window.performance.now());
    }, []);

    function clearArmHold() {
        if (armTimeoutRef.current !== null) {
            window.clearTimeout(armTimeoutRef.current);
            armTimeoutRef.current = null;
        }
        armStartedAtRef.current = null;
        armedSourceRef.current = null;
        setArmReady(false);
    }

    function cancelArmedTimer() {
        const source = armedSourceRef.current;
        if (source === null) {
            return;
        }
        clearArmHold();
        setTimerPhase(source);
        setArmedSource(null);
        setClockMs(window.performance.now());
    }

    function armTimer(source: Exclude<ArmedSource, null>) {
        if (armedSourceRef.current !== null) {
            return;
        }
        const startedAt = window.performance.now();
        armStartedAtRef.current = startedAt;
        armedSourceRef.current = source;
        setTimerPhase("armed");
        setArmedSource(source);
        setArmReady(false);
        setClockMs(startedAt);
        armTimeoutRef.current = window.setTimeout(() => {
            if (armedSourceRef.current !== null) {
                setArmReady(true);
            }
            armTimeoutRef.current = null;
        }, TIMER_ARM_HOLD_MS);
    }

    function beginInspection() {
        clearArmHold();
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
        clearArmHold();
        const now = window.performance.now();
        const nextPenalty = penaltyForInspectionElapsed(now - (inspectionStartedAt ?? now));
        setTimerPhase("running");
        setArmedSource(null);
        setRunStartedAt(now);
        setStoppedElapsedMs(null);
        setFinalPenalty(nextPenalty);
        setClockMs(now);
    }

    function releaseArmedTimer(source: Exclude<ArmedSource, null>) {
        clearArmHold();
        if (source === "inspection") {
            beginRunning();
            return;
        }
        if (inspectionEnabled) {
            beginInspection();
        } else {
            beginRunning();
        }
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

    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (overlayOpen || hasModalDialog()) {
                if (event.code === "Space") {
                    event.preventDefault();
                }
                cancelArmedTimer();
                return;
            }
            if (activeView !== "timer") {
                cancelArmedTimer();
                return;
            }
            if (attemptSaveStatus === "saving" || attemptSaveStatus === "error") {
                cancelArmedTimer();
                return;
            }

            if (timerPhase === "running") {
                if (event.code === "Space") {
                    event.preventDefault();
                }
                stopTimer();
                return;
            }

            if (isEditingScramble || isTextEntryTarget(event.target) || event.code !== "Space" || event.repeat
                || armedSourceRef.current !== null) {
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
            }
        };

        const handleKeyUp = (event: KeyboardEvent) => {
            if (overlayOpen || hasModalDialog()) {
                if (event.code === "Space") {
                    event.preventDefault();
                }
                cancelArmedTimer();
                return;
            }
            if (activeView !== "timer" || isEditingScramble
                || attemptSaveStatus === "saving" || attemptSaveStatus === "error") {
                cancelArmedTimer();
                return;
            }
            if (event.code !== "Space" || isTextEntryTarget(event.target)) {
                return;
            }

            event.preventDefault();
            blurFocusedButton();
            const source = armedSourceRef.current;
            const startedAt = armStartedAtRef.current;
            if (source !== null && startedAt !== null) {
                if (window.performance.now() - startedAt >= TIMER_ARM_HOLD_MS) {
                    releaseArmedTimer(source);
                } else {
                    cancelArmedTimer();
                }
            }
        };

        const cancelOnWindowBlur = () => cancelArmedTimer();

        window.addEventListener("keydown", handleKeyDown);
        window.addEventListener("keyup", handleKeyUp);
        window.addEventListener("blur", cancelOnWindowBlur);
        return () => {
            window.removeEventListener("keydown", handleKeyDown);
            window.removeEventListener("keyup", handleKeyUp);
            window.removeEventListener("blur", cancelOnWindowBlur);
        };
    }, [
        activeView,
        attemptSaveStatus,
        clientAttemptId,
        committedScramble,
        crossFace,
        f2lMode,
        inspectionEnabled,
        finalPenalty,
        inspectionStartedAt,
        isEditingScramble,
        overlayOpen,
        result,
        runStartedAt,
        solutionStatus,
        timerPhase,
    ]);

    useEffect(() => () => {
        if (armTimeoutRef.current !== null) {
            window.clearTimeout(armTimeoutRef.current);
        }
        armTimeoutRef.current = null;
        armStartedAtRef.current = null;
        armedSourceRef.current = null;
    }, []);

    useEffect(() => {
        if (activeView !== "timer" || overlayOpen || isEditingScramble
            || attemptSaveStatus === "saving" || attemptSaveStatus === "error") {
            cancelArmedTimer();
        }
    }, [activeView, attemptSaveStatus, isEditingScramble, overlayOpen]);

    return {
        timerPhase,
        armedSource,
        armReady,
        stoppedElapsedMs,
        finalPenalty,
        inspectionPenalty,
        inspectionElapsedMs,
        runningElapsedMs,
        attemptLocked,
        resetTimer,
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

function hasModalDialog(): boolean {
    return document.querySelector('[role="dialog"][aria-modal="true"]') !== null;
}

function blurFocusedButton() {
    if (document.activeElement instanceof HTMLButtonElement) {
        document.activeElement.blur();
    }
}
