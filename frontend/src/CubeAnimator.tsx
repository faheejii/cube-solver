import { useState } from "react";
import "cubing/twisty";
import type { SolveResponse, SolveStage } from "./types";

export type PlaybackStageId = "full" | "cross" | "f2l" | "oll" | "pll";

type StageOption = {
  id: PlaybackStageId;
  label: string;
  setupAlgorithm: string;
  algorithm: string;
};

const PLAYBACK_SPEEDS = [0.5, 1, 1.5, 2, 3] as const;

type Props = {
  result: SolveResponse;
  selectedStage?: PlaybackStageId;
  onSelectedStageChange?: (stage: PlaybackStageId) => void;
  compact?: boolean;
};

export default function CubeAnimator({
  result,
  selectedStage,
  onSelectedStageChange,
  compact = false,
}: Props) {
  const options = stageOptions(result);
  const [internalSelectedId, setInternalSelectedId] = useState<PlaybackStageId>("full");
  const [playbackSpeed, setPlaybackSpeed] = useState<(typeof PLAYBACK_SPEEDS)[number]>(1);
  const selectedId = selectedStage ?? internalSelectedId;
  const selected = options.find((option) => option.id === selectedId) ?? options[0];

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
                className={option.id === selected.id ? "stage-tab active" : "stage-tab"}
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
          key={`${selected.id}-${selected.setupAlgorithm}-${selected.algorithm}-${playbackSpeed}`}
          puzzle="3x3x3"
          experimental-setup-alg={selected.setupAlgorithm}
          alg={selected.algorithm}
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
          <span>Setup: {selected.setupAlgorithm || "Solved cube"}</span>
          <span>Solution: {selected.algorithm || "No moves for this stage"}</span>
        </div>
      ) : null}
    </section>
  );
}

function stageOptions(result: SolveResponse): StageOption[] {
  const crossSetup = result.scramble;
  const f2lSetup = combineRawAlgorithms(result.scramble, result.cross.algorithm);
  const ollSetup = combineRawAlgorithms(result.scramble, result.cross.algorithm, result.f2l.algorithm);
  const pllSetup = combineRawAlgorithms(
    result.scramble,
    result.cross.algorithm,
    result.f2l.algorithm,
    result.oll.algorithm,
  );

  return [
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
}

function stageOption(stage: SolveStage, setupAlgorithm: string): StageOption {
  return {
    id: stage.name as Exclude<PlaybackStageId, "full">,
    label: stage.name.toUpperCase(),
    setupAlgorithm,
    algorithm: stage.algorithm,
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
