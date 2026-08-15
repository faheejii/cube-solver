import {useEffect, useRef, useState} from "react";

type Props = {
    setupAlgorithm: string;
    algorithm: string;
    stage: string;
    playbackSpeed: number;
    moveIndex?: number;
    moveCount?: number;
};

type PlayerInstance = HTMLElement & {
    pause?: () => void;
    jumpToStart?: (options: {flash: boolean}) => void;
};

type PlayerConstructor = new (config: {
    puzzle: string;
    experimentalSetupAlg: string;
    alg: string;
    visualization: "2D";
    tempoScale: number;
    background: "none";
    controlPanel: "none";
    hintFacelets: "none";
}) => PlayerInstance;

type ReferenceStatus = "loading" | "ready" | "error";

export default function ReferenceCubePlayer({setupAlgorithm, algorithm, stage, playbackSpeed, moveIndex = 0, moveCount = 0}: Props) {
    const hostRef = useRef<HTMLDivElement>(null);
    const playerRef = useRef<PlayerInstance | null>(null);
    const [status, setStatus] = useState<ReferenceStatus>("loading");

    useEffect(() => {
        let active = true;
        const host = hostRef.current;
        if (!host) return;
        setStatus("loading");
        host.replaceChildren();

        void import("cubing/twisty")
            .then(({TwistyPlayer}) => {
                if (!active) return;
                const player = new (TwistyPlayer as unknown as PlayerConstructor)({
                    puzzle: "3x3x3",
                    experimentalSetupAlg: setupAlgorithm,
                    alg: algorithm,
                    visualization: "2D",
                    tempoScale: playbackSpeed,
                    background: "none",
                    controlPanel: "none",
                    hintFacelets: "none",
                });
                playerRef.current = player;
                player.setAttribute("data-reference-instance", "true");
                host.appendChild(player);
                player.pause?.();
                player.jumpToStart?.({flash: false});
                requestAnimationFrame(() => {
                    if (active) setStatus("ready");
                });
            })
            .catch(() => {
                if (active) setStatus("error");
            });

        return () => {
            active = false;
            playerRef.current?.pause?.();
            if (playerRef.current?.isConnected) playerRef.current.remove();
            playerRef.current = null;
        };
    }, [setupAlgorithm, algorithm, stage, playbackSpeed]);

    return (
        <div
            className="reference-cube-player"
            data-reference-player="cubing.js"
            data-reference-stage={stage}
            data-reference-setup={setupAlgorithm}
            data-reference-alg={algorithm}
            data-reference-move-index={moveIndex}
            data-reference-move-count={moveCount}
            data-reference-status={status}
            data-testid="reference-panel"
        >
            <div className="reference-cube-heading">
                <strong>cubing.js 2D reference</strong>
                <span>Stage: {stage} · Move {moveIndex} / {moveCount}</span>
            </div>
            <div ref={hostRef} className="reference-cube-host" data-reference-host="true" />
            {status === "loading" ? <p className="reference-cube-status">Loading reference...</p> : null}
            {status === "error" ? <p className="reference-cube-status error">Reference player failed to load.</p> : null}
            {status === "ready" ? <p className="reference-cube-status">Paused at identical prefix</p> : null}
            <div className="reference-cube-metadata">
                <span>Setup: {setupAlgorithm || "Solved cube"}</span>
                <span>Algorithm: {algorithm || "No moves"}</span>
            </div>
        </div>
    );
}
