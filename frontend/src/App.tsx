import { FormEvent, startTransition, useState } from "react";
import CubeAnimator from "./CubeAnimator";
import { solveCube } from "./api";
import type { SolveResponse, SolveStage } from "./types";

const DEFAULT_SCRAMBLE = "R D R' D2 R D' R'";
const FACE_OPTIONS = ["U", "D", "F", "B", "L", "R"] as const;

export default function App() {
  const [scramble, setScramble] = useState(DEFAULT_SCRAMBLE);
  const [crossFace, setCrossFace] = useState("U");
  const [useLegacyF2L, setUseLegacyF2L] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<SolveResponse | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const nextResult = await solveCube({
        scramble,
        crossFace,
        useLegacyF2L,
      });
      startTransition(() => {
        setResult(nextResult);
      });
    } catch (submitError) {
      const message = submitError instanceof Error ? submitError.message : "Solve request failed";
      setError(message);
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="page-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">CFOP Workbench</p>
          <h1>Cube Solver</h1>
        </div>
        <div className={result?.fullySolved ? "system-pill ready" : "system-pill"}>
          {result?.fullySolved ? "Solved" : "Ready"}
        </div>
      </header>

      <section className="workspace">
        <form className="solver-card" onSubmit={handleSubmit}>
          <div className="panel-heading">
            <p className="eyebrow">Scramble</p>
            <h2>Input</h2>
          </div>

          <label className="field">
            <textarea
              rows={5}
              value={scramble}
              onChange={(event) => setScramble(event.target.value)}
              placeholder="Enter a scramble like R U R' U'"
            />
          </label>

          <div className="field-row">
            <label className="field">
              <span>Cross face</span>
              <select value={crossFace} onChange={(event) => setCrossFace(event.target.value)}>
                {FACE_OPTIONS.map((face) => (
                  <option key={face} value={face}>
                    {face}
                  </option>
                ))}
              </select>
            </label>

            <label className="toggle">
              <input
                type="checkbox"
                checked={useLegacyF2L}
                onChange={(event) => setUseLegacyF2L(event.target.checked)}
              />
              <span>Use legacy F2L DB</span>
            </label>
          </div>

          <button className="solve-button" type="submit" disabled={loading}>
            {loading ? "Solving..." : "Solve scramble"}
          </button>
        </form>

        {result ? (
          <CubeAnimator result={result} />
        ) : (
          <section className="empty-visualizer" aria-label="Cube preview">
            <div className="glass-cube-mark">
              <span />
              <span />
              <span />
              <span />
              <span />
              <span />
              <span />
              <span />
              <span />
            </div>
            <p>Enter a scramble to preview the solution playback.</p>
          </section>
        )}
      </section>

      {error ? (
        <section className="status-banner status-error">
          <strong>Request failed.</strong> {error}
        </section>
      ) : null}

      {result ? (
        <section className="results-grid">
          <article className="summary-card">
            <p className="eyebrow">Solve Summary</p>
            <h2>{result.fullySolved ? "Solved in current frame" : "Partial solve result"}</h2>
            <div className="summary-metrics">
              <Metric label="Total moves" value={String(result.totalMoveCount)} />
              <Metric label="Elapsed" value={`${result.elapsedMs.toFixed(3)} ms`} />
              <Metric label="F2L mode" value={result.f2lMode} />
              <Metric label="Solved slots" value={result.solvedF2LSlots} />
            </div>
          </article>

          <article className="database-card">
            <p className="eyebrow">F2L Case Coverage</p>
            <div className="summary-metrics">
              <Metric label="Setup cases" value={String(result.f2lSetupCaseCount)} />
              <Metric label="Insert cases" value={String(result.f2lInsertCaseCount)} />
              <Metric label="Cross face" value={result.crossFace} />
              <Metric label="Legacy mode" value={result.useLegacyF2L ? "yes" : "no"} />
            </div>
          </article>

          <StageCard stage={result.cross} accent="amber" />
          <StageCard stage={result.f2l} accent="teal" />
          <StageCard stage={result.oll} accent="rust" />
          <StageCard stage={result.pll} accent="ink" />
        </section>
      ) : null}
    </main>
  );
}

function StageCard({ stage, accent }: { stage: SolveStage; accent: string }) {
  return (
    <article className={`stage-card accent-${accent}`}>
      <div className="stage-header">
        <p className="eyebrow">{stage.name.toUpperCase()}</p>
        <span className={stage.solved ? "stage-pill solved" : "stage-pill pending"}>
          {stage.solved ? "Solved" : "Pending"}
        </span>
      </div>
      <p className="stage-algorithm">{stage.algorithm || "No algorithm returned"}</p>
      <div className="stage-footer">
        <span>{stage.moveCount} moves</span>
        <span>{stage.status}</span>
      </div>
    </article>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
