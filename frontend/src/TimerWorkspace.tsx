import {useLayoutEffect, useRef, useState} from "react";
import {
    Check,
    Pencil,
    RefreshCw,
    X,
} from "lucide-react";
import ScrambleCube from "./ScrambleCube";
import CrossFaceSelect from "./CrossFaceSelect";
import {formatMetricTime, formatRollingAverage} from "./format";
import type {SolveResponse, SolveStatistics} from "./types";

type FaceOption = {
    value: string;
    label: string;
};

type Props = {
    scramble: string;
    draftScramble: string;
    crossFace: string;
    f2lMode: "greedy" | "optimized";
    faceOptions: readonly FaceOption[];
    isEditingScramble: boolean;
    generatingScramble: boolean;
    attemptLocked: boolean;
    solutionStatus: string;
    result: SolveResponse | null;
    statistics: SolveStatistics | null;
    timerPhase: string;
    timerValue: string;
    timerHint: string;
    timerDetail: string;
    onDraftChange: (value: string) => void;
    onCrossFaceChange: (value: string) => void;
    onF2LModeChange: (value: "greedy" | "optimized") => void;
    onGenerateScramble: () => void;
    onEnterEdit: () => void;
    onCancelEdit: () => void;
    onSaveEdit: () => void;
    onShowSolution: () => void;
    onTimerPointerDown: () => void;
    onTimerPointerUp: () => void;
};

