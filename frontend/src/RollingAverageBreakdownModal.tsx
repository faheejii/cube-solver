import {useEffect, useId, useRef, useState} from "react";
import type {KeyboardEvent} from "react";
import {createPortal} from "react-dom";
import {LoaderCircle, X} from "lucide-react";
import {fetchSolveHistory} from "./api";
import {formatHistoryTime, formatRollingAverage} from "./format";
import {calculateRollingWindowBreakdown, type RollingWindowDisposition} from "./solveStatistics";
import type {SolveHistoryEntry, SolveHistoryResponse} from "./types";

type Props = {
    size: 5 | 12;
    solveCount: number | null;
    onClose: () => void;
    onOpenSolve: (entry: SolveHistoryEntry, solveNumber?: number, filteredResult?: boolean, returnFocusTo?: HTMLElement | SVGElement) => void;
};

export default function RollingAverageBreakdownModal({size, solveCount, onClose, onOpenSolve}: Props) {
    const [entries, setEntries] = useState<SolveHistoryEntry[] | null>(null);
    const [totalCount, setTotalCount] = useState<number | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [retryKey, setRetryKey] = useState(0);
    const dialogRef = useRef<HTMLElement>(null);
    const titleId = useId();
    const descriptionId = useId();
    const label = size === 5 ? "Ao5" : "Ao12";

    useEffect(() => {
        let active = true;
        setEntries(null);
        setError(null);
        void fetchSolveHistory(size).then((response) => {
            if (!active) return;
            const page = response as SolveHistoryResponse & {totalCount?: number};
            setEntries(page.items);
            setTotalCount(page.totalCount ?? solveCount);
        }).catch((loadError: unknown) => {
            if (active) {
                setError(loadError instanceof Error ? loadError.message : `Could not load the ${label} solves.`);
            }
        });
        return () => { active = false; };
    }, [label, retryKey, size, solveCount]);

    const breakdown = entries === null ? null : calculateRollingWindowBreakdown(entries, size);
    const actualSolveCount = totalCount ?? solveCount ?? entries?.length ?? 0;

    function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
        if (event.key === "Escape") {
            event.preventDefault();
            event.stopPropagation();
            onClose();
            return;
        }
        if (event.key !== "Tab") return;

        const focusable = dialogRef.current?.querySelectorAll<HTMLElement>(
            'button:not(:disabled), [href], [tabindex]:not([tabindex="-1"])',
        );
        if (!focusable?.length) {
            event.preventDefault();
            dialogRef.current?.focus();
            return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    return createPortal((
        <div
            className="rolling-average-backdrop"
            role="presentation"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) onClose();
            }}
        >
            <section
                ref={dialogRef}
                className={`rolling-average-dialog metric-${label.toLowerCase()}`}
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                aria-describedby={descriptionId}
                tabIndex={-1}
                onKeyDown={handleKeyDown}
                onMouseDown={(event) => event.stopPropagation()}
            >
                <header className="rolling-average-header">
                    <div>
                        <h2 id={titleId}>{label} breakdown</h2>
                        <p id={descriptionId}>
                            {size === 5
                                ? "The average of the middle 3 times from the latest 5 solves."
                                : "The average of the middle 10 times from the latest 12 solves."}
                        </p>
                    </div>
                    <button className="icon-button" type="button" onClick={onClose} aria-label={`Close ${label} breakdown`} autoFocus>
                        <X size={18}/>
                    </button>
                </header>

                {error ? (
                    <div className="rolling-average-state error" role="alert">
                        <span>{error}</span>
                        <button type="button" onClick={() => setRetryKey((current) => current + 1)}>Retry</button>
                    </div>
                ) : entries === null ? (
                    <div className="rolling-average-state" role="status">
                        <LoaderCircle size={18}/>
                        Loading the latest solves…
                    </div>
                ) : entries.length === 0 ? (
                    <div className="rolling-average-state" role="status">No solves yet</div>
                ) : breakdown ? (
                    <>
                        <div className="rolling-average-result" aria-live="polite">
                            <span>{label}</span>
                            <strong>{formatRollingAverage(breakdown.average)}</strong>
                            <p>
                                {breakdown.average.status === "insufficient"
                                    ? `${entries.length} of ${size} solves · record ${size - entries.length} more to establish this average.`
                                    : breakdown.average.status === "dnf"
                                        ? "Two or more DNFs make this average a DNF."
                                        : "Fastest and slowest results are excluded; the remaining times are averaged."}
                            </p>
                        </div>
                        <div className="rolling-average-list" aria-label={`Latest ${size} solves`}>
                            {breakdown.solves.map(({entry, disposition}, index) => {
                                const solveNumber = actualSolveCount - index;
                                const time = formatHistoryTime(entry.officialMs, entry.penalty, entry.dnf);
                                const createdAt = new Date(entry.createdAt).toLocaleString();
                                const status = dispositionLabel(disposition);
                                return (
                                    <button
                                        className="rolling-average-solve"
                                        type="button"
                                        key={entry.id}
                                        onClick={(event) => onOpenSolve(
                                            entry,
                                            solveNumber > 0 ? solveNumber : undefined,
                                            false,
                                            event.currentTarget,
                                        )}
                                        aria-label={`Open solve ${solveNumber > 0 ? solveNumber : entry.id}, ${time}, ${status}, ${createdAt}`}
                                    >
                                        <span className="rolling-average-solve-number">{solveNumber > 0 ? `#${solveNumber}` : `#${entry.id}`}</span>
                                        <span className="rolling-average-solve-time">{time}</span>
                                        <time dateTime={entry.createdAt}>{createdAt}</time>
                                        <span className={`rolling-average-disposition disposition-${disposition}`}>{status}</span>
                                    </button>
                                );
                            })}
                        </div>
                    </>
                ) : null}
            </section>
        </div>
    ), document.body);
}

function dispositionLabel(disposition: RollingWindowDisposition): string {
    switch (disposition) {
        case "counted": return "Counted";
        case "dropped-fastest": return "Dropped · fastest";
        case "dropped-slowest": return "Dropped · slowest";
        case "dnf": return "DNF";
        case "not-counted": return "Not averaged";
    }
}
