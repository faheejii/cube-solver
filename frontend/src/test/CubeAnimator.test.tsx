import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import type {SolveResponse} from "../types";
import {CubePlaybackModeContext, CubePreviewModeContext} from "../CubePreviewModeContext";

vi.mock("../DeferredCubePreview", () => ({
    default: ({
                  setupAlgorithm,
                  algorithm,
                  stage,
                  isPlaying,
                  onPlaybackComplete,
                  interactiveView,
                  displayMode,
                  "data-playback-alg": playbackAlgorithm,
              }: {
        setupAlgorithm: string;
        algorithm: string;
        stage: string;
        isPlaying?: boolean;
        onPlaybackComplete?: () => void;
        interactiveView?: boolean;
        displayMode?: "2d" | "3d";
        "data-playback-alg": string;
    }) => (
        <div data-testid="custom-preview" data-setup={setupAlgorithm} data-algorithm={algorithm} data-stage={stage} data-is-playing={String(isPlaying)} data-interactive-view={String(interactiveView)} data-playback-alg={playbackAlgorithm} data-display-mode={displayMode}>
            <button type="button" onClick={onPlaybackComplete}>Complete custom playback</button>
        </div>
    ),
}));

import CubeAnimator from "../CubeAnimator";

const result = {
    scramble: "R U",
    crossFace: "D",
    f2lMode: "greedy",
    f2lSetupCaseCount: 0,
    f2lInsertCaseCount: 0,
    cross: stage("cross", "F"),
    f2l: {
        ...stage("f2l", "U R"),
        traceComplete: true,
        pairAlgorithmMatchesStage: true,
        pairs: [],
    },
    oll: stage("oll", "R U R'"),
    pll: stage("pll", "U2"),
    solvedF2LSlots: "[]",
    fullySolved: true,
    totalMoveCount: 7,
    elapsedMs: 1,
    comparison: null,
} as SolveResponse;

describe("CubeAnimator", () => {
    it("uses the playback setting independently of the timer preview mode", () => {
        const {unmount} = render(
            <CubePreviewModeContext.Provider value="2d">
                <CubeAnimator result={result}/>
            </CubePreviewModeContext.Provider>,
        );

        expect(screen.getByRole("heading", {name: "3D Playback"})).toBeInTheDocument();
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-display-mode", "3d");
        unmount();

        render(
            <CubePlaybackModeContext.Provider value="2d">
                <CubeAnimator result={result}/>
            </CubePlaybackModeContext.Provider>,
        );
        expect(screen.getByRole("heading", {name: "2D Playback"})).toBeInTheDocument();
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-display-mode", "2d");
    });

    it("starts with the complete custom solution ready to play", () => {
        render(<CubeAnimator result={result}/>);

        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-interactive-view", "true");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-algorithm", "F U R R U R' U2");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-playback-alg", "F U R R U R' U2");
        expect(screen.getByRole("button", {name: "Play playback"})).toBeInTheDocument();
        expect(screen.queryByText("Notation comparison")).not.toBeInTheDocument();
    });

    it("plays, pauses, and restarts the custom animation", () => {
        render(<CubeAnimator result={result}/>);

        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        expect(screen.getByRole("button", {name: "Pause playback"})).toBeInTheDocument();
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "true");

        fireEvent.click(screen.getByRole("button", {name: "Pause playback"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "false");

        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        fireEvent.click(screen.getByRole("button", {name: "Complete custom playback"}));
        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "true");

        fireEvent.click(screen.getByRole("button", {name: "Restart playback"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "false");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-algorithm", "F U R R U R' U2");
    });

    it("resets playback when stage or speed changes", () => {
        render(<CubeAnimator result={result}/>);

        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        fireEvent.click(screen.getByRole("button", {name: "Next stage"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "false");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-stage", "cross");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-algorithm", "F");

        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        fireEvent.change(screen.getByRole("combobox", {name: "Playback speed"}), {target: {value: "3"}});
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "false");
    });

    it("preserves pair setup composition for individual F2L playback", () => {
        const pairResult = {
            ...result,
            f2l: {
                ...result.f2l,
                pairs: [
                    {order: 1, algorithm: "L U"},
                    {order: 2, algorithm: "R U'"},
                ],
            },
        } as SolveResponse;
        render(<CubeAnimator result={pairResult} selectedStage="f2l" selectedF2LPair={2}/>);

        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U F L U");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-algorithm", "R U'");
    });
});

function stage(name: string, algorithm: string) {
    return {name, algorithm, moveCount: algorithm.split(" ").length, solved: true, status: "ok"};
}
