import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import type {ComponentProps} from "react";
import StatisticsRail from "../StatisticsRail";
import type {SolveHistoryEntry, SolveStatistics} from "../types";

vi.mock("../StatisticsModal", () => ({
    default: ({onClose, onOpenSolve}: {onClose: () => void; onOpenSolve: (entry: SolveHistoryEntry) => void}) => (
        <section role="dialog" aria-label="Statistics dialog">
            <button type="button" onClick={onClose}>Close statistics dialog</button>
            <button type="button" onClick={() => onOpenSolve(entry)}>Open solve from statistics</button>
        </section>
    ),
}));

const entry: SolveHistoryEntry = {
    id: 12,
    clientAttemptId: "attempt-12",
    scramble: "R U",
    crossFaceRequested: "U",
    timerMs: 12300,
    officialMs: 12300,
    penalty: "none",
    dnf: false,
    fastCrossFaceRequested: "U",
    optimizedCrossFaceRequested: null,
    createdAt: "2026-09-25T16:00:00Z",
};

const statistics: SolveStatistics = {
    solveCount: 12,
    dnfCount: 0,
    bestMs: 12300,
    averageMs: 12300,
    ao5: {status: "insufficient", valueMs: null},
    ao12: {status: "insufficient", valueMs: null},
    recentSolves: [],
};

function renderRail(overrides: Partial<ComponentProps<typeof StatisticsRail>> = {}) {
    const props = {
        statistics,
        loading: false,
        entries: [entry],
        historyStatus: "ready" as const,
        historyError: null,
        loadingMore: false,
        hasMore: true,
        solveCount: 12,
        onLoadMore: vi.fn(),
        onRetry: vi.fn(),
        onOpenHistory: vi.fn(),
        onOpenSolve: vi.fn(),
        ...overrides,
    };
    const view = render(<StatisticsRail {...props}/>);
    return {...view, props};
}

describe("StatisticsRail solves list", () => {
    it("opens statistics from its More button without changing the Solves More action", async () => {
        const {props} = renderRail();
        fireEvent.click(screen.getByRole("button", {name: "More statistics"}));
        expect(await screen.findByRole("dialog", {name: "Statistics dialog"})).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Open solve from statistics"}));
        expect(props.onOpenSolve).toHaveBeenCalledWith(entry);

        fireEvent.click(document.querySelector(".recent-card .rail-card-header button")!);
        expect(props.onOpenHistory).toHaveBeenCalledOnce();
    });

    it("shows shared history entries and opens the selected solve", () => {
        const {props} = renderRail();

        expect(screen.getAllByText("Solves")).toHaveLength(2);
        expect(screen.queryByText("Recent solves")).not.toBeInTheDocument();
        expect(screen.getByText("#12")).toBeInTheDocument();
        expect(screen.getByRole("region", {name: "Solves list"})).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: /12\.30/}));
        expect(props.onOpenSolve).toHaveBeenCalledWith(entry);
    });

    it("requests another page when the list scrolls near its end and keeps a button fallback", () => {
        const {props} = renderRail();
        const list = screen.getByRole("region", {name: "Solves list"});
        Object.defineProperties(list, {
            scrollHeight: {configurable: true, value: 500},
            clientHeight: {configurable: true, value: 200},
            scrollTop: {configurable: true, value: 270},
        });

        fireEvent.scroll(list);
        expect(props.onLoadMore).toHaveBeenCalledTimes(1);
        fireEvent.click(screen.getByRole("button", {name: "Load more"}));
        expect(props.onLoadMore).toHaveBeenCalledTimes(2);
    });

    it("shows loading, empty, and retryable error states", () => {
        const {rerender} = renderRail({entries: [], historyStatus: "loading"});
        expect(screen.getByText("Loading solves…")).toBeInTheDocument();

        rerender(<StatisticsRail
            statistics={statistics}
            loading={false}
            entries={[]}
            historyStatus="ready"
            historyError={null}
            loadingMore={false}
            hasMore={false}
            solveCount={0}
            onLoadMore={vi.fn()}
            onRetry={vi.fn()}
            onOpenHistory={vi.fn()}
            onOpenSolve={vi.fn()}
        />);
        expect(screen.getByText("No solves yet")).toBeInTheDocument();

        const onRetry = vi.fn();
        rerender(<StatisticsRail
            statistics={statistics}
            loading={false}
            entries={[]}
            historyStatus="error"
            historyError="History unavailable"
            loadingMore={false}
            hasMore={false}
            solveCount={null}
            onLoadMore={vi.fn()}
            onRetry={onRetry}
            onOpenHistory={vi.fn()}
            onOpenSolve={vi.fn()}
        />);
        fireEvent.click(screen.getByRole("button", {name: "Retry"}));
        expect(screen.getByText("History unavailable")).toBeInTheDocument();
        expect(onRetry).toHaveBeenCalledOnce();
    });
});
