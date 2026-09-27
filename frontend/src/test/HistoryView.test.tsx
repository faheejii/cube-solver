import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import HistoryView from "../HistoryView";
import type {SolveHistoryEntry, SolveStatistics} from "../types";

vi.mock("../StatisticsModal", () => ({
    default: ({onClose, onOpenSolve}: {onClose: () => void; onOpenSolve: (entry: SolveHistoryEntry) => void}) => (
        <section role="dialog" aria-label="Statistics dialog">
            <button type="button" onClick={onClose}>Close statistics dialog</button>
            <button type="button" onClick={() => onOpenSolve(entries[0])}>Open solve from statistics</button>
        </section>
    ),
}));

vi.mock("../DeferredCubePreview", () => ({
    default: ({setupAlgorithm, compact, displayMode, staticPreview, unloadWhenOutOfView}: {
        setupAlgorithm: string;
        compact: boolean;
        displayMode: string;
        staticPreview: boolean;
        unloadWhenOutOfView: boolean;
    }) => (
        <div
            data-testid="history-cube-preview"
            data-preview-setup={setupAlgorithm}
            data-compact={String(compact)}
            data-display-mode={displayMode}
            data-static-preview={String(staticPreview)}
            data-unload-when-out-of-view={String(unloadWhenOutOfView)}
        />
    ),
}));

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
    const onOpenSolve = vi.fn();
    const onDeleteSolve = vi.fn();
    const view = render(
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
            onOpenSolve={onOpenSolve}
            onDeleteSolve={onDeleteSolve}
        />
    );
    return {...view, onOpenSolve, onDeleteSolve};
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

    it("opens the solution by activating the solve row and keeps delete separate", () => {
        const {onOpenSolve, onDeleteSolve} = renderHistory(8);

        expect(screen.queryByText(/Cross|Fast saved|Fast missing|Optimized saved|Optimized missing/)).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Solution"})).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Open solution for solve 15.00"}));
        expect(onOpenSolve).toHaveBeenCalledWith(entries[0]);

        fireEvent.click(screen.getByRole("button", {name: "Delete solve 15.00"}));
        expect(onDeleteSolve).toHaveBeenCalledWith(entries[0]);
        expect(onOpenSolve).toHaveBeenCalledTimes(1);
    });

    it("opens the solution from the keyboard-accessible row control", () => {
        const {onOpenSolve} = renderHistory(8);
        const row = screen.getByRole("button", {name: "Open solution for solve 15.00"});

        fireEvent.keyDown(row, {key: "Enter"});
        fireEvent.keyDown(row, {key: " "});

        expect(onOpenSolve).toHaveBeenNthCalledWith(1, entries[0]);
        expect(onOpenSolve).toHaveBeenNthCalledWith(2, entries[0]);
    });

    it("renders a compact static 3D cube in each solve's scramble state", () => {
        renderHistory(8);

        const preview = screen.getAllByTestId("history-cube-preview")[0];
        expect(preview).toHaveAttribute("data-preview-setup", entries[0].scramble);
        expect(preview).toHaveAttribute("data-compact", "true");
        expect(preview).toHaveAttribute("data-display-mode", "3d");
        expect(preview).toHaveAttribute("data-static-preview", "true");
        expect(preview).toHaveAttribute("data-unload-when-out-of-view", "true");
        expect(preview.parentElement).toHaveAttribute("aria-hidden", "true");
    });
});

describe("HistoryView statistics", () => {
    it("shows the solve statistics summary above history", () => {
        renderHistory(8);

        expect(screen.getByRole("region", {name: "Solve statistics"})).toBeInTheDocument();
        expect(screen.getByText("Best")).toBeInTheDocument();
        expect(screen.getByText("Ao5")).toBeInTheDocument();
        expect(screen.getByText("Ao12")).toBeInTheDocument();
        expect(screen.getByText("Mean")).toBeInTheDocument();
        expect(screen.getByText("Solves")).toBeInTheDocument();
        expect(screen.getByText("DNFs")).toBeInTheDocument();
        expect(screen.getByText("12.00")).toBeInTheDocument();
        expect(screen.getByText("1")).toBeInTheDocument();
    });

    it("opens the statistics modal from the History summary", async () => {
        renderHistory(8);

        fireEvent.click(screen.getByRole("button", {name: "More statistics"}));

        expect(await screen.findByRole("dialog", {name: "Statistics dialog"})).toBeInTheDocument();
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
