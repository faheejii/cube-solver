import {Activity, BookOpen, History, LogOut, Moon, Sun, Timer} from "lucide-react";
import type {AuthUser} from "./types";

export type DashboardView = "timer" | "history" | "processes" | "algorithms";

type Props = {
    activeView: DashboardView;
    theme: "light" | "dark";
    activeProcessCount: number;
    user: AuthUser;
    onViewChange: (view: DashboardView) => void;
    onToggleTheme: () => void;
    onLogout: () => void;
};

export default function DashboardSidebar({
                                             activeView,
                                             theme,
                                             activeProcessCount,
                                             user,
                                             onViewChange,
                                             onToggleTheme,
                                             onLogout,
                                         }: Props) {
    return (
        <aside className="dashboard-sidebar">
            <div className="dashboard-brand">
        <span className="dashboard-brand-mark" aria-hidden="true">
          <i/>
          <i/>
          <i/>
        </span>
                <span>
          <strong>Cube Solver</strong>
          <small>CFOP timer</small>
        </span>
            </div>

            <nav className="dashboard-nav" aria-label="Primary navigation">
                <button
                    className={activeView === "timer" ? "dashboard-nav-item active" : "dashboard-nav-item"}
                    type="button"
                    onClick={() => onViewChange("timer")}
                >
                    <Timer size={19}/>
                    <span>Timer</span>
                </button>
                <button
                    className={activeView === "history" ? "dashboard-nav-item active" : "dashboard-nav-item"}
                    type="button"
                    onClick={() => onViewChange("history")}
                >
                    <History size={19}/>
                    <span>History</span>
                </button>
                <button
                    className={activeView === "processes" ? "dashboard-nav-item active" : "dashboard-nav-item"}
                    type="button"
                    onClick={() => onViewChange("processes")}
                >
                    <Activity size={19}/>
                    <span>Solutions</span>
                    {activeProcessCount > 0 ? (
                        <strong className="dashboard-nav-badge">{activeProcessCount}</strong>
                    ) : null}
                </button>
                {user.role === "admin" ? (
                    <button
                        className={activeView === "algorithms" ? "dashboard-nav-item active" : "dashboard-nav-item"}
                        type="button"
                        onClick={() => onViewChange("algorithms")}
                    >
                        <BookOpen size={19}/>
                        <span>Algorithms</span>
                    </button>
                ) : null}
            </nav>

            <div className="dashboard-sidebar-footer">
                <span className="sidebar-user" title={user.email}>{user.displayName || user.email}</span>
                <button
                    className="sidebar-theme-button"
                    type="button"
                    onClick={onToggleTheme}
                    aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
                >
                    {theme === "dark" ? <Sun size={18}/> : <Moon size={18}/>}
                    <span>{theme === "dark" ? "Light mode" : "Dark mode"}</span>
                </button>
                <button className="sidebar-theme-button" type="button" onClick={onLogout}>
                    <LogOut size={18}/><span>Sign out</span>
                </button>
            </div>
        </aside>
    );
}
