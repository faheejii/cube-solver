import {useEffect, useState, type FormEvent} from "react";
import App from "./App";
import {fetchCurrentUser, login, logout, register, SESSION_EXPIRED_EVENT} from "./api";
import type {AuthUser} from "./types";

type AuthMode = "login" | "register";

export default function AuthenticatedApp() {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [checking, setChecking] = useState(true);
    const [sessionExpired, setSessionExpired] = useState(false);
    const [startupError, setStartupError] = useState<string | null>(null);

    useEffect(() => {
        let active = true;
        void fetchCurrentUser()
            .then((currentUser) => {
                if (active) setUser(currentUser);
            })
            .catch((error) => {
                if (active) {
                    setStartupError(error instanceof Error ? error.message : "Could not reach the API");
                }
            })
            .finally(() => {
                if (active) setChecking(false);
            });
        const expireSession = () => {
            setUser(null);
            setSessionExpired(true);
            setChecking(false);
        };
        window.addEventListener(SESSION_EXPIRED_EVENT, expireSession);
        return () => {
            active = false;
            window.removeEventListener(SESSION_EXPIRED_EVENT, expireSession);
        };
    }, []);

    async function handleLogout() {
        try {
            await logout();
        } finally {
            setUser(null);
            setSessionExpired(false);
        }
    }

    if (checking) return <AuthLoading/>;
    if (!user) {
        return (
            <AuthScreen
                sessionExpired={sessionExpired}
                initialError={startupError}
                onAuthenticated={(authenticatedUser) => {
                    setUser(authenticatedUser);
                    setSessionExpired(false);
                }}
            />
        );
    }
    return <App user={user} onLogout={() => void handleLogout()}/>;
}

function AuthLoading() {
    return (
        <main className="auth-shell" aria-label="Loading account">
            <div className="auth-card auth-loading-card">
                <Brand/>
                <span className="auth-loader" aria-hidden="true"/>
                <p>Restoring your session...</p>
            </div>
        </main>
    );
}

function AuthScreen({sessionExpired, initialError, onAuthenticated}: {
    sessionExpired: boolean;
    initialError: string | null;
    onAuthenticated: (user: AuthUser) => void;
}) {
    const [mode, setMode] = useState<AuthMode>("login");
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(initialError);

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSubmitting(true);
        setError(null);
        const data = new FormData(event.currentTarget);
        const email = String(data.get("email") ?? "").trim();
        const password = String(data.get("password") ?? "");
        try {
            const authenticatedUser = mode === "login"
                ? await login({email, password})
                : await register({
                    displayName: String(data.get("displayName") ?? "").trim(),
                    email,
                    password,
                });
            onAuthenticated(authenticatedUser);
        } catch (authError) {
            setError(authError instanceof Error ? authError.message : "Authentication failed");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <main className="auth-shell">
            <section className="auth-card">
                <Brand/>
                <div className="auth-copy">
                    <p className="section-label">Personal workspace</p>
                    <h1>{mode === "login" ? "Welcome back" : "Create your account"}</h1>
                    <p>Your solves, statistics, and active solutions stay tied to your account.</p>
                </div>
                {sessionExpired ? (
                    <div className="dashboard-alert error" role="alert">
                        Your session expired. Sign in again to continue.
                    </div>
                ) : null}
                <form className="auth-form" onSubmit={(event) => void handleSubmit(event)}>
                    {mode === "register" ? (
                        <label>
                            <span>Display name</span>
                            <input name="displayName" autoComplete="name" required maxLength={80}/>
                        </label>
                    ) : null}
                    <label>
                        <span>Email</span>
                        <input name="email" type="email" autoComplete="email" required/>
                    </label>
                    <label>
                        <span>Password</span>
                        <input
                            name="password"
                            type="password"
                            autoComplete={mode === "login" ? "current-password" : "new-password"}
                            required
                            minLength={8}
                        />
                    </label>
                    {error ? <p className="auth-error" role="alert">{error}</p> : null}
                    <button className="dashboard-primary-button auth-submit" type="submit" disabled={submitting}>
                        {submitting ? "Please wait..." : mode === "login" ? "Sign in" : "Create account"}
                    </button>
                </form>
                <button
                    className="auth-mode-button"
                    type="button"
                    onClick={() => {
                        setMode((current) => current === "login" ? "register" : "login");
                        setError(null);
                    }}
                >
                    {mode === "login" ? "New here? Create an account" : "Already registered? Sign in"}
                </button>
            </section>
        </main>
    );
}

function Brand() {
    return (
        <div className="dashboard-brand auth-brand">
            <span className="dashboard-brand-mark" aria-hidden="true"><i/><i/><i/></span>
            <span><strong>Cube Solver</strong><small>CFOP timer</small></span>
        </div>
    );
}
