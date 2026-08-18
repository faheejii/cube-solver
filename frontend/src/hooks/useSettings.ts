import {useCallback, useEffect, useState} from "react";

export const SETTINGS_STORAGE_KEY = "cube-solver-settings";
export const DEFAULT_SOLVE_DEADLINE_SECONDS = 15;
export const MIN_SOLVE_DEADLINE_SECONDS = 5;
export const MAX_SOLVE_DEADLINE_SECONDS = 120;

export type Theme = "light" | "dark";
export type AppSettings = {
    solveDeadlineSeconds: number;
    inspectionEnabled: boolean;
    theme: Theme;
};

export const DEFAULT_SETTINGS: AppSettings = {
    solveDeadlineSeconds: DEFAULT_SOLVE_DEADLINE_SECONDS,
    inspectionEnabled: true,
    theme: "dark",
};

export function clampDeadline(seconds: number): number {
    return Math.min(MAX_SOLVE_DEADLINE_SECONDS, Math.max(MIN_SOLVE_DEADLINE_SECONDS, seconds));
}

export function normalizeSettings(value: unknown): AppSettings {
    if (!value || typeof value !== "object") return DEFAULT_SETTINGS;
    const candidate = value as Partial<AppSettings>;
    const deadline = Number(candidate.solveDeadlineSeconds);
    return {
        solveDeadlineSeconds: Number.isFinite(deadline)
            ? clampDeadline(Math.round(deadline))
            : DEFAULT_SETTINGS.solveDeadlineSeconds,
        inspectionEnabled: typeof candidate.inspectionEnabled === "boolean"
            ? candidate.inspectionEnabled
            : DEFAULT_SETTINGS.inspectionEnabled,
        theme: candidate.theme === "light" || candidate.theme === "dark"
            ? candidate.theme
            : DEFAULT_SETTINGS.theme,
    };
}

export function loadSettings(): AppSettings {
    try {
        const stored = window.localStorage?.getItem(SETTINGS_STORAGE_KEY);
        if (stored) {
            return normalizeSettings(JSON.parse(stored));
        }
        const savedTheme = window.localStorage?.getItem("cube-solver-theme");
        if (savedTheme === "light" || savedTheme === "dark") {
            return {...DEFAULT_SETTINGS, theme: savedTheme};
        }
        return DEFAULT_SETTINGS;
    } catch {
        return DEFAULT_SETTINGS;
    }
}

export function useSettings() {
    const [settings, setSettings] = useState<AppSettings>(loadSettings);
    useEffect(() => {
        window.localStorage?.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings));
        document.documentElement.dataset.theme = settings.theme;
        document.documentElement.style.colorScheme = settings.theme;
    }, [settings]);
    const updateSettings = useCallback((changes: Partial<AppSettings>) => {
        setSettings((current) => normalizeSettings({...current, ...changes}));
    }, []);
    return {settings, updateSettings};
}
