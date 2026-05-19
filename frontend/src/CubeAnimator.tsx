import { useState } from "react";
import "cubing/twisty";
import type { SolveResponse, SolveStage } from "./types";

type StageOption = {
  id: string;
  label: string;
  setupAlgorithm: string;
  algorithm: string;
};

export default function CubeAnimator({ result }: { result: SolveResponse }) {
  const options = stageOptions(result);
  const [selectedId, setSelectedId] = useState(options[0]?.id ?? "full");
  const selected = options.find((option) => option.id === selectedId) ?? options[0];

  return (
    <section className="visualizer-section" aria-label="Cube animation">
      <div className="visualizer-toolbar">
        <div>
          <p className="eyebrow">Playback</p>
          <h2>3D Cube</h2>
        </div>

        <div className="stage-tabs" aria-label="Animation stage">
          {options.map((option) => (
            <button
              key={option.id}
              type="button"
              className={option.id === selected.id ? "stage-tab active" : "stage-tab"}
              onClick={() => setSelectedId(option.id)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="cube-player-shell">
        <twisty-player
          key={`${selected.id}-${selected.setupAlgorithm}-${selected.algorithm}`}
          puzzle="3x3x3"
          experimental-setup-alg={selected.setupAlgorithm}
          alg={selected.algorithm}
          background="none"
          control-panel="bottom-row"
          hint-facelets="none"
          camera-latitude="28"
          camera-longitude="34"
        />
      </div>

      <div className="visualizer-footer">
        <span>Setup: {selected.setupAlgorithm || "Solved cube"}</span>
        <span>{selected.algorithm || "No moves for this stage"}</span>
      </div>
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
    id: stage.name,
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
