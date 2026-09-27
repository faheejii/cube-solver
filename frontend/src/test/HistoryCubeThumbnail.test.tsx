import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import HistoryCubeThumbnail from "../HistoryCubeThumbnail";
import {CUBE_FACE_COLORS} from "../cubeFaceColors";
import {projectCubeThumbnail} from "../cube/cubeThumbnail";
import {applyRenderMoves, renderStateSignature, solvedRenderCube} from "../cube/renderCubeState";
import {parseAlgorithm} from "../cube/notation";

function averagePoint(svg: SVGSVGElement, face: string) {
    const points = [...svg.querySelectorAll(`[data-face="${face}"]`)].flatMap((polygon) =>
        (polygon.getAttribute("points") ?? "").split(" ").map((point) => point.split(",").map(Number)),
    );
    return {
        x: points.reduce((sum, [x]) => sum + x, 0) / points.length,
        y: points.reduce((sum, [, y]) => sum + y, 0) / points.length,
    };
}

describe("HistoryCubeThumbnail", () => {
    it("shows the solved U/R/F faces with the shared colors and fixed viewBox", () => {
        render(<HistoryCubeThumbnail scramble=""/>);
        const svg = screen.getByTestId("history-cube-thumbnail");

        expect(svg).toHaveAttribute("viewBox", "0 0 100 96");
        expect(svg.querySelectorAll("polygon")).toHaveLength(27);
        for (const face of ["U", "R", "F"] as const) {
            const stickers = svg.querySelectorAll(`[data-face="${face}"]`);
            expect(stickers).toHaveLength(9);
            expect(stickers[4]).toHaveAttribute("data-color", face);
            expect(stickers[4]).toHaveAttribute("fill", CUBE_FACE_COLORS[face]);
        }
        expect(svg.querySelector("canvas")).not.toBeInTheDocument();
    });

    it("projects U above the side faces and the R face to the right of F", () => {
        const {container} = render(<HistoryCubeThumbnail scramble=""/>);
        const svg = container.querySelector("svg")!;
        const upper = averagePoint(svg, "U");
        const front = averagePoint(svg, "F");
        const right = averagePoint(svg, "R");

        expect(upper.y).toBeLessThan(front.y);
        expect(upper.y).toBeLessThan(right.y);
        expect(front.x).toBeLessThan(right.x);
    });

    it.each(["R U F2", "rw U' x", "M E2 S'"])(
        "uses the cube model for scramble state %s",
        (scramble) => {
            const state = applyRenderMoves(solvedRenderCube(), parseAlgorithm(scramble));
            const expectedStickers = projectCubeThumbnail(state).map(({face, color}) => ({face, color}));
            const {container} = render(<HistoryCubeThumbnail scramble={scramble}/>);
            const svg = screen.getByTestId("history-cube-thumbnail");
            const actualStickers = [...svg.querySelectorAll("polygon")].map((polygon) => ({
                face: polygon.getAttribute("data-face"),
                color: polygon.getAttribute("data-color"),
            }));

            expect(svg).toHaveAttribute("data-preview-setup", scramble);
            expect(svg).toHaveAttribute("data-render-state", renderStateSignature(state));
            expect(actualStickers).toEqual(expectedStickers);
            expect(container.querySelector("canvas")).not.toBeInTheDocument();
        },
    );

    it("renders a fixed-size-safe fallback for invalid stored notation", () => {
        render(<HistoryCubeThumbnail scramble="R not-a-move U"/>);

        expect(screen.getByRole("img", {name: "Cube preview unavailable"})).toHaveClass("history-cube-thumbnail-fallback");
        expect(screen.queryByTestId("history-cube-thumbnail")).not.toBeInTheDocument();
    });
});
