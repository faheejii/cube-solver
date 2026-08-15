import {describe, expect, it} from "vitest";
import {faceletsFromCubeModel, isSolved, solvedCube} from "../cube/cubeState";
import {applyRenderMove, applyRenderMoves, renderStateSignature, solvedRenderCube} from "../cube/renderCubeState";
import {invertMoves, parseAlgorithm} from "../cube/notation";

describe("render cube state", () => {
    it("starts solved and applies setup through the shared move model", () => {
        const setup = parseAlgorithm("R U F2");
        const state = applyRenderMoves(solvedRenderCube(), setup);
        expect(renderStateSignature(state)).toBe(faceletsFromCubeModel(state).join(""));
        expect(renderStateSignature(solvedRenderCube())).toBe(solvedCube().join(""));
    });

    it("keeps render state synchronized for all supported move families", () => {
        const tokens = ["U", "R'", "F2", "D", "L'", "B2", "Uw", "rw'", "M", "E'", "S2", "X", "Y'", "Z2"];
        for (const token of tokens) {
            const move = parseAlgorithm(token)[0];
            const next = applyRenderMove(solvedRenderCube(), move);
            expect(renderStateSignature(next)).toBe(faceletsFromCubeModel(next).join(""));
        }
    });

    it("tracks the exact R cubie and sticker state without a parallel facelet mutator", () => {
        const state = applyRenderMoves(solvedRenderCube(), parseAlgorithm("R"));
        expect(state.cubies.find((cubie) => cubie.id === "1,1,1")?.position).toEqual({x: 1, y: 1, z: -1});
        expect(renderStateSignature(state)).toBe("UUFUUFUUF" + "RRRRRRRRR" + "FFDFFDFFD" + "DDBDDBDDB" + "LLLLLLLLL" + "UBBUBBUBB");
    });

    it("returns render state to solved after inverse playback", () => {
        const moves = parseAlgorithm("U' L U D B' D' L2 F R' B' U B2 U2 R2 L2 U F2 U F2 L2");
        const state = applyRenderMoves(applyRenderMoves(solvedRenderCube(), moves), invertMoves(moves));
        expect(isSolved(faceletsFromCubeModel(state))).toBe(true);
        expect(renderStateSignature(state)).toBe(solvedCube().join(""));
    });

    it("returns render state to solved after four quarter turns", () => {
        for (const token of ["R", "U", "F", "D", "L", "B", "M", "E", "S", "X", "Y", "Z"]) {
            const state = applyRenderMoves(solvedRenderCube(), parseAlgorithm(`${token} ${token} ${token} ${token}`));
            expect(isSolved(faceletsFromCubeModel(state))).toBe(true);
        }
    });
});
