import {useCallback, useEffect, useRef, useState} from "react";
import {deleteSolve, fetchSolveHistory, fetchSolveStatistics} from "../api";
import {formatHistoryTime} from "../format";
import type {SolveHistoryEntry, SolveStatistics} from "../types";

type HistoryStatus = "idle" | "loading" | "ready" | "error";

type Options = {
    activeView: string;
    onNotice: (notice: string) => void;
};

export function useHistoryData({activeView, onNotice}: Options) {
    const [historyStatus, setHistoryStatus] = useState<HistoryStatus>("idle");
    const [historyError, setHistoryError] = useState<string | null>(null);
    const [historyEntries, setHistoryEntries] = useState<SolveHistoryEntry[]>([]);
    const [historyCursor, setHistoryCursor] = useState<string | null>(null);
    const [historyLoadingMore, setHistoryLoadingMore] = useState(false);
    const [deletingSolveId, setDeletingSolveId] = useState<number | null>(null);
    const [statistics, setStatistics] = useState<SolveStatistics | null>(null);
    const [statisticsLoading, setStatisticsLoading] = useState(true);
    const historyLoadRequestedRef = useRef(false);

    const loadHistory = useCallback(async () => {
        setHistoryStatus("loading");
        setHistoryError(null);
        try {
            const page = await fetchSolveHistory(25);
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
        if (!historyCursor || historyLoadingMore) {
            return;
        }
        setHistoryLoadingMore(true);
        try {
            const page = await fetchSolveHistory(25, historyCursor);
            setHistoryEntries((current) => [
                ...current,
                ...page.items.filter((entry) => current.every((existing) => existing.id !== entry.id)),
            ]);
            setHistoryCursor(page.nextCursor);
        } catch (loadError) {
            const message = loadError instanceof Error ? loadError.message : "History request failed";
            setHistoryError(message);
        } finally {
            setHistoryLoadingMore(false);
        }
    }, [historyCursor, historyLoadingMore]);

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
        if (activeView === "history" && !historyLoadRequestedRef.current) {
            historyLoadRequestedRef.current = true;
            void loadHistory();
        }
    }, [activeView, loadHistory]);

    const handleDeleteSolve = useCallback(async (entry: SolveHistoryEntry) => {
        if (deletingSolveId !== null) {
            return;
        }
        const displayedTime = formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf);
        if (!window.confirm(
            `Delete the ${displayedTime} solve permanently?\n\nThis also deletes all saved Fast and Optimized solutions.`
        )) {
            return;
        }

        setDeletingSolveId(entry.id);
        setHistoryError(null);
        try {
            await deleteSolve(entry.id);
            setHistoryEntries((current) => current.filter((solve) => solve.id !== entry.id));
            onNotice("Solve deleted");
            await Promise.all([loadHistory(), loadStatistics()]);
        } catch (deleteError) {
            const message = deleteError instanceof Error ? deleteError.message : "Solve deletion failed";
            setHistoryError(message);
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
