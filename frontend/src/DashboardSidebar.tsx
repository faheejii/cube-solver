import { Activity, History, Moon, Sun, Timer } from "lucide-react";

export type DashboardView = "timer" | "history" | "processes";

type Props = {
  activeView: DashboardView;
  theme: "light" | "dark";
  activeProcessCount: number;
  onViewChange: (view: DashboardView) => void;
  onToggleTheme: () => void;
};

export default function DashboardSidebar({
  activeView,
  theme,
  activeProcessCount,
  onViewChange,
  onToggleTheme,
}: Props) {
  return (
    <aside className="dashboard-sidebar">
      <div className="dashboard-brand">
        <span className="dashboard-brand-mark" aria-hidden="true">
          <i />
          <i />
          <i />
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
          <Timer size={19} />
          <span>Timer</span>
        </button>
        <button
          className={activeView === "history" ? "dashboard-nav-item active" : "dashboard-nav-item"}
          type="button"
          onClick={() => onViewChange("history")}
        >
          <History size={19} />
          <span>History</span>
        </button>
        <button
          className={activeView === "processes" ? "dashboard-nav-item active" : "dashboard-nav-item"}
          type="button"
          onClick={() => onViewChange("processes")}
        >
          <Activity size={19} />
          <span>Solutions</span>
          {activeProcessCount > 0 ? (
            <strong className="dashboard-nav-badge">{activeProcessCount}</strong>
          ) : null}
        </button>
      </nav>

      <div className="dashboard-sidebar-footer">
        <span>Local profile</span>
        <button
          className="sidebar-theme-button"
          type="button"
          onClick={onToggleTheme}
          aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}
        >
          {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
          <span>{theme === "dark" ? "Light mode" : "Dark mode"}</span>
        </button>
      </div>
    </aside>
  );
}
