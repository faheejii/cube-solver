import {useEffect, useId, useState} from "react";
import {createPortal} from "react-dom";
import {LoaderCircle, X} from "lucide-react";
import {fetchSolveHistory} from "./api";
import {formatHistoryTime, formatSolveTime} from "./format";
import {calculateRollingMetricSeries, calculateWindowStatistics} from "./solveStatistics";
import StatisticsSummary from "./StatisticsSummary";
import type {SolveHistoryEntry, SolveStatistics} from "./types";

type Props = {
    statistics: SolveStatistics | null;
    onClose: () => void;
    onOpenSolve: (entry: SolveHistoryEntry, solveNumber?: number, filteredResult?: boolean, returnFocusTo?: HTMLElement | SVGElement) => void;
};

type ChartPoint = {
    entry: SolveHistoryEntry;
    index: number;
    solveNumber: number;
    x: number;
    y: number | null;
    status: "value" | "dnf" | "insufficient";
};

type Range = "last50" | "all";
type Metric = "time" | "ao5" | "ao12";
const HISTORY_WINDOW = 50;
const HISTORY_PAGE_SIZE = 100;
const CHART_WIDTH = 840;
const CHART_HEIGHT = 380;
const PLOT = {left: 76, right: 24, top: 58, bottom: 322};

