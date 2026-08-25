import {render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import HistoryView from "../HistoryView";
import type {SolveHistoryEntry, SolveStatistics} from "../types";

const entries: SolveHistoryEntry[] = [
    {
        id: 8,
        clientAttemptId: "attempt-8",
        scramble: "R U",
        crossFaceRequested: "U",
        timerMs: 15000,
        officialMs: 15000,
        penalty: "none",
        dnf: false,
        fastCrossFaceRequested: "U",
        optimizedCrossFaceRequested: null,
        createdAt: "2026-08-24T16:00:00Z",
    },
    {
        id: 7,
        clientAttemptId: "attempt-7",
        scramble: "F R",
        crossFaceRequested: "U",
        timerMs: 16000,
        officialMs: 16000,
        penalty: "none",
        dnf: false,
        fastCrossFaceRequested: "U",
        optimizedCrossFaceRequested: null,
        createdAt: "2026-08-24T15:59:00Z",
    },
];

const statistics: SolveStatistics = {
    solveCount: 8,
    dnfCount: 1,
    bestMs: 12000,
    averageMs: 15000,
    ao5: {status: "value", valueMs: 14000},
    ao12: {status: "value", valueMs: 15500},
    recentSolves: entries,
};

function renderHistory(solveCount: number | null) {
    return render(
        <HistoryView
            entries={entries}
            loading={false}
            loadingMore={false}
            error={null}
            hasMore={true}
            solveCount={solveCount}
            statistics={statistics}
            statisticsLoading={false}
            deletingSolveId={null}
            onRefresh={vi.fn()}
            onLoadMore={vi.fn()}
            onOpenSolve={vi.fn()}
            onDeleteSolve={vi.fn()}
        />
    );
}

describe("HistoryView numbering", () => {
    it("numbers newest entries from the global solve count", () => {
        renderHistory(8);

        expect(screen.getByText("08")).toBeInTheDocument();
        expect(screen.getByText("07")).toBeInTheDocument();
    });

    it("continues with local numbering when the total count is unavailable", () => {
        renderHistory(null);

        expect(screen.getByText("01")).toBeInTheDocument();
        expect(screen.getByText("02")).toBeInTheDocument();
    });
});

describe("HistoryView statistics", () => {
    it("shows the solve statistics summary above history", () => {
        renderHistory(8);

        expect(screen.getByRole("region", {name: "Solve statistics"})).toBeInTheDocument();
        expect(screen.getByText("Best")).toBeInTheDocument();
        expect(screen.getByText("Ao5")).toBeInTheDocument();
        expect(screen.getByText("Ao12")).toBeInTheDocument();
        expect(screen.getByText("Average")).toBeInTheDocument();
        expect(screen.getByText("Solves")).toBeInTheDocument();
        expect(screen.getByText("DNFs")).toBeInTheDocument();
        expect(screen.getByText("12.00")).toBeInTheDocument();
        expect(screen.getByText("1")).toBeInTheDocument();
    });

    it("shows loading placeholders and the empty state", () => {
        render(
            <HistoryView
                entries={[]}
                loading={false}
                loadingMore={false}
                error={null}
                hasMore={false}
                solveCount={null}
                statistics={null}
                statisticsLoading={true}
                deletingSolveId={null}
                onRefresh={vi.fn()}
                onLoadMore={vi.fn()}
                onOpenSolve={vi.fn()}
                onDeleteSolve={vi.fn()}
            />
        );

        expect(screen.getAllByText("…")).toHaveLength(2);
        expect(screen.getByText("No solves yet")).toBeInTheDocument();
    });
});
