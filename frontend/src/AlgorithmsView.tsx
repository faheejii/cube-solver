import {Clipboard, LoaderCircle, Pause, Play, Plus, Search, ShieldCheck, Trash2, X} from "lucide-react";
import {useEffect, useMemo, useRef, useState} from "react";
import {
    actOnAlgorithmRevision,
    createAlgorithm,
    createAlgorithmRevision,
    deleteExperimentalAlgorithm,
    fetchAdminSolverProfile,
    fetchAlgorithmDetail,
    fetchAlgorithms,
    updateAdminSolverProfile,
    validateAlgorithmDefinition,
} from "./api";
import {useCubePlaybackMode} from "./CubePreviewModeContext";
import DeferredCubePreview from "./DeferredCubePreview";
import ConfirmationDialog from "./ConfirmationDialog";
import type {
    AlgorithmCatalogEntry,
    AlgorithmDefinitionRequest,
    AlgorithmDetail,
    AlgorithmValidationResult,
    AlgorithmWorkspaceEntry,
    AuthUser,
} from "./types";

const PHASES = ["", "setup", "insert", "oll", "pll"];
const SLOTS = ["", "FR", "FL", "BL", "BR"];
const SUPPORTED_ACTIONS = ["submit", "withdraw", "deprecate", "approve", "request-changes", "restore"] as const;
const ACTION_LABELS: Record<(typeof SUPPORTED_ACTIONS)[number], string> = {
    submit: "Share for testing",
    withdraw: "Withdraw from testing",
    deprecate: "Deprecate",
    approve: "Approve as canonical",
    "request-changes": "Request changes",
    restore: "Restore as canonical",
};
const INITIAL_DEFINITION: AlgorithmDefinitionRequest = {
    phase: "setup",
    name: "",
    algorithm: "",
    notes: "",
    nonPreservedSlot: "FR",
    sourceSetup: "",
};
type WorkflowActionTarget = {
    algorithmId: string;
    revisionId: string;
    revisionNumber: number;
    revisionVersion: number;
    name: string;
    permittedActions: string[];
};

function catalogFilters(selection: string): {status?: string; scope?: string; includeNonCanonical: boolean} {
    if (selection === "disabled") return {status: "", includeNonCanonical: false};
    if (selection === "my-experimental") return {status: "experimental", scope: "my-experimental", includeNonCanonical: true};
    if (selection === "shared-testing") return {status: "test-only", scope: "shared-testing", includeNonCanonical: true};
    if (selection === "review-queue") return {status: "test-only", scope: "review-queue", includeNonCanonical: true};
    if (selection === "deprecated" || selection === "superseded") return {status: selection, includeNonCanonical: true};
    return {status: selection, includeNonCanonical: selection === "all"};
}

