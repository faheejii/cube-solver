import {Clipboard, LoaderCircle, Pause, Play, Search} from "lucide-react";
import {useEffect, useMemo, useRef, useState} from "react";
import {fetchAdminSolverProfile, fetchAlgorithms, updateAdminSolverProfile} from "./api";
import {useCubePlaybackMode} from "./CubePreviewModeContext";
import DeferredCubePreview from "./DeferredCubePreview";
import type {AlgorithmCatalogEntry} from "./types";

const PHASES = ["", "setup", "insert", "oll", "pll"];
const SLOTS = ["", "FR", "FL", "BL", "BR"];

export default function AlgorithmsView() {
    const playbackMode = useCubePlaybackMode();
    const [entries, setEntries] = useState<AlgorithmCatalogEntry[]>([]);
    const [phase, setPhase] = useState("");
    const [slot, setSlot] = useState("");
    const [search, setSearch] = useState("");
    const [status, setStatus] = useState("");
    const [preview, setPreview] = useState<AlgorithmCatalogEntry | null>(null);
    const [previewIsPlaying, setPreviewIsPlaying] = useState(true);
    const [previewComplete, setPreviewComplete] = useState(false);
    const [previewKey, setPreviewKey] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [copied, setCopied] = useState<string | null>(null);
    const [disabledAlgorithms, setDisabledAlgorithms] = useState<string[]>([]);
    const [profileLoading, setProfileLoading] = useState(true);
    const [profileLoadError, setProfileLoadError] = useState<string | null>(null);
    const [profileSaving, setProfileSaving] = useState(false);
    const [profileSaveError, setProfileSaveError] = useState<string | null>(null);
    const [profileSaved, setProfileSaved] = useState(false);
    const [profileLoadAttempt, setProfileLoadAttempt] = useState(0);
    const profileSaveLock = useRef(false);

    useEffect(() => {
        let disposed = false;
        setProfileLoading(true);
        setProfileLoadError(null);
        void fetchAdminSolverProfile()
            .then((profile) => {
                if (!disposed) {
                    setDisabledAlgorithms(profile.disabledAlgorithms);
                    setProfileSaved(true);
                }
            })
            .catch((requestError) => {
                if (!disposed) setProfileLoadError(requestError instanceof Error ? requestError.message : "Could not load your solver profile.");
            })
            .finally(() => {
                if (!disposed) setProfileLoading(false);
            });
        return () => {
            disposed = true;
        };
    }, [profileLoadAttempt]);

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

    function openPreview(entry: AlgorithmCatalogEntry) {
        setPreview(entry);
        setPreviewIsPlaying(true);
        setPreviewComplete(false);
        setPreviewKey((key) => key + 1);
    }

    function togglePreviewPlayback() {
        if (previewComplete) {
            setPreviewComplete(false);
            setPreviewKey((key) => key + 1);
            setPreviewIsPlaying(true);
            return;
        }
        setPreviewIsPlaying((playing) => !playing);
    }

    async function saveSolverProfile(nextDisabledAlgorithms: string[]) {
        if (profileSaveLock.current) return;
        profileSaveLock.current = true;
        setProfileSaving(true);
        setProfileSaveError(null);
        setProfileSaved(false);
        try {
            await updateAdminSolverProfile(nextDisabledAlgorithms);
            setProfileSaved(true);
        } catch (requestError) {
            setProfileSaveError(requestError instanceof Error ? requestError.message : "Could not save your solver profile.");
        } finally {
            profileSaveLock.current = false;
            setProfileSaving(false);
        }
    }

    function toggleAlgorithm(entry: AlgorithmCatalogEntry, checked: boolean) {
        if (entry.status !== "canonical" || profileLoading || profileLoadError || profileSaving || profileSaveLock.current) return;
        const id = `${entry.phase}:${entry.name}`;
        const next = checked
            ? [...new Set([...disabledAlgorithms, id])]
            : disabledAlgorithms.filter((disabledId) => disabledId !== id);
        setDisabledAlgorithms(next);
        void saveSolverProfile(next);
    }

    return (
        <section className="dashboard-algorithms-view">
            <header className="algorithms-view-header">
                <div>
                    <h1>Algorithms</h1>
                    <p>Browse solver cases. Disable canonical algorithms to test your own solves; the profile only affects your account.</p>
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

            <div className={`algorithm-profile-status${profileLoadError || profileSaveError ? " has-error" : ""}`} aria-live="polite">
                {profileLoading ? <span>Loading your solver profile…</span> : null}
                {profileLoadError ? (
                    <>
                        <span role="alert">Could not load your solver profile. {profileLoadError}</span>
                        <button className="dashboard-secondary-button compact" type="button" onClick={() => setProfileLoadAttempt((attempt) => attempt + 1)}>Retry</button>
                    </>
                ) : null}
                {!profileLoading && !profileLoadError && profileSaving ? <span>Saving solver profile…</span> : null}
                {!profileLoading && !profileLoadError && profileSaveError ? (
                    <>
                        <span role="alert">Changes were not saved. Your selection is kept here. {profileSaveError}</span>
                        <button className="dashboard-secondary-button compact" type="button" onClick={() => void saveSolverProfile(disabledAlgorithms)}>Retry</button>
                    </>
                ) : null}
                {!profileLoading && !profileLoadError && !profileSaving && !profileSaveError && profileSaved ? <span>Solver profile saved to your account.</span> : null}
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
                            {entry.status === "canonical" ? (
                                <label className="algorithm-profile-toggle">
                                    <input
                                        type="checkbox"
                                        checked={disabledAlgorithms.includes(`${entry.phase}:${entry.name}`)}
                                        disabled={profileLoading || Boolean(profileLoadError) || profileSaving}
                                        onChange={(event) => toggleAlgorithm(entry, event.target.checked)}
                                        aria-label={`Disable ${entry.name} for my solver`}
                                    />
                                    <span>Disable</span>
                                </label>
                            ) : <span className="algorithm-profile-readonly">View only</span>}
                            <button className="dashboard-secondary-button compact" type="button" onClick={() => void copyAlgorithm(entry)}>
                                <Clipboard size={14}/>{copied === entry.name ? "Copied" : "Copy algorithm"}
                            </button>
                            <button className="dashboard-secondary-button compact" type="button" onClick={() => openPreview(entry)}>
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
                            <DeferredCubePreview
                                key={`${previewKey}-${preview.phase}-${preview.name}`}
                                setupAlgorithm={preview.previewSetup ?? ""}
                                algorithm={preview.algorithm}
                                displayMode={playbackMode}
                                isPlaying={previewIsPlaying}
                                onPlaybackComplete={() => {
                                    setPreviewIsPlaying(false);
                                    setPreviewComplete(true);
                                }}
                            />
                        </div>
                        <div className="playback-controls-bottom algorithm-preview-controls" aria-label="Playback controls">
                            <button
                                type="button"
                                className={previewIsPlaying ? "icon-button primary playback-toggle" : "icon-button playback-toggle"}
                                onClick={togglePreviewPlayback}
                                aria-label={previewIsPlaying ? "Pause playback" : "Play playback"}
                                title={previewIsPlaying ? "Pause playback" : "Play playback"}
                            >
                                {previewIsPlaying ? <Pause size={16} aria-hidden="true"/> : <Play size={16} aria-hidden="true"/>}
                            </button>
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
