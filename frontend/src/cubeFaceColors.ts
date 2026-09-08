export const CUBE_FACE_COLORS = {
    U: "#f5f5f5",
    D: "#f5d547",
    F: "#35b86b",
    B: "#3d72d8",
    R: "#d94b4b",
    L: "#f08b35",
} as const;

export type CubeFace = keyof typeof CUBE_FACE_COLORS;
