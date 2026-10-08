import {lazy, Suspense, useEffect, useRef, useState} from "react";
import {ArrowUpRight, Info, LoaderCircle, RefreshCw, Search, Trash2, X} from "lucide-react";
import HistoryCubeThumbnail from "./HistoryCubeThumbnail";
import {formatHistoryTime} from "./format";
import StatisticsSummary from "./StatisticsSummary";
import {useFilteredSolveHistory} from "./useFilteredSolveHistory";
import {parseHistorySearch} from "./historySearch";
import type {SolveHistoryEntry, SolveStatistics} from "./types";

const StatisticsModal = lazy(() => import("./StatisticsModal"));
const SEARCH_DEBOUNCE_MS = 300;

function readUrlFilters(): {query: string} {
    const params = new URLSearchParams(window.location.search);
    return {query: params.get("q") ?? ""};
}

function writeUrlFilters(query: string) {
    const url = new URL(window.location.href);
    if (query) url.searchParams.set("q", query);
    else url.searchParams.delete("q");
    url.searchParams.delete("penalty");
    window.history.replaceState(window.history.state, "", `${url.pathname}${url.search}${url.hash}`);
}

type Props = {
    entries: SolveHistoryEntry[];
    loading: boolean;
    loadingMore: boolean;
    error: string | null;
    hasMore: boolean;
    solveCount: number | null;
    statistics: SolveStatistics | null;
    statisticsLoading: boolean;
    deletingSolveId: number | null;
    onRefresh: () => void;
    onLoadMore: () => void;
    onOpenSolve: (entry: SolveHistoryEntry, solveNumber?: number, filteredResult?: boolean) => void;
    onDeleteSolve: (entry: SolveHistoryEntry) => void;
};

