import {act, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import CubeNetPreview from "../CubeNetPreview";
import {CUBE_FACE_COLORS} from "../cubeFaceColors";
import {applyRenderMoves, renderStateSignature, solvedRenderCube} from "../cube/renderCubeState";
import {parseAlgorithm} from "../cube/notation";

describe("CubeNetPreview", () => {
    it("renders all six project-owned net faces using the shared cube colors", () => {
        render(<CubeNetPreview isPlaying={false}/>);

        expect(screen.getByRole("img", {name: "2D cube net preview"})).toBeInTheDocument();
        for (const [face, color] of Object.entries(CUBE_FACE_COLORS)) {
            const stickers = document.querySelectorAll(`[data-face="${face}"]`);
            expect(stickers).toHaveLength(9);
            expect(stickers[4]).toHaveAttribute("fill", color);
        }
    });

    it("separates face blocks with wider gutters than the sticker gaps", () => {
        render(<CubeNetPreview isPlaying={false}/>);
        const upperFaceStickers = document.querySelectorAll('[data-face="U"]');
        const frontFaceTop = document.querySelector('[data-face="F"]');
        const upperFaceBottom = upperFaceStickers[6];
        const upperFaceTop = upperFaceStickers[0];
        const upperY = Number(upperFaceBottom?.getAttribute("y"));
        const frontY = Number(frontFaceTop?.getAttribute("y"));
        const stickerY = Number(upperFaceTop?.getAttribute("y"));

        expect(frontY - (upperY + 0.93)).toBeGreaterThan(stickerY + 1 - (stickerY + 0.93));
    });

    it("renders scramble setup state and preserves playback metadata", () => {
        render(<CubeNetPreview setupAlgorithm="R U" algorithm="F2" stage="cross" isPlaying={false}/>);
        const preview = screen.getByRole("img", {name: "2D cube net preview"});

        expect(preview).toHaveAttribute("data-preview-setup", "R U");
        expect(preview).toHaveAttribute("data-preview-alg", "F2");
        expect(preview).toHaveAttribute("data-preview-stage", "cross");
        expect(preview).toHaveAttribute("data-render-state", renderStateSignature(applyRenderMoves(solvedRenderCube(), parseAlgorithm("R U"))));
        expect(preview).toHaveAttribute("data-render-move-index", "0");
    });

    it("advances solution playback move by move and reports completion", () => {
        vi.useFakeTimers();
        const onPlaybackComplete = vi.fn();
        render(<CubeNetPreview algorithm="R U" isPlaying onPlaybackComplete={onPlaybackComplete}/>);

        act(() => vi.advanceTimersByTime(320));
        act(() => vi.advanceTimersByTime(320));

        expect(screen.getByRole("img", {name: "2D cube net preview"})).toHaveAttribute("data-render-move-index", "2");
        expect(onPlaybackComplete).toHaveBeenCalledOnce();
        vi.useRealTimers();
    });
});
