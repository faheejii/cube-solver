import {useEffect, useMemo, useRef, useState, type ComponentProps} from "react";
import {applyRenderMoves, renderStateSignature, solvedRenderCube} from "./cube/renderCubeState";
import {faceletsFromCubeModel} from "./cube/cubeState";
import {parseAlgorithm} from "./cube/notation";
import {CUBE_FACE_COLORS} from "./cubeFaceColors";
import type CubePreview from "./CubePreview";
import "./styles/cube-preview.css";

type Props = ComponentProps<typeof CubePreview>;
const FACELET_FACE_ORDER = ["U", "R", "F", "D", "L", "B"] as const;
const FACE_GAP = 0.22;
const FACE_STEP = 3 + FACE_GAP;

const NET_FACES = [
    {face: "U", x: FACE_STEP, y: 0},
    {face: "L", x: 0, y: FACE_STEP},
    {face: "F", x: FACE_STEP, y: FACE_STEP},
    {face: "R", x: FACE_STEP * 2, y: FACE_STEP},
    {face: "B", x: FACE_STEP * 3, y: FACE_STEP},
    {face: "D", x: FACE_STEP, y: FACE_STEP * 2},
] as const;

export default function CubeNetPreview({
                                           setupAlgorithm = "",
                                           algorithm = "",
                                           stage = "",
                                           compact = false,
                                           playbackSpeed = 1,
                                           isPlaying,
                                           onPlaybackComplete,
                                           "data-playback-alg": playbackAttribute,
                                       }: Props) {
    const moves = useMemo(() => parseAlgorithm(algorithm), [algorithm]);
    const setupState = useMemo(
        () => applyRenderMoves(solvedRenderCube(), parseAlgorithm(setupAlgorithm)),
        [setupAlgorithm],
    );
    const [moveIndex, setMoveIndex] = useState(0);
    const completionCallback = useRef(onPlaybackComplete);
    const completed = useRef(false);
    const currentState = useMemo(
        () => applyRenderMoves(setupState, moves.slice(0, moveIndex)),
        [setupState, moves, moveIndex],
    );
    const facelets = faceletsFromCubeModel(currentState);

    useEffect(() => {
        completionCallback.current = onPlaybackComplete;
    }, [onPlaybackComplete]);

    useEffect(() => {
        setMoveIndex(0);
        completed.current = false;
    }, [setupAlgorithm, algorithm, stage]);

    useEffect(() => {
        if (isPlaying === false) return;
        if (moveIndex >= moves.length) {
            if (!completed.current) {
                completed.current = true;
                completionCallback.current?.();
            }
            return;
        }
        const timer = window.setTimeout(
            () => setMoveIndex((current) => Math.min(current + 1, moves.length)),
            320 / Math.max(0.1, playbackSpeed),
        );
        return () => window.clearTimeout(timer);
    }, [isPlaying, moveIndex, moves.length, playbackSpeed]);

    return (
        <div
            className={compact ? "custom-cube-preview compact cube-net-preview" : "custom-cube-preview cube-net-preview"}
            role="img"
            aria-label="2D cube net preview"
            data-preview-setup={setupAlgorithm}
            data-preview-alg={algorithm}
            data-preview-stage={stage}
            data-playback-alg={playbackAttribute ?? algorithm}
            data-playback-state={isPlaying ?? true ? "playing" : "paused"}
            data-interactive-view="false"
            data-render-state={renderStateSignature(currentState)}
            data-render-move-index={moveIndex}
        >
            <svg className="cube-net-svg" viewBox={`0 0 ${3 + FACE_STEP * 3} ${3 + FACE_STEP * 2}`} role="presentation" aria-hidden="true">
                {NET_FACES.flatMap(({face, x, y}) =>
                    Array.from({length: 9}, (_, index) => {
                        const color = facelets[FACELET_FACE_ORDER.indexOf(face) * 9 + index];
                        return (
                            <rect
                                key={`${face}-${index}`}
                                x={x + (index % 3) + 0.035}
                                y={y + Math.floor(index / 3) + 0.035}
                                width="0.93"
                                height="0.93"
                                rx="0.08"
                                fill={CUBE_FACE_COLORS[color]}
                                data-face={face}
                                data-color={color}
                            />
                        );
                    }),
                )}
            </svg>
        </div>
    );
}
