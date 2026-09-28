import {act, renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import type {SolveHistoryEntry} from "../types";

const api = vi.hoisted(() => ({
    deleteSolve: vi.fn(),
    fetchSolveHistory: vi.fn(),
    fetchSolveStatistics: vi.fn(),
}));

vi.mock("../api", () => api);

import {useHistoryData} from "../hooks/useHistoryData";

function makeEntry(id: number): SolveHistoryEntry {
    return {
        id,
        clientAttemptId: `attempt-${id}`,
        scramble: "R U",
        crossFaceRequested: "U",
        timerMs: 12000,
        officialMs: 12000,
        penalty: "none",
        dnf: false,
        fastCrossFaceRequested: "U",
        optimizedCrossFaceRequested: null,
        createdAt: "2026-09-25T16:00:00Z",
    };
}

describe("useHistoryData pagination", () => {
    beforeEach(() => {
        api.deleteSolve.mockReset();
        api.fetchSolveHistory.mockReset();
        api.fetchSolveStatistics.mockReset();
        api.fetchSolveStatistics.mockResolvedValue({
            solveCount: 21,
            dnfCount: 0,
            bestMs: null,
            averageMs: null,
            ao5: {status: "insufficient", valueMs: null},
            ao12: {status: "insufficient", valueMs: null},
            recentSolves: [],
        });
    });

    it("preloads 20 entries and appends cursor pages without duplicates", async () => {
        const firstPage = Array.from({length: 20}, (_, index) => makeEntry(21 - index));
        api.fetchSolveHistory
            .mockResolvedValueOnce({items: firstPage, nextCursor: "cursor-2"})
            .mockResolvedValueOnce({items: [makeEntry(1), makeEntry(2)], nextCursor: null});

        const {result} = renderHook(() => useHistoryData({onNotice: vi.fn()}));

        await waitFor(() => expect(result.current.historyEntries).toHaveLength(20));
        expect(api.fetchSolveHistory).toHaveBeenNthCalledWith(1, 20);
        expect(result.current.historyCursor).toBe("cursor-2");

        await act(async () => result.current.loadMoreHistory());

        expect(api.fetchSolveHistory).toHaveBeenNthCalledWith(2, 20, "cursor-2");
        expect(result.current.historyEntries.map((item) => item.id)).toEqual([
            ...firstPage.map((item) => item.id),
            1,
        ]);
        expect(result.current.historyCursor).toBeNull();
    });
});
