import {LoaderCircle, Search, ShieldCheck, UserRoundCog, X} from "lucide-react";
import {useCallback, useEffect, useRef, useState} from "react";
import {ApiError, fetchAdminUsers, updateAdminUserRole, type AdminManagedUser} from "./api";
import ConfirmationDialog from "./ConfirmationDialog";
import type {AuthUser, UserRole} from "./types";

const ROLE_OPTIONS: {value: UserRole; label: string}[] = [
    {value: "user", label: "User"},
    {value: "maintainer", label: "Maintainer"},
    {value: "admin", label: "Admin"},
];

type Props = {
    currentUser: AuthUser;
    onAuthorizationFailure: () => Promise<void>;
};

type PendingRoleChange = {user: AdminManagedUser; role: UserRole};

export default function UserManagementView({currentUser, onAuthorizationFailure}: Props) {
    const [query, setQuery] = useState("");
    const [committedQuery, setCommittedQuery] = useState("");
    const [composing, setComposing] = useState(false);
    const [items, setItems] = useState<AdminManagedUser[]>([]);
    const [selectedRoles, setSelectedRoles] = useState<Record<string, UserRole>>({});
    const [cursor, setCursor] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [loadingMore, setLoadingMore] = useState(false);
    const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
    const [retryCount, setRetryCount] = useState(0);
    const [notice, setNotice] = useState<string | null>(null);
    const [pendingChange, setPendingChange] = useState<PendingRoleChange | null>(null);
    const [changePending, setChangePending] = useState(false);
    const [changeError, setChangeError] = useState<string | null>(null);
    const requestSequence = useRef(0);
    const loadMoreLock = useRef(false);
    const authFailureHandler = useRef(onAuthorizationFailure);
    authFailureHandler.current = onAuthorizationFailure;

    useEffect(() => {
        if (composing || query === committedQuery) return;
        const timeout = window.setTimeout(() => setCommittedQuery(query), 300);
        return () => window.clearTimeout(timeout);
    }, [committedQuery, composing, query]);

    const notifyAuthorizationFailure = useCallback((error: unknown) => {
        if (error instanceof ApiError && error.status === 403) {
            void authFailureHandler.current().catch(() => undefined);
            return "You no longer have permission to manage accounts. Your access is being refreshed.";
        }
        return error instanceof Error ? error.message : "Could not load accounts.";
    }, []);

    useEffect(() => {
        const sequence = ++requestSequence.current;
        loadMoreLock.current = false;
        setItems([]);
        setCursor(null);
        setLoading(true);
        setLoadError(null);
        setLoadMoreError(null);
        setSelectedRoles({});

        void fetchAdminUsers(committedQuery)
            .then((response) => {
                if (requestSequence.current !== sequence) return;
                setItems(response.items);
                setCursor(response.nextCursor);
            })
            .catch((error: unknown) => {
                if (requestSequence.current !== sequence) return;
                setLoadError(notifyAuthorizationFailure(error));
            })
            .finally(() => {
                if (requestSequence.current === sequence) setLoading(false);
            });

        return () => {
            if (requestSequence.current === sequence) requestSequence.current++;
        };
    }, [committedQuery, notifyAuthorizationFailure, retryCount]);

    function clearSearch() {
        setNotice(null);
        setQuery("");
        setCommittedQuery("");
        document.getElementById("admin-user-search")?.focus();
    }

    async function loadMoreUsers() {
        if (!cursor || loadingMore || loadMoreLock.current) return;
        const sequence = requestSequence.current;
        const requestedCursor = cursor;
        loadMoreLock.current = true;
        setLoadingMore(true);
        setLoadMoreError(null);
        try {
            const response = await fetchAdminUsers(committedQuery, requestedCursor);
            if (requestSequence.current !== sequence) return;
            setItems((current) => {
                const existingIds = new Set(current.map((item) => item.id));
                return [...current, ...response.items.filter((item) => !existingIds.has(item.id))];
            });
            setCursor(response.nextCursor);
        } catch (error) {
            if (requestSequence.current === sequence) {
                setLoadMoreError(notifyAuthorizationFailure(error));
            }
        } finally {
            loadMoreLock.current = false;
            if (requestSequence.current === sequence) setLoadingMore(false);
        }
    }

    function requestRoleChange(user: AdminManagedUser) {
        const role = selectedRoles[user.id] ?? user.role;
        if (role === user.role || user.protected || user.externalId === currentUser.id) return;
        setChangeError(null);
        setPendingChange({user, role});
    }

    async function confirmRoleChange() {
        if (!pendingChange || changePending) return;
        setChangePending(true);
        setChangeError(null);
        try {
            const updatedUser = await updateAdminUserRole(
                pendingChange.user.id,
                pendingChange.role,
                pendingChange.user.updatedAt,
            );
            setItems((current) => current.map((item) => item.id === updatedUser.id ? updatedUser : item));
            setSelectedRoles((current) => {
                const next = {...current};
                delete next[updatedUser.id];
                return next;
            });
            setPendingChange(null);
        } catch (error) {
            const message = notifyAuthorizationFailure(error);
            if (error instanceof ApiError && error.status === 409) {
                setPendingChange(null);
                setNotice("This account changed while you were reviewing it. The list is refreshing; check its current role before trying again.");
                setRetryCount((count) => count + 1);
            } else {
                setChangeError(message);
            }
        } finally {
            setChangePending(false);
        }
    }

    const confirmationName = pendingChange?.user.displayName || pendingChange?.user.email || "this account";
    const currentRoleLabel = pendingChange ? roleLabel(pendingChange.user.role) : "";
    const nextRoleLabel = pendingChange ? roleLabel(pendingChange.role) : "";

    return (
        <section className="dashboard-admin-users-view" aria-labelledby="admin-users-title">
            <header className="admin-users-header">
                <div>
                    <p className="section-label">Access</p>
                    <h1 id="admin-users-title">User management</h1>
                    <p>Assign account access. Role changes apply to new requests after the server confirms them.</p>
                </div>
                <span className="admin-users-header-mark" aria-hidden="true"><UserRoundCog size={22}/></span>
            </header>

            <div className="admin-users-toolbar">
                <div className="admin-users-search">
                    <Search size={17} aria-hidden="true"/>
                    <input
                        id="admin-user-search"
                        type="text"
                        aria-label="Search name or email"
                        value={query}
                        onChange={(event) => {
                            setNotice(null);
                            setQuery(event.target.value);
                        }}
                        onCompositionStart={() => setComposing(true)}
                        onCompositionEnd={(event) => {
                            setNotice(null);
                            setComposing(false);
                            setQuery(event.currentTarget.value);
                        }}
                        placeholder="Search name or email"
                        autoComplete="off"
                        aria-describedby="admin-user-search-note"
                    />
                    {query ? (
                        <button className="admin-users-clear" type="button" aria-label="Clear user search" onClick={clearSearch}>
                            <X size={16}/>
                        </button>
                    ) : null}
                </div>
                <span id="admin-user-search-note" className="admin-users-search-note">Search is private to this view.</span>
            </div>

            {loadError ? (
                <div className="dashboard-alert error" role="alert">
                    <span>{loadError}</span>
                    <button className="dashboard-secondary-button compact" type="button" onClick={() => setRetryCount((count) => count + 1)}>Retry</button>
                </div>
            ) : null}

            {notice ? <div className="admin-users-notice" role="status">{notice}</div> : null}

            <div className="admin-users-list" aria-busy={loading}>
                {loading ? (
                    <div className="history-loading" role="status"><LoaderCircle size={20}/> Loading accounts</div>
                ) : null}
                {!loading && !loadError && items.length === 0 ? (
                    <div className="history-empty-state">
                        <strong>{committedQuery ? "No matching accounts" : "No accounts to show"}</strong>
                        <span>{committedQuery ? "Try another name or email address." : "Accounts will appear here when available."}</span>
                    </div>
                ) : null}
                {!loading && items.length > 0 ? (
                    <div className="admin-users-table-wrap">
                        <table className="admin-users-table">
                            <thead><tr><th scope="col">Account</th><th scope="col">Email</th><th scope="col">Role</th><th scope="col"><span className="sr-only">Role action</span></th></tr></thead>
                            <tbody>
                                {items.map((account) => {
                                    const isProtected = account.protected;
                                    const isSelf = account.externalId === currentUser.id;
                                    const selectedRole = selectedRoles[account.id] ?? account.role;
                                    const disabled = isProtected || isSelf;
                                    return (
                                        <tr key={account.id}>
                                            <td data-label="Account">
                                                <span className="admin-user-name">{account.displayName || "Unnamed account"}</span>
                                                {isProtected ? <span className="admin-user-protected"><ShieldCheck size={13}/> Protected account</span> : null}
                                            </td>
                                            <td data-label="Email" className="admin-user-email">{account.email}</td>
                                            <td data-label="Role">
                                                <div className="admin-user-role-control">
                                                    <select
                                                        value={selectedRole}
                                                        disabled={disabled}
                                                        aria-label={`Role for ${account.email}`}
                                                        aria-describedby={isSelf ? `admin-user-self-role-note-${account.id}` : undefined}
                                                        onChange={(event) => setSelectedRoles((current) => ({...current, [account.id]: event.target.value as UserRole}))}
                                                    >
                                                        {ROLE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                                                    </select>
                                                    {isSelf ? (
                                                        <span id={`admin-user-self-role-note-${account.id}`} className="admin-user-role-note">
                                                            Your own role cannot be changed here
                                                        </span>
                                                    ) : null}
                                                </div>
                                            </td>
                                            <td data-label="Action">
                                                <button
                                                    className="dashboard-secondary-button compact"
                                                    type="button"
                                                    disabled={disabled || selectedRole === account.role}
                                                    onClick={() => requestRoleChange(account)}
                                                >Change role</button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                ) : null}
            </div>

            {loadMoreError ? (
                <div className="admin-users-load-more-error" role="alert">
                    <span>{loadMoreError}</span>
                    <button className="dashboard-secondary-button compact" type="button" onClick={() => void loadMoreUsers()}>Retry</button>
                </div>
            ) : null}
            {cursor && !loading && !loadError ? (
                <div className="admin-users-pagination">
                    <span>{items.length} accounts shown</span>
                    <button className="dashboard-secondary-button" type="button" disabled={loadingMore} onClick={() => void loadMoreUsers()}>
                        {loadingMore ? <><LoaderCircle size={16}/> Loading…</> : "Load more"}
                    </button>
                </div>
            ) : null}

            {pendingChange ? (
                <ConfirmationDialog
                    title="Change account role?"
                    description={`${confirmationName} will change from ${currentRoleLabel} to ${nextRoleLabel}. This affects new requests; active solve jobs keep their current access.`}
                    confirmLabel="Change role"
                    pendingLabel="Saving role…"
                    pending={changePending}
                    error={changeError}
                    onCancel={() => {
                        if (!changePending) {
                            setPendingChange(null);
                            setChangeError(null);
                        }
                    }}
                    onConfirm={() => void confirmRoleChange()}
                />
            ) : null}
        </section>
    );
}

function roleLabel(role: UserRole): string {
    return ROLE_OPTIONS.find((option) => option.value === role)?.label ?? role;
}
