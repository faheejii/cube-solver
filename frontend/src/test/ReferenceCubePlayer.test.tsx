import {render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

class MockTwistyPlayer extends HTMLElement {
    static configs: unknown[] = [];
    constructor(config: unknown) {
        super();
        MockTwistyPlayer.configs.push(config);
    }
    pause = vi.fn();
    play = vi.fn();
    jumpToStart = vi.fn();
}

if (!customElements.get("twisty-player")) {
    customElements.define("twisty-player", MockTwistyPlayer);
}

vi.mock("cubing/twisty", () => ({TwistyPlayer: MockTwistyPlayer}));

import ReferenceCubePlayer from "../ReferenceCubePlayer";

afterEach(() => {
    MockTwistyPlayer.configs = [];
});

describe("ReferenceCubePlayer", () => {
    it("creates a 2D player with the shared setup and algorithm", async () => {
        render(<ReferenceCubePlayer setupAlgorithm="R U R'" algorithm="" stage="cross" playbackSpeed={3} moveIndex={1} moveCount={4}/>);

        const panel = screen.getByTestId("reference-panel");
        await waitFor(() => expect(panel).toHaveAttribute("data-reference-status", "ready"));

        expect(MockTwistyPlayer.configs).toEqual([{
            puzzle: "3x3x3",
            experimentalSetupAlg: "R U R'",
            alg: "",
            visualization: "2D",
            tempoScale: 3,
            background: "none",
            controlPanel: "none",
            hintFacelets: "none",
        }]);
        const player = panel.querySelector("[data-reference-instance]") as MockTwistyPlayer;
        expect(player.pause).toHaveBeenCalledOnce();
        expect(player.jumpToStart).toHaveBeenCalledWith({flash: false});
        expect(panel.querySelector("[data-reference-instance]")).toBeInTheDocument();
        expect(panel).toHaveAttribute("data-reference-setup", "R U R'");
        expect(panel).toHaveAttribute("data-reference-alg", "");
        expect(panel).toHaveAttribute("data-reference-move-index", "1");
        expect(panel).toHaveAttribute("data-reference-move-count", "4");
        expect(screen.getByText("Paused at identical prefix")).toBeInTheDocument();
    });

    it("cleans up the player when the panel unmounts", async () => {
        const {unmount} = render(<ReferenceCubePlayer setupAlgorithm="R" algorithm="U" stage="full" playbackSpeed={1}/>);
        const panel = screen.getByTestId("reference-panel");
        await waitFor(() => expect(panel).toHaveAttribute("data-reference-status", "ready"));

        const player = panel.querySelector("[data-reference-instance]") as MockTwistyPlayer;
        unmount();
        expect(player.pause).toHaveBeenCalledTimes(2);
        expect(player.isConnected).toBe(false);
    });
});
