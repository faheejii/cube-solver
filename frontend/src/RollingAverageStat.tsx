import {lazy, Suspense, useRef, useState} from "react";
import {formatRollingAverage} from "./format";
import type {RollingAverage} from "./types";
import type {SolveHistoryEntry} from "./types";

const RollingAverageBreakdownModal = lazy(() => import("./RollingAverageBreakdownModal"));

type Props = {
    size: 5 | 12;
    value: RollingAverage | null;
    solveCount: number | null;
    onOpenSolve: (entry: SolveHistoryEntry, solveNumber?: number, filteredResult?: boolean, returnFocusTo?: HTMLElement | SVGElement) => void;
    className: string;
};

export default function RollingAverageStat({size, value, solveCount, onOpenSolve, className}: Props) {
    const [open, setOpen] = useState(false);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const label = size === 5 ? "Ao5" : "Ao12";
    const displayedValue = formatRollingAverage(value);

    function close() {
        setOpen(false);
        window.requestAnimationFrame(() => triggerRef.current?.focus());
    }

    return (
        <>
            <button
                ref={triggerRef}
                type="button"
                className={`${className} rolling-average-stat`}
                aria-label={`Open ${label} breakdown, ${displayedValue}`}
                onClick={() => setOpen(true)}
            >
                <span>{label}</span>
                <strong>{displayedValue}</strong>
            </button>
            {open ? (
                <Suspense fallback={null}>
                    <RollingAverageBreakdownModal
                        size={size}
                        solveCount={solveCount}
                        onClose={close}
                        onOpenSolve={onOpenSolve}
                    />
                </Suspense>
            ) : null}
        </>
    );
}
