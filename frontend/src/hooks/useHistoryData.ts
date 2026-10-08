import {useCallback, useEffect, useRef, useState} from "react";
import {deleteSolve, fetchSolveHistory, fetchSolveStatistics} from "../api";
import type {SolveHistoryEntry, SolveStatistics} from "../types";

type HistoryStatus = "idle" | "loading" | "ready" | "error";

type Options = {
    onNotice: (notice: string) => void;
};

const HISTORY_PAGE_SIZE = 20;

export function useHistoryData({onNotice}: Options) {
    const [historyStatus, setHistoryStatus] = useState<HistoryStatus>("idle");
    const [historyError, setHistoryError] = useState<string | null>(null);
    const [historyEntries, setHistoryEntries] = useState<SolveHistoryEntry[]>([]);
    const [historyCursor, setHistoryCursor] = useState<string | null>(null);
    const [historyLoadingMore, setHistoryLoadingMore] = useState(false);
    const [deletingSolveId, setDeletingSolveId] = useState<number | null>(null);
    const [deleteError, setDeleteError] = useState<string | null>(null);
    const [statistics, setStatistics] = useState<SolveStatistics | null>(null);
    const [statisticsLoading, setStatisticsLoading] = useState(true);
    const historyLoadRequestedRef = useRef(false);
    const historyLoadingMoreRef = useRef(false);

    const loadHistory = useCallback(async () => {
        setHistoryStatus("loading");
        setHistoryError(null);
        try {
            const page = await fetchSolveHistory(HISTORY_PAGE_SIZE);
            setHistoryEntries(page.items);
            setHistoryCursor(page.nextCursor);
            setHistoryStatus("ready");
        } catch (loadError) {
            const message = loadError instanceof Error ? loadError.message : "History request failed";
            setHistoryError(message);
            setHistoryStatus("error");
        }
    }, []);

    const loadMoreHistory = useCallback(async () => {
        if (!historyCursor || historyLoadingMoreRef.current) {
            return;
        }
        historyLoadingMoreRef.current = true;
        setHistoryLoadingMore(true);
        setHistoryError(null);
        try {
            const page = await fetchSolveHistory(HISTORY_PAGE_SIZE, historyCursor);
            setHistoryEntries((current) => [
                ...current,
                ...page.items.filter((entry) => current.every((existing) => existing.id !== entry.id)),
            ]);
            setHistoryCursor(page.nextCursor);
        } catch (loadError) {
            const message = loadError instanceof Error ? loadError.message : "History request failed";
            setHistoryError(message);
        } finally {
            historyLoadingMoreRef.current = false;
            setHistoryLoadingMore(false);
        }
    }, [historyCursor]);

    const loadStatistics = useCallback(async () => {
        setStatisticsLoading(true);
        try {
            setStatistics(await fetchSolveStatistics());
        } catch {
            setStatistics(null);
        } finally {
            setStatisticsLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadStatistics();
    }, [loadStatistics]);

    useEffect(() => {
        if (!historyLoadRequestedRef.current) {
            historyLoadRequestedRef.current = true;
            void loadHistory();
        }
    }, [loadHistory]);

    const handleDeleteSolve = useCallback(async (entry: SolveHistoryEntry): Promise<boolean> => {
        if (deletingSolveId !== null) {
            return false;
        }

        setDeletingSolveId(entry.id);
        setDeleteError(null);
        setHistoryError(null);
        try {
            await deleteSolve(entry.id);
            setHistoryEntries((current) => current.filter((solve) => solve.id !== entry.id));
            onNotice("Solve deleted");
            await Promise.all([loadHistory(), loadStatistics()]);
            return true;
        } catch (deleteError) {
            const message = deleteError instanceof Error ? deleteError.message : "Solve deletion failed";
            setDeleteError(message);
            return false;
        } finally {
            setDeletingSolveId(null);
        }
    }, [deletingSolveId, loadHistory, loadStatistics, onNotice]);

    return {
        historyStatus,
        historyError,
        historyEntries,
        historyCursor,
        historyLoadingMore,
        deletingSolveId,
        deleteError,
        clearDeleteError: () => setDeleteError(null),
        statistics,
        statisticsLoading,
        loadHistory,
        loadMoreHistory,
        loadStatistics,
        handleDeleteSolve,
        setHistoryEntries,
        setHistoryStatus,
        setHistoryError,
    };
}
