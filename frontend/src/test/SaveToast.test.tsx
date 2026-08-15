import {act, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import SaveToast from "../SaveToast";

describe("SaveToast", () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it("announces one save notification and lets the user dismiss it", () => {
        const onDismiss = vi.fn();
        render(<SaveToast message="Solve saved · 0.33" onDismiss={onDismiss}/>);

        expect(screen.getByRole("status")).toHaveTextContent("Solve saved · 0.33");
        expect(screen.getAllByRole("status")).toHaveLength(1);

        fireEvent.click(screen.getByRole("button", {name: "Dismiss notification"}));
        expect(onDismiss).toHaveBeenCalledTimes(1);
    });

    it("automatically dismisses a save notification after five seconds", () => {
        vi.useFakeTimers();
        const onDismiss = vi.fn();
        render(<SaveToast message="Solve saved · 0.33" onDismiss={onDismiss}/>);

        act(() => vi.advanceTimersByTime(4_999));
        expect(onDismiss).not.toHaveBeenCalled();

        act(() => vi.advanceTimersByTime(1));
        expect(onDismiss).toHaveBeenCalledTimes(1);
    });

    it("restarts the dismissal timer when a newer save notice replaces the current one", () => {
        vi.useFakeTimers();
        const onDismiss = vi.fn();
        const {rerender} = render(<SaveToast message="Solve saved · 0.33" onDismiss={onDismiss}/>);

        act(() => vi.advanceTimersByTime(4_000));
        rerender(<SaveToast message="Solve saved · 0.42" onDismiss={onDismiss}/>);

        act(() => vi.advanceTimersByTime(1_000));
        expect(onDismiss).not.toHaveBeenCalled();
        expect(screen.getByRole("status")).toHaveTextContent("Solve saved · 0.42");

        act(() => vi.advanceTimersByTime(4_000));
        expect(onDismiss).toHaveBeenCalledTimes(1);
    });
});
