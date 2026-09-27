import {act, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {useTimer, type TimerPhase} from "../hooks/useTimer";

function TimerHarness({
                         inspectionEnabled,
                         overlayOpen = false,
                         activeView = "timer",
                         isEditingScramble = false,
                         attemptSaveStatus = "idle",
                         onStopped = () => undefined,
                     }: {
    inspectionEnabled: boolean;
    overlayOpen?: boolean;
    activeView?: string;
    isEditingScramble?: boolean;
    attemptSaveStatus?: "idle" | "saving" | "saved" | "error";
    onStopped?: () => void;
}) {
    const timer = useTimer({
        activeView,
        overlayOpen,
        isEditingScramble,
        attemptSaveStatus,
        committedScramble: "R",
        crossFace: "U",
        f2lMode: "greedy",
        inspectionEnabled,
        clientAttemptId: "attempt",
        solutionStatus: "idle",
        result: null,
        onStopped,
    });
    return <>
        <div>{timer.timerPhase as TimerPhase}</div>
        <div>{timer.armReady ? "ready" : "not ready"}</div>
        <div>{timer.finalPenalty}</div>
        <input aria-label="Timer test input"/>
    </>;
}

function keyDownSpace() {
    fireEvent.keyDown(window, {code: "Space", key: " "});
}

function keyUpSpace() {
    fireEvent.keyUp(window, {code: "Space", key: " "});
}

async function holdSpaceFor(ms: number) {
    keyDownSpace();
    await act(async () => {
        await vi.advanceTimersByTimeAsync(ms);
    });
    keyUpSpace();
}

describe("useTimer keyboard controls", () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it("requires a continuous 500ms Space hold and shows readiness at the threshold", async () => {
        render(<TimerHarness inspectionEnabled={true}/>);
        fireEvent.keyDown(window, {code: "Enter", key: "Enter"});
        expect(screen.getByText("idle")).toBeInTheDocument();
        keyDownSpace();
        expect(screen.getByText("armed")).toBeInTheDocument();
        expect(screen.getByText("not ready")).toBeInTheDocument();
        fireEvent.keyDown(window, {code: "Space", key: " ", repeat: true});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(499);
        });
        expect(screen.getByText("not ready")).toBeInTheDocument();
        expect(screen.getByText("armed")).toBeInTheDocument();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(1);
        });
        expect(screen.getByText("ready")).toBeInTheDocument();
        keyUpSpace();
        expect(screen.getByText("inspection")).toBeInTheDocument();
    });

    it.each(["idle", "stopped"] as const)("cancels an early release back to %s", async (source) => {
        render(<TimerHarness inspectionEnabled={false}/>);
        if (source === "stopped") {
            await holdSpaceFor(500);
            fireEvent.keyDown(window, {code: "KeyA", key: "a"});
            expect(screen.getByText("stopped")).toBeInTheDocument();
        }
        keyDownSpace();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(499);
        });
        keyUpSpace();
        expect(screen.getByText(source)).toBeInTheDocument();
        expect(screen.getByText("not ready")).toBeInTheDocument();
    });

    it("cancels an early release in inspection without ending inspection", async () => {
        render(<TimerHarness inspectionEnabled={true}/>);
        await holdSpaceFor(500);
        expect(screen.getByText("inspection")).toBeInTheDocument();
        keyDownSpace();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(499);
        });
        keyUpSpace();
        expect(screen.getByText("inspection")).toBeInTheDocument();
    });

    it("starts a solve directly after a threshold hold when inspection is disabled", async () => {
        render(<TimerHarness inspectionEnabled={false}/>);
        await holdSpaceFor(500);
        expect(screen.getByText("running")).toBeInTheDocument();
    });

    it("preserves inspection time while holding Space to start the solve", async () => {
        render(<TimerHarness inspectionEnabled={true}/>);
        await holdSpaceFor(500);
        await act(async () => {
            await vi.advanceTimersByTimeAsync(14_600);
        });
        await holdSpaceFor(500);
        expect(screen.getByText("running")).toBeInTheDocument();
        expect(screen.getByText("+2")).toBeInTheDocument();
    });

    it.each([
        ["a letter", "KeyA", "a"],
        ["a modifier", "ShiftLeft", "Shift"],
        ["Space", "Space", " "],
    ])("stops a running solve with %s", async (_description, code, key) => {
        const onStopped = vi.fn();
        render(<TimerHarness inspectionEnabled={false} onStopped={onStopped}/>);
        await holdSpaceFor(500);
        fireEvent.keyDown(window, {code, key});
        expect(screen.getByText("stopped")).toBeInTheDocument();
        expect(onStopped).toHaveBeenCalledOnce();
    });

    it("stops a running solve even when the pressed key targets a text field", async () => {
        render(<TimerHarness inspectionEnabled={false}/>);
        await holdSpaceFor(500);
        fireEvent.keyDown(screen.getByLabelText("Timer test input"), {code: "KeyA", key: "a"});
        expect(screen.getByText("stopped")).toBeInTheDocument();
    });

    it("does not stop the timer while an overlay is open", async () => {
        const {rerender} = render(<TimerHarness inspectionEnabled={false}/>);
        await holdSpaceFor(500);
        rerender(<TimerHarness inspectionEnabled={false} overlayOpen/>);
        fireEvent.keyDown(window, {code: "KeyA", key: "a"});
        expect(screen.getByText("running")).toBeInTheDocument();
    });

    it("does not stop a running solve while an accessible modal is open", async () => {
        render(<TimerHarness inspectionEnabled={false}/>);
        await holdSpaceFor(500);
        const modal = document.createElement("div");
        modal.setAttribute("role", "dialog");
        modal.setAttribute("aria-modal", "true");
        document.body.append(modal);
        fireEvent.keyDown(window, {code: "KeyA", key: "a"});
        expect(screen.getByText("running")).toBeInTheDocument();
        modal.remove();
    });

    it("cancels an in-progress hold when an accessible modal opens", async () => {
        render(<TimerHarness inspectionEnabled={false}/>);
        keyDownSpace();
        const modal = document.createElement("div");
        modal.setAttribute("role", "dialog");
        modal.setAttribute("aria-modal", "true");
        document.body.append(modal);
        await act(async () => {
            await vi.advanceTimersByTimeAsync(600);
        });
        keyUpSpace();
        expect(screen.getByText("idle")).toBeInTheDocument();
        expect(screen.getByText("not ready")).toBeInTheDocument();
        modal.remove();
    });

    it("cancels arming when the view changes, editing begins, or an attempt locks", () => {
        const {rerender} = render(<TimerHarness inspectionEnabled={false}/>);
        keyDownSpace();
        rerender(<TimerHarness inspectionEnabled={false} activeView="history"/>);
        expect(screen.getByText("idle")).toBeInTheDocument();
        keyUpSpace();
        expect(screen.getByText("idle")).toBeInTheDocument();

        rerender(<TimerHarness inspectionEnabled={false}/>);
        keyDownSpace();
        rerender(<TimerHarness inspectionEnabled={false} isEditingScramble/>);
        expect(screen.getByText("idle")).toBeInTheDocument();

        rerender(<TimerHarness inspectionEnabled={false}/>);
        keyDownSpace();
        rerender(<TimerHarness inspectionEnabled={false} attemptSaveStatus="saving"/>);
        expect(screen.getByText("idle")).toBeInTheDocument();
    });

    it("clears the pending readiness timer when unmounted", () => {
        const {unmount} = render(<TimerHarness inspectionEnabled={false}/>);
        keyDownSpace();
        expect(vi.getTimerCount()).toBe(1);
        unmount();
        expect(vi.getTimerCount()).toBe(0);
    });
});