export default function StatisticsModal({statistics, onClose, onOpenSolve}: Props) {
    const [range, setRange] = useState<Range>("last50");
    const [metric, setMetric] = useState<Metric>("time");
    const [last50Entries, setLast50Entries] = useState<SolveHistoryEntry[] | null>(null);
    const [allEntries, setAllEntries] = useState<SolveHistoryEntry[] | null>(null);
    const [errors, setErrors] = useState<Partial<Record<Range, string>>>({});
    const [retryKey, setRetryKey] = useState(0);
    const [activePoint, setActivePoint] = useState<number | null>(null);
    const titleId = useId();
    const descriptionId = useId();
    const entries = range === "last50" ? last50Entries : allEntries;
    const error = errors[range] ?? null;

    useEffect(() => {
        let cancelled = false;
        const cached = range === "last50" ? last50Entries : allEntries;
        if (cached !== null) return;

        setErrors((current) => {
            const next = {...current};
            delete next[range];
            return next;
        });
        const load = range === "last50" ? loadLast50() : loadAll(() => cancelled);
        load.then((loaded) => {
            if (!cancelled && loaded !== null) {
                if (range === "last50") setLast50Entries(loaded);
                else setAllEntries(loaded);
            }
        }).catch((loadError: unknown) => {
            if (!cancelled) {
                setErrors((current) => ({
                    ...current,
                    [range]: loadError instanceof Error ? loadError.message : "Could not load solve history.",
                }));
            }
        });
        return () => {
            cancelled = true;
        };
    }, [allEntries, last50Entries, range, retryKey]);

    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                // Let a solution dialog opened from the chart handle Escape first.
                if (document.querySelector(".solution-modal-backdrop")) return;
                event.preventDefault();
                onClose();
            }
        };
        window.addEventListener("keydown", onKeyDown);
        return () => window.removeEventListener("keydown", onKeyDown);
    }, [onClose]);

    const newestFirst = entries ?? [];
    const chronologicalEntries = [...newestFirst].reverse();
    const points = makeChartPoints(chronologicalEntries, statistics?.solveCount ?? newestFirst.length, metric);
    const validPoints = points.filter((point): point is ChartPoint & {y: number} => point.y !== null);
    const yDomain = getYDomain(validPoints.map((point) => point.y));
    const yTicks = createTicks(yDomain.min, yDomain.max, 4);
    const chartLines = makeLineSegments(points, yDomain.min, yDomain.max);
    const selected = activePoint === null ? null : points[activePoint] ?? null;
    const showing = entries?.length ?? 0;
    const windowStatistics = entries === null ? null : calculateWindowStatistics(entries);
    const plottedMean = validPoints.length === 0
        ? null
        : validPoints.reduce((sum, point) => sum + point.y, 0) / validPoints.length;
    const meanY = plottedMean === null ? null : scaleY(plottedMean, yDomain.min, yDomain.max);
    const bestPointValue = validPoints.length === 0 ? null : Math.min(...validPoints.map((point) => point.y));

    return createPortal((
        <div
            className="statistics-modal-backdrop"
            role="presentation"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) onClose();
            }}
        >
            <section
                className="statistics-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                aria-describedby={descriptionId}
                onMouseDown={(event) => event.stopPropagation()}
            >
                <header className="statistics-modal-header">
                    <div>
                        <h2 id={titleId}>Statistics</h2>
                        <p id={descriptionId}>
                            {range === "last50" ? `Solve-time trend for up to the latest ${HISTORY_WINDOW} solves.` : "Solve-time trend across all saved solves."}
                        </p>
                    </div>
                    <button className="icon-button" type="button" onClick={onClose} aria-label="Close statistics" autoFocus>
                        <X size={18}/>
                    </button>
                </header>

                <div className="statistics-modal-controls">
                    <div className="statistics-range-control" role="group" aria-label="Statistics range">
                        <button
                            type="button"
                            className={range === "last50" ? "active" : ""}
                            aria-pressed={range === "last50"}
                            onClick={() => { setActivePoint(null); setRange("last50"); }}
                        >
                            Last 50
                        </button>
                        <button
                            type="button"
                            className={range === "all" ? "active" : ""}
                            aria-pressed={range === "all"}
                            onClick={() => { setActivePoint(null); setRange("all"); }}
                        >
                            All solves
                        </button>
                    </div>

                    <div className="statistics-metric-control" role="group" aria-label="Chart metric">
                        {(["time", "ao5", "ao12"] as const).map((value) => (
                            <button
                                key={value}
                                type="button"
                                className={metric === value ? "active" : ""}
                                aria-pressed={metric === value}
                                onClick={() => { setActivePoint(null); setMetric(value); }}
                            >
                                {value === "time" ? "Time" : value === "ao5" ? "Ao5" : "Ao12"}
                            </button>
                        ))}
                    </div>
                </div>

                <StatisticsSummary
                    statistics={windowStatistics}
                    loading={entries === null}
                    onOpenSolve={onOpenSolve}
                    onOpenBestSolve={entries === null ? undefined : (bestMs) => {
                        const bestEntry = newestFirst.find((entry) => !entry.dnf && entry.officialMs === bestMs);
                        if (!bestEntry) throw new Error("Best solve is not available in this range.");
                        const bestPoint = points.find((point) => point.entry.id === bestEntry.id);
                        onOpenSolve(bestEntry, bestPoint?.solveNumber);
                    }}
                />

                <section className="statistics-chart-section" aria-label={`${metricLabel(metric)} chart`}>
                    <div className="statistics-chart-heading">
                        <h3>{metricLabel(metric)}</h3>
                        <span>{entries === null ? "" : `Showing ${showing} ${showing === 1 ? "solve" : "solves"}`}</span>
                    </div>
                    {error ? (
                        <div className="statistics-chart-message error" role="alert">
                            <span>{error}</span>
                            <button type="button" onClick={() => setRetryKey((current) => current + 1)}>Retry</button>
                        </div>
                    ) : entries === null ? (
                        <div className="statistics-chart-message" role="status">
                            <LoaderCircle size={20}/>
                            {range === "all" ? "Loading all solves…" : "Loading solve history…"}
                        </div>
                    ) : entries.length === 0 ? (
                        <div className="statistics-chart-message" role="status">No solves yet</div>
                    ) : (
                        <>
                            <svg
                                className="statistics-chart"
                                viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
                                role="group"
                                aria-label={`${metricLabel(metric)} for ${showing} ${range === "all" ? "total" : "recent"} ${showing === 1 ? "solve" : "solves"}, oldest to newest`}
                                preserveAspectRatio="xMidYMid meet"
                            >
                                <text className="chart-axis-title" x="0" y="20">DNF</text>
                                <line className="chart-dnf-guide" x1={PLOT.left} x2={CHART_WIDTH - PLOT.right} y1="26" y2="26"/>
                                {validPoints.length > 0 ? yTicks.map((tick) => {
                                    const y = scaleY(tick, yDomain.min, yDomain.max);
                                    return (
                                        <g key={tick}>
                                            <line className="chart-grid-line" x1={PLOT.left} x2={CHART_WIDTH - PLOT.right} y1={y} y2={y}/>
                                            <text className="chart-axis-label" x={PLOT.left - 10} y={y + 4} textAnchor="end">
                                                {formatSolveTime(tick * 1000)}
                                            </text>
                                        </g>
                                    );
                                }) : null}
                                {meanY === null ? null : (
                                    <line
                                        className="chart-mean-guide"
                                        x1={PLOT.left}
                                        x2={CHART_WIDTH - PLOT.right}
                                        y1={meanY}
                                        y2={meanY}
                                        aria-label={`Mean ${metricLabel(metric)} ${formatSolveTime(plottedMean! * 1000)}`}
                                    />
                                )}
                                {chartLines.map((line, index) => (
                                    <path key={index} className="chart-time-line" d={line}/>
                                ))}
                                {points.map((point) => {
                                    if (point.status === "insufficient") return null;
                                    const isDnf = point.status === "dnf";
                                    const y = isDnf ? 26 : scaleY(point.y!, yDomain.min, yDomain.max);
                                    const date = new Date(point.entry.createdAt).toLocaleString();
                                    const label = isDnf
                                        ? `Solve ${point.solveNumber}: DNF, ${date}`
                                        : `Solve ${point.solveNumber}: ${metric === "time"
                                            ? formatHistoryTime(point.entry.officialMs, point.entry.penalty, point.entry.dnf)
                                            : formatSolveTime(point.y! * 1000)}, ${date}`;
                                    const isBest = !isDnf && point.y === bestPointValue;
                                    return (
                                        <g
                                            key={point.entry.id}
                                            className={`chart-point${isDnf ? " dnf" : ""}${isBest ? " best" : ""}${activePoint === point.index ? " active" : ""}`}
                                            transform={`translate(${point.x} ${y})`}
                                            role="button"
                                            tabIndex={0}
                                            aria-label={`${label}${isBest ? ", Best" : ""}. Open solution`}
                                            onClick={(event) => {
                                                setActivePoint(point.index);
                                                event.currentTarget.focus();
                                                onOpenSolve(point.entry, point.solveNumber, false, event.currentTarget);
                                            }}
                                            onKeyDown={(event) => {
                                                if (event.key === "Enter" || event.key === " ") {
                                                    event.preventDefault();
                                                    onOpenSolve(point.entry, point.solveNumber, false, event.currentTarget);
                                                }
                                            }}
                                            onMouseEnter={() => setActivePoint(point.index)}
                                            onMouseLeave={() => setActivePoint((current) => current === point.index ? null : current)}
                                            onFocus={() => setActivePoint(point.index)}
                                            onBlur={() => setActivePoint((current) => current === point.index ? null : current)}
                                        >
                                            {isDnf ? <path d="M0 -6 L6 5 L-6 5 Z"/> : <circle r="4"/>}
                                        </g>
                                    );
                                })}
                                <text className="chart-axis-label" x={PLOT.left} y={CHART_HEIGHT - 16}>Older</text>
                                <text className="chart-axis-label" x={CHART_WIDTH - PLOT.right} y={CHART_HEIGHT - 16} textAnchor="end">Newer</text>
                                {entries.length > 1 ? (
                                    <text className="chart-axis-title" x={(PLOT.left + CHART_WIDTH - PLOT.right) / 2} y={CHART_HEIGHT - 2} textAnchor="middle">
                                        Solve order
                                    </text>
                                ) : null}
                            </svg>
                            <div className="statistics-chart-legend" aria-label="Chart legend">
                                <span><i className="chart-legend-time"/> {metricLabel(metric)}</span>
                                <span><i className="chart-legend-mean"/> Mean</span>
                                <span><i className="chart-legend-best"/> Best</span>
                                <span><i className="chart-legend-dnf"/> DNF</span>
                            </div>
                            <p className="statistics-chart-detail" role="status" aria-live="polite">
                                {selected ? describePoint(selected, metric) : "Hover over or focus a point to inspect it; select a point to open its solution."}
                            </p>
                        </>
                    )}
                </section>
            </section>
        </div>
    ), document.body);
}

