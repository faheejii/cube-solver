import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import {useTimer, type TimerPhase} from "../hooks/useTimer";

function TimerHarness({inspectionEnabled}: {inspectionEnabled: boolean}) {
    const timer = useTimer({
        activeView: "timer",
        overlayOpen: false,
        isEditingScramble: false,
        attemptSaveStatus: "idle",
        committedScramble: "R",
        crossFace: "U",
        f2lMode: "greedy",
        inspectionEnabled,
        clientAttemptId: "attempt",
        solutionStatus: "idle",
        result: null,
        onStopped: () => undefined,
    });
    return <div onPointerDown={timer.handleTimerPointerDown} onPointerUp={timer.handleTimerPointerUp}>{timer.timerPhase as TimerPhase}</div>;
}

describe("useTimer inspection setting", () => {
    it("starts inspection when enabled", () => {
        render(<TimerHarness inspectionEnabled={true}/>);
        const timer = screen.getByText("idle");
        fireEvent.pointerDown(timer);
        fireEvent.pointerUp(timer);
        expect(screen.getByText("inspection")).toBeInTheDocument();
    });

    it("starts the solve directly when inspection is disabled", () => {
        render(<TimerHarness inspectionEnabled={false}/>);
        const timer = screen.getByText("idle");
        fireEvent.pointerDown(timer);
        fireEvent.pointerUp(timer);
        expect(screen.getByText("running")).toBeInTheDocument();
    });
});
