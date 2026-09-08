import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import type {SolveResponse} from "../types";

vi.mock("../CubeAnimator", () => ({
    default: ({selectedF2LPair}: { selectedF2LPair: number | null }) => (
        <div data-testid="cube-animator">Pair playback: {selectedF2LPair ?? "full F2L"}</div>
    ),
}));

import SolutionResultBody from "../SolutionResultBody";

const result: SolveResponse = {
    scramble: "R U",
    crossFace: "D",
    f2lMode: "optimized",
    f2lSetupCaseCount: 12,
    f2lInsertCaseCount: 41,
    cross: stage("cross", "F"),
    f2l: {
        ...stage("f2l", "U R U' R'"),
        traceComplete: true,
        pairAlgorithmMatchesStage: true,
        pairs: [pair(1, "FR", "SHORTEST_AVAILABLE_PAIR"), pair(2, "FL", "PRESERVES_SOLVED_SLOTS")],
    },
    oll: stage("oll", "R U R'"),
    pll: stage("pll", "U"),
    solvedF2LSlots: "[FR, FL]",
    fullySolved: true,
    totalMoveCount: 9,
    elapsedMs: 12.3,
    comparison: {
        fast: {crossFace: "D", f2lMoves: 5, ollMoves: 3, pllMoves: 1, totalMoves: 9, rotationCount: 1, pairOrder: ["FR", "FL"], pairTraceComplete: true},
        optimized: {crossFace: "D", f2lMoves: 4, ollMoves: 3, pllMoves: 1, totalMoves: 8, rotationCount: 0, pairOrder: ["FL", "FR"], pairTraceComplete: true},
        f2lMoveDifference: -1,
        ollMoveDifference: 0,
        pllMoveDifference: 0,
        totalMoveDifference: -1,
        rotationDifference: -1,
        pairOrderChanged: true,
        explanationCodes: ["SHORTER_TOTAL_ROUTE", "FEWER_ROTATIONS"],
    },
};

describe("SolutionResultBody F2L explanation", () => {
    it("renders pair cards without the removed comparison panel", () => {
        render(<SolutionResultBody result={result} requestedCross="D"/>);

        fireEvent.click(screen.getByRole("button", {name: /F2L/i}));

        expect(screen.getByText("Pair 1")).toBeInTheDocument();
        expect(screen.getByText("Shortest available pair")).toBeInTheDocument();
        expect(screen.queryByText("Preserves FL")).not.toBeInTheDocument();
        expect(screen.queryByText("Preserves solved slots")).not.toBeInTheDocument();
        expect(screen.queryByText("Needs pairing")).not.toBeInTheDocument();
        expect(screen.queryByText("Already connected")).not.toBeInTheDocument();
        expect(screen.queryByText("Recovery Unpair")).not.toBeInTheDocument();
        expect(screen.queryByText(/Case:/)).not.toBeInTheDocument();
        expect(screen.queryByText("12 setup cases")).not.toBeInTheDocument();
        expect(screen.queryByText("41 insert cases")).not.toBeInTheDocument();
        expect(screen.queryByText("[FR, FL]")).not.toBeInTheDocument();
        expect(screen.queryByText("Fast vs Optimized")).not.toBeInTheDocument();
    });

    it("selects an individual pair for playback", () => {
        render(<SolutionResultBody result={result} requestedCross="D"/>);

        fireEvent.click(screen.getByRole("button", {name: /F2L/i}));
        fireEvent.click(screen.getByRole("button", {name: /Pair 2/i}));

        expect(screen.getByTestId("cube-animator")).toHaveTextContent("Pair playback: 2");
    });

    it("describes an empty current trace without legacy saved-solution wording", () => {
        render(<SolutionResultBody result={{
            ...result,
            f2l: {...result.f2l, pairs: []},
        }} requestedCross="D"/>);

        fireEvent.click(screen.getByRole("button", {name: /F2L/i}));

        expect(screen.getByText("No F2L pairs generated.")).toBeInTheDocument();
        expect(screen.queryByText(/Pair trace unavailable/i)).not.toBeInTheDocument();
    });
});

function stage(name: string, algorithm: string) {
    return {name, algorithm, moveCount: algorithm ? algorithm.split(" ").length : 0, solved: true, status: "ok"};
}

function pair(order: number, targetSlot: string, reasonCode: string) {
    return {
        order,
        corner: "DFR",
        edge: "FR",
        targetSlot,
        algorithm: "U R",
        moveCount: 2,
        completeMoves: ["U", "R"],
        startMoveIndex: order === 1 ? 0 : 2,
        endMoveIndex: order === 1 ? 1 : 3,
        moveBreakdownAvailable: false,
        setupAlgorithm: null,
        pairingAlgorithm: null,
        insertionAlgorithm: null,
        stateBefore: {cornerPerm: [], cornerOri: [], edgePerm: [], edgeOri: []},
        orientationBefore: {up: "U", right: "R", front: "F"},
        stateAfter: {cornerPerm: [], cornerOri: [], edgePerm: [], edgeOri: []},
        orientationAfter: {up: "U", right: "R", front: "F"},
        preservedSlots: order === 2 ? ["FR"] : [],
        case: {
            cornerPosition: "URF",
            cornerOrientation: 0,
            edgePosition: "UF",
            edgeOrientation: 0,
            initiallyConnected: false,
            cornerInTargetSlot: false,
            edgeInMiddleLayer: false,
        },
        selectionEvidence: {
            pairMoveCount: 2,
            remainingF2LMoveCount: 0,
            totalRouteMoveCount: 2,
            rotationCount: 0,
            preservesSolvedSlots: order === 2,
            pairWasAlreadyConnected: false,
            shortestAvailablePair: order === 1,
            selectedForGlobalRoute: order === 2,
            reasonCodes: [reasonCode],
        },
    };
}