export default function TimerWorkspace({
                                           scramble,
                                           draftScramble,
                                           crossFace,
                                           f2lMode,
                                           faceOptions,
                                           isEditingScramble,
                                           generatingScramble,
                                           attemptLocked,
                                           solutionStatus,
                                           result,
                                           statistics,
                                           timerPhase,
                                           timerValue,
                                           timerHint,
                                           timerDetail,
                                           onDraftChange,
                                           onCrossFaceChange,
                                           onF2LModeChange,
                                           onGenerateScramble,
                                           onEnterEdit,
                                           onCancelEdit,
                                           onSaveEdit,
                                           onShowSolution,
                                           onTimerPointerDown,
                                           onTimerPointerUp,
                                       }: Props) {
    const scrambleTextRef = useRef<HTMLParagraphElement>(null);
    const [scrambleFontSize, setScrambleFontSize] = useState<number | null>(null);

    useLayoutEffect(() => {
        const textElement = scrambleTextRef.current;
        const container = textElement?.parentElement;
        if (!textElement || !container) {
            return;
        }
        if (typeof ResizeObserver === "undefined") {
            return;
        }

        const fitScramble = () => {
            const availableWidth = textElement.clientWidth;
            const maximumSize = Math.min(42, Math.max(24, availableWidth * 0.035));
            const minimumSize = 10;
            const targetWidth = availableWidth * 0.9;

            const computedStyle = window.getComputedStyle(textElement);
            const measureElement = document.createElement("span");
            measureElement.style.position = "absolute";
            measureElement.style.visibility = "hidden";
            measureElement.style.whiteSpace = "nowrap";
            measureElement.style.fontFamily = computedStyle.fontFamily;
            measureElement.style.fontWeight = computedStyle.fontWeight;
            measureElement.style.letterSpacing = computedStyle.letterSpacing;
            measureElement.style.fontSize = `${maximumSize}px`;
            measureElement.textContent = textElement.textContent ?? "";
            document.body.appendChild(measureElement);
            const naturalWidth = measureElement.getBoundingClientRect().width;
            measureElement.remove();

            const fittedSize = naturalWidth > targetWidth
                ? Math.max(minimumSize, maximumSize * targetWidth / naturalWidth)
                : maximumSize;

            setScrambleFontSize(Math.round(fittedSize * 10) / 10);
        };

        fitScramble();
        const resizeObserver = new ResizeObserver(fitScramble);
        resizeObserver.observe(container);
        return () => resizeObserver.disconnect();
    }, [scramble, isEditingScramble]);

    return (
        <section className="timer-workspace">
            <header className="workspace-toolbar">
                <div className="toolbar-group">
                    <span className="toolbar-product">3×3</span>
                    <span className="toolbar-separator"/>
                    <div className="compact-control">
                        <span>Cross</span>
                        <CrossFaceSelect
                            value={crossFace}
                            onChange={onCrossFaceChange}
                            options={faceOptions}
                            disabled={attemptLocked}
                        />
                    </div>
                    <span className="toolbar-separator"/>
                    <div className="workspace-mode-switch" aria-label="F2L mode">
                        <button
                            className={f2lMode === "greedy" ? "active" : ""}
                            type="button"
                            onClick={() => onF2LModeChange("greedy")}
                            disabled={attemptLocked}
                        >
                            Fast
                        </button>
                        <button
                            className={f2lMode === "optimized" ? "active" : ""}
                            type="button"
                            onClick={() => onF2LModeChange("optimized")}
                            disabled={attemptLocked}
                        >
                            Optimized
                        </button>
                    </div>
                </div>
            </header>

            <section className="dashboard-scramble-card">
                <span className="dashboard-kicker">{isEditingScramble ? "Edit scramble" : "Scramble"}</span>
                {isEditingScramble ? (
                    <textarea
                        rows={2}
                        value={draftScramble}
                        onChange={(event) => onDraftChange(event.target.value)}
                        autoFocus
                    />
                ) : (
                    <p ref={scrambleTextRef} style={scrambleFontSize ? {fontSize: `${scrambleFontSize}px`} : undefined}>
                        {scramble || "Preparing scramble…"}
                    </p>
                )}
                <div className="dashboard-scramble-actions">
                    <button
                        type="button"
                        onClick={onGenerateScramble}
                        disabled={generatingScramble || attemptLocked}
                        aria-label="Generate new scramble"
                        title="Generate new scramble"
                    >
                        <RefreshCw size={18}/>
                    </button>
                    {isEditingScramble ? (
                        <>
                            <button type="button" onClick={onCancelEdit} aria-label="Cancel edit">
                                <X size={18}/>
                            </button>
                            <button type="button" onClick={onSaveEdit} aria-label="Save scramble">
                                <Check size={18}/>
                            </button>
                        </>
                    ) : (
                        <button
                            type="button"
                            onClick={onEnterEdit}
                            disabled={attemptLocked}
                            aria-label="Edit scramble"
                        >
                            <Pencil size={17}/>
                        </button>
                    )}
                </div>
            </section>

            <section
                className={`dashboard-timer-stage phase-${timerPhase}`}
                onPointerDown={onTimerPointerDown}
                onPointerUp={onTimerPointerUp}
                onPointerCancel={onTimerPointerUp}
                aria-label="Solve timer"
            >
                <div className="timer-display-group">
                    <div className="dashboard-timer-number">{timerValue}</div>
                    <div className="dashboard-inline-stats">
                        <InlineStat label="Best" value={formatMetricTime(statistics?.bestMs ?? null)} accent="blue"/>
                        <InlineStat label="Ao5" value={formatRollingAverage(statistics?.ao5 ?? null)} accent="violet"/>
                        <InlineStat label="Ao12" value={formatRollingAverage(statistics?.ao12 ?? null)} accent="cyan"/>
                    </div>
                    <ScrambleCube scramble={scramble}/>
                </div>
                <button
                    className="timer-start-capsule"
                    type="button"
                    onPointerDown={(event) => event.stopPropagation()}
                    onPointerUp={(event) => event.stopPropagation()}
                    onPointerCancel={(event) => event.stopPropagation()}
                    onClick={onShowSolution}
                    disabled={solutionStatus !== "ready" || result === null}
                    aria-label={solutionStatus === "ready" && result !== null ? "Show solution" : undefined}
                >
                    <strong>{timerHint}</strong>
                    <span>{timerDetail}</span>
                </button>
            </section>
        </section>
    );
}

function InlineStat({
                        label,
                        value,
                        accent,
                    }: {
    label: string;
    value: string;
    accent: string;
}) {
    return (
        <div className={`inline-stat accent-${accent}`}>
            <span>{label}</span>
            <strong>{value}</strong>
        </div>
    );
}
