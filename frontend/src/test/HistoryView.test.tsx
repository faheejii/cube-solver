import {act, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {fetchSolveHistory} from "../api";
import HistoryView from "../HistoryView";
import type {SolveHistoryEntry, SolveStatistics} from "../types";

vi.mock("../api", () => ({fetchSolveHistory: vi.fn()}));

vi.mock("../StatisticsModal", () => ({
    default: ({onClose, onOpenSolve}: {onClose: () => void; onOpenSolve: (entry: SolveHistoryEntry) => void}) => (
        <section role="dialog" aria-label="Statistics dialog">
            <button type="button" onClick={onClose}>Close statistics dialog</button>
            <button type="button" onClick={() => onOpenSolve(entries[0])}>Open solve from statistics</button>
        </section>
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

const fetchHistoryMock = vi.mocked(fetchSolveHistory);

beforeEach(() => {
    window.history.replaceState({}, "", "/");
    vi.useRealTimers();
    vi.clearAllMocks();
    fetchHistoryMock.mockResolvedValue({items: [], nextCursor: null, totalCount: 0});
});

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
        expect(onOpenSolve).toHaveBeenCalledWith(entries[0], 8, false);

        fireEvent.click(screen.getByRole("button", {name: "Delete solve 15.00"}));
        expect(onDeleteSolve).toHaveBeenCalledWith(entries[0]);
        expect(onOpenSolve).toHaveBeenCalledTimes(1);
    });

    it("opens the solution from the keyboard-accessible row control", () => {
        const {onOpenSolve} = renderHistory(8);
        const row = screen.getByRole("button", {name: "Open solution for solve 15.00"});

        expect(row.tagName).toBe("BUTTON");
        expect(row).toHaveAttribute("type", "button");
        fireEvent.click(row);
        expect(onOpenSolve).toHaveBeenCalledWith(entries[0], 8, false);
    });

    it("renders a decorative SVG cube in each solve's scramble state", () => {
        renderHistory(8);

        const preview = screen.getAllByTestId("history-cube-thumbnail")[0];
        expect(preview).toHaveAttribute("data-preview-setup", entries[0].scramble);
        expect(preview.tagName.toLowerCase()).toBe("svg");
        expect(preview.querySelectorAll("polygon")).toHaveLength(27);
        expect(preview.parentElement).toHaveAttribute("aria-hidden", "true");
    });
});

describe("HistoryView statistics", () => {
    it("shows the solve statistics summary above history", () => {
        renderHistory(8);

        expect(screen.getByRole("region", {name: "All-history solve statistics"})).toBeInTheDocument();
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

describe("HistoryView filters", () => {
    it("keeps the default unfiltered list on shared entries and parent pagination", () => {
        const onLoadMore = vi.fn();
        render(
            <HistoryView
                entries={entries}
                loading={false}
                loadingMore={false}
                error={null}
                hasMore={true}
                solveCount={8}
                statistics={statistics}
                statisticsLoading={false}
                deletingSolveId={null}
                onRefresh={vi.fn()}
                onLoadMore={onLoadMore}
                onOpenSolve={vi.fn()}
                onDeleteSolve={vi.fn()}
            />
        );

        expect(screen.getByText("R U")).toBeInTheDocument();
        expect(screen.getByText("F R")).toBeInTheDocument();
        expect(fetchHistoryMock).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole("button", {name: "Load more"}));
        expect(onLoadMore).toHaveBeenCalledOnce();
    });

    it("debounces scramble search and requests the active query with server pagination", async () => {
        vi.useFakeTimers();
        fetchHistoryMock.mockResolvedValue({items: [entries[1]], nextCursor: "next", totalCount: 4});
        renderHistory(8);
        const search = screen.getByRole("searchbox", {name: "Search solves"});
        fireEvent.change(search, {target: {value: "F R"}});

        await act(async () => {
            vi.advanceTimersByTime(299);
        });
        expect(fetchHistoryMock).not.toHaveBeenCalled();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(1);
        });

        expect(fetchHistoryMock).toHaveBeenCalledWith(20, null, {q: "F R"});
        expect(screen.getByText("Showing 1 of 4 matching solves")).toBeInTheDocument();
        expect(screen.getByText("F R")).toBeInTheDocument();
        expect(screen.queryByText("R U")).not.toBeInTheDocument();
    });

    it("routes valid time expressions to the time filter and preserves the raw query in the URL", async () => {
        vi.useFakeTimers();
        fetchHistoryMock.mockResolvedValue({items: [entries[0]], nextCursor: null, totalCount: 1});
        renderHistory(8);
        const search = screen.getByRole("searchbox", {name: "Search solves"});
        fireEvent.change(search, {target: {value: "*.21"}});

        await act(async () => {
            await vi.advanceTimersByTimeAsync(300);
        });

        expect(fetchHistoryMock).toHaveBeenCalledWith(20, null, {time: "*.21"});
        expect(window.location.search).toBe("?q=*.21");
        expect(screen.queryByText(/format not recognized/i)).not.toBeInTheDocument();
    });

    it("shows format guidance and avoids a request for malformed time-shaped input", async () => {
        vi.useFakeTimers();
        renderHistory(8);
        fireEvent.change(screen.getByRole("searchbox", {name: "Search solves"}), {target: {value: "*.2"}});

        await act(async () => {
            await vi.advanceTimersByTimeAsync(300);
        });

        expect(fetchHistoryMock).not.toHaveBeenCalled();
        expect(screen.getByText("Use S.CC or M:SS.CC for a time, * as a wildcard, or search scramble moves.")).toBeInTheDocument();
        expect(window.location.search).toBe("?q=*.2");
    });

    it("exposes searchable time examples through keyboard-accessible help", () => {
        renderHistory(8);
        const help = screen.getByRole("button", {name: "Search syntax help"});
        expect(help).toHaveAttribute("aria-expanded", "false");

        fireEvent.focus(help);
        expect(screen.getByRole("tooltip")).toBeVisible();
        expect(screen.getByRole("tooltip")).toHaveTextContent("*:21.*");

        fireEvent.keyDown(help, {key: "Escape"});
        expect(help).toHaveAttribute("aria-expanded", "false");
        expect(help).not.toHaveFocus();
    });

    it("toggles search help on touch activation", () => {
        renderHistory(8);
        const help = screen.getByRole("button", {name: "Search syntax help"});

        fireEvent.pointerDown(help, {pointerType: "touch"});
        fireEvent.click(help);
        expect(screen.getByRole("tooltip")).toBeVisible();

        fireEvent.pointerDown(help, {pointerType: "touch"});
        fireEvent.click(help);
        expect(screen.getByRole("tooltip", {hidden: true})).not.toBeVisible();
    });

    it("loads search results, appends without duplicates, and uses the local cursor", async () => {
        vi.useFakeTimers();
        fetchHistoryMock
            .mockResolvedValueOnce({items: [entries[0]], nextCursor: "cursor-20", totalCount: 21})
            .mockResolvedValueOnce({items: [entries[0], entries[1]], nextCursor: null, totalCount: 21});
        renderHistory(8);

        fireEvent.change(screen.getByRole("searchbox", {name: "Search solves"}), {target: {value: "R"}});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(300);
        });
        vi.useRealTimers();
        expect(await screen.findByText("Showing 1 of 21 matching solves")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Load more"}));

        await waitFor(() => expect(fetchHistoryMock).toHaveBeenNthCalledWith(
            2,
            20,
            "cursor-20",
            {q: "R"},
        ));
        await waitFor(() => expect(screen.getByText("Showing 2 of 21 matching solves")).toBeInTheDocument());
        expect(screen.getAllByRole("button", {name: /Open solution for solve/})).toHaveLength(2);
        expect(screen.queryByRole("button", {name: "Load more"})).not.toBeInTheDocument();
    });

    it("resets the filtered list and cursor when the committed query changes", async () => {
        vi.useFakeTimers();
        fetchHistoryMock
            .mockResolvedValueOnce({items: [entries[0]], nextCursor: "old-cursor", totalCount: 1})
            .mockResolvedValueOnce({items: [entries[1]], nextCursor: null, totalCount: 1});
        renderHistory(8);
        const search = screen.getByRole("searchbox", {name: "Search solves"});
        fireEvent.change(search, {target: {value: "R"}});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(300);
        });
        vi.useRealTimers();
        expect(screen.getByText("Showing 1 of 1 matching solves")).toBeInTheDocument();
        expect(screen.getByText("R U")).toBeInTheDocument();

        vi.useFakeTimers();
        fireEvent.change(search, {target: {value: "F"}});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(300);
        });
        vi.useRealTimers();
        await waitFor(() => expect(fetchHistoryMock).toHaveBeenNthCalledWith(2, 20, null, {q: "F"}));
        expect(await screen.findByText("F R")).toBeInTheDocument();
        expect(screen.queryByText("R U")).not.toBeInTheDocument();
    });

    it("ignores IME composition until composition ends, clears immediately, and returns focus", async () => {
        vi.useFakeTimers();
        renderHistory(8);
        const search = screen.getByRole("searchbox", {name: "Search solves"});
        fireEvent.compositionStart(search);
        fireEvent.change(search, {target: {value: "R U"}});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(500);
        });
        expect(fetchHistoryMock).not.toHaveBeenCalled();

        fireEvent.compositionEnd(search, {data: "R U"});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(300);
        });
        vi.useRealTimers();
        await waitFor(() => expect(fetchHistoryMock).toHaveBeenCalledWith(20, null, {q: "R U"}));

        const clear = screen.getAllByRole("button", {name: "Clear search"})[0];
        fireEvent.click(clear);
        expect(search).toHaveValue("");
        expect(search).toHaveFocus();
        expect(window.location.search).toBe("");
        expect(screen.getByText("R U")).toBeInTheDocument();
    });

    it("restores search from the URL and removes the retired penalty filter", async () => {
        window.history.replaceState({}, "", "/?q=R+U&penalty=%2B2");
        renderHistory(8);

        expect(screen.getByRole("searchbox", {name: "Search solves"})).toHaveValue("R U");
        expect(screen.queryByRole("combobox", {name: "Filter solves by penalty"})).not.toBeInTheDocument();
        await waitFor(() => expect(fetchHistoryMock).toHaveBeenCalledWith(20, null, {q: "R U"}));
        expect(await screen.findByText("Showing 0 of 0 matching solves")).toBeInTheDocument();
        expect(screen.getByText("No matching solves")).toBeInTheDocument();
        expect(screen.getAllByRole("button", {name: "Clear search"})).toHaveLength(2);
        await waitFor(() => expect(window.location.search).toBe("?q=R+U"));
    });

    it("shows a retryable search error and recovers on retry", async () => {
        vi.useFakeTimers();
        fetchHistoryMock
            .mockRejectedValueOnce(new Error("History request failed"))
            .mockResolvedValueOnce({items: [entries[0]], nextCursor: null, totalCount: 1});
        renderHistory(8);
        fireEvent.change(screen.getByRole("searchbox", {name: "Search solves"}), {target: {value: "R"}});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(300);
        });
        vi.useRealTimers();

        expect(await screen.findByRole("alert")).toHaveTextContent("History request failed");
        fireEvent.click(screen.getByRole("button", {name: "Retry"}));

        expect(await screen.findByText("Showing 1 of 1 matching solves")).toBeInTheDocument();
        expect(screen.getByText("R U")).toBeInTheDocument();
    });

    it("refreshes search results when a shared solve is removed", async () => {
        window.history.replaceState({}, "", "/?q=R");
        fetchHistoryMock
            .mockResolvedValueOnce({items: [entries[0]], nextCursor: null, totalCount: 1})
            .mockResolvedValueOnce({items: [], nextCursor: null, totalCount: 0});

        const makeView = (sharedEntries: SolveHistoryEntry[]) => (
            <HistoryView
                entries={sharedEntries}
                loading={false}
                loadingMore={false}
                error={null}
                hasMore={false}
                solveCount={8}
                statistics={statistics}
                statisticsLoading={false}
                deletingSolveId={null}
                onRefresh={vi.fn()}
                onLoadMore={vi.fn()}
                onOpenSolve={vi.fn()}
                onDeleteSolve={vi.fn()}
            />
        );
        const view = render(makeView(entries));
        expect(await screen.findByText("Showing 1 of 1 matching solves")).toBeInTheDocument();
        expect(screen.getByText("R U")).toBeInTheDocument();

        view.rerender(makeView(entries.slice(1)));

        await waitFor(() => expect(fetchHistoryMock).toHaveBeenNthCalledWith(2, 20, null, {q: "R"}));
        expect(await screen.findByText("No matching solves")).toBeInTheDocument();
    });

    it("does not let an older filter response replace newer results", async () => {
        vi.useFakeTimers();
        let resolveOld: ((page: {items: SolveHistoryEntry[]; nextCursor: null; totalCount: number}) => void) | undefined;
        fetchHistoryMock
            .mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve; }))
            .mockResolvedValueOnce({items: [entries[1]], nextCursor: null, totalCount: 1});
        renderHistory(8);
        const search = screen.getByRole("searchbox", {name: "Search solves"});
        fireEvent.change(search, {target: {value: "old"}});
        await act(async () => {
            await vi.advanceTimersByTimeAsync(300);
        });
        vi.useRealTimers();
        fireEvent.change(search, {target: {value: "new"}});
        vi.useFakeTimers();
        await act(async () => {
            await vi.advanceTimersByTimeAsync(300);
        });
        vi.useRealTimers();
        expect(await screen.findByText("F R")).toBeInTheDocument();
        await act(async () => {
            resolveOld?.({items: [entries[0]], nextCursor: null, totalCount: 1});
            await Promise.resolve();
        });
        expect(screen.getByText("F R")).toBeInTheDocument();
        expect(screen.queryByText("R U")).not.toBeInTheDocument();
    });
});
