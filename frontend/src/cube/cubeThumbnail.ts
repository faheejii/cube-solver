import {stickerFaceForNormal, type CubeModel, type Face} from "./cubeState";

export type ThumbnailFace = Extract<Face, "U" | "R" | "F">;

export type ProjectedCubeSticker = {
    face: ThumbnailFace;
    color: Face;
    points: string;
    depth: number;
};

const FACE_DIRECTIONS: Record<ThumbnailFace, readonly [number, number, number]> = {
    U: [0, 1, 0],
    R: [1, 0, 0],
    F: [0, 0, 1],
};

const TANGENT_AXES: Record<ThumbnailFace, readonly [readonly [number, number, number], readonly [number, number, number]]> = {
    U: [[1, 0, 0], [0, 0, 1]],
    R: [[0, 1, 0], [0, 0, 1]],
    F: [[1, 0, 0], [0, 1, 0]],
};

const STICKER_HALF_SIZE = 0.45;
const HORIZONTAL_SCALE = 13;
const DEPTH_SCALE = 7;
const VERTICAL_SCALE = 16.5;
const CENTER_X = 50;
const CENTER_Y = 48;

/** Projects the visible U/R/F stickers into a fixed isometric SVG view. */
export function projectCubeThumbnail(state: CubeModel): ProjectedCubeSticker[] {
    const stickers: ProjectedCubeSticker[] = [];

    for (const cubie of state.cubies) {
        for (const sticker of cubie.stickers) {
            const face = stickerFaceForNormal(sticker.normal);
            if (!face || !(face in FACE_DIRECTIONS)) continue;

            const visibleFace = face as ThumbnailFace;
            const [normalX, normalY, normalZ] = FACE_DIRECTIONS[visibleFace];
            const [axisA, axisB] = TANGENT_AXES[visibleFace];
            const corners = [
                [-STICKER_HALF_SIZE, -STICKER_HALF_SIZE],
                [STICKER_HALF_SIZE, -STICKER_HALF_SIZE],
                [STICKER_HALF_SIZE, STICKER_HALF_SIZE],
                [-STICKER_HALF_SIZE, STICKER_HALF_SIZE],
            ] as const;
            const points = corners.map(([offsetA, offsetB]) => {
                const x = cubie.position.x + normalX * 0.5 + axisA[0] * offsetA + axisB[0] * offsetB;
                const y = cubie.position.y + normalY * 0.5 + axisA[1] * offsetA + axisB[1] * offsetB;
                const z = cubie.position.z + normalZ * 0.5 + axisA[2] * offsetA + axisB[2] * offsetB;
                return projectPoint(x, y, z);
            });

            stickers.push({
                face: visibleFace,
                color: sticker.color,
                points: points.map(({x, y}) => `${x.toFixed(2)},${y.toFixed(2)}`).join(" "),
                depth: cubie.position.x + cubie.position.y + cubie.position.z + normalX + normalY + normalZ,
            });
        }
    }

    return stickers.sort((left, right) => left.depth - right.depth);
}

function projectPoint(x: number, y: number, z: number) {
    return {
        x: CENTER_X + (x - z) * HORIZONTAL_SCALE,
        y: CENTER_Y + (x + z) * DEPTH_SCALE - y * VERTICAL_SCALE,
    };
}
