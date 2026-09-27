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
    return render(<StatisticsModal statistics={statistics} onClose={onClose}/>);
}

function summaryValue(label: string): string {
    const stat = [...document.querySelectorAll(".statistics-modal .rail-stat")]
        .find((element) => element.querySelector("span")?.textContent === label);
    return stat?.querySelector("strong")?.textContent ?? "";
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
        expect(screen.getByRole("button", {name: "Last 50"})).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByRole("button", {name: "All solves"})).toHaveAttribute("aria-pressed", "false");

        expect(await screen.findByRole("img", {name: /Solve 1: 10\.00/})).toBeInTheDocument();
        expect(screen.getByRole("img", {name: /Solve 2: 12\.00\+/})).toBeInTheDocument();
        expect(screen.getByRole("img", {name: /Solve 3: DNF/})).toBeInTheDocument();
        expect(api.fetchSolveHistory).toHaveBeenCalledWith(50);
        expect(screen.getByText("Showing 3 solves")).toBeInTheDocument();
        expect(summaryValue("Solves")).toBe("3");
        expect(summaryValue("DNFs")).toBe("1");
        expect(summaryValue("Best")).toBe("10.00");
        expect(summaryValue("Average")).toBe("11.00");

        fireEvent.focus(screen.getByRole("img", {name: /Solve 2:/}));
        expect(screen.getByRole("status")).toHaveTextContent("Solve 2 · 12.00+");
        expect(document.querySelectorAll(".chart-time-line")).toHaveLength(1);
        expect(document.querySelector(".chart-point.dnf path")).toBeInTheDocument();
    });

    it("loads all cursor pages, deduplicates entries, updates the selected-range summary, and reuses cached ranges", async () => {
        const newest = Array.from({length: 55}, (_, index) => makeEntry(55 - index, {
            officialMs: 20_000 + (55 - index) * 100,
            createdAt: new Date(Date.UTC(2026, 0, 1) + (55 - index) * 1000).toISOString(),
        }));
        api.fetchSolveHistory
            .mockResolvedValueOnce({items: newest.slice(0, 50), nextCursor: null})
            .mockResolvedValueOnce({items: newest.slice(0, 50), nextCursor: "cursor-50"})
            .mockResolvedValueOnce({items: [newest[49], ...newest.slice(50)], nextCursor: null});

        renderModal();
        expect(await screen.findByRole("img", {name: /Solve 1:/})).toBeInTheDocument();
        expect(summaryValue("Solves")).toBe("50");

        fireEvent.click(screen.getByRole("button", {name: "All solves"}));
        expect(await screen.findByText("Showing 55 solves")).toBeInTheDocument();
        expect(summaryValue("Solves")).toBe("55");
        expect(api.fetchSolveHistory).toHaveBeenNthCalledWith(2, 100, null);
        expect(api.fetchSolveHistory).toHaveBeenNthCalledWith(3, 100, "cursor-50");
        expect(screen.getAllByRole("img", {name: /^Solve \d+:/})).toHaveLength(55);

        fireEvent.click(screen.getByRole("button", {name: "Last 50"}));
        expect(screen.getByText("Showing 50 solves")).toBeInTheDocument();
        expect(api.fetchSolveHistory).toHaveBeenCalledTimes(3);
    });

    it("shows loading and retryable errors while All solves pages are being fetched", async () => {
        let resolveFirstPage: (page: {items: SolveHistoryEntry[]; nextCursor: string | null}) => void = () => {};
        api.fetchSolveHistory
            .mockResolvedValueOnce({items: [makeEntry(2)], nextCursor: null})
            .mockImplementationOnce(() => new Promise((resolve) => { resolveFirstPage = resolve; }))
            .mockRejectedValueOnce(new Error("Second page unavailable"))
            .mockResolvedValueOnce({items: [makeEntry(2)], nextCursor: "next"})
            .mockResolvedValueOnce({items: [makeEntry(1)], nextCursor: null});
        renderModal();
        await screen.findByRole("img", {name: /Solve 3:/});

        fireEvent.click(screen.getByRole("button", {name: "All solves"}));
        expect(screen.getByText("Loading all solves…")).toBeInTheDocument();
        resolveFirstPage({items: [makeEntry(2)], nextCursor: "cursor"});
        expect(await screen.findByRole("alert")).toHaveTextContent("Second page unavailable");
        fireEvent.click(screen.getByRole("button", {name: "Retry"}));
        expect(await screen.findByText("Showing 2 solves")).toBeInTheDocument();
        expect(api.fetchSolveHistory).toHaveBeenCalledTimes(5);
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
