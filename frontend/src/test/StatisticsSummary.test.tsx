import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import StatisticsSummary from "../StatisticsSummary";
import type {SolveHistoryEntry, SolveStatistics} from "../types";

const api = vi.hoisted(() => ({fetchSolveHistory: vi.fn()}));
vi.mock("../api", () => api);

const statistics: SolveStatistics = {
    solveCount: 2,
    dnfCount: 0,
    bestMs: 12_340,
    averageMs: 13_000,
    ao5: {status: "insufficient", valueMs: null},
    ao12: {status: "insufficient", valueMs: null},
    recentSolves: [],
};

describe("StatisticsSummary", () => {
    it("opens the solve represented by Best and exposes its busy state", async () => {
        let resolveBest: (() => void) | undefined;
        const onOpenBestSolve = vi.fn(() => new Promise<void>((resolve) => { resolveBest = resolve; }));
        render(<StatisticsSummary statistics={statistics} loading={false} onOpenBestSolve={onOpenBestSolve} onOpenSolve={vi.fn()}/>);

        const bestButton = screen.getByRole("button", {name: "Open best solve 12.34"});
        fireEvent.click(bestButton);
        expect(onOpenBestSolve).toHaveBeenCalledWith(12_340);
        expect(bestButton).toHaveAttribute("aria-busy", "true");

        resolveBest?.();
        await waitFor(() => expect(bestButton).toHaveAttribute("aria-busy", "false"));
    });

    it("keeps Best unavailable when no valid best time exists and reports lookup failures", async () => {
        const onOpenBestSolve = vi.fn().mockRejectedValue(new Error("No longer available"));
        const {rerender} = render(
            <StatisticsSummary statistics={{...statistics, bestMs: null}} loading={false} onOpenBestSolve={onOpenBestSolve} onOpenSolve={vi.fn()}/>,
        );
        expect(screen.getByRole("button", {name: "Open best solve —"})).toBeDisabled();

        rerender(<StatisticsSummary statistics={statistics} loading={false} onOpenBestSolve={onOpenBestSolve} onOpenSolve={vi.fn()}/>);
        fireEvent.click(screen.getByRole("button", {name: "Open best solve 12.34"}));
        expect(await screen.findByRole("alert")).toHaveTextContent("Could not open the best solve. Try again.");
    });

    it("opens the Ao5 breakdown from its stat and links its rows to the matching solve", async () => {
        const entries = Array.from({length: 5}, (_, index): SolveHistoryEntry => ({
            id: 5 - index,
            clientAttemptId: `attempt-${5 - index}`,
            scramble: `R U ${index}`,
            crossFaceRequested: "U",
            timerMs: 10_000 + index * 1_000,
            officialMs: 10_000 + index * 1_000,
            penalty: "none",
            dnf: false,
            fastCrossFaceRequested: "U",
            optimizedCrossFaceRequested: null,
            createdAt: new Date(Date.UTC(2026, 0, 5 - index)).toISOString(),
        }));
        api.fetchSolveHistory.mockResolvedValue({items: entries, nextCursor: null, totalCount: 8});
        const onOpenSolve = vi.fn();
        render(<StatisticsSummary statistics={statistics} loading={false} onOpenSolve={onOpenSolve}/>);

        fireEvent.click(screen.getByRole("button", {name: "Open Ao5 breakdown, —"}));
        const dialog = await screen.findByRole("dialog", {name: "Ao5 breakdown"});
        expect(api.fetchSolveHistory).toHaveBeenCalledWith(5);
        expect(await within(dialog).findByText(/Fastest and slowest results are excluded/)).toBeInTheDocument();
        expect(within(dialog).getByText("#8")).toBeInTheDocument();
        expect(within(dialog).getByText("Dropped · fastest")).toBeInTheDocument();
        expect(within(dialog).getByText("Dropped · slowest")).toBeInTheDocument();

        fireEvent.click(within(dialog).getByRole("button", {name: /Open solve 8,/}));
        expect(onOpenSolve).toHaveBeenCalledWith(entries[0], 8, false, expect.any(HTMLButtonElement));
    });

    it("opens Ao12 even when the value is insufficient and restores focus on close", async () => {
        api.fetchSolveHistory.mockResolvedValue({items: [], nextCursor: null, totalCount: 0});
        render(<StatisticsSummary statistics={statistics} loading={false} onOpenSolve={vi.fn()}/>);
        const trigger = screen.getByRole("button", {name: "Open Ao12 breakdown, —"});

        fireEvent.click(trigger);
        expect(await screen.findByRole("dialog", {name: "Ao12 breakdown"})).toBeInTheDocument();
        expect(api.fetchSolveHistory).toHaveBeenCalledWith(12);
        fireEvent.keyDown(screen.getByRole("dialog", {name: "Ao12 breakdown"}), {key: "Escape"});
        await waitFor(() => expect(trigger).toHaveFocus());
    });
});
