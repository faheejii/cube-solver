import { AlertTriangle, ChevronDown } from "lucide-react";
import { useState } from "react";
import CubeAnimator, { type PlaybackStageId } from "./CubeAnimator";
import type { SolveResponse, SolveStage } from "./types";

type Props = {
  result: SolveResponse;
  requestedCross: string;
};

export default function SolutionResultBody({ result, requestedCross }: Props) {
  const [selectedStage, setSelectedStage] = useState<PlaybackStageId>("full");
  const stages = [result.cross, result.f2l, result.oll, result.pll];
  const selectedDetail = selectedStage === "full" ? null : selectedStage;
  const crossDiffers = normalizedCross(requestedCross) !== normalizedCross(result.crossFace);

  function selectStage(stage: PlaybackStageId) {
    setSelectedStage(stage);
  }

  function toggleStage(stage: SolveStage) {
    const stageId = stage.name as Exclude<PlaybackStageId, "full">;
    setSelectedStage((current) => current === stageId ? "full" : stageId);
  }

  return (
    <>
      <div className="solution-summary-strip">
        <SummaryItem label="Total" value={`${result.totalMoveCount} moves`} />
        <SummaryItem label="Solver time" value={`${result.elapsedMs.toFixed(1)} ms`} />
        {crossDiffers ? <SummaryItem label="Chosen cross" value={result.crossFace} /> : null}
        <SummaryItem label="Solved slots" value={`${solvedSlotCount(result.solvedF2LSlots)}/4`} />
      </div>

      <div className="solution-result-layout">
        <CubeAnimator
          result={result}
          selectedStage={selectedStage}
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
                  className={expanded ? "solution-stage-row expanded" : "solution-stage-row"}
                  key={stage.name}
                >
                  <button type="button" onClick={() => toggleStage(stage)} aria-expanded={expanded}>
                    <span>
                      <strong>{stage.name.toUpperCase()}</strong>
                      {!stage.solved ? (
                        <i title={stage.status}>
                          <AlertTriangle size={14} />
                          Needs attention
                        </i>
                      ) : null}
                    </span>
                    <span>
                      <small>{stage.moveCount} moves</small>
                      <ChevronDown size={16} />
                    </span>
                  </button>

                  {expanded ? (
                    <div className="solution-stage-detail">
                      <p>{stage.algorithm || "No moves required"}</p>
                      {!stage.solved ? <span>{stage.status}</span> : null}
                      {stage.name === "f2l" ? (
                        <div className="solution-stage-f2l-meta">
                          <span>{result.f2lSetupCaseCount} setup cases</span>
                          <span>{result.f2lInsertCaseCount} insert cases</span>
                          <span>{result.solvedF2LSlots}</span>
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                </article>
              );
            })}
          </div>
        </aside>
      </div>
    </>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
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

function solvedSlotCount(summary: string): number {
  return (summary.match(/\b(FR|FL|BL|BR)\b/g) ?? []).length;
}
