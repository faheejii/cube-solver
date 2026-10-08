import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {fetchSolveHistory} from "./api";
import {parseHistorySearch} from "./historySearch";
import type {SolveHistoryEntry, SolveHistoryResponse} from "./types";

const PAGE_SIZE = 20;

type FilteredPage = SolveHistoryResponse & {totalCount: number};

type FilteredHistoryState = {
    filterKey: string | null;
    entries: SolveHistoryEntry[];
    cursor: string | null;
    totalCount: number | null;
    loading: boolean;
    loadingMore: boolean;
    error: string | null;
    retryCursor: string | null;
};

const EMPTY_STATE: FilteredHistoryState = {
    filterKey: null,
    entries: [],
    cursor: null,
    totalCount: null,
    loading: false,
    loadingMore: false,
    error: null,
    retryCursor: null,
};

function getFilteredPage(page: SolveHistoryResponse | FilteredPage): FilteredPage {
    if (typeof page.totalCount !== "number" || !Number.isInteger(page.totalCount) || page.totalCount < 0) {
        throw new Error("Filtered history response is missing a valid totalCount");
    }
    return page as FilteredPage;
}

function uniqueEntries(entries: SolveHistoryEntry[]): SolveHistoryEntry[] {
    const seen = new Set<number>();
    return entries.filter((entry) => {
        if (seen.has(entry.id)) return false;
        seen.add(entry.id);
        return true;
    });
}

function sharedEntriesRevision(entries: SolveHistoryEntry[]): string {
    return entries.map((entry) => `${entry.id}:${entry.penalty}:${entry.officialMs}:${entry.dnf}`).join("|");
}

export function useFilteredSolveHistory(
    query: string,
    sharedEntries: SolveHistoryEntry[],
) {
    const filterKey = query;
    const parsedQuery = useMemo(() => parseHistorySearch(query), [query]);
    const active = parsedQuery.kind !== "empty";
    const requestFilter = useMemo(() => {
        if (parsedQuery.kind === "time") return {time: parsedQuery.value};
        if (parsedQuery.kind === "scramble") return {q: parsedQuery.value};
        return null;
    }, [parsedQuery]);
    const sharedRevision = sharedEntriesRevision(sharedEntries);
    const [state, setState] = useState<FilteredHistoryState>(EMPTY_STATE);
    const requestIdRef = useRef(0);
    const loadingMoreRef = useRef(false);
    const lastFilterKeyRef = useRef<string | null>(null);

    const loadFirstPage = useCallback(async (preserveEntries: boolean) => {
        const requestId = ++requestIdRef.current;
        loadingMoreRef.current = false;
        setState((current) => ({
            filterKey,
            entries: preserveEntries && current.filterKey === filterKey ? current.entries : [],
            cursor: preserveEntries && current.filterKey === filterKey ? current.cursor : null,
            totalCount: preserveEntries && current.filterKey === filterKey ? current.totalCount : null,
            loading: true,
            loadingMore: false,
            error: null,
            retryCursor: null,
        }));

        if (!requestFilter) {
            setState({
                filterKey,
                entries: [],
                cursor: null,
                totalCount: 0,
                loading: false,
                loadingMore: false,
                error: null,
                retryCursor: null,
            });
            return;
        }

        try {
            const response = await fetchSolveHistory(PAGE_SIZE, null, requestFilter);
            const page = getFilteredPage(response);
            if (requestId !== requestIdRef.current) return;
            setState({
                filterKey,
                entries: uniqueEntries(page.items),
                cursor: page.nextCursor,
                totalCount: page.totalCount,
                loading: false,
                loadingMore: false,
                error: null,
                retryCursor: null,
            });
        } catch (loadError) {
            if (requestId !== requestIdRef.current) return;
            setState((current) => ({
                ...current,
                filterKey,
                loading: false,
                loadingMore: false,
                error: loadError instanceof Error ? loadError.message : "History request failed",
                retryCursor: null,
            }));
        }
    }, [filterKey, requestFilter]);

    useEffect(() => {
        if (!active) {
            lastFilterKeyRef.current = null;
            requestIdRef.current += 1;
            loadingMoreRef.current = false;
            setState(EMPTY_STATE);
            return;
        }

        const preserveEntries = lastFilterKeyRef.current === filterKey;
        lastFilterKeyRef.current = filterKey;
        void loadFirstPage(preserveEntries);
        return () => {
            requestIdRef.current += 1;
            loadingMoreRef.current = false;
        };
    }, [active, filterKey, loadFirstPage, sharedRevision]);

    const loadMore = useCallback(async () => {
        const cursor = state.cursor;
        if (!active || !cursor || state.loading || state.loadingMore || loadingMoreRef.current) return;
        if (!requestFilter) return;

        const requestId = requestIdRef.current;
        loadingMoreRef.current = true;
        setState((current) => ({...current, loadingMore: true, error: null, retryCursor: null}));
        try {
            const response = await fetchSolveHistory(PAGE_SIZE, cursor, requestFilter);
            const page = getFilteredPage(response);
            if (requestId !== requestIdRef.current) return;
            setState((current) => ({
                ...current,
                entries: uniqueEntries([...current.entries, ...page.items]),
                cursor: page.nextCursor,
                totalCount: page.totalCount,
                loadingMore: false,
                error: null,
                retryCursor: null,
            }));
        } catch (loadError) {
            if (requestId !== requestIdRef.current) return;
            setState((current) => ({
                ...current,
                loadingMore: false,
                error: loadError instanceof Error ? loadError.message : "History request failed",
                retryCursor: cursor,
            }));
        } finally {
            if (requestId === requestIdRef.current) loadingMoreRef.current = false;
        }
    }, [active, requestFilter, state.cursor, state.loading, state.loadingMore]);

    const retry = useCallback(() => {
        if (state.retryCursor) {
            void loadMore();
        } else {
            void loadFirstPage(true);
        }
    }, [loadFirstPage, loadMore, state.retryCursor]);

    const refresh = useCallback(() => {
        if (active) void loadFirstPage(true);
    }, [active, loadFirstPage]);

    const stateMatchesFilter = state.filterKey === filterKey;
    return {
        active,
        entries: stateMatchesFilter ? state.entries : [],
        cursor: stateMatchesFilter ? state.cursor : null,
        totalCount: stateMatchesFilter ? state.totalCount : null,
        loading: active && (!stateMatchesFilter || state.loading),
        loadingMore: stateMatchesFilter && state.loadingMore,
        error: stateMatchesFilter ? state.error : null,
        refresh,
        retry,
        loadMore,
    };
}
