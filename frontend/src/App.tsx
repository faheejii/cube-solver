import { FormEvent, startTransition, useEffect, useRef, useState } from "react";
import { randomScrambleForEvent } from "cubing/scramble";
import CubeAnimator from "./CubeAnimator";
import { solveCube } from "./api";
import type { SolveResponse, SolveStage } from "./types";

const DEFAULT_SCRAMBLE = "R D R' D2 R D' R'";
const FACE_OPTIONS = ["U", "D", "F", "B", "L", "R"] as const;
type Theme = "light" | "dark";

function initialTheme(): Theme {
  const savedTheme = window.localStorage.getItem("cube-solver-theme");
  if (savedTheme === "light" || savedTheme === "dark") {
    return savedTheme;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export default function App() {
  const [scramble, setScramble] = useState(DEFAULT_SCRAMBLE);
  const [crossFace, setCrossFace] = useState("U");
  const [loading, setLoading] = useState(false);
  const [generatingScramble, setGeneratingScramble] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<SolveResponse | null>(null);
  const [showSummary, setShowSummary] = useState(false);
  const [pendingSummaryScroll, setPendingSummaryScroll] = useState(false);
  const [theme, setTheme] = useState<Theme>(initialTheme);
  const summaryRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    window.localStorage.setItem("cube-solver-theme", theme);
  }, [theme]);

  useEffect(() => {
    if (!pendingSummaryScroll || !showSummary) {
      return;
    }
    summaryRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    setPendingSummaryScroll(false);
  }, [pendingSummaryScroll, showSummary]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setShowSummary(false);
    setPendingSummaryScroll(false);

    try {
      const nextResult = await solveCube({
        scramble,
        crossFace,
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

  function handleShowSummary() {
    setShowSummary(true);
    setPendingSummaryScroll(true);
  }

  async function handleGenerateScramble() {
    setGeneratingScramble(true);
    setError(null);

    try {
      const nextScramble = await randomScrambleForEvent("333");
      setScramble(nextScramble.toString());
      setResult(null);
      setShowSummary(false);
      setPendingSummaryScroll(false);
    } catch (scrambleError) {
      const message =
        scrambleError instanceof Error ? scrambleError.message : "Scramble generation failed";
      setError(message);
    } finally {
      setGeneratingScramble(false);
    }
  }

  function toggleTheme() {
    setTheme((currentTheme) => (currentTheme === "dark" ? "light" : "dark"));
  }

  return (
    <main className="page-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">CFOP Workbench</p>
          <h1>Cube Solver</h1>
        </div>
        <div className="header-actions">
          <button
            className="theme-toggle"
            type="button"
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
          >
            <span className="theme-toggle-track">
              <span className="theme-toggle-thumb" />
            </span>
            <span>{theme === "dark" ? "Dark" : "Light"}</span>
          </button>
          <div className={result?.fullySolved ? "system-pill ready" : "system-pill"}>
            {result?.fullySolved ? "Solved" : "Ready"}
          </div>
        </div>
      </header>

      <section className="workspace">
        <form className="solver-card" onSubmit={handleSubmit}>
          <div className="panel-heading">
            <p className="eyebrow">Scramble</p>
            <div className="panel-title-row">
              <h2>Input</h2>
              <button
                className="generate-button"
                type="button"
                onClick={handleGenerateScramble}
                disabled={generatingScramble}
              >
                {generatingScramble ? "Generating..." : "Generate WCA scramble"}
              </button>
            </div>
          </div>

          <label className="field">
            <textarea
              rows={5}
              value={scramble}
              onChange={(event) => setScramble(event.target.value)}
              placeholder="Enter a scramble like R U R' U'"
            />
          </label>

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
        <section className="summary-reveal">
          <button className="summary-button" type="button" onClick={handleShowSummary}>
            {showSummary ? "Jump to solve summary" : "View solve summary"}
          </button>
        </section>
      ) : null}

      {result && showSummary ? (
        <section className="results-grid" ref={summaryRef}>
          <article className="summary-card">
            <p className="eyebrow">Solve Summary</p>
            <h2>{result.fullySolved ? "Solved in current frame" : "Partial solve result"}</h2>
            <div className="summary-metrics">
              <Metric label="Total moves" value={String(result.totalMoveCount)} />
              <Metric label="Elapsed" value={`${result.elapsedMs.toFixed(3)} ms`} />
              <Metric label="Solved slots" value={result.solvedF2LSlots} />
              <Metric label="Cross face" value={result.crossFace} />
            </div>
          </article>

          <article className="database-card">
            <p className="eyebrow">F2L Case Coverage</p>
            <div className="summary-metrics">
              <Metric label="Setup cases" value={String(result.f2lSetupCaseCount)} />
              <Metric label="Insert cases" value={String(result.f2lInsertCaseCount)} />
              <Metric label="F2L strategy" value="two-phase DB + fallback" />
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
