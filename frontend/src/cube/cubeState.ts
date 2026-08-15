import type {Move} from "./notation";
import {matchesLayer, moveSpec, quarterTurns, rotateVector, type Vector} from "./moveGeometry";

export type Face = "U" | "R" | "F" | "D" | "L" | "B";
export type CubeState = readonly Face[];
export type CubeSticker = {color: Face; normal: Vector};
export type CubeCubie = {id: string; position: Vector; stickers: CubeSticker[]};
export type CubeModel = {cubies: CubeCubie[]};

const FACES: Face[] = ["U", "R", "F", "D", "L", "B"];
const FACE_INDEX = new Map(FACES.map((face, index) => [face, index]));

export function solvedCubeModel(): CubeModel {
    const cubies: CubeCubie[] = [];
    for (let x = -1; x <= 1; x++) for (let y = -1; y <= 1; y++) for (let z = -1; z <= 1; z++) {
        const stickers: CubeSticker[] = [];
        if (y === 1) stickers.push({color: "U", normal: {x: 0, y: 1, z: 0}});
        if (x === 1) stickers.push({color: "R", normal: {x: 1, y: 0, z: 0}});
        if (z === 1) stickers.push({color: "F", normal: {x: 0, y: 0, z: 1}});
        if (y === -1) stickers.push({color: "D", normal: {x: 0, y: -1, z: 0}});
        if (x === -1) stickers.push({color: "L", normal: {x: -1, y: 0, z: 0}});
        if (z === -1) stickers.push({color: "B", normal: {x: 0, y: 0, z: -1}});
        cubies.push({id: `${x},${y},${z}`, position: {x, y, z}, stickers});
    }
    return {cubies};
}

export function applyCubeModelMove(state: CubeModel, move: Move): CubeModel {
    const spec = moveSpec(move);
    const turns = quarterTurns(move);
    return {
        cubies: state.cubies.map((cubie) => !matchesLayer(cubie.position, move) ? cubie : {
            ...cubie,
            position: rotateVector(cubie.position, spec.axis, turns),
            stickers: cubie.stickers.map((sticker) => ({
                ...sticker,
                normal: rotateVector(sticker.normal, spec.axis, turns),
            })),
        }),
    };
}

export function applyCubeModelMoves(state: CubeModel, moves: Move[]): CubeModel {
    return moves.reduce(applyCubeModelMove, state);
}

export function faceletsFromCubeModel(state: CubeModel): CubeState {
    const facelets = Array<Face>(54);
    for (const cubie of state.cubies) {
        for (const sticker of cubie.stickers) {
            facelets[indexForSticker(cubie.position, sticker.normal)] = sticker.color;
        }
    }
    if (facelets.some((facelet) => facelet === undefined)) {
        throw new Error("Cube model does not contain all 54 facelets");
    }
    return facelets;
}

export function solvedCube(): CubeState {
    return faceletsFromCubeModel(solvedCubeModel());
}

export function applyMoves(state: CubeState, moves: Move[]): CubeState {
    return faceletsFromCubeModel(applyCubeModelMoves(cubeModelFromFacelets(state), moves));
}

export function applyMove(state: CubeState, move: Move): CubeState {
    return applyMoves(state, [move]);
}

export function isSolved(state: CubeState): boolean {
    return state.every((facelet, index) => facelet === FACES[Math.floor(index / 9)]);
}

export function stickerFaceForNormal(normal: Vector): Face | null {
    if (normal.y === 1) return "U";
    if (normal.x === 1) return "R";
    if (normal.z === 1) return "F";
    if (normal.y === -1) return "D";
    if (normal.x === -1) return "L";
    if (normal.z === -1) return "B";
    return null;
}

function cubeModelFromFacelets(facelets: CubeState): CubeModel {
    if (facelets.length !== 54) throw new Error(`Expected 54 facelets, received ${facelets.length}`);
    const cubies = new Map<string, CubeCubie>();
    for (let faceIndex = 0; faceIndex < FACES.length; faceIndex++) {
        for (let index = 0; index < 9; index++) {
            const {position, normal} = stickerCoordinate(FACES[faceIndex], index);
            const id = `${position.x},${position.y},${position.z}`;
            const cubie = cubies.get(id) ?? {id, position, stickers: []};
            cubie.stickers.push({color: facelets[faceIndex * 9 + index], normal});
            cubies.set(id, cubie);
        }
    }
    return {cubies: [...cubies.values()]};
}

function stickerCoordinate(face: Face, index: number): {position: Vector; normal: Vector} {
    const row = Math.floor(index / 3) - 1;
    const column = index % 3 - 1;
    switch (face) {
        case "U": return {position: {x: column, y: 1, z: row}, normal: {x: 0, y: 1, z: 0}};
        case "R": return {position: {x: 1, y: -row, z: -column}, normal: {x: 1, y: 0, z: 0}};
        case "F": return {position: {x: column, y: -row, z: 1}, normal: {x: 0, y: 0, z: 1}};
        case "D": return {position: {x: column, y: -1, z: -row}, normal: {x: 0, y: -1, z: 0}};
        case "L": return {position: {x: -1, y: -row, z: column}, normal: {x: -1, y: 0, z: 0}};
        case "B": return {position: {x: -column, y: -row, z: -1}, normal: {x: 0, y: 0, z: -1}};
    }
}

function indexForSticker(position: Vector, normal: Vector): number {
    const face = stickerFaceForNormal(normal);
    if (!face) throw new Error(`Invalid sticker normal ${JSON.stringify(normal)}`);
    const {x, y, z} = position;
    let row: number;
    let column: number;
    switch (face) {
        case "U": row = z; column = x; break;
        case "R": row = -y; column = -z; break;
        case "F": row = -y; column = x; break;
        case "D": row = -z; column = x; break;
        case "L": row = -y; column = z; break;
        case "B": row = -y; column = -x; break;
    }
    return FACE_INDEX.get(face)! * 9 + (row + 1) * 3 + column + 1;
}
