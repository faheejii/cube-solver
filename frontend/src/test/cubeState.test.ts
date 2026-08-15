import {cube3x3x3} from "cubing/puzzles";
import {describe, expect, it} from "vitest";
import {
    applyCubeModelMoves,
    applyMoves,
    faceletsFromCubeModel,
    isSolved,
    solvedCube,
    solvedCubeModel,
    type CubeModel,
} from "../cube/cubeState";
import {moveSpec} from "../cube/moveGeometry";
import {formatMove, invertMoves, parseAlgorithm, parseMove} from "../cube/notation";

describe("cube notation", () => {
    it("parses normal, prime, double, wide, rotation, and slice moves", () => {
        expect(parseAlgorithm("R U' F2 rw x y' M E2 S")).toEqual([
            {face: "R", amount: 1, wide: false}, {face: "U", amount: 3, wide: false},
            {face: "F", amount: 2, wide: false}, {face: "R", amount: 1, wide: true},
            {face: "X", amount: 1, wide: false}, {face: "Y", amount: 3, wide: false},
            {face: "M", amount: 1, wide: false}, {face: "E", amount: 2, wide: false},
            {face: "S", amount: 1, wide: false},
        ]);
        expect(formatMove(parseMove("R'"))).toBe("R'");
        expect(formatMove(parseMove("x2"))).toBe("x2");
        expect(() => parseMove("Q")).toThrow("Unsupported cube move");
    });

    it("uses an exhaustive standard direction and layer specification", () => {
        const expected = {
            R: ["x", [1], -1], L: ["x", [-1], 1],
            U: ["y", [1], -1], D: ["y", [-1], 1],
            F: ["z", [1], -1], B: ["z", [-1], 1],
            M: ["x", [0], 1], E: ["y", [0], 1], S: ["z", [0], -1],
            X: ["x", [-1, 0, 1], -1], Y: ["y", [-1, 0, 1], -1], Z: ["z", [-1, 0, 1], -1],
        } as const;
        for (const [token, [axis, layers, quarterTurn]] of Object.entries(expected)) {
            expect(moveSpec(parseMove(token))).toEqual({axis, layers, quarterTurn});
        }
        expect(moveSpec(parseMove("r"))).toEqual({axis: "x", layers: [0, 1], quarterTurn: -1});
        expect(moveSpec(parseMove("l"))).toEqual({axis: "x", layers: [-1, 0], quarterTurn: 1});
        expect(moveSpec(parseMove("u"))).toEqual({axis: "y", layers: [0, 1], quarterTurn: -1});
        expect(moveSpec(parseMove("d"))).toEqual({axis: "y", layers: [-1, 0], quarterTurn: 1});
        expect(moveSpec(parseMove("f"))).toEqual({axis: "z", layers: [0, 1], quarterTurn: -1});
        expect(moveSpec(parseMove("b"))).toEqual({axis: "z", layers: [-1, 0], quarterTurn: 1});
    });

    it("moves R and R' in the standard directions with exact facelet fixtures", () => {
        const r = applyMoves(solvedCube(), parseAlgorithm("R"));
        const rPrime = applyMoves(solvedCube(), parseAlgorithm("R'"));

        expect(r.join("")).toBe("UUFUUFUUF" + "RRRRRRRRR" + "FFDFFDFFD" + "DDBDDBDDB" + "LLLLLLLLL" + "UBBUBBUBB");
        expect(rPrime.join("")).toBe("UUBUUBUUB" + "RRRRRRRRR" + "FFUFFUFFU" + "DDFDDFDDF" + "LLLLLLLLL" + "DBBDBBDBB");

        const rModel = applyCubeModelMoves(solvedCubeModel(), parseAlgorithm("R"));
        expect(rModel.cubies.find((cubie) => cubie.id === "1,1,1")?.position).toEqual({x: 1, y: 1, z: -1});
        expect(rModel.cubies.find((cubie) => cubie.id === "1,1,0")?.position).toEqual({x: 1, y: 0, z: -1});
        expect(faceletsFromCubeModel(rModel)).toEqual(r);
    });

    it("matches standard cubing.js wide, slice, and rotation equivalences", async () => {
        const equivalences = [
            ["r", "R M'"], ["l", "L M"], ["u", "U E'"], ["d", "D E"], ["f", "F S"], ["b", "B S'"],
            ["x", "R M' L'"], ["y", "U E' D'"], ["z", "F S B'"],
        ] as const;
        const kpuzzle = await cube3x3x3.kpuzzle();
        for (const [actual, equivalent] of equivalences) {
            expect(applyMoves(solvedCube(), parseAlgorithm(actual))).toEqual(applyMoves(solvedCube(), parseAlgorithm(equivalent)));
            expect(kpuzzle.defaultPattern().applyAlg(actual).isIdentical(kpuzzle.defaultPattern().applyAlg(equivalent))).toBe(true);
        }
    });

    it("matches cubing.js piece permutations for every supported quarter turn", async () => {
        const kpuzzle = await cube3x3x3.kpuzzle();
        for (const token of ["U", "D", "L", "R", "F", "B", "u", "d", "l", "r", "f", "b", "M", "E", "S", "x", "y", "z"]) {
            const actual = applyCubeModelMoves(solvedCubeModel(), parseAlgorithm(token));
            const expected = kpuzzle.defaultPattern().applyAlg(token).patternData;
            expect(piecePermutation(actual, EDGE_POSITIONS), `${token} edge permutation`).toEqual(expected.EDGES.pieces);
            expect(piecePermutation(actual, CORNER_POSITIONS), `${token} corner permutation`).toEqual(expected.CORNERS.pieces);
            expect(piecePermutation(actual, CENTER_POSITIONS), `${token} center permutation`).toEqual(expected.CENTERS.pieces);
        }
    });

    it("matches cubing.js after every prefix of the playback regression", async () => {
        const setup = "L D' B R D' R F R' L2 B L2 U2 F2 U2 B R2 B2 L2 F2";
        const algorithm = "z2 F R' L' F' L2 y U' R U' R' U R' U2 R U' R' U' R U L' U L U2 L' U L U L U2 L' U L U L' U' L F R' F R F2 L' R U R' F' R U R' U' R' F R2 U' R'";
        const kpuzzle = await cube3x3x3.kpuzzle();
        const tokens = [...parseAlgorithm(setup), ...parseAlgorithm(algorithm)];
        let actual = solvedCubeModel();
        let expected = kpuzzle.defaultPattern();
        for (let index = 0; index < tokens.length; index++) {
            actual = applyCubeModelMoves(actual, [tokens[index]]);
            expected = expected.applyMove(formatMove(tokens[index]));
            expect(piecePermutation(actual, EDGE_POSITIONS), `edge prefix ${index + 1}`).toEqual(expected.patternData.EDGES.pieces);
            expect(piecePermutation(actual, CORNER_POSITIONS), `corner prefix ${index + 1}`).toEqual(expected.patternData.CORNERS.pieces);
            expect(piecePermutation(actual, CENTER_POSITIONS), `center prefix ${index + 1}`).toEqual(expected.patternData.CENTERS.pieces);
        }
    });

    it("returns to solved after a move sequence and its inverse", () => {
        const moves = parseAlgorithm("R U F2 M' E S r u' f2 x y' z2");
        expect(applyMoves(applyMoves(solvedCube(), moves), invertMoves(moves))).toEqual(solvedCube());
    });

    it("returns to solved after four turns for every supported move", () => {
        for (const token of ["U", "D", "L", "R", "F", "B", "u", "d", "l", "r", "f", "b", "M", "E", "S", "x", "y", "z"]) {
            expect(isSolved(applyMoves(solvedCube(), parseAlgorithm(`${token} ${token} ${token} ${token}`)))).toBe(true);
        }
    });

    it("keeps the supplied scramble deterministic", () => {
        const scramble = "U' L U D B' D' L2 F R' B' U B2 U2 R2 L2 U F2 U F2 L2";
        const state = applyMoves(solvedCube(), parseAlgorithm(scramble));
        expect(state.join("")).toBe("LBLBUUUFDLLFDRRLRDRUFFFBUBUBLBRDDDFRDUFLLDBFRURBUBDFLR");
        expect(isSolved(state)).toBe(false);
        expect(state.filter((facelet) => facelet === "U")).toHaveLength(9);
    });
});

