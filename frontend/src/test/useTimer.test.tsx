import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import {useTimer, type TimerPhase} from "../hooks/useTimer";

function TimerHarness({
                         inspectionEnabled,
                         overlayOpen = false,
                         onStopped = () => undefined,
                     }: {
    inspectionEnabled: boolean;
    overlayOpen?: boolean;
    onStopped?: () => void;
}) {
    const timer = useTimer({
        activeView: "timer",
        overlayOpen,
        isEditingScramble: false,
        attemptSaveStatus: "idle",
        committedScramble: "R",
        crossFace: "U",
        f2lMode: "greedy",
        inspectionEnabled,
        clientAttemptId: "attempt",
        solutionStatus: "idle",
        result: null,
        onStopped,
    });
    return <><div>{timer.timerPhase as TimerPhase}</div><input aria-label="Timer test input"/></>;
}

function pressSpace() {
    fireEvent.keyDown(window, {code: "Space", key: " "});
    fireEvent.keyUp(window, {code: "Space", key: " "});
}

describe("useTimer keyboard controls", () => {
    it("starts inspection with Space when enabled and ignores other idle keys", () => {
        render(<TimerHarness inspectionEnabled={true}/>);
        fireEvent.keyDown(window, {code: "Enter", key: "Enter"});
        expect(screen.getByText("idle")).toBeInTheDocument();
        fireEvent.keyDown(window, {code: "Space", key: " "});
        expect(screen.getByText("armed")).toBeInTheDocument();
        fireEvent.keyUp(window, {code: "Space", key: " "});
        expect(screen.getByText("inspection")).toBeInTheDocument();
    });

    it("starts the solve directly with Space when inspection is disabled", () => {
        render(<TimerHarness inspectionEnabled={false}/>);
        pressSpace();
        expect(screen.getByText("running")).toBeInTheDocument();
    });

    it.each([
        ["a letter", "KeyA", "a"],
        ["a modifier", "ShiftLeft", "Shift"],
        ["Space", "Space", " "],
    ])("stops a running solve with %s", (_description, code, key) => {
        const onStopped = vi.fn();
        render(<TimerHarness inspectionEnabled={false} onStopped={onStopped}/>);
        pressSpace();

        fireEvent.keyDown(window, {code, key});

        expect(screen.getByText("stopped")).toBeInTheDocument();
        expect(onStopped).toHaveBeenCalledOnce();
    });

    it("stops a running solve even when the pressed key targets a text field", () => {
        render(<TimerHarness inspectionEnabled={false}/>);
        pressSpace();

        fireEvent.keyDown(screen.getByLabelText("Timer test input"), {code: "KeyA", key: "a"});

        expect(screen.getByText("stopped")).toBeInTheDocument();
    });

    it("does not stop the timer while an overlay is open", () => {
        const {rerender} = render(<TimerHarness inspectionEnabled={false}/>);
        pressSpace();
        rerender(<TimerHarness inspectionEnabled={false} overlayOpen/>);

        fireEvent.keyDown(window, {code: "KeyA", key: "a"});

        expect(screen.getByText("running")).toBeInTheDocument();
    });
});
