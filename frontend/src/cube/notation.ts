export type Move = {
    face: "U" | "D" | "L" | "R" | "F" | "B" | "M" | "E" | "S" | "X" | "Y" | "Z";
    amount: 1 | 2 | 3;
    wide: boolean;
};

const MOVE_PATTERN = /^([UDLRFBMESXYZxyzudlrfb])([wW]?)(2|')?$/;

export function parseAlgorithm(algorithm: string): Move[] {
    return algorithm.trim() === ""
        ? []
        : algorithm.trim().split(/\s+/).map(parseMove);
}

export function parseMove(token: string): Move {
    const match = MOVE_PATTERN.exec(token);
    if (!match) {
        throw new Error(`Unsupported cube move: ${token}`);
    }
    const rawFace = match[1];
    const face = rawFace.toUpperCase() as Move["face"];
    const suffix = match[3];
    return {
        face,
        amount: suffix === "2" ? 2 : suffix === "'" ? 3 : 1,
        wide: match[2] !== "" || rawFace === rawFace.toLowerCase() && "udlrfb".includes(rawFace),
    };
}

export function invertMoves(moves: Move[]): Move[] {
    return [...moves].reverse().map((move) => ({...move, amount: (4 - move.amount) as Move["amount"] || 4}));
}

export function formatMove(move: Move): string {
    const face = "XYZ".includes(move.face)
        ? move.face.toLowerCase()
        : move.wide && "UDLRFB".includes(move.face) ? `${move.face}w` : move.face;
    return `${face}${move.amount === 2 ? "2" : move.amount === 3 ? "'" : ""}`;
}
