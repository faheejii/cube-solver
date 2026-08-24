import {Activity, BookOpen, History, LogOut, Settings, Timer} from "lucide-react";
import type {AuthUser} from "./types";

export type DashboardView = "timer" | "history" | "processes" | "algorithms" | "settings";

type Props = {
    activeView: DashboardView;
    activeProcessCount: number;
    user: AuthUser;
    onViewChange: (view: DashboardView) => void;
    onLogout: () => void;
};

export default function DashboardSidebar({
                                             activeView,
                                             activeProcessCount,
                                             user,
                                             onViewChange,
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
                <button
                    className={activeView === "settings" ? "dashboard-nav-item active" : "dashboard-nav-item"}
                    type="button"
                    onClick={() => onViewChange("settings")}
                >
                    <Settings size={19}/>
                    <span>Settings</span>
                </button>
            </nav>

            <div className="dashboard-sidebar-footer">
                <span className="sidebar-user" title={user.email}>{user.displayName || user.email}</span>
                <button className="sidebar-theme-button" type="button" onClick={onLogout}>
                    <LogOut size={18}/><span>Sign out</span>
                </button>
            </div>
        </aside>
    );
}
