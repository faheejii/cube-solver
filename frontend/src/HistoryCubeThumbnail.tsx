import {useMemo} from "react";
import {applyRenderMoves, renderStateSignature, solvedRenderCube} from "./cube/renderCubeState";
import {parseAlgorithm} from "./cube/notation";
import {projectCubeThumbnail} from "./cube/cubeThumbnail";
import {CUBE_FACE_COLORS} from "./cubeFaceColors";

const VIEW_BOX = "0 0 100 96";

export default function HistoryCubeThumbnail({scramble}: {scramble: string}) {
    const projection = useMemo(() => {
        try {
            const state = applyRenderMoves(solvedRenderCube(), parseAlgorithm(scramble));
            return {stateSignature: renderStateSignature(state), stickers: projectCubeThumbnail(state)};
        } catch {
            return null;
        }
    }, [scramble]);

    if (!projection) {
        return <span className="history-cube-thumbnail-fallback" role="img" aria-label="Cube preview unavailable"/>;
    }

    return (
        <svg
            className="history-cube-thumbnail"
            viewBox={VIEW_BOX}
            role="img"
            aria-label={`Scrambled cube preview: ${scramble}`}
            data-preview-setup={scramble}
            data-render-state={projection.stateSignature}
            data-testid="history-cube-thumbnail"
        >
            {projection.stickers.map((sticker, index) => (
                <polygon
                    key={`${sticker.face}-${index}`}
                    points={sticker.points}
                    fill={CUBE_FACE_COLORS[sticker.color]}
                    stroke="#14242b"
                    strokeWidth="0.8"
                    strokeLinejoin="round"
                    data-face={sticker.face}
                    data-color={sticker.color}
                />
            ))}
        </svg>
    );
}
