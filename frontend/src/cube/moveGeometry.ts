import type {Move} from "./notation";

export type Axis = "x" | "y" | "z";
export type Vector = {x: number; y: number; z: number};
export type MoveSpec = {
    axis: Axis;
    layers: readonly number[];
    quarterTurn: 1 | -1;
};

const ALL_LAYERS = [-1, 0, 1] as const;

// Signs use Three.js/right-hand-rule axes. They intentionally follow standard
// cubing notation as interpreted while looking directly at the named face.
const MOVE_SPECS: Record<Move["face"], MoveSpec> = {
    R: {axis: "x", layers: [1], quarterTurn: -1},
    L: {axis: "x", layers: [-1], quarterTurn: 1},
    U: {axis: "y", layers: [1], quarterTurn: -1},
    D: {axis: "y", layers: [-1], quarterTurn: 1},
    F: {axis: "z", layers: [1], quarterTurn: -1},
    B: {axis: "z", layers: [-1], quarterTurn: 1},
    M: {axis: "x", layers: [0], quarterTurn: 1},
    E: {axis: "y", layers: [0], quarterTurn: 1},
    S: {axis: "z", layers: [0], quarterTurn: -1},
    X: {axis: "x", layers: ALL_LAYERS, quarterTurn: -1},
    Y: {axis: "y", layers: ALL_LAYERS, quarterTurn: -1},
    Z: {axis: "z", layers: ALL_LAYERS, quarterTurn: -1},
};

const WIDE_LAYERS: Partial<Record<Move["face"], readonly number[]>> = {
    R: [0, 1], L: [-1, 0], U: [0, 1], D: [-1, 0], F: [0, 1], B: [-1, 0],
};

export function moveSpec(move: Move): MoveSpec {
    const base = MOVE_SPECS[move.face];
    return move.wide && WIDE_LAYERS[move.face]
        ? {...base, layers: WIDE_LAYERS[move.face]!}
        : base;
}

export function matchesLayer(position: Vector, move: Move): boolean {
    const spec = moveSpec(move);
    return spec.layers.includes(position[spec.axis]);
}

export function quarterTurns(move: Move): number {
    const amount = move.amount === 3 ? -1 : move.amount;
    return amount * moveSpec(move).quarterTurn;
}

export function rotationRadians(move: Move): number {
    return quarterTurns(move) * Math.PI / 2;
}

export function rotateVector(vector: Vector, axis: Axis, amount: number): Vector {
    let result = {...vector};
    const turns = ((amount % 4) + 4) % 4;
    for (let index = 0; index < turns; index++) {
        result = axis === "x"
            ? {x: result.x, y: -result.z, z: result.y}
            : axis === "y"
                ? {x: result.z, y: result.y, z: -result.x}
                : {x: -result.y, y: result.x, z: result.z};
    }
    return result;
}
