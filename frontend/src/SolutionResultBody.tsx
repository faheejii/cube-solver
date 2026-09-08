import {AlertTriangle, ChevronDown} from "lucide-react";
import {useState} from "react";
import CubeAnimator, {type PlaybackStageId} from "./CubeAnimator";
import type {F2LPair, SolveResponse, SolveStage} from "./types";

type Props = {
    result: SolveResponse;
    requestedCross: string;
};

export default function SolutionResultBody({result, requestedCross}: Props) {
    const [selectedStage, setSelectedStage] = useState<PlaybackStageId>("full");
    const [selectedF2LPair, setSelectedF2LPair] = useState<number | null>(null);
    const stages = [result.cross, result.f2l, result.oll, result.pll];
    const selectedDetail = selectedStage === "full" ? null : selectedStage;
    const crossDiffers = normalizedCross(requestedCross) !== normalizedCross(result.crossFace);

    function selectStage(stage: PlaybackStageId) {
        setSelectedStage(stage);
        if (stage !== "f2l") {
            setSelectedF2LPair(null);
        }
    }

    function toggleStage(stage: SolveStage) {
        const stageId = stage.name as Exclude<PlaybackStageId, "full">;
        const collapsing = selectedStage === stageId;
        setSelectedStage(collapsing ? "full" : stageId);
        if (stageId !== "f2l" || collapsing) {
            setSelectedF2LPair(null);
        }
    }

    function selectF2LPair(order: number) {
        setSelectedStage("f2l");
        setSelectedF2LPair((current) => current === order ? null : order);
    }

    return (
        <div className="solution-result-body">
            <div className="solution-summary-strip">
                <SummaryItem label="Total" value={`${result.totalMoveCount} moves`}/>
                <SummaryItem label="Solver time" value={`${result.elapsedMs.toFixed(1)} ms`}/>
                {crossDiffers ? <SummaryItem label="Chosen cross" value={result.crossFace}/> : null}
            </div>

            <div className="solution-result-layout">
                <CubeAnimator
                    result={result}
                    selectedStage={selectedStage}
                    selectedF2LPair={selectedF2LPair}
                    onSelectedStageChange={selectStage}
                    compact
                />

                <aside className="solution-stage-rail" aria-label="Solution stages">
                    <div className="solution-stage-rail-heading">
                        <span>Stages</span>
                        <small>Select a stage to inspect and play it</small>
                    </div>
                    <div className="solution-stage-list">
                        {stages.map((stage) => {
                            const expanded = selectedDetail === stage.name;
                            return (
                                <article
                                    className={expanded ? `solution-stage-row solution-stage-row-${stage.name} expanded` : `solution-stage-row solution-stage-row-${stage.name}`}
                                    key={stage.name}
                                >
                                    <button type="button" onClick={() => toggleStage(stage)} aria-expanded={expanded}>
                    <span>
                      <strong>{stage.name.toUpperCase()}</strong>
                        {!stage.solved ? (
                            <i title={stage.status}>
                                <AlertTriangle size={14}/>
                                Needs attention
                            </i>
                        ) : null}
                    </span>
                                        <span>
                      <small>{stage.moveCount} moves</small>
                      <ChevronDown size={16}/>
                    </span>
                                    </button>

                                    {expanded ? (
                                        <div className="solution-stage-detail">
                                            <p>{stage.algorithm || "No moves required"}</p>
                                            {!stage.solved ? <span>{stage.status}</span> : null}
                                            {stage.name === "f2l" ? (
                                                <F2LDetails
                                                    result={result}
                                                    selectedPair={selectedF2LPair}
                                                    onPairSelect={selectF2LPair}
                                                />
                                            ) : null}
                                        </div>
                                    ) : null}
                                </article>
                            );
                        })}
                    </div>
                </aside>
            </div>
        </div>
    );
}

function F2LDetails({
                       result,
                       selectedPair,
                       onPairSelect,
                   }: {
    result: SolveResponse;
    selectedPair: number | null;
    onPairSelect: (order: number) => void;
}) {
    const pairs = result.f2l.pairs;
    return (
        <div className="solution-f2l-details">
            {pairs.length === 0 ? (
                <p className="solution-f2l-empty">No F2L pairs generated.</p>
            ) : (
                <div className="solution-pair-list" aria-label="F2L pair solutions">
                    {pairs.map((pair) => (
                        <F2LPairCard
                            key={pair.order}
                            pair={pair}
                            selected={selectedPair === pair.order}
                            onSelect={() => onPairSelect(pair.order)}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

function F2LPairCard({
                       pair,
                       selected,
                       onSelect,
                   }: {
    pair: F2LPair;
    selected: boolean;
    onSelect: () => void;
}) {
    return (
        <article className={selected ? "solution-pair-card selected" : "solution-pair-card"}>
            <button type="button" className="solution-pair-select" onClick={onSelect} aria-pressed={selected}>
                <span>
                    <strong>Pair {pair.order}</strong>
                    <small>{pair.corner} + {pair.edge} · {pair.targetSlot}</small>
                </span>
                <span className="solution-pair-moves">{pair.moveCount} moves</span>
            </button>
            <div className="solution-pair-body">
                <code>{pair.algorithm || "No moves"}</code>
                <div className="solution-pair-tags">
                    {reasonCodes(pair)
                        .filter((code) => code !== "PAIR_ALREADY_CONNECTED"
                            && code !== "PRESERVES_SOLVED_SLOTS"
                            && code !== "RECOVERY_UNPAIR")
                        .map((code) => <span key={code}>{reasonLabel(code)}</span>)}
                </div>
                <div className="solution-pair-facts">
                    {pair.moveBreakdownAvailable ? (
                        <span>Breakdown: {pair.setupAlgorithm ?? ""} {pair.pairingAlgorithm ?? ""} {pair.insertionAlgorithm ?? ""}</span>
                    ) : <span>Move breakdown unavailable</span>}
                </div>
            </div>
        </article>
    );
}

function reasonCodes(pair: F2LPair): string[] {
    return pair.selectionEvidence.reasonCodes;
}

function reasonLabel(code: string): string {
    return {
        SHORTEST_AVAILABLE_PAIR: "Shortest available pair",
        NO_ROTATION_REQUIRED: "No rotation required",
        FEWER_ROTATIONS: "Fewer rotations",
        LOWER_REMAINING_F2L_COST: "Lower remaining F2L cost",
        LOWER_TOTAL_CFOP_COST: "Lower total CFOP cost",
        BETTER_GLOBAL_ROUTE: "Better global route",
    }[code] ?? formatCode(code);
}

function formatCode(code: string): string {
    return code.toLowerCase().replace(/_/g, " ").replace(/(^|\s)\S/g, (letter: string) => letter.toUpperCase());
}

function SummaryItem({label, value}: { label: string; value: string }) {
    return (
        <div>
            <span>{label}</span>
            <strong>{value}</strong>
        </div>
    );
}

function normalizedCross(value: string): string {
    return value.trim().toUpperCase();
}