const EDGE_POSITIONS = [
    "0,1,1", "1,1,0", "0,1,-1", "-1,1,0",
    "0,-1,1", "1,-1,0", "0,-1,-1", "-1,-1,0",
    "1,0,1", "-1,0,1", "1,0,-1", "-1,0,-1",
] as const;

const CORNER_POSITIONS = [
    "1,1,1", "1,1,-1", "-1,1,-1", "-1,1,1",
    "1,-1,1", "-1,-1,1", "-1,-1,-1", "1,-1,-1",
] as const;

// cubing.js center orbit order is U, L, F, R, B, D.
const CENTER_POSITIONS = ["0,1,0", "-1,0,0", "0,0,1", "1,0,0", "0,0,-1", "0,-1,0"] as const;

function piecePermutation(model: CubeModel, positions: readonly string[]): number[] {
    const solvedIndex = new Map(positions.map((position, index) => [position, index]));
    return positions.map((position) => {
        const cubie = model.cubies.find((candidate) => vectorKey(candidate.position) === position);
        const index = cubie ? solvedIndex.get(cubie.id) : undefined;
        if (index === undefined) throw new Error(`Missing cubie at ${position}`);
        return index;
    });
}

function vectorKey(vector: {x: number; y: number; z: number}): string {
    return `${vector.x},${vector.y},${vector.z}`;
}
