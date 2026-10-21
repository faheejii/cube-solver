import {render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {beforeEach, describe, expect, it} from "vitest";
import SettingsView from "../SettingsView";
import {
    DEFAULT_SETTINGS,
    SETTINGS_STORAGE_KEY,
    crossSearchRequestPreferences,
    loadSettings,
    normalizeSettings,
    useSettings,
} from "../hooks/useSettings";

describe("settings", () => {
    beforeEach(() => window.localStorage?.clear());

    it("loads safe defaults and clamps invalid persisted values", () => {
        expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
        expect(normalizeSettings({solveDeadlineSeconds: 999, inspectionEnabled: false, theme: "light"})).toMatchObject({
            solveDeadlineSeconds: 120,
            inspectionEnabled: false,
            deepColorNeutralOptimization: false,
            fastAvoidBTurns: true,
            optimizedAvoidBTurns: false,
            theme: "light",
            cubePreviewMode: "3d",
            cubePlaybackMode: "3d",
        });
        window.localStorage?.setItem(SETTINGS_STORAGE_KEY, "not json");
        expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
    });

    it("loads the saved cube preview mode", () => {
        window.localStorage?.setItem(SETTINGS_STORAGE_KEY, JSON.stringify({...DEFAULT_SETTINGS, cubePreviewMode: "2d"}));
        expect(loadSettings().cubePreviewMode).toBe("2d");
        expect(loadSettings().cubePlaybackMode).toBe("3d");
    });

    it("normalizes and persists the cross-search preferences", async () => {
        window.localStorage?.setItem(SETTINGS_STORAGE_KEY, JSON.stringify({
            ...DEFAULT_SETTINGS,
            fastAvoidBTurns: false,
            optimizedAvoidBTurns: true,
        }));

        function SettingsProbe() {
            const {settings, updateSettings} = useSettings();
            return <SettingsView settings={settings} onChange={updateSettings}/>;
        }

        render(<SettingsProbe/>);
        expect(screen.getByRole("switch", {name: "Avoid B turns in Fast"})).not.toBeChecked();
        expect(screen.getByRole("switch", {name: "Avoid B turns in Optimized"})).toBeChecked();

        await userEvent.click(screen.getByRole("switch", {name: "Avoid B turns in Fast"}));
        await waitFor(() => {
            expect(JSON.parse(window.localStorage.getItem(SETTINGS_STORAGE_KEY) ?? "{}")).toMatchObject({
                fastAvoidBTurns: true,
                optimizedAvoidBTurns: true,
            });
        });
    });

    it("builds effective cross-search request preferences for the active solver mode", () => {
        expect(crossSearchRequestPreferences(DEFAULT_SETTINGS, "greedy")).toEqual({
            avoidBTurns: true,
        });
        expect(crossSearchRequestPreferences(DEFAULT_SETTINGS, "optimized")).toEqual({
            avoidBTurns: false,
        });
        expect(crossSearchRequestPreferences({
            ...DEFAULT_SETTINGS,
            optimizedAvoidBTurns: true,
        }, "optimized")).toEqual({
            avoidBTurns: true,
        });
    });

    it("renders and updates timer settings", async () => {
        const user = userEvent.setup();
        let settings = DEFAULT_SETTINGS;
        const onChange = (changes: Partial<typeof settings>) => { settings = {...settings, ...changes}; };
        const {rerender} = render(<SettingsView settings={settings} onChange={onChange}/>);

        expect(screen.getByRole("heading", {name: "Settings"})).toBeInTheDocument();
        expect(screen.getByRole("switch", {name: "Inspection time"})).toBeChecked();
        expect(screen.getByRole("switch", {name: "Deep color-neutral optimization"})).not.toBeChecked();
        expect(screen.getByRole("switch", {name: "Avoid B turns in Fast"})).toBeChecked();
        expect(screen.getByRole("switch", {name: "Avoid B turns in Optimized"})).not.toBeChecked();
        const previewGroup = screen.getByRole("group", {name: "Cube preview"});
        const playbackGroup = screen.getByRole("group", {name: "Cube playback"});
        expect(within(previewGroup).getByRole("button", {name: "3D"})).toHaveAttribute("aria-pressed", "true");
        expect(within(playbackGroup).getByRole("button", {name: "3D"})).toHaveAttribute("aria-pressed", "true");
        await user.click(screen.getByRole("switch", {name: "Inspection time"}));
        expect(settings.inspectionEnabled).toBe(false);
        await user.click(screen.getByRole("switch", {name: "Deep color-neutral optimization"}));
        expect(settings.deepColorNeutralOptimization).toBe(true);
        await user.click(screen.getByRole("switch", {name: "Avoid B turns in Optimized"}));
        expect(settings.optimizedAvoidBTurns).toBe(true);
        await user.click(within(previewGroup).getByRole("button", {name: "2D"}));
        expect(settings.cubePreviewMode).toBe("2d");
        await user.click(within(playbackGroup).getByRole("button", {name: "2D"}));
        expect(settings.cubePlaybackMode).toBe("2d");
        rerender(<SettingsView settings={settings} onChange={onChange}/>);
        expect(within(screen.getByRole("group", {name: "Cube preview"})).getByRole("button", {name: "2D"})).toHaveAttribute("aria-pressed", "true");
        expect(within(screen.getByRole("group", {name: "Cube playback"})).getByRole("button", {name: "2D"})).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByLabelText("Solution computation time limit in seconds")).toHaveValue(15);
    });
});