async function loadLast50(): Promise<SolveHistoryEntry[]> {
    const page = await fetchSolveHistory(HISTORY_WINDOW);
    return page.items;
}

async function loadAll(isCancelled: () => boolean): Promise<SolveHistoryEntry[] | null> {
    const entries: SolveHistoryEntry[] = [];
    const seenIds = new Set<number>();
    const seenCursors = new Set<string>();
    let cursor: string | null = null;

    do {
        if (isCancelled()) return null;
        const page = await fetchSolveHistory(HISTORY_PAGE_SIZE, cursor);
        if (isCancelled()) return null;
        for (const entry of page.items) {
            if (!seenIds.has(entry.id)) {
                seenIds.add(entry.id);
                entries.push(entry);
            }
        }
        cursor = page.nextCursor;
        if (cursor && seenCursors.has(cursor)) {
            throw new Error("History pagination did not advance. Please retry.");
        }
        if (cursor) seenCursors.add(cursor);
    } while (cursor !== null);

    return entries;
}

function makeChartPoints(entries: SolveHistoryEntry[], solveCount: number | null, metric: Metric): ChartPoint[] {
    const width = CHART_WIDTH - PLOT.left - PLOT.right;
    const newestFirst = [...entries].reverse();
    const rollingSeries = metric === "time" ? null : calculateRollingMetricSeries(newestFirst, metric === "ao5" ? 5 : 12);
    const rolling = rollingSeries === null
        ? null
        : new Map(newestFirst.map((entry, index) => [entry.id, rollingSeries[index]]));
    return entries.map((entry, index) => {
        const rollingPoint = rolling?.get(entry.id);
        const status: ChartPoint["status"] = metric === "time"
            ? entry.dnf || entry.penalty === "dnf" || entry.officialMs === null ? "dnf" : "value"
            : rollingPoint?.status ?? "insufficient";
        const valueMs = metric === "time" ? entry.officialMs : rollingPoint?.valueMs ?? null;
        return {
            entry,
            index,
            solveNumber: Math.max(entries.length, solveCount ?? 0) - entries.length + index + 1,
            x: entries.length <= 1 ? PLOT.left + width / 2 : PLOT.left + (index / (entries.length - 1)) * width,
            y: status === "value" && valueMs !== null ? valueMs / 1000 : null,
            status,
        };
    });
}