export default function HistoryView({
                                        entries,
                                        loading,
                                        loadingMore,
                                        error,
                                        hasMore,
                                        solveCount,
                                        statistics,
                                        statisticsLoading,
                                        deletingSolveId,
                                        onRefresh,
                                        onLoadMore,
                                        onOpenSolve,
                                        onDeleteSolve,
                                    }: Props) {
    const [statisticsOpen, setStatisticsOpen] = useState(false);
    const statisticsMoreButtonRef = useRef<HTMLButtonElement>(null);
    const searchInputRef = useRef<HTMLInputElement>(null);
    const searchHelpButtonRef = useRef<HTMLButtonElement>(null);
    const searchHelpPointerType = useRef<string | null>(null);
    const searchHelpHovering = useRef(false);
    const [searchHelpOpen, setSearchHelpOpen] = useState(false);
    const initialFilters = useRef(readUrlFilters());
    const [searchDraft, setSearchDraft] = useState(initialFilters.current.query);
    const [searchQuery, setSearchQuery] = useState(initialFilters.current.query);
    const [isComposing, setIsComposing] = useState(false);
    const filteredHistory = useFilteredSolveHistory(searchQuery, entries);
    const parsedSearch = parseHistorySearch(searchQuery);
    const invalidTimeSearch = parsedSearch.kind === "invalid-time";

    useEffect(() => {
        if (isComposing || searchDraft === searchQuery) return;
        const timeoutId = window.setTimeout(() => setSearchQuery(searchDraft), SEARCH_DEBOUNCE_MS);
        return () => window.clearTimeout(timeoutId);
    }, [isComposing, searchDraft, searchQuery]);

    useEffect(() => {
        writeUrlFilters(searchQuery);
    }, [searchQuery]);

    useEffect(() => {
        function restoreUrlFilters() {
            const filters = readUrlFilters();
            setSearchDraft(filters.query);
            setSearchQuery(filters.query);
            setIsComposing(false);
        }
        window.addEventListener("popstate", restoreUrlFilters);
        return () => window.removeEventListener("popstate", restoreUrlFilters);
    }, []);

    function closeStatistics() {
        setStatisticsOpen(false);
        window.requestAnimationFrame(() => statisticsMoreButtonRef.current?.focus());
    }

    function clearSearch() {
        setSearchDraft("");
        setSearchQuery("");
        setIsComposing(false);
        writeUrlFilters("");
        searchInputRef.current?.focus();
    }

    const visibleEntries = filteredHistory.active ? filteredHistory.entries : entries;
    const viewLoading = filteredHistory.active ? filteredHistory.loading : loading;
    const viewLoadingMore = filteredHistory.active ? filteredHistory.loadingMore : loadingMore;
    const viewError = filteredHistory.active ? filteredHistory.error : error;
    const viewHasMore = filteredHistory.active ? filteredHistory.cursor !== null : hasMore;
    const viewTotalCount = filteredHistory.active ? filteredHistory.totalCount : solveCount;
    const filtersPending = searchDraft !== searchQuery;

    return (
        <section className="dashboard-history-view">
            <header className="history-view-header">
                <div>
                    <h1>History</h1>
                    <p>Review times, scrambles, and saved CFOP solution variants.</p>
                </div>
                <button
                    className="dashboard-secondary-button"
                    type="button"
                    onClick={filteredHistory.active ? filteredHistory.refresh : onRefresh}
                    disabled={filteredHistory.active && filteredHistory.loading}
                >
                    <RefreshCw size={16}/>
                    Refresh
                </button>
            </header>

            <div className="history-filter-toolbar">
                <div className="history-search-control">
                    <div className="history-search-label-row">
                        <label className="history-filter-label" htmlFor="history-search">Search solves</label>
                        <button
                            ref={searchHelpButtonRef}
                            className="history-search-help-trigger"
                            type="button"
                            aria-label="Search syntax help"
                            aria-expanded={searchHelpOpen}
                            aria-controls="history-search-help"
                            aria-describedby="history-search-help"
                            onPointerDown={(event) => { searchHelpPointerType.current = event.pointerType; }}
                            onPointerEnter={(event) => {
                                if (event.pointerType !== "touch") {
                                    searchHelpHovering.current = true;
                                    setSearchHelpOpen(true);
                                }
                            }}
                            onPointerLeave={(event) => {
                                if (event.pointerType !== "touch") {
                                    searchHelpHovering.current = false;
                                    window.setTimeout(() => {
                                        if (!searchHelpHovering.current && document.activeElement !== searchHelpButtonRef.current) {
                                            setSearchHelpOpen(false);
                                        }
                                    }, 80);
                                }
                            }}
                            onFocus={() => {
                                if (searchHelpPointerType.current !== "touch") setSearchHelpOpen(true);
                            }}
                            onBlur={() => setSearchHelpOpen(false)}
                            onClick={() => {
                                if (searchHelpPointerType.current === "touch") setSearchHelpOpen((open) => !open);
                                searchHelpPointerType.current = null;
                            }}
                            onKeyDown={(event) => {
                                if (event.key === "Escape") {
                                    setSearchHelpOpen(false);
                                    event.currentTarget.blur();
                                }
                            }}
                        >
                            <Info size={14}/>
                        </button>
                        <div
                            className="history-search-help"
                            id="history-search-help"
                            role="tooltip"
                            hidden={!searchHelpOpen}
                            onPointerEnter={() => { searchHelpHovering.current = true; }}
                            onPointerLeave={() => {
                                searchHelpHovering.current = false;
                                if (document.activeElement !== searchHelpButtonRef.current) setSearchHelpOpen(false);
                            }}
                        >
                                Search scramble moves or solve times. Use <code>S.CC</code> or <code>M:SS.CC</code> for an exact time.
                                <code>*.21</code> matches times ending in .21; <code>*:21.*</code> matches minute-long solves at 21 seconds;
                                <code>9.*</code> matches 9-second solves. Add <code>+</code> to match +2 only, or search <code>DNF</code>.
                        </div>
                    </div>
                    <span className="history-search-field">
                        <Search size={16} aria-hidden="true"/>
                        <input
                            ref={searchInputRef}
                            id="history-search"
                            type="search"
                            value={searchDraft}
                            placeholder="Scramble, 0.21, *.21…"
                            autoComplete="off"
                            onChange={(event) => setSearchDraft(event.currentTarget.value)}
                            onCompositionStart={() => setIsComposing(true)}
                            onCompositionEnd={(event) => {
                                setSearchDraft(event.currentTarget.value);
                                setIsComposing(false);
                            }}
                            onKeyDown={(event) => {
                                if (event.key === "Enter" && !event.nativeEvent.isComposing && !isComposing) {
                                    setSearchQuery(searchDraft);
                                }
                            }}
                        />
                        {searchDraft ? (
                            <button
                                className="history-search-clear"
                                type="button"
                                aria-label="Clear search"
                                onClick={clearSearch}
                            >
                                <X size={15}/>
                            </button>
                        ) : null}
                    </span>
                </div>
            </div>

            <section className="history-statistics" aria-label="All-history solve statistics">
                <div className="history-statistics-header">
                    <span>All-history statistics</span>
                    <button
                        ref={statisticsMoreButtonRef}
                        type="button"
                        aria-label="More statistics"
                        onClick={() => setStatisticsOpen(true)}
                    >
                        More <ArrowUpRight size={14}/>
                    </button>
                </div>
                <StatisticsSummary statistics={statistics} loading={statisticsLoading}/>
            </section>

            {statisticsOpen ? (
                <Suspense fallback={null}>
                    <StatisticsModal
                        statistics={statistics}
                        onClose={closeStatistics}
                        onOpenSolve={onOpenSolve}
                    />
                </Suspense>
            ) : null}

            {filtersPending ? (
                <div className="history-filter-status" role="status">
                    {isComposing ? "Finish entering search to filter solves." : "Updating search…"}
                </div>
            ) : null}
            {invalidTimeSearch && !filtersPending ? (
                <div className="history-search-format-hint" role="status">
                    Use S.CC or M:SS.CC for a time, * as a wildcard, or search scramble moves.
                </div>
            ) : null}
            {filteredHistory.active && filteredHistory.totalCount !== null && !invalidTimeSearch ? (
                <div className="history-results-count" role="status" aria-live="polite">
                    Showing {visibleEntries.length} of {filteredHistory.totalCount} matching solves
                </div>
            ) : null}
            {viewError && (!filteredHistory.active || visibleEntries.length === 0) ? (
                <div className="dashboard-alert error history-request-error" role="alert">
                    <span>{viewError}</span>
                    {filteredHistory.active ? (
                        <button type="button" className="dashboard-secondary-button compact" onClick={filteredHistory.retry}>
                            Retry
                        </button>
                    ) : null}
                </div>
            ) : null}
            {viewLoading && visibleEntries.length === 0 ? (
                <div className="history-loading" role="status"><LoaderCircle size={22}/>
                    {filteredHistory.active ? "Searching solves" : "Loading history"}
                </div>
            ) : null}
            {!viewLoading && !viewError && visibleEntries.length === 0 && !invalidTimeSearch ? (
                <div className="history-empty-state">
                    {filteredHistory.active ? (
                        <>
                            <strong>No matching solves</strong>
                            <span>Try another time or scramble search.</span>
                            <button
                                type="button"
                                className="history-clear-filters"
                                onClick={() => {
                                    setSearchDraft("");
                                    setSearchQuery("");
                                    setIsComposing(false);
                                    searchInputRef.current?.focus();
                                }}
                            >
                                Clear search
                            </button>
                        </>
                    ) : (
                        <>
                            <strong>No solves yet</strong>
                            <span>Complete a timed solve and it will appear here.</span>
                        </>
                    )}
                </div>
            ) : null}

            <div className="history-table" aria-busy={viewLoading || viewLoadingMore}>
                {visibleEntries.map((entry, index) => (
                    <article className="history-table-row" key={entry.id}>
                        <button
                            className="history-row-open"
                            type="button"
                            disabled={deletingSolveId === entry.id}
                            aria-label={`Open solution for solve ${formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}`}
                            onClick={() => onOpenSolve(
                                entry,
                                viewTotalCount === null ? index + 1 : viewTotalCount - index,
                                filteredHistory.active,
                            )}
                        >
                            <span className="history-index">
                                {String(viewTotalCount === null ? index + 1 : viewTotalCount - index).padStart(2, "0")}
                            </span>
                            <span className="history-scramble-preview" aria-hidden="true">
                                <HistoryCubeThumbnail scramble={entry.scramble}/>
                            </span>
                            <span className="history-time-cell">
                                <strong>{formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}</strong>
                                <small>{new Date(entry.createdAt).toLocaleString()}</small>
                            </span>
                            <span className="history-scramble">{entry.scramble}</span>
                        </button>
                        <div className="history-row-actions">
                            <button
                                className="history-delete-button"
                                type="button"
                                onClick={() => onDeleteSolve(entry)}
                                disabled={deletingSolveId !== null}
                                aria-label={`Delete solve ${formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf)}`}
                                title="Delete solve"
                            >
                                {deletingSolveId === entry.id ? <LoaderCircle size={15}/> : <Trash2 size={15}/>}
                            </button>
                        </div>
                    </article>
                ))}
            </div>

            {viewHasMore ? (
                <button
                    className="history-load-more"
                    type="button"
                    onClick={filteredHistory.active ? filteredHistory.loadMore : onLoadMore}
                    disabled={viewLoadingMore || viewLoading || filtersPending}
                    aria-busy={viewLoadingMore}
                >
                    {viewLoadingMore ? "Loading…" : "Load more"}
                </button>
            ) : null}
            {filteredHistory.active && viewError && visibleEntries.length > 0 ? (
                <div className="history-filter-inline-error" role="alert">
                    <span>{viewError}</span>
                    <button type="button" onClick={filteredHistory.retry}>Retry</button>
                </div>
            ) : null}
        </section>
    );
}
