import {Moon, Sun} from "lucide-react";
import {MAX_SOLVE_DEADLINE_SECONDS, MIN_SOLVE_DEADLINE_SECONDS, type AppSettings} from "./hooks/useSettings";

type Props = {settings: AppSettings; onChange: (changes: Partial<AppSettings>) => void};

export default function SettingsView({settings, onChange}: Props) {
    return (
        <section className="dashboard-settings-view" aria-labelledby="settings-title">
            <header className="dashboard-page-header">
                <div>
                    <p className="section-label">Preferences</p>
                    <h1 id="settings-title">Settings</h1>
                    <p>Customize timer behavior and solver processing for this browser.</p>
                </div>
            </header>
            <div className="settings-grid">
                <section className="settings-card" aria-labelledby="timer-settings-title">
                    <p className="section-label">Timer</p>
                    <h2 id="timer-settings-title">Solve behavior</h2>
                    <label className="settings-control">
                        <span><strong>Inspection time</strong><small>Use the standard 15-second inspection before each solve.</small></span>
                        <input type="checkbox" role="switch" aria-label="Inspection time" checked={settings.inspectionEnabled} onChange={(event) => onChange({inspectionEnabled: event.target.checked})}/>
                    </label>
                    <label className="settings-field">
                        <span><strong>Solver processing limit</strong><small>Maximum time allowed to compute a solution.</small></span>
                        <span className="settings-input-suffix">
                            <input type="number" min={MIN_SOLVE_DEADLINE_SECONDS} max={MAX_SOLVE_DEADLINE_SECONDS} step={1} value={settings.solveDeadlineSeconds} onChange={(event) => onChange({solveDeadlineSeconds: Number(event.target.value)})} aria-label="Solver processing limit in seconds"/>
                            <span>seconds</span>
                        </span>
                    </label>
                    <p className="settings-note">Choose between {MIN_SOLVE_DEADLINE_SECONDS} and {MAX_SOLVE_DEADLINE_SECONDS} seconds. Changes apply to new solution requests.</p>
                </section>
                <section className="settings-card" aria-labelledby="appearance-settings-title">
                    <p className="section-label">Appearance</p>
                    <h2 id="appearance-settings-title">Theme</h2>
                    <div className="settings-theme-options" role="group" aria-label="Theme">
                        <button className={settings.theme === "dark" ? "active" : ""} type="button" onClick={() => onChange({theme: "dark"})}><Moon size={17}/> Dark</button>
                        <button className={settings.theme === "light" ? "active" : ""} type="button" onClick={() => onChange({theme: "light"})}><Sun size={17}/> Light</button>
                    </div>
                </section>
            </div>
        </section>
    );
}
