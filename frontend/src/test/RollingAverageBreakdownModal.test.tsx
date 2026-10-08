import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import type {SolveHistoryEntry} from "../types";

const api = vi.hoisted(() => ({fetchSolveHistory: vi.fn()}));
vi.mock("../api", () => api);

import RollingAverageBreakdownModal from "../RollingAverageBreakdownModal";

function entry(id: number, officialMs: number | null, penalty = "none"): SolveHistoryEntry {
    return {
        id,
        clientAttemptId: `attempt-${id}`,
        scramble: "R U",
        crossFaceRequested: "U",
        timerMs: officialMs,
        officialMs,
        penalty,
        dnf: penalty === "dnf",
        fastCrossFaceRequested: "U",
        optimizedCrossFaceRequested: null,
        createdAt: new Date(Date.UTC(2026, 0, id)).toISOString(),
    };
}

describe("RollingAverageBreakdownModal", () => {
    beforeEach(() => api.fetchSolveHistory.mockReset());

    it("shows the DNF outcome and lists each solve with its disposition", async () => {
        const entries = [
            entry(5, null, "dnf"),
            entry(4, 14_000, "+2"),
            entry(3, 13_000),
            entry(2, 12_000),
            entry(1, 11_000),
        ];
        api.fetchSolveHistory.mockResolvedValue({items: entries, nextCursor: null, totalCount: 5});
        const onOpenSolve = vi.fn();
        render(<RollingAverageBreakdownModal size={5} solveCount={5} onClose={vi.fn()} onOpenSolve={onOpenSolve}/>);

        const dialog = await screen.findByRole("dialog", {name: "Ao5 breakdown"});
        expect(dialog).toHaveTextContent("13.00");
        expect(dialog).toHaveTextContent("Dropped · slowest");
        expect(dialog).toHaveTextContent("14.00+");
        fireEvent.click(screen.getByRole("button", {name: /Open solve 5, DNF/}));
        expect(onOpenSolve).toHaveBeenCalledWith(entries[0], 5, false, expect.any(HTMLButtonElement));
    });

    it("offers retry after load errors and renders an empty state", async () => {
        api.fetchSolveHistory
            .mockRejectedValueOnce(new Error("History unavailable"))
            .mockResolvedValueOnce({items: [], nextCursor: null, totalCount: 0});
        render(<RollingAverageBreakdownModal size={12} solveCount={0} onClose={vi.fn()} onOpenSolve={vi.fn()}/>);

        expect(await screen.findByRole("alert")).toHaveTextContent("History unavailable");
        fireEvent.click(screen.getByRole("button", {name: "Retry"}));
        expect(await screen.findByRole("status")).toHaveTextContent("No solves yet");
        expect(api.fetchSolveHistory).toHaveBeenCalledTimes(2);
        expect(api.fetchSolveHistory).toHaveBeenNthCalledWith(1, 12);
        expect(api.fetchSolveHistory).toHaveBeenNthCalledWith(2, 12);
    });

    it("traps focus inside the modal when tabbing backwards from the close button", async () => {
        api.fetchSolveHistory.mockResolvedValue({items: [entry(1, 10_000)], nextCursor: null, totalCount: 1});
        render(<RollingAverageBreakdownModal size={5} solveCount={1} onClose={vi.fn()} onOpenSolve={vi.fn()}/>);
        const close = await screen.findByRole("button", {name: "Close Ao5 breakdown"});
        await waitFor(() => expect(close).toHaveFocus());

        fireEvent.keyDown(close, {key: "Tab", shiftKey: true});
        expect(screen.getByRole("button", {name: /Open solve 1,/})).toHaveFocus();
    });
});