function metricLabel(metric: Metric): string {
    return metric === "time" ? "Solve time" : metric === "ao5" ? "Ao5" : "Ao12";
}

function makeLineSegments(points: ChartPoint[], min: number, max: number): string[] {
    const segments: string[][] = [];
    let segment: string[] = [];
    points.forEach((point) => {
        if (point.y === null) {
            if (segment.length) segments.push(segment);
            segment = [];
            return;
        }
        segment.push(`${segment.length === 0 ? "M" : "L"}${point.x},${scaleY(point.y, min, max)}`);
    });
    if (segment.length) segments.push(segment);
    return segments.filter((line) => line.length > 1).map((line) => line.join(" "));
}

function getYDomain(values: number[]) {
    if (values.length === 0) return {min: 0, max: 1};
    const low = Math.min(...values);
    const high = Math.max(...values);
    const padding = Math.max((high - low) * 0.12, 0.5);
    return {min: Math.max(0, low - padding), max: high + padding};
}

function createTicks(min: number, max: number, count: number) {
    return Array.from({length: count}, (_, index) => max - ((max - min) * index) / (count - 1));
}

function scaleY(value: number, min: number, max: number) {
    const height = PLOT.bottom - PLOT.top;
    return PLOT.top + ((max - value) / Math.max(max - min, Number.EPSILON)) * height;
}

function describePoint(point: ChartPoint, metric: Metric) {
    const value = point.status === "dnf"
        ? "DNF"
            : point.status === "insufficient"
                ? `Insufficient solves for ${metricLabel(metric)}`
                : metric === "time"
                ? formatHistoryTime(point.entry.officialMs, point.entry.penalty, point.entry.dnf)
                : formatSolveTime(point.y! * 1000);
    return `Solve ${point.solveNumber} · ${value} · ${new Date(point.entry.createdAt).toLocaleString()}`;
}
