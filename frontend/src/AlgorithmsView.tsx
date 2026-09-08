import {Clipboard, LoaderCircle, Search} from "lucide-react";
import {useEffect, useMemo, useState} from "react";
import {fetchAlgorithms} from "./api";
import CubePreview from "./CubePreview";
import type {AlgorithmCatalogEntry} from "./types";

const PHASES = ["", "setup", "insert", "oll", "pll"];
const SLOTS = ["", "FR", "FL", "BL", "BR"];

export default function AlgorithmsView() {
    const [entries, setEntries] = useState<AlgorithmCatalogEntry[]>([]);
    const [phase, setPhase] = useState("");
    const [slot, setSlot] = useState("");
    const [search, setSearch] = useState("");
    const [status, setStatus] = useState("");
    const [preview, setPreview] = useState<AlgorithmCatalogEntry | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [copied, setCopied] = useState<string | null>(null);

    useEffect(() => {
        let disposed = false;
        setLoading(true);
        setError(null);
        void fetchAlgorithms({phase, slot, search, status})
            .then((response) => {
                if (!disposed) setEntries(response.items);
            })
            .catch((requestError) => {
                if (!disposed) setError(requestError instanceof Error ? requestError.message : "Could not load algorithms");
            })
            .finally(() => {
                if (!disposed) setLoading(false);
            });
        return () => {
            disposed = true;
        };
    }, [phase, slot, search, status]);

    const groupedCount = useMemo(() => entries.length, [entries]);

    async function copyAlgorithm(entry: AlgorithmCatalogEntry) {
        await navigator.clipboard.writeText(entry.algorithm);
        setCopied(entry.name);
        window.setTimeout(() => setCopied((current) => current === entry.name ? null : current), 1400);
    }

    return (
        <section className="dashboard-algorithms-view">
            <header className="algorithms-view-header">
                <div>
                    <h1>Algorithms</h1>
                    <p>Browse the canonical F2L, OLL, and PLL cases available to the solver.</p>
                </div>
                <span className="algorithms-count">{groupedCount} cases</span>
            </header>

            <div className="algorithms-filters">
                <label className="algorithms-search">
                    <Search size={16}/>
                    <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search cases or moves"/>
                </label>
                <select aria-label="Algorithm phase" value={phase} onChange={(event) => setPhase(event.target.value)}>
                    {PHASES.map((value) => <option key={value} value={value}>{value ? value.toUpperCase() : "All phases"}</option>)}
                </select>
                <select aria-label="Algorithm slot" value={slot} onChange={(event) => setSlot(event.target.value)}>
                    <option value="">All slots</option>
                    {SLOTS.slice(1).map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
                <select aria-label="Algorithm status" value={status} onChange={(event) => setStatus(event.target.value)}>
                    <option value="">Canonical</option>
                    <option value="experimental">Experimental</option>
                    <option value="deprecated">Deprecated</option>
                    <option value="test-only">Test-only</option>
                    <option value="all">All statuses</option>
                </select>
            </div>

            {error ? <div className="dashboard-alert error">{error}</div> : null}
            {loading ? <div className="history-loading"><LoaderCircle size={20}/> Loading algorithms</div> : null}
            {!loading && !error && entries.length === 0 ? (
                <div className="history-empty-state"><strong>No matching algorithms</strong><span>Try a different phase, slot, or search.</span></div>
            ) : null}
            <div className="algorithm-catalog-list">
                {entries.map((entry) => (
                    <article className="algorithm-catalog-row" key={`${entry.phase}-${entry.name}-${entry.slot}`}>
                        <div className="algorithm-row-case">
                            <div>
                                <span className="algorithm-phase">{entry.phase}</span>
                                <h2>{entry.name}</h2>
                            </div>
                            <span className="algorithm-slot">{entry.slot ?? entry.nonPreservedSlot ?? entry.phase.toUpperCase()}</span>
                        </div>
                        <code className="algorithm-moves">{entry.algorithm}</code>
                        <div className="algorithm-row-meta">
                            <span className="algorithm-status">{entry.status}</span>
                            <span>Preserves {entry.preservedSlots.length ? entry.preservedSlots.join(" ") : "none"}</span>
                        </div>
                        <details className="algorithm-row-details">
                            <summary>Details</summary>
                            <div>
                                <span>{signatureSummary(entry)}</span>
                                {entry.sourceSetup ? <code>Source setup: {entry.sourceSetup}</code> : null}
                            </div>
                        </details>
                        <div className="algorithm-row-actions">
                            <button className="dashboard-secondary-button compact" type="button" onClick={() => void copyAlgorithm(entry)}>
                                <Clipboard size={14}/>{copied === entry.name ? "Copied" : "Copy algorithm"}
                            </button>
                            <button className="dashboard-secondary-button compact" type="button" onClick={() => setPreview(entry)}>
                                Test on cube
                            </button>
                        </div>
                    </article>
                ))}
            </div>
            {preview ? (
                <div className="algorithm-preview-backdrop" role="presentation" onClick={() => setPreview(null)}>
                    <section className="algorithm-preview-dialog" role="dialog" aria-modal="true" aria-label={`${preview.name} preview`} onClick={(event) => event.stopPropagation()}>
                        <div className="algorithm-preview-header">
                            <div><span className="algorithm-phase">{preview.phase}</span><h2>{preview.name}</h2></div>
                            <button className="dashboard-secondary-button compact" type="button" onClick={() => setPreview(null)}>Close</button>
                        </div>
                        <div className="cube-player-shell algorithm-preview-player">
                            <CubePreview
                                setupAlgorithm={preview.previewSetup ?? ""}
                                algorithm={preview.algorithm}
                            />
                        </div>
                        <p className="algorithm-preview-context">
                            {preview.previewSetup
                                ? <>Setup state: <code>{preview.previewSetup}</code></>
                                : "Insert preview starts from a solved cube."}
                        </p>
                        <code className="algorithm-moves">{preview.algorithm}</code>
                    </section>
                </div>
            ) : null}
        </section>
    );
}

function signatureSummary(entry: AlgorithmCatalogEntry): string {
    if (entry.signature.kind === "f2l" || entry.signature.kind === "f2l-setup") {
        return `${entry.signature.cornerPosition}/${entry.signature.cornerOrientation} · ${entry.signature.edgePosition}/${entry.signature.edgeOrientation}`;
    }
    if (entry.signature.kind === "pll") {
        return `Corners ${entry.signature.urfPiece} ${entry.signature.uflPiece} ${entry.signature.ulbPiece} ${entry.signature.ubrPiece}`;
    }
    const facelets = Object.entries(entry.signature)
        .filter(([key]) => key !== "kind")
        .filter(([, value]) => value === true)
        .map(([key]) => key.toUpperCase());
    return `OLL facelets: ${facelets.join(" ") || "none"}`;
}
