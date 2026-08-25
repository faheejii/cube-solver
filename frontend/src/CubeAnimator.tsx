import {lazy, Suspense, useEffect, useState} from "react";
import {Pause, Play, RotateCcw, SkipBack, SkipForward} from "lucide-react";
import type {SolveResponse, SolveStage} from "./types";
import CubePreview from "./CubePreview";

const ReferenceCubePlayer = import.meta.env.DEV
    ? lazy(() => import("./ReferenceCubePlayer"))
    : null;

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
    const debugMoves = splitAlgorithm(activeOption.algorithm);
    const [debugMoveIndex, setDebugMoveIndex] = useState(0);
    const [debugPlayback, setDebugPlayback] = useState(false);
    const [isPlaying, setIsPlaying] = useState(false);
    const [playbackComplete, setPlaybackComplete] = useState(false);
    const [playerKey, setPlayerKey] = useState(0);

    useEffect(() => {
        setDebugMoveIndex(0);
        setDebugPlayback(false);
        setIsPlaying(false);
        setPlaybackComplete(false);
        setPlayerKey((key) => key + 1);
    }, [activeOption.id, activeOption.pairOrder, activeOption.setupAlgorithm, activeOption.algorithm]);

    const effectiveSetup = import.meta.env.DEV && !debugPlayback
        ? combineRawAlgorithms(activeOption.setupAlgorithm, ...debugMoves.slice(0, debugMoveIndex))
        : activeOption.setupAlgorithm;
    const effectiveAlgorithm = import.meta.env.DEV && !debugPlayback ? "" : activeOption.algorithm;
    const currentDebugMove = debugMoveIndex === 0 ? "Setup" : debugMoves[debugMoveIndex - 1];

    function selectStage(stage: PlaybackStageId) {
        resetPlayback();
        if (selectedStage === undefined) {
            setInternalSelectedId(stage);
        }
        onSelectedStageChange?.(stage);
    }

    function selectPlaybackSpeed(speed: (typeof PLAYBACK_SPEEDS)[number]) {
        resetPlayback();
        setPlaybackSpeed(speed);
    }

    function togglePlayback() {
        if (playbackComplete) {
            setDebugPlayback(true);
            setPlaybackComplete(false);
            setPlayerKey((key) => key + 1);
            setIsPlaying(true);
            return;
        }
        if (!isPlaying) {
            setDebugPlayback(true);
        }
        setIsPlaying((playing) => !playing);
    }

    function resetPlayback() {
        setIsPlaying(false);
        setPlaybackComplete(false);
        setPlayerKey((key) => key + 1);
    }

    function selectAdjacentStage(offset: -1 | 1) {
        const currentIndex = options.findIndex((option) => option.id === activeOption.id && option.pairOrder === activeOption.pairOrder);
        const nextIndex = currentIndex + offset;
        if (nextIndex < 0 || nextIndex >= options.length) {
            return;
        }
        selectStage(options[nextIndex].id);
    }

    return (
        <section className={compact ? "visualizer-section compact" : "visualizer-section"} aria-label="Cube animation">
            <div className="visualizer-toolbar">
                <div>
                    <p className="section-label">Playback</p>
                    <h2>3D Cube</h2>
                </div>
            </div>

            <div className="cube-player-shell">
                <CubePreview
                    key={`${activeOption.id}-${activeOption.pairOrder ?? ""}-${effectiveSetup}-${effectiveAlgorithm}-${playbackSpeed}-${playerKey}`}
                    setupAlgorithm={effectiveSetup}
                    algorithm={effectiveAlgorithm}
                    stage={activeOption.id}
                    playbackSpeed={playbackSpeed}
                    isPlaying={isPlaying}
                    interactiveView
                    onPlaybackComplete={() => {
                        setIsPlaying(false);
                        setPlaybackComplete(true);
                    }}
                    data-playback-alg={activeOption.algorithm}
                />
            </div>

            <div className="playback-controls-bottom" aria-label="Playback controls">
                <button
                    type="button"
                    className="icon-button"
                    onClick={() => selectAdjacentStage(-1)}
                    disabled={options.findIndex((option) => option.id === activeOption.id && option.pairOrder === activeOption.pairOrder) <= 0}
                    aria-label="Previous stage"
                    title="Previous stage"
                >
                    <SkipBack size={16} aria-hidden="true"/>
                </button>
                <button
                    type="button"
                    className="icon-button"
                    onClick={resetPlayback}
                    aria-label="Restart playback"
                    title="Restart playback"
                >
                    <RotateCcw size={15} aria-hidden="true"/>
                </button>
                <button
                    type="button"
                    className={isPlaying ? "icon-button primary playback-toggle" : "icon-button playback-toggle"}
                    onClick={togglePlayback}
                    aria-label={isPlaying ? "Pause playback" : "Play playback"}
                    title={isPlaying ? "Pause playback" : "Play playback"}
                >
                    {isPlaying ? <Pause size={16} aria-hidden="true"/> : <Play size={16} aria-hidden="true"/>}
                </button>
                <button
                    type="button"
                    className="icon-button"
                    onClick={() => selectAdjacentStage(1)}
                    disabled={options.findIndex((option) => option.id === activeOption.id && option.pairOrder === activeOption.pairOrder) >= options.length - 1}
                    aria-label="Next stage"
                    title="Next stage"
                >
                    <SkipForward size={16} aria-hidden="true"/>
                </button>
                <label className="playback-speed-select">
                    <span>Speed</span>
                    <select
                        aria-label="Playback speed"
                        value={playbackSpeed}
                        onChange={(event) => selectPlaybackSpeed(Number(event.target.value) as (typeof PLAYBACK_SPEEDS)[number])}
                    >
                        {PLAYBACK_SPEEDS.map((speed) => (
                            <option key={speed} value={speed}>{speed}x</option>
                        ))}
                    </select>
                </label>
            </div>

            {import.meta.env.DEV && ReferenceCubePlayer ? (
                <div
                    className="cube-comparison-debug"
                    data-comparison-stage={activeOption.id}
                    data-comparison-source-setup={activeOption.setupAlgorithm}
                    data-comparison-source-alg={activeOption.algorithm}
                    data-comparison-effective-setup={effectiveSetup}
                    data-comparison-effective-alg={effectiveAlgorithm}
                    data-comparison-move-index={debugMoveIndex}
                    data-comparison-move-count={debugMoves.length}
                    data-comparison-mode={debugPlayback ? "playback" : "step"}
                >
                    <div className="cube-comparison-controls" aria-label="Cube comparison step controls">
                        <div>
                            <strong>Notation comparison</strong>
                            <span>Move {debugMoveIndex} / {debugMoves.length}: {currentDebugMove}</span>
                        </div>
                        <div className="cube-comparison-buttons">
                            <button type="button" onClick={() => { resetPlayback(); setDebugPlayback(false); setDebugMoveIndex(0); }} disabled={!debugPlayback && debugMoveIndex === 0}>Reset</button>
                            <button type="button" onClick={() => setDebugMoveIndex((index) => Math.max(0, index - 1))} disabled={debugPlayback || debugMoveIndex === 0}>Previous</button>
                            <button type="button" onClick={() => setDebugMoveIndex((index) => Math.min(debugMoves.length, index + 1))} disabled={debugPlayback || debugMoveIndex === debugMoves.length}>Next</button>
                            <button type="button" onClick={() => { resetPlayback(); setDebugMoveIndex(0); setDebugPlayback((playing) => !playing); }}>
                                {debugPlayback ? "Return to steps" : "Play custom"}
                            </button>
                        </div>
                    </div>
                    <div className="cube-comparison-notation">
                        <span>Original setup: {activeOption.setupAlgorithm || "Solved cube"}</span>
                        <span>Original algorithm: {activeOption.algorithm || "No moves"}</span>
                        <span>Effective setup: {effectiveSetup || "Solved cube"}</span>
                    </div>
                    <Suspense fallback={<div className="reference-cube-loading">Loading cubing.js reference...</div>}>
                        <ReferenceCubePlayer
                            setupAlgorithm={effectiveSetup}
                            algorithm={effectiveAlgorithm}
                            stage={activeOption.id}
                            playbackSpeed={playbackSpeed}
                            moveIndex={debugMoveIndex}
                            moveCount={debugMoves.length}
                        />
                    </Suspense>
                </div>
            ) : null}

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

function splitAlgorithm(algorithm: string): string[] {
    return algorithm.trim() ? algorithm.trim().split(/\s+/) : [];
}
