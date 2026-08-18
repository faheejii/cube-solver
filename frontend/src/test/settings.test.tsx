import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {beforeEach, describe, expect, it} from "vitest";
import SettingsView from "../SettingsView";
import {
    DEFAULT_SETTINGS,
    SETTINGS_STORAGE_KEY,
    loadSettings,
    normalizeSettings,
} from "../hooks/useSettings";

describe("settings", () => {
    beforeEach(() => window.localStorage?.clear());

    it("loads safe defaults and clamps invalid persisted values", () => {
        expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
        expect(normalizeSettings({solveDeadlineSeconds: 999, inspectionEnabled: false, theme: "light"})).toMatchObject({
            solveDeadlineSeconds: 120,
            inspectionEnabled: false,
            theme: "light",
        });
        window.localStorage?.setItem(SETTINGS_STORAGE_KEY, "not json");
        expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
    });

    it("renders and updates timer settings", async () => {
        const user = userEvent.setup();
        let settings = DEFAULT_SETTINGS;
        render(<SettingsView settings={settings} onChange={(changes) => { settings = {...settings, ...changes}; }}/>);

        expect(screen.getByRole("heading", {name: "Settings"})).toBeInTheDocument();
        expect(screen.getByRole("switch", {name: "Inspection time"})).toBeChecked();
        await user.click(screen.getByRole("switch", {name: "Inspection time"}));
        expect(settings.inspectionEnabled).toBe(false);
        expect(screen.getByLabelText("Solver processing limit in seconds")).toHaveValue(15);
    });
});
