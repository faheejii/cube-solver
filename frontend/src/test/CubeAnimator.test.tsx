import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import type {SolveResponse} from "../types";

vi.mock("../CubePreview", () => ({
    default: ({
                  setupAlgorithm,
                  algorithm,
                  stage,
                  isPlaying,
                  onPlaybackComplete,
                  interactiveView,
                  "data-playback-alg": playbackAlgorithm,
              }: {
        setupAlgorithm: string;
        algorithm: string;
        stage: string;
        isPlaying?: boolean;
        onPlaybackComplete?: () => void;
        interactiveView?: boolean;
        "data-playback-alg": string;
    }) => (
        <div data-testid="custom-preview" data-setup={setupAlgorithm} data-algorithm={algorithm} data-stage={stage} data-is-playing={String(isPlaying)} data-interactive-view={String(interactiveView)} data-playback-alg={playbackAlgorithm}>
            <button type="button" onClick={onPlaybackComplete}>Complete custom playback</button>
        </div>
    ),
}));

vi.mock("../ReferenceCubePlayer", () => ({
    default: ({setupAlgorithm, algorithm, stage, moveIndex, moveCount}: {setupAlgorithm: string; algorithm: string; stage: string; moveIndex: number; moveCount: number}) => (
        <div data-testid="reference-preview" data-setup={setupAlgorithm} data-algorithm={algorithm} data-stage={stage} data-move-index={moveIndex} data-move-count={moveCount}/>
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

describe("CubeAnimator debug comparison", () => {
    it("enables orbit interaction only for the solution playback preview", async () => {
        render(<CubeAnimator result={result}/>);

        await waitFor(() => expect(screen.getByTestId("reference-preview")).toBeInTheDocument());

        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-interactive-view", "true");
    });

    it("starts solution playback paused and plays only after the control is pressed", async () => {
        render(<CubeAnimator result={result}/>);
        await waitFor(() => expect(screen.getByTestId("reference-preview")).toBeInTheDocument());

        expect(screen.getByRole("button", {name: "Play playback"})).toBeInTheDocument();
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "false");

        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        expect(screen.getByRole("button", {name: "Pause playback"})).toBeInTheDocument();
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "true");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-algorithm", "F U R R U R' U2");

        fireEvent.click(screen.getByRole("button", {name: "Pause playback"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "false");
    });

    it("resets to paused when stage or speed changes and restarts from setup after completion", async () => {
        render(<CubeAnimator result={result}/>);
        await waitFor(() => expect(screen.getByTestId("reference-preview")).toBeInTheDocument());

        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        fireEvent.click(screen.getByRole("button", {name: "Complete custom playback"}));
        expect(screen.getByRole("button", {name: "Play playback"})).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "true");
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U");

        fireEvent.click(screen.getByRole("button", {name: "Next stage"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "false");
        expect(screen.getByRole("button", {name: "Play playback"})).toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        fireEvent.change(screen.getByRole("combobox", {name: "Playback speed"}), {target: {value: "3"}});
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-is-playing", "false");
        expect(screen.getByRole("button", {name: "Play playback"})).toBeInTheDocument();
    });

    it("passes identical setup and algorithm values to both players", async () => {
        render(<CubeAnimator result={result}/>);

        await waitFor(() => expect(screen.getByTestId("reference-preview")).toBeInTheDocument());

        const custom = screen.getByTestId("custom-preview");
        const reference = screen.getByTestId("reference-preview");
        expect(reference).toHaveAttribute("data-setup", custom.getAttribute("data-setup"));
        expect(custom).toHaveAttribute("data-setup", "R U");
        expect(custom).toHaveAttribute("data-algorithm", "");
        expect(custom).toHaveAttribute("data-playback-alg", "F U R R U R' U2");
        expect(reference).toHaveAttribute("data-algorithm", custom.getAttribute("data-algorithm"));
        expect(reference).toHaveAttribute("data-stage", "full");
        expect(screen.getByText("Original algorithm: F U R R U R' U2")).toBeInTheDocument();
    });

    it("keeps setup parity when switching to a stage", async () => {
        render(<CubeAnimator result={result}/>);

        await waitFor(() => expect(screen.getByTestId("reference-preview")).toBeInTheDocument());
        await screen.getByRole("button", {name: "Next stage"}).click();

        const custom = screen.getByTestId("custom-preview");
        const reference = screen.getByTestId("reference-preview");
        expect(custom).toHaveAttribute("data-setup", "R U");
        expect(custom).toHaveAttribute("data-algorithm", "");
        expect(custom).toHaveAttribute("data-stage", "cross");
        expect(reference).toHaveAttribute("data-setup", custom.getAttribute("data-setup"));
        expect(reference).toHaveAttribute("data-algorithm", custom.getAttribute("data-algorithm"));
        expect(reference).toHaveAttribute("data-stage", "cross");
    });

    it("steps both players through identical full-algorithm prefixes and resets", async () => {
        render(<CubeAnimator result={result}/>);
        await waitFor(() => expect(screen.getByTestId("reference-preview")).toBeInTheDocument());

        const comparison = document.querySelector("[data-comparison-stage]")!;
        fireEvent.click(screen.getByRole("button", {name: "Next"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U F");
        expect(screen.getByTestId("reference-preview")).toHaveAttribute("data-setup", "R U F");
        expect(screen.getByTestId("reference-preview")).toHaveAttribute("data-move-index", "1");
        expect(comparison).toHaveAttribute("data-comparison-effective-alg", "");

        fireEvent.click(screen.getByRole("button", {name: "Next"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U F U");
        fireEvent.click(screen.getByRole("button", {name: "Previous"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U F");
        fireEvent.click(screen.getByRole("button", {name: "Reset"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U");
        expect(screen.getByTestId("reference-preview")).toHaveAttribute("data-setup", "R U");
    });

    it("can replay the custom animation without losing custom/reference notation parity", async () => {
        render(<CubeAnimator result={result}/>);
        await waitFor(() => expect(screen.getByTestId("reference-preview")).toBeInTheDocument());

        fireEvent.click(screen.getByRole("button", {name: "Play custom"}));
        const custom = screen.getByTestId("custom-preview");
        const reference = screen.getByTestId("reference-preview");
        expect(custom).toHaveAttribute("data-setup", "R U");
        expect(custom).toHaveAttribute("data-algorithm", "F U R R U R' U2");
        expect(reference).toHaveAttribute("data-setup", custom.getAttribute("data-setup"));
        expect(reference).toHaveAttribute("data-algorithm", custom.getAttribute("data-algorithm"));
        expect(document.querySelector("[data-comparison-stage]")).toHaveAttribute("data-comparison-mode", "playback");

        fireEvent.click(screen.getByRole("button", {name: "Return to steps"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-algorithm", "");
        expect(document.querySelector("[data-comparison-stage]")).toHaveAttribute("data-comparison-mode", "step");
    });

    it("preserves pair setup composition while stepping an individual F2L pair", async () => {
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
        await waitFor(() => expect(screen.getByTestId("reference-preview")).toBeInTheDocument());

        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U F L U");
        expect(screen.getByTestId("reference-preview")).toHaveAttribute("data-setup", "R U F L U");
        fireEvent.click(screen.getByRole("button", {name: "Next"}));
        expect(screen.getByTestId("custom-preview")).toHaveAttribute("data-setup", "R U F L U R");
        expect(screen.getByTestId("reference-preview")).toHaveAttribute("data-setup", "R U F L U R");
    });
});

function stage(name: string, algorithm: string) {
    return {name, algorithm, moveCount: algorithm.split(" ").length, solved: true, status: "ok"};
}
