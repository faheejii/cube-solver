import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import type {SolveHistoryEntry, SolveStatistics} from "../types";

const api = vi.hoisted(() => ({fetchSolveHistory: vi.fn()}));
vi.mock("../api", () => api);

import StatisticsModal from "../StatisticsModal";

const statistics: SolveStatistics = {
    solveCount: 3,
    dnfCount: 1,
    bestMs: 10_000,
    averageMs: 11_000,
    ao5: {status: "insufficient", valueMs: null},
    ao12: {status: "insufficient", valueMs: null},
    recentSolves: [],
};

function makeEntry(id: number, values: Partial<SolveHistoryEntry> = {}): SolveHistoryEntry {
    return {
        id,
        clientAttemptId: `attempt-${id}`,
        scramble: "R U",
        crossFaceRequested: "U",
        timerMs: 10_000,
        officialMs: 10_000,
        penalty: "none",
        dnf: false,
        fastCrossFaceRequested: "U",
        optimizedCrossFaceRequested: null,
        createdAt: `2026-09-2${id}T12:00:00Z`,
        ...values,
    };
}

function renderModal(onClose = vi.fn()) {
    return render(<StatisticsModal statistics={statistics} statisticsLoading={false} onClose={onClose}/>);
}

describe("StatisticsModal", () => {
    beforeEach(() => api.fetchSolveHistory.mockReset());

    it("fetches the latest 50, plots oldest first, includes +2 time, and marks DNF separately", async () => {
        api.fetchSolveHistory.mockResolvedValue({
            items: [
                makeEntry(3, {officialMs: null, timerMs: 14_000, penalty: "dnf", dnf: true}),
                makeEntry(2, {officialMs: 12_000, timerMs: 10_000, penalty: "+2"}),
                makeEntry(1, {officialMs: 10_000, createdAt: "2026-09-20T12:00:00Z"}),
            ],
            nextCursor: null,
        });

        renderModal();

        expect(document.querySelector(".statistics-modal-backdrop")?.parentElement).toBe(document.body);

        expect(await screen.findByRole("img", {name: /Solve 1: 10\.00/})).toBeInTheDocument();
        expect(screen.getByRole("img", {name: /Solve 2: 12\.00\+/})).toBeInTheDocument();
        expect(screen.getByRole("img", {name: /Solve 3: DNF/})).toBeInTheDocument();
        expect(api.fetchSolveHistory).toHaveBeenCalledWith(50);
        expect(screen.getByText("Showing 3 solves")).toBeInTheDocument();

        fireEvent.focus(screen.getByRole("img", {name: /Solve 2:/}));
        expect(screen.getByRole("status")).toHaveTextContent("Solve 2 · 12.00+");
        expect(document.querySelectorAll(".chart-time-line")).toHaveLength(1);
        expect(document.querySelector(".chart-point.dnf path")).toBeInTheDocument();
    });

    it("shows loading and empty states when a user has no history", async () => {
        api.fetchSolveHistory.mockImplementation(() => new Promise((resolve) => {
            window.setTimeout(() => resolve({items: [], nextCursor: null}), 20);
        }));
        renderModal();

        expect(screen.getByText("Loading solve history…")).toBeInTheDocument();
        expect(await screen.findByText("No solves yet")).toBeInTheDocument();
    });

    it("offers retry after a failed history request", async () => {
        api.fetchSolveHistory
            .mockRejectedValueOnce(new Error("History unavailable"))
            .mockResolvedValueOnce({items: [makeEntry(1)], nextCursor: null});
        renderModal();

        expect(await screen.findByRole("alert")).toHaveTextContent("History unavailable");
        fireEvent.click(screen.getByRole("button", {name: "Retry"}));
        expect(await screen.findByRole("img", {name: /Solve 3:/})).toBeInTheDocument();
        expect(api.fetchSolveHistory).toHaveBeenCalledTimes(2);
    });

    it("closes on Escape and backdrop click", async () => {
        const onClose = vi.fn();
        api.fetchSolveHistory.mockResolvedValue({items: [], nextCursor: null});
        renderModal(onClose);

        fireEvent.keyDown(window, {key: "Escape"});
        expect(onClose).toHaveBeenCalledOnce();
        fireEvent.mouseDown(screen.getByRole("presentation"));
        await waitFor(() => expect(onClose).toHaveBeenCalledTimes(2));
    });
});
