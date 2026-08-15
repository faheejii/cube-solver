import {
    applyCubeModelMove,
    applyCubeModelMoves,
    faceletsFromCubeModel,
    solvedCubeModel,
    type CubeCubie,
    type CubeModel,
    type CubeSticker,
    type Face,
} from "./cubeState";
import type {Move} from "./notation";
import type {Vector} from "./moveGeometry";

export type RenderSticker = CubeSticker;
export type RenderCubie = CubeCubie;
export type RenderCubeState = CubeModel;

const MATERIAL_NORMALS: Vector[] = [
    {x: 1, y: 0, z: 0}, {x: -1, y: 0, z: 0},
    {x: 0, y: 1, z: 0}, {x: 0, y: -1, z: 0},
    {x: 0, y: 0, z: 1}, {x: 0, y: 0, z: -1},
];

export function solvedRenderCube(): RenderCubeState {
    return solvedCubeModel();
}

export function applyRenderMove(state: RenderCubeState, move: Move): RenderCubeState {
    return applyCubeModelMove(state, move);
}

export function applyRenderMoves(state: RenderCubeState, moves: Move[]): RenderCubeState {
    return applyCubeModelMoves(state, moves);
}

export function renderStateSignature(state: RenderCubeState): string {
    return faceletsFromCubeModel(state).join("");
}

export function materialsForCubie(cubie: RenderCubie): (Face | null)[] {
    return MATERIAL_NORMALS.map((normal) => cubie.stickers.find((sticker) =>
        sticker.normal.x === normal.x && sticker.normal.y === normal.y && sticker.normal.z === normal.z,
    )?.color ?? null);
}
