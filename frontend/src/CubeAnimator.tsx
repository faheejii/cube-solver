import {useState} from "react";
import "cubing/twisty";
import type {SolveResponse, SolveStage} from "./types";

export type PlaybackStageId = "full" | "cross" | "f2l" | "oll" | "pll";

type StageOption = {
    id: PlaybackStageId;
    label: string;
    setupAlgorithm: string;
    algorithm: string;
    pairOrder?: number;
};

const PLAYBACK_SPEEDS = [0.5, 1, 1.5, 2, 3] as const;

type Props = {
    result: SolveResponse;
    selectedStage?: PlaybackStageId;
    onSelectedStageChange?: (stage: PlaybackStageId) => void;
    selectedF2LPair?: number | null;
    compact?: boolean;
};

export default function CubeAnimator({
                                         result,
                                         selectedStage,
                                         onSelectedStageChange,
                                         selectedF2LPair = null,
                                         compact = false,
                                     }: Props) {
    const options = stageOptions(result, selectedF2LPair);
    const [internalSelectedId, setInternalSelectedId] = useState<PlaybackStageId>("full");
    const [playbackSpeed, setPlaybackSpeed] = useState<(typeof PLAYBACK_SPEEDS)[number]>(1);
    const selectedId = selectedStage ?? internalSelectedId;
    const selected = selectedF2LPair !== null && selectedId === "f2l"
        ? f2lPairOption(result, selectedF2LPair) ?? options.find((option) => option.id === selectedId)
        : options.find((option) => option.id === selectedId);
    const activeOption = selected ?? options[0];

    function selectStage(stage: PlaybackStageId) {
        if (selectedStage === undefined) {
            setInternalSelectedId(stage);
        }
        onSelectedStageChange?.(stage);
    }

    return (
        <section className={compact ? "visualizer-section compact" : "visualizer-section"} aria-label="Cube animation">
            <div className="visualizer-toolbar">
                <div>
                    <p className="section-label">Playback</p>
                    <h2>3D Cube</h2>
                </div>

                <div className="playback-controls">
                    <div className="stage-tabs" aria-label="Animation stage">
                        {options.map((option) => (
                            <button
                                key={option.id}
                                type="button"
                                className={option.id === activeOption.id ? "stage-tab active" : "stage-tab"}
                                onClick={() => selectStage(option.id)}
                            >
                                {option.label}
                            </button>
                        ))}
                    </div>

                    <div className="speed-control" aria-label="Playback speed">
                        <span>Speed</span>
                        <div className="speed-options">
                            {PLAYBACK_SPEEDS.map((speed) => (
                                <button
                                    key={speed}
                                    type="button"
                                    className={speed === playbackSpeed ? "speed-option active" : "speed-option"}
                                    onClick={() => setPlaybackSpeed(speed)}
                                >
                                    {speed}x
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            </div>

            <div className="cube-player-shell">
                <twisty-player
                    key={`${activeOption.id}-${activeOption.pairOrder ?? ""}-${activeOption.setupAlgorithm}-${activeOption.algorithm}-${playbackSpeed}`}
                    puzzle="3x3x3"
                    experimental-setup-alg={activeOption.setupAlgorithm}
                    alg={activeOption.algorithm}
                    tempo-scale={String(playbackSpeed)}
                    background="none"
                    control-panel="bottom-row"
                    hint-facelets="none"
                    camera-latitude="28"
                    camera-longitude="34"
                />
            </div>

            {!compact ? (
                <div className="visualizer-footer">
                    <span>Setup: {activeOption.setupAlgorithm || "Solved cube"}</span>
                    <span>Solution: {activeOption.algorithm || "No moves for this stage"}</span>
                </div>
            ) : null}
        </section>
    );
}

function stageOptions(result: SolveResponse, selectedF2LPair: number | null): StageOption[] {
    const crossSetup = result.scramble;
    const f2lSetup = combineRawAlgorithms(result.scramble, result.cross.algorithm);
    const ollSetup = combineRawAlgorithms(result.scramble, result.cross.algorithm, result.f2l.algorithm);
    const pllSetup = combineRawAlgorithms(
        result.scramble,
        result.cross.algorithm,
        result.f2l.algorithm,
        result.oll.algorithm,
    );

    const options: StageOption[] = [
        {
            id: "full",
            label: "Full",
            setupAlgorithm: result.scramble,
            algorithm: combineAlgorithms(result.cross, result.f2l, result.oll, result.pll),
        },
        stageOption(result.cross, crossSetup),
        stageOption(result.f2l, f2lSetup),
        stageOption(result.oll, ollSetup),
        stageOption(result.pll, pllSetup),
    ];
    if (selectedF2LPair !== null) {
        const pair = f2lPairOption(result, selectedF2LPair);
        if (pair) {
            options[2] = pair;
        }
    }
    return options;
}

function stageOption(stage: SolveStage, setupAlgorithm: string): StageOption {
    return {
        id: stage.name as Exclude<PlaybackStageId, "full">,
        label: stage.name.toUpperCase(),
        setupAlgorithm,
        algorithm: stage.algorithm,
    };
}

function f2lPairOption(result: SolveResponse, order: number): StageOption | null {
    const pairs = result.f2l.pairs ?? [];
    const pair = pairs.find((candidate) => candidate.order === order);
    if (!pair) {
        return null;
    }
    const precedingPairs = pairs
        .filter((candidate) => candidate.order < order)
        .sort((first, second) => first.order - second.order)
        .map((candidate) => candidate.algorithm);
    return {
        id: "f2l",
        pairOrder: order,
        label: `Pair ${order}`,
        setupAlgorithm: combineRawAlgorithms(result.scramble, result.cross.algorithm, ...precedingPairs),
        algorithm: pair.algorithm,
    };
}

function combineAlgorithms(...stages: SolveStage[]): string {
    return stages
        .map((stage) => stage.algorithm.trim())
        .filter(Boolean)
        .join(" ");
}

function combineRawAlgorithms(...algorithms: string[]): string {
    return algorithms
        .map((algorithm) => algorithm.trim())
        .filter(Boolean)
        .join(" ");
}