export default function AlgorithmsView({currentUser, onAuthorizationFailure}: {
    currentUser: AuthUser;
    onAuthorizationFailure: () => Promise<void>;
}) {
    const playbackMode = useCubePlaybackMode();
    const [entries, setEntries] = useState<AlgorithmWorkspaceEntry[]>([]);
    const [phase, setPhase] = useState("");
    const [slot, setSlot] = useState("");
    const [search, setSearch] = useState("");
    const [committedSearch, setCommittedSearch] = useState("");
    const [searchComposing, setSearchComposing] = useState(false);
    const [status, setStatus] = useState("");
    const [cursor, setCursor] = useState<string | null>(null);
    const [preview, setPreview] = useState<AlgorithmCatalogEntry | null>(null);
    const [previewIsPlaying, setPreviewIsPlaying] = useState(true);
    const [previewComplete, setPreviewComplete] = useState(false);
    const [previewKey, setPreviewKey] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [loadingMore, setLoadingMore] = useState(false);
    const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
    const [catalogReload, setCatalogReload] = useState(0);
    const [catalogNotice, setCatalogNotice] = useState<string | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);
    const [actionPendingId, setActionPendingId] = useState<string | null>(null);
    const [pendingAction, setPendingAction] = useState<{target: WorkflowActionTarget; action: typeof SUPPORTED_ACTIONS[number]; reason: string} | null>(null);
    const [pendingActionError, setPendingActionError] = useState<string | null>(null);
    const [detailId, setDetailId] = useState<string | null>(null);
    const [detail, setDetail] = useState<AlgorithmDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [pendingDelete, setPendingDelete] = useState<{algorithmId: string; name: string; revisionIds: string[]} | null>(null);
    const [deletePending, setDeletePending] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);
    const [authoringOpen, setAuthoringOpen] = useState(false);
    const [authoringTarget, setAuthoringTarget] = useState<{algorithmId: string; expectedVersion: number} | null>(null);
    const [definition, setDefinition] = useState<AlgorithmDefinitionRequest>(INITIAL_DEFINITION);
    const [validatedDefinition, setValidatedDefinition] = useState<AlgorithmDefinitionRequest | null>(null);
    const [validationResult, setValidationResult] = useState<AlgorithmValidationResult | null>(null);
    const [authoringError, setAuthoringError] = useState<string | null>(null);
    const [authoringBusy, setAuthoringBusy] = useState(false);
    const [copied, setCopied] = useState<string | null>(null);
    const [disabledAlgorithms, setDisabledAlgorithms] = useState<string[]>([]);
    const [disabledAlgorithmIds, setDisabledAlgorithmIds] = useState<string[]>([]);
    const [enabledTrialRevisionIds, setEnabledTrialRevisionIds] = useState<string[]>([]);
    const [inactiveTrialRevisionIds, setInactiveTrialRevisionIds] = useState<string[]>([]);
    const [profileLoading, setProfileLoading] = useState(true);
    const [profileLoadError, setProfileLoadError] = useState<string | null>(null);
    const [profileSaving, setProfileSaving] = useState(false);
    const [profileSaveError, setProfileSaveError] = useState<string | null>(null);
    const [profileSaved, setProfileSaved] = useState(false);
    const [profileLoadAttempt, setProfileLoadAttempt] = useState(0);
    const profileSaveLock = useRef(false);
    const searchInputRef = useRef<HTMLInputElement>(null);
    const listRequestId = useRef(0);
    const detailRequestId = useRef(0);
    const loadMoreLock = useRef(false);

    function refreshAfterForbidden(error: unknown): boolean {
        if (typeof error !== "object" || error === null || !("status" in error) || error.status !== 403) return false;
        void onAuthorizationFailure();
        return true;
    }

    useEffect(() => {
        let disposed = false;
        setProfileLoading(true);
        setProfileLoadError(null);
        void fetchAdminSolverProfile()
            .then((profile) => {
                if (!disposed) {
                    setDisabledAlgorithms(profile.disabledAlgorithms);
                    setDisabledAlgorithmIds(profile.disabledAlgorithmIds ?? []);
                    setEnabledTrialRevisionIds(profile.enabledTrialRevisionIds ?? []);
                    setInactiveTrialRevisionIds(profile.inactiveTrialRevisionIds ?? []);
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
        if (searchComposing || search === committedSearch) return;
        const timeout = window.setTimeout(() => setCommittedSearch(search), 300);
        return () => window.clearTimeout(timeout);
    }, [committedSearch, search, searchComposing]);

    useEffect(() => {
        let disposed = false;
        const requestId = ++listRequestId.current;
        loadMoreLock.current = false;
        setEntries([]);
        setCursor(null);
        setLoading(true);
        setError(null);
        setLoadMoreError(null);
        // Disabled is a per-admin profile filter, not a catalog status. The
        // catalog's default response contains canonical entries only.
        void fetchAlgorithms({phase, slot, search: committedSearch, ...catalogFilters(status), limit: 20})
            .then((response) => {
                if (disposed || listRequestId.current !== requestId) return;
                setEntries(response.items);
                setCursor(response.nextCursor);
            })
            .catch((requestError) => {
                if (!disposed && listRequestId.current === requestId) {
                    if (!refreshAfterForbidden(requestError)) {
                        setError(requestError instanceof Error ? requestError.message : "Could not load algorithms");
                    }
                }
            })
            .finally(() => {
                if (!disposed && listRequestId.current === requestId) setLoading(false);
            });
        return () => {
            disposed = true;
            if (listRequestId.current === requestId) listRequestId.current++;
        };
    }, [phase, slot, committedSearch, status, catalogReload]);

    const visibleEntries = useMemo(() => {
        if (status !== "disabled") return entries;
        const disabledIds = new Set(disabledAlgorithmIds);
        return entries.filter((entry) => disabledIds.has(entry.algorithmId)
            || disabledAlgorithms.includes(`${entry.phase}:${entry.name}`));
    }, [disabledAlgorithmIds, disabledAlgorithms, entries, status]);
    const groupedCount = visibleEntries.length;

    async function loadMoreAlgorithms() {
        if (!cursor || loadingMore || loadMoreLock.current) return;
        const requestId = listRequestId.current;
        const nextCursor = cursor;
        loadMoreLock.current = true;
        setLoadingMore(true);
        setLoadMoreError(null);
        try {
            const response = await fetchAlgorithms({
                phase,
                slot,
                search: committedSearch,
                ...catalogFilters(status),
                cursor: nextCursor,
                limit: 20,
            });
            if (listRequestId.current !== requestId) return;
            setEntries((current) => {
                const knownRevisions = new Set(current.map((entry) => entry.revisionId));
                return [...current, ...response.items.filter((entry) => !knownRevisions.has(entry.revisionId))];
            });
            setCursor(response.nextCursor);
        } catch (requestError) {
            if (listRequestId.current === requestId) {
                if (!refreshAfterForbidden(requestError)) {
                    setLoadMoreError(requestError instanceof Error ? requestError.message : "Could not load more algorithms");
                }
            }
        } finally {
            loadMoreLock.current = false;
            if (listRequestId.current === requestId) setLoadingMore(false);
        }
    }

    async function openDetail(algorithmId: string) {
        const requestId = ++detailRequestId.current;
        setDetailId(algorithmId);
        setDetail(null);
        setDetailLoading(true);
        setDetailError(null);
        try {
            const response = await fetchAlgorithmDetail(algorithmId);
            if (detailRequestId.current === requestId) setDetail(response);
        } catch (requestError) {
            if (detailRequestId.current === requestId && !refreshAfterForbidden(requestError)) {
                setDetailError(requestError instanceof Error ? requestError.message : "Could not load revision history");
            }
        } finally {
            if (detailRequestId.current === requestId) setDetailLoading(false);
        }
    }

    async function refreshDetailIfOpen(algorithmId: string) {
        if (detailId !== algorithmId) return;
        try {
            setDetail(await fetchAlgorithmDetail(algorithmId));
            setDetailError(null);
        } catch (requestError) {
            if (!refreshAfterForbidden(requestError)) {
                setDetailError(requestError instanceof Error ? requestError.message : "Could not refresh revision history");
            }
        }
    }

    async function permanentlyDeleteAlgorithm() {
        if (!pendingDelete || deletePending) return;
        const target = pendingDelete;
        setDeletePending(true);
        setDeleteError(null);
        try {
            await deleteExperimentalAlgorithm(target.algorithmId);
            const deletedRevisions = new Set(target.revisionIds);
            setEnabledTrialRevisionIds((current) => current.filter((revisionId) => !deletedRevisions.has(revisionId)));
            setInactiveTrialRevisionIds((current) => current.filter((revisionId) => !deletedRevisions.has(revisionId)));
            setPendingDelete(null);
            setDetailId(null);
            setDetail(null);
            setCatalogNotice(`Deleted ${target.name} and its experimental revision history.`);
            setCatalogReload((value) => value + 1);
            window.setTimeout(() => searchInputRef.current?.focus(), 0);
        } catch (requestError) {
            if (refreshAfterForbidden(requestError)) {
                setPendingDelete(null);
            } else {
                setDeleteError(requestError instanceof Error ? requestError.message : "Could not delete this algorithm.");
            }
        } finally {
            setDeletePending(false);
        }
    }

    function requestWorkflowAction(target: WorkflowActionTarget, action: string) {
        if (!target.permittedActions.includes(action) || !SUPPORTED_ACTIONS.includes(action as typeof SUPPORTED_ACTIONS[number])) return;
        const supportedAction = action as typeof SUPPORTED_ACTIONS[number];
        if (["approve", "deprecate", "request-changes", "restore"].includes(action)) {
            setPendingAction({target, action: supportedAction, reason: ""});
            setPendingActionError(null);
            return;
        }
        void performWorkflowAction(target, supportedAction);
    }

    async function performWorkflowAction(target: WorkflowActionTarget, action: string, reason?: string) {
        if (!target.permittedActions.includes(action) || actionPendingId) return;
        setActionPendingId(target.revisionId);
        setActionError(null);
        setPendingActionError(null);
        try {
            await actOnAlgorithmRevision(target.algorithmId, target.revisionId, action, target.revisionVersion, reason);
            setPendingAction(null);
            setCatalogNotice(`${ACTION_LABELS[action as keyof typeof ACTION_LABELS]} completed for ${target.name} revision ${target.revisionNumber}.`);
            setCatalogReload((value) => value + 1);
            void refreshDetailIfOpen(target.algorithmId);
        } catch (requestError) {
            const message = requestError instanceof Error ? requestError.message : "Could not update the algorithm revision.";
            if (requestError instanceof Error && "status" in requestError && requestError.status === 409) {
                setPendingAction(null);
                setCatalogNotice("This revision changed while you were reviewing it. The catalog is refreshing; review its current status before trying again.");
                setCatalogReload((value) => value + 1);
                void refreshDetailIfOpen(target.algorithmId);
            } else if (refreshAfterForbidden(requestError)) {
                setPendingAction(null);
            } else if (pendingAction?.target.revisionId === target.revisionId) {
                setPendingActionError(message);
            } else {
                setActionError(message);
            }
        } finally {
            setActionPendingId(null);
        }
    }

    function updateDefinition<K extends keyof AlgorithmDefinitionRequest>(key: K, value: AlgorithmDefinitionRequest[K]) {
        setDefinition((current) => ({...current, [key]: value}));
        setValidationResult(null);
        setValidatedDefinition(null);
        setAuthoringError(null);
    }

    function definitionPayload(): AlgorithmDefinitionRequest {
        const base = {
            phase: definition.phase,
            name: definition.name.trim(),
            algorithm: definition.algorithm.trim(),
            notes: definition.notes.trim(),
        };
        if (definition.phase === "setup") {
            return {...base, nonPreservedSlot: definition.nonPreservedSlot ?? "", sourceSetup: definition.sourceSetup ?? ""};
        }
        if (definition.phase === "insert") return {...base, slot: definition.slot ?? ""};
        return base;
    }

    async function validateDefinition() {
        if (authoringBusy) return;
        setAuthoringBusy(true);
        setAuthoringError(null);
        setValidationResult(null);
        setValidatedDefinition(null);
        try {
            const result = await validateAlgorithmDefinition(definitionPayload());
            setValidationResult(result);
            setValidatedDefinition({
                phase: result.phase as AlgorithmDefinitionRequest["phase"],
                name: result.name,
                algorithm: result.algorithm,
                notes: definition.notes.trim(),
                ...(result.slot ? {slot: result.slot} : {}),
                ...(result.nonPreservedSlot ? {nonPreservedSlot: result.nonPreservedSlot} : {}),
                ...(result.sourceSetup ? {sourceSetup: result.sourceSetup} : {}),
            });
        } catch (requestError) {
            if (!refreshAfterForbidden(requestError)) {
                setAuthoringError(requestError instanceof Error ? requestError.message : "Could not validate this definition.");
            }
        } finally {
            setAuthoringBusy(false);
        }
    }

    async function saveDefinition() {
        if (!validatedDefinition || authoringBusy) return;
        setAuthoringBusy(true);
        setAuthoringError(null);
        try {
            const created = authoringTarget
                ? await createAlgorithmRevision(authoringTarget.algorithmId, validatedDefinition, authoringTarget.expectedVersion)
                : await createAlgorithm(validatedDefinition);
            setAuthoringOpen(false);
            setAuthoringTarget(null);
            setCatalogNotice(authoringTarget
                ? `Saved experimental revision ${created.revisionNumber}. It is not enabled for solver use.`
                : `Created ${created.algorithmId} as experimental revision ${created.revisionNumber}. It is not enabled for solver use.`);
            setPhase(validatedDefinition.phase);
            setSlot("");
            setSearch(validatedDefinition.name);
            setCommittedSearch(validatedDefinition.name);
            setStatus("experimental");
            void openDetail(created.algorithmId);
            setCatalogReload((value) => value + 1);
        } catch (requestError) {
            if (typeof requestError === "object" && requestError !== null && "status" in requestError
                && requestError.status === 409 && authoringTarget) {
                setAuthoringError("This revision changed while you were editing. Your draft is still here; review the latest revision before trying again.");
                void refreshDetailIfOpen(authoringTarget.algorithmId);
            } else if (!refreshAfterForbidden(requestError)) {
                setAuthoringError(requestError instanceof Error ? requestError.message : "Could not create this algorithm.");
            }
        } finally {
            setAuthoringBusy(false);
        }
    }

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

    async function saveSolverProfile(nextDisabledIds: string[], nextTrialRevisionIds = enabledTrialRevisionIds) {
        if (profileSaveLock.current) return;
        profileSaveLock.current = true;
        setProfileSaving(true);
        setProfileSaveError(null);
        setProfileSaved(false);
        try {
            const saved = await updateAdminSolverProfile({
                disabledAlgorithmIds: nextDisabledIds,
                enabledTrialRevisionIds: nextTrialRevisionIds,
            });
            setDisabledAlgorithms(saved.disabledAlgorithms);
            setDisabledAlgorithmIds(saved.disabledAlgorithmIds ?? []);
            setEnabledTrialRevisionIds(saved.enabledTrialRevisionIds ?? []);
            setInactiveTrialRevisionIds(saved.inactiveTrialRevisionIds ?? []);
            setProfileSaved(true);
        } catch (requestError) {
            if (!refreshAfterForbidden(requestError)) {
                setProfileSaveError(requestError instanceof Error ? requestError.message : "Could not save your solver profile.");
            }
        } finally {
            profileSaveLock.current = false;
            setProfileSaving(false);
        }
    }

    function toggleAlgorithm(entry: AlgorithmWorkspaceEntry, checked: boolean) {
        if (entry.status !== "canonical" || profileLoading || profileLoadError || profileSaving || profileSaveLock.current) return;
        const id = entry.algorithmId;
        const next = checked
            ? [...new Set([...disabledAlgorithmIds, id])]
            : disabledAlgorithmIds.filter((disabledId) => disabledId !== id);
        setDisabledAlgorithmIds(next);
        void saveSolverProfile(next);
    }

    function toggleTrial(entry: AlgorithmWorkspaceEntry, checked: boolean) {
        if (!entry.trialEligible || profileLoading || profileLoadError || profileSaving || profileSaveLock.current) return;
        const next = checked
            ? [...new Set([...enabledTrialRevisionIds, entry.revisionId])]
            : enabledTrialRevisionIds.filter((revisionId) => revisionId !== entry.revisionId);
        setEnabledTrialRevisionIds(next);
        void saveSolverProfile(disabledAlgorithmIds, next);
    }

    function removeInactiveTrial(revisionId: string) {
        if (profileSaving || profileLoadError) return;
        const next = enabledTrialRevisionIds.filter((id) => id !== revisionId);
        setEnabledTrialRevisionIds(next);
        void saveSolverProfile(disabledAlgorithmIds, next);
    }

    return (
        <section className="dashboard-algorithms-view">
            <header className="algorithms-view-header">
                <div>
                    <h1>Algorithms</h1>
                    <p>Browse solver cases. Disable canonical algorithms to test your own solves; the profile only affects your account.</p>
                </div>
                <div className="algorithms-header-actions">
                <span className="algorithms-count">{groupedCount} shown</span>
                    <button className="dashboard-primary-button compact" type="button" onClick={() => {
                        setAuthoringTarget(null);
                        setDefinition(INITIAL_DEFINITION);
                        setValidatedDefinition(null);
                        setValidationResult(null);
                        setAuthoringError(null);
                        setAuthoringOpen(true);
                    }}><Plus size={15}/> New algorithm</button>
                </div>
            </header>

            <div className="algorithms-filters">
                <label className="algorithms-search">
                    <Search size={16}/>
                    <input
                        ref={searchInputRef}
                        value={search}
                        onChange={(event) => setSearch(event.target.value)}
                        onCompositionStart={() => setSearchComposing(true)}
                        onCompositionEnd={(event) => {
                            setSearchComposing(false);
                            setSearch(event.currentTarget.value);
                        }}
                        placeholder="Search cases or moves"
                        aria-label="Search algorithms"
                    />
                    {search ? <button className="icon-button" type="button" aria-label="Clear algorithm search" onClick={() => {
                        setSearch("");
                        setCommittedSearch("");
                    }}><X size={15}/></button> : null}
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
                    <option value="my-experimental">My experimental</option>
                    <option value="shared-testing">Shared testing</option>
                    {currentUser.role === "admin" ? <option value="review-queue">Review queue</option> : null}
                    <option value="deprecated">Deprecated</option>
                    <option value="superseded">Superseded</option>
                    <option value="disabled">Disabled only</option>
                    <option value="all">All visible revisions</option>
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
                {!profileLoading && !profileLoadError && inactiveTrialRevisionIds.length > 0 ? (
                    <span>Inactive trial selections are excluded from new solves: {inactiveTrialRevisionIds.map((id) => (
                        <span className="algorithm-inactive-trial" key={id}>
                            <code>{id.slice(0, 8)}</code>
                            <button className="algorithm-detail-link" type="button" disabled={profileSaving}
                                onClick={() => removeInactiveTrial(id)} aria-label={`Remove inactive trial revision ${id}`}>
                                Remove
                            </button>
                        </span>
                    ))}</span>
                ) : null}
                {!profileLoading && !profileLoadError && profileSaving ? <span>Saving solver profile…</span> : null}
                {!profileLoading && !profileLoadError && profileSaveError ? (
                    <>
                        <span role="alert">Changes were not saved. Your selection is kept here. {profileSaveError}</span>
                        <button className="dashboard-secondary-button compact" type="button" onClick={() => void saveSolverProfile(disabledAlgorithmIds, enabledTrialRevisionIds)}>Retry</button>
                    </>
                ) : null}
                {!profileLoading && !profileLoadError && !profileSaving && !profileSaveError && profileSaved ? <span>Solver profile saved to your account.</span> : null}
            </div>

            {error ? <div className="dashboard-alert error">{error}</div> : null}
            {catalogNotice ? <div className="dashboard-alert success" role="status">{catalogNotice}</div> : null}
            {actionError ? <div className="dashboard-alert error" role="alert">{actionError}</div> : null}
            {loading ? <div className="history-loading"><LoaderCircle size={20}/> Loading algorithms</div> : null}
            {!loading && !error && visibleEntries.length === 0
                && !(status === "disabled" && (profileLoading || profileLoadError)) ? (
                <div className="history-empty-state"><strong>No matching algorithms</strong><span>Try a different phase, slot, search, or status.</span></div>
            ) : null}
            <div className="algorithm-catalog-list">
                {visibleEntries.map((entry) => (
                    <article className="algorithm-catalog-row" key={entry.revisionId}>
                        <div className="algorithm-row-case">
                            <div>
                                <span className="algorithm-phase">{entry.phase}</span>
                                <h2><button className="algorithm-detail-link" type="button" onClick={() => void openDetail(entry.algorithmId)}>{entry.name}</button></h2>
                            </div>
                            <span className="algorithm-slot">{entry.slot ?? entry.nonPreservedSlot ?? entry.phase.toUpperCase()}</span>
                        </div>
                        <code className="algorithm-moves">{entry.algorithm}</code>
                        <div className="algorithm-row-meta">
                            <span className="algorithm-status">{entry.status}</span>
                            <span>Preserves {entry.preservedSlots.length ? entry.preservedSlots.join(" ") : "none"}</span>
                        </div>
                        <div className="algorithm-row-details">
                            <button className="algorithm-detail-link" type="button" onClick={() => void openDetail(entry.algorithmId)}>Details · r{entry.revisionNumber}</button>
                            <div><span>{signatureSummary(entry)}</span>{entry.sourceSetup ? <code>Source setup: {entry.sourceSetup}</code> : null}</div>
                        </div>
                        <div className="algorithm-row-actions">
                            {entry.status === "canonical" ? (
                                <label className="algorithm-profile-toggle">
                                    <input
                                        type="checkbox"
                                        checked={disabledAlgorithmIds.includes(entry.algorithmId)
                                            || disabledAlgorithms.includes(`${entry.phase}:${entry.name}`)}
                                        disabled={profileLoading || Boolean(profileLoadError) || profileSaving}
                                        onChange={(event) => toggleAlgorithm(entry, event.target.checked)}
                                        aria-label={`Disable ${entry.name} for my solver`}
                                    />
                                    <span>Disable</span>
                                </label>
                            ) : entry.trialEligible ? (
                                <label className="algorithm-profile-toggle">
                                    <input
                                        type="checkbox"
                                        checked={enabledTrialRevisionIds.includes(entry.revisionId)}
                                        disabled={profileLoading || Boolean(profileLoadError) || profileSaving}
                                        onChange={(event) => toggleTrial(entry, event.target.checked)}
                                        aria-label={`Enable revision ${entry.revisionNumber} of ${entry.name} for my solver`}
                                    />
                                    <span>Use for my solves</span>
                                </label>
                            ) : <span className="algorithm-profile-readonly">View only</span>}
                                        {SUPPORTED_ACTIONS.filter((action) => entry.permittedActions.includes(action)).map((action) => (
                                <button
                                    key={action}
                                    className={action === "deprecate" ? "dashboard-secondary-button compact danger-text" : "dashboard-secondary-button compact"}
                                    type="button"
                                    disabled={actionPendingId === entry.revisionId}
                                    onClick={() => requestWorkflowAction(entry, action)}
                                >{actionPendingId === entry.revisionId ? <LoaderCircle size={14}/> : null}{ACTION_LABELS[action]}</button>
                            ))}
                            <button
                                className="icon-button"
                                type="button"
                                onClick={() => void copyAlgorithm(entry)}
                                aria-label={copied === entry.name ? "Copied" : "Copy algorithm"}
                                title={copied === entry.name ? "Copied" : "Copy algorithm"}
                            >
                                <Clipboard size={15}/>
                            </button>
                            <button
                                className="icon-button"
                                type="button"
                                onClick={() => openPreview(entry)}
                                aria-label="Test on cube"
                                title="Test on cube"
                            >
                                <Play size={15}/>
                            </button>
                        </div>
                    </article>
                ))}
            </div>
            {loadMoreError ? <div className="dashboard-alert error" role="alert">{loadMoreError}</div> : null}
            {cursor ? <div className="algorithm-load-more">
                <button className="dashboard-secondary-button" type="button" disabled={loadingMore} onClick={() => void loadMoreAlgorithms()}>
                    {loadingMore ? <><LoaderCircle size={15}/> Loading…</> : "Load more"}
                </button>
                {loadMoreError ? <button className="dashboard-secondary-button compact" type="button" disabled={loadingMore} onClick={() => void loadMoreAlgorithms()}>Retry</button> : null}
            </div> : null}
            {detailId ? (
                <div className="algorithm-preview-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setDetailId(null); }}>
                    <section className="algorithm-preview-dialog algorithm-governance-dialog" role="dialog" aria-modal="true" aria-label="Algorithm details">
                        <div className="algorithm-preview-header">
                            <div><span className="algorithm-phase">{detail?.phase ?? "Algorithm"}</span><h2>{detail?.name ?? "Algorithm details"}</h2></div>
                            <button className="dashboard-secondary-button compact" type="button" onClick={() => setDetailId(null)}>Close</button>
                        </div>
                        {detailLoading ? <div className="history-loading"><LoaderCircle size={18}/> Loading revisions…</div> : null}
                        {detailError ? <div className="dashboard-alert error" role="alert">{detailError}<button className="dashboard-secondary-button compact" type="button" onClick={() => void openDetail(detailId)}>Retry</button></div> : null}
                        {detail ? <>
                            <p className="algorithm-governance-summary">Algorithm ID <code>{detail.id}</code>{detail.activeCanonicalRevisionId ? <> · Active canonical revision <code>{detail.activeCanonicalRevisionId}</code></> : " · No active canonical revision"}</p>
                            {detail.canCreateRevision || detail.canDeleteExperimentalAlgorithm ? <div className="algorithm-detail-header-actions">
                                {detail.canCreateRevision ? <button className="dashboard-primary-button compact" type="button" onClick={() => {
                                    const latest = detail.revisions[0];
                                    const source = latest ? parseAlgorithmDefinition(latest.definitionJson) : null;
                                    if (!source) {
                                        setDetailError("The latest revision could not be loaded for editing.");
                                        return;
                                    }
                                    setAuthoringTarget({algorithmId: detail.id, expectedVersion: latest.version});
                                    setDefinition(source);
                                    setValidationResult(null);
                                    setValidatedDefinition(null);
                                    setAuthoringError(null);
                                    setAuthoringOpen(true);
                                }}>Create experimental revision</button> : null}
                                {detail.canDeleteExperimentalAlgorithm ? <button className="dashboard-secondary-button compact danger-text" type="button" onClick={() => {
                                    setDeleteError(null);
                                    setPendingDelete({algorithmId: detail.id, name: detail.name,
                                        revisionIds: detail.revisions.map((revision) => revision.id)});
                                }}><Trash2 size={15}/> Delete experimental algorithm</button> : null}
                            </div> : null}
                            {(() => {
                                const canonical = detail.revisions.find((revision) => revision.id === detail.activeCanonicalRevisionId);
                                const pending = detail.revisions.find((revision) => revision.status === "test-only"
                                    || revision.status === "experimental");
                                const canonicalDefinition = canonical ? parseAlgorithmDefinition(canonical.definitionJson) : null;
                                const pendingDefinition = pending ? parseAlgorithmDefinition(pending.definitionJson) : null;
                                if (!canonical || !pending || canonical.id === pending.id || !canonicalDefinition || !pendingDefinition) return null;
                                return <section className="algorithm-revision-comparison" aria-label="Pending revision comparison">
                                    <h3>Pending revision compared with active canonical</h3>
                                    {[{revision: canonical, definition: canonicalDefinition, label: "Active canonical"},
                                        {revision: pending, definition: pendingDefinition, label: "Pending revision"}].map(({revision, definition: candidate, label}) => {
                                        const metrics = algorithmMetrics(candidate.algorithm);
                                        return <article key={revision.id}>
                                            <h4>{label} · r{revision.revisionNumber} · {revision.status}</h4>
                                            <p>{metrics.moves} moves · {metrics.rotations} rotations · Author #{revision.authorUserId ?? "system"}</p>
                                            <code className="algorithm-moves">{candidate.algorithm}</code>
                                            <p>{candidate.notes || "No notes"}</p>
                                        </article>;
                                    })}
                                </section>;
                            })()}
                            <h3>Revisions</h3>
                            <div className="algorithm-revision-list">
                                {detail.revisions.map((revision) => {
                                    const parsed = parseAlgorithmDefinition(revision.definitionJson);
                                    const permittedRevisionActions = revision.permittedActions ?? [];
                                    return <article className="algorithm-revision-row" key={revision.id}>
                                        <div className="algorithm-revision-heading"><strong>Revision {revision.revisionNumber}</strong><span className="algorithm-status">{revision.status}</span><span>{revision.id === detail.activeCanonicalRevisionId ? "Active" : ""}</span></div>
                                        {parsed ? <><code className="algorithm-moves">{parsed.algorithm}</code>{parsed.notes ? <p>{parsed.notes}</p> : null}</> : <p>Definition details are unavailable.</p>}
                                        {permittedRevisionActions.length ? <div className="algorithm-row-actions">
                                            {SUPPORTED_ACTIONS.filter((action) => permittedRevisionActions.includes(action)).map((action) => <button className="dashboard-secondary-button compact" type="button" key={action} onClick={() => requestWorkflowAction({
                                                algorithmId: detail.id,
                                                revisionId: revision.id,
                                                revisionNumber: revision.revisionNumber,
                                                revisionVersion: revision.version,
                                                name: detail.name,
                                                permittedActions: permittedRevisionActions,
                                            }, action)}>{ACTION_LABELS[action]}</button>)}
                                        </div> : null}
                                    </article>;
                                })}
                            </div>
                            <h3>Workflow history</h3>
                            {detail.history.length ? <ol className="algorithm-workflow-history">{detail.history.map((event, index) => {
                                const revision = detail.revisions.find((candidate) => candidate.id === event.revisionId);
                                return <li key={`${event.revisionId}-${event.occurredAt}-${index}`}>
                                <strong>{event.action.replace(/_/g, " ")}</strong>
                                <span>Revision {revision?.revisionNumber ?? event.revisionId}</span>
                                <span>{event.previousStatus ?? "—"} → {event.newStatus ?? "—"}</span>
                                <span>{event.actorUserId == null ? "System" : `Account #${event.actorUserId}`}</span>
                                <time>{new Date(event.occurredAt).toLocaleString()}</time>{event.reason ? <p>{event.reason}</p> : null}
                            </li>;})}</ol> : <p>No workflow events yet.</p>}
                        </> : null}
                    </section>
                </div>
            ) : null}
            {authoringOpen ? (
                <div className="algorithm-preview-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !authoringBusy) setAuthoringOpen(false); }}>
                    <section className="algorithm-preview-dialog algorithm-governance-dialog" role="dialog" aria-modal="true" aria-labelledby="algorithm-authoring-title">
                        <div className="algorithm-preview-header"><div><span className="algorithm-phase">Experimental</span><h2 id="algorithm-authoring-title">{authoringTarget ? "New revision" : "New algorithm"}</h2></div><button className="dashboard-secondary-button compact" type="button" disabled={authoringBusy} onClick={() => { setAuthoringOpen(false); setAuthoringTarget(null); }}>Close</button></div>
                        <p>{authoringTarget ? "This creates a new immutable experimental revision. The current canonical revision stays active until an admin approves this exact revision." : "New algorithms start as experimental and are not used in solves automatically."}</p>
                        <div className="algorithm-authoring-fields">
                            <label>Phase<select value={definition.phase} onChange={(event) => updateDefinition("phase", event.target.value as AlgorithmDefinitionRequest["phase"])}><option value="setup">F2L setup</option><option value="insert">F2L insert</option><option value="oll">OLL</option><option value="pll">PLL</option></select></label>
                            <label>Name<input value={definition.name} onChange={(event) => updateDefinition("name", event.target.value)} maxLength={120}/></label>
                            {definition.phase === "insert" ? <label>Target slot<select value={definition.slot ?? "FR"} onChange={(event) => updateDefinition("slot", event.target.value)}>{SLOTS.slice(1).map((value) => <option key={value}>{value}</option>)}</select></label> : null}
                            {definition.phase === "setup" ? <>
                                <label>Non-preserved slot<select value={definition.nonPreservedSlot ?? "FR"} onChange={(event) => updateDefinition("nonPreservedSlot", event.target.value)}>{SLOTS.slice(1).map((value) => <option key={value}>{value}</option>)}</select></label>
                                <label>Source setup<input value={definition.sourceSetup ?? ""} onChange={(event) => updateDefinition("sourceSetup", event.target.value)}/></label>
                            </> : null}
                            <label>Algorithm<textarea value={definition.algorithm} onChange={(event) => updateDefinition("algorithm", event.target.value)} rows={3}/></label>
                            <label>Notes<textarea value={definition.notes} onChange={(event) => updateDefinition("notes", event.target.value)} rows={2}/></label>
                        </div>
                        {authoringError ? <div className="dashboard-alert error" role="alert">{authoringError}</div> : null}
                        {validationResult ? <div className="dashboard-alert success" role="status"><ShieldCheck size={16}/>{validationResult.validatorNote || "Definition is valid."}</div> : null}
                        <div className="algorithm-row-actions">
                            <button className="dashboard-secondary-button" type="button" disabled={authoringBusy} onClick={() => void validateDefinition()}>{authoringBusy ? "Checking…" : "Validate"}</button>
                            <button className="dashboard-primary-button" type="button" disabled={!validatedDefinition || authoringBusy} onClick={() => void saveDefinition()}>{authoringBusy ? "Saving…" : authoringTarget ? "Create experimental revision" : "Create experimental"}</button>
                        </div>
                    </section>
                </div>
            ) : null}
            {pendingAction ? <ConfirmationDialog
                title={pendingAction.action === "approve" ? "Approve algorithm?"
                    : pendingAction.action === "deprecate" ? "Deprecate algorithm?"
                        : pendingAction.action === "restore" ? "Restore algorithm?" : "Request changes?"}
                description={pendingAction.action === "approve"
                    ? `Approve ${pendingAction.target.name} revision ${pendingAction.target.revisionNumber} as canonical for solver use.`
                    : pendingAction.action === "deprecate"
                        ? `Deprecate ${pendingAction.target.name} revision ${pendingAction.target.revisionNumber}. New solves will no longer use it.`
                        : pendingAction.action === "restore"
                            ? `Restore ${pendingAction.target.name} revision ${pendingAction.target.revisionNumber} as canonical for new solves.`
                            : `Return ${pendingAction.target.name} revision ${pendingAction.target.revisionNumber} to its author for changes. Add a reason so the author knows what to address.`}
                confirmLabel={ACTION_LABELS[pendingAction.action]}
                tone={pendingAction.action === "deprecate" ? "danger" : "neutral"}
                pending={actionPendingId === pendingAction.target.revisionId}
                confirmDisabled={pendingAction.action === "request-changes" && !pendingAction.reason.trim()}
                error={pendingActionError}
                onCancel={() => setPendingAction(null)}
                onConfirm={() => void performWorkflowAction(pendingAction.target, pendingAction.action, pendingAction.reason)}
            >
                {pendingAction.action === "request-changes" ? <label className="confirmation-dialog-reason">
                    Reason<textarea required autoFocus rows={3} value={pendingAction.reason}
                        onChange={(event) => setPendingAction((current) => current ? {...current, reason: event.target.value} : null)}
                        placeholder="Explain the changes needed"/>
                </label> : pendingAction.action === "approve" || pendingAction.action === "deprecate" || pendingAction.action === "restore" ? <label className="confirmation-dialog-reason">
                    Reason (optional)<textarea rows={2} value={pendingAction.reason}
                        onChange={(event) => setPendingAction((current) => current ? {...current, reason: event.target.value} : null)}
                        placeholder="Add a note to the workflow history"/>
                </label> : null}
            </ConfirmationDialog> : null}
            {pendingDelete ? <ConfirmationDialog
                title="Delete experimental algorithm?"
                description={`Permanently delete ${pendingDelete.name}, all ${pendingDelete.revisionIds.length} private revisions, and their workflow history. This cannot be undone, and these revisions cannot be restored or used as a starting point later.`}
                confirmLabel="Delete permanently"
                pendingLabel="Deleting…"
                cancelLabel="Keep algorithm"
                tone="danger"
                pending={deletePending}
                error={deleteError}
                onCancel={() => { setPendingDelete(null); setDeleteError(null); }}
                onConfirm={() => void permanentlyDeleteAlgorithm()}
            /> : null}
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
    if (!entry.signature) return "Not indexed for solver use";
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

function parseAlgorithmDefinition(value: string): AlgorithmDefinitionRequest | null {
    try {
        const parsed = JSON.parse(value) as Partial<AlgorithmDefinitionRequest>;
        if (!parsed.phase || !parsed.name || typeof parsed.algorithm !== "string") return null;
        return parsed as AlgorithmDefinitionRequest;
    } catch {
        return null;
    }
}

function algorithmMetrics(algorithm: string): {moves: number; rotations: number} {
    const tokens = algorithm.trim().split(/\s+/).filter(Boolean);
    const rotations = tokens.filter((token) => /^[xyz](?:2|')?$/.test(token)).length;
    return {moves: tokens.length - rotations, rotations};
}
