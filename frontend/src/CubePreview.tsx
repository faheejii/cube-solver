import {useEffect, useRef, useState} from "react";
import * as THREE from "three";
import {OrbitControls} from "three/addons/controls/OrbitControls.js";
import {parseAlgorithm as parseNotation, type Move} from "./cube/notation";
import {applyRenderMove, applyRenderMoves, materialsForCubie, renderStateSignature, solvedRenderCube, type RenderCubeState, type RenderCubie} from "./cube/renderCubeState";
import {matchesLayer, moveSpec, rotationRadians, type MoveSpec} from "./cube/moveGeometry";
import "./styles/cube-preview.css";

type Props = {
    setupAlgorithm?: string;
    algorithm?: string;
    stage?: string;
    compact?: boolean;
    playbackSpeed?: number;
    isPlaying?: boolean;
    onPlaybackComplete?: () => void;
    interactiveView?: boolean;
    "data-playback-alg"?: string;
};
type VisualCubie = {mesh: THREE.Mesh; id: string};

const COLORS: Record<string, number> = {U: 0xf5f5f5, D: 0xf5d547, F: 0x35b86b, B: 0x3d72d8, R: 0xd94b4b, L: 0xf08b35};

export default function CubePreview({
    setupAlgorithm = "",
    algorithm = "",
    stage = "",
    compact = false,
    playbackSpeed = 1,
    isPlaying,
    onPlaybackComplete,
    interactiveView = false,
    "data-playback-alg": playbackAttribute,
}: Props) {
    const hostRef = useRef<HTMLDivElement>(null);
    const [unsupported, setUnsupported] = useState(false);
    const isPlayingRef = useRef(isPlaying ?? true);
    const playbackCompleteRef = useRef(onPlaybackComplete);
    const pausedAtRef = useRef<number | null>(null);

    useEffect(() => {
        const next = isPlaying ?? true;
        if (isPlayingRef.current !== next) {
            isPlayingRef.current = next;
            pausedAtRef.current = performance.now();
        }
    }, [isPlaying]);

    useEffect(() => {
        playbackCompleteRef.current = onPlaybackComplete;
    }, [onPlaybackComplete]);

    useEffect(() => {
        const host = hostRef.current;
        if (!host) return;
        let renderer: THREE.WebGLRenderer;
        try { renderer = new THREE.WebGLRenderer({antialias: true, alpha: true}); }
        catch { setUnsupported(true); return; }
        setUnsupported(false);

        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(32, 1, 0.1, 100);
        camera.position.set(5.2, 4.4, 6.5);
        camera.lookAt(0, 0, 0);
        scene.add(new THREE.AmbientLight(0xffffff, 1.8));
        const key = new THREE.DirectionalLight(0xffffff, 2.2);
        key.position.set(4, 6, 8);
        scene.add(key);
        renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
        renderer.setClearColor(0x000000, 0);
        renderer.domElement.setAttribute("aria-label", "Drag to rotate the cube view");
        host.appendChild(renderer.domElement);
        const orbitControls = interactiveView ? new OrbitControls(camera, renderer.domElement) : null;
        if (orbitControls) {
            orbitControls.enableDamping = true;
            orbitControls.dampingFactor = 0.08;
            orbitControls.enableZoom = false;
            orbitControls.enablePan = false;
            orbitControls.minPolarAngle = 0.2;
            orbitControls.maxPolarAngle = Math.PI - 0.2;
            orbitControls.target.set(0, 0, 0);
            orbitControls.update();
        }

        const cubeGroup = new THREE.Group();
        scene.add(cubeGroup);
        const visualCubies: VisualCubie[] = [];
        const initial = applyRenderMoves(solvedRenderCube(), parseNotation(setupAlgorithm));
        let logicalState: RenderCubeState = initial;
        host.dataset.renderState = renderStateSignature(logicalState);
        host.dataset.renderStage = stage;

        for (const cubie of logicalState.cubies) {
            const mesh = new THREE.Mesh(new THREE.BoxGeometry(.96, .96, .96), stickerMaterials(cubie));
            cubeGroup.add(mesh);
            visualCubies.push({mesh, id: cubie.id});
        }
        syncMeshes(visualCubies, logicalState, cubeGroup);

        const moves = parseNotation(algorithm);
        let frame = 0;
        let index = 0;
        let playbackComplete = false;
        let active: {group: THREE.Group; move: Move; spec: MoveSpec; cubies: VisualCubie[]; start: number} | null = null;
        const resize = () => {
            const rect = host.getBoundingClientRect();
            const size = Math.max(120, Math.min(rect.width, rect.height || rect.width));
            renderer.setSize(size, size, false);
            camera.aspect = 1;
            camera.updateProjectionMatrix();
        };
        const begin = () => {
            if (index >= moves.length) return;
            const move = moves[index++];
            const spec = moveSpec(move);
            const selectedIds = new Set(logicalState.cubies.filter((cubie) => matchesLayer(cubie.position, move)).map((cubie) => cubie.id));
            const selected = visualCubies.filter((cubie) => selectedIds.has(cubie.id));
            const group = new THREE.Group();
            cubeGroup.add(group);
            selected.forEach((cubie) => { cubeGroup.remove(cubie.mesh); group.attach(cubie.mesh); });
            active = {group, move, spec, cubies: selected, start: performance.now()};
            host.dataset.renderMoveIndex = String(index - 1);
        };
        const completePlayback = () => {
            if (playbackComplete) return;
            playbackComplete = true;
            playbackCompleteRef.current?.();
        };
        const tick = (now: number) => {
            if (!isPlayingRef.current) {
                pausedAtRef.current ??= now;
                orbitControls?.update();
                renderer.render(scene, camera);
                frame = requestAnimationFrame(tick);
                return;
            }
            if (pausedAtRef.current !== null) {
                if (active) active.start += now - pausedAtRef.current;
                pausedAtRef.current = null;
            }
            if (!active) begin();
            if (active) {
                const progress = Math.min(1, (now - active.start) / (320 / playbackSpeed));
                active.group.rotation.set(0, 0, 0);
                active.group.rotation[active.spec.axis] = rotationRadians(active.move) * progress;
                if (progress === 1) {
                    const done = active;
                    logicalState = applyRenderMove(logicalState, done.move);
                    done.group.rotation.set(0, 0, 0);
                    done.cubies.forEach((cubie) => cubeGroup.attach(cubie.mesh));
                    done.group.removeFromParent();
                    syncMeshes(visualCubies, logicalState, cubeGroup);
                    host.dataset.renderState = renderStateSignature(logicalState);
                    host.dataset.renderMoveIndex = String(index);
                    active = null;
                    if (index >= moves.length) completePlayback();
                }
            } else if (index >= moves.length) {
                completePlayback();
            }
            orbitControls?.update();
            renderer.render(scene, camera);
            frame = requestAnimationFrame(tick);
        };

        resize();
        window.addEventListener("resize", resize);
        frame = requestAnimationFrame(tick);
        return () => {
            cancelAnimationFrame(frame);
            window.removeEventListener("resize", resize);
            orbitControls?.dispose();
            renderer.dispose();
            host.replaceChildren();
            visualCubies.forEach(({mesh}) => {
                mesh.geometry.dispose();
                (Array.isArray(mesh.material) ? mesh.material : [mesh.material]).forEach((material) => material.dispose());
            });
        };
    }, [setupAlgorithm, algorithm, playbackSpeed, stage, interactiveView]);

    return <div ref={hostRef} className={compact ? "custom-cube-preview compact" : "custom-cube-preview"} aria-label={interactiveView ? "Interactive cube preview. Drag to rotate the cube view." : "Cube preview"} data-preview-setup={setupAlgorithm} data-preview-alg={algorithm} data-preview-stage={stage} data-playback-alg={playbackAttribute ?? algorithm} data-playback-state={isPlaying ?? true ? "playing" : "paused"} data-interactive-view={interactiveView ? "true" : "false"}>{unsupported && <span className="cube-preview-fallback">3D preview unavailable in this browser.</span>}</div>;
}

function syncMeshes(visualCubies: VisualCubie[], state: RenderCubeState, cubeGroup: THREE.Group) {
    for (const visual of visualCubies) {
        const cubie = state.cubies.find((candidate) => candidate.id === visual.id);
        if (!cubie) continue;
        cubeGroup.attach(visual.mesh);
        visual.mesh.position.set(cubie.position.x * 1.02, cubie.position.y * 1.02, cubie.position.z * 1.02);
        visual.mesh.quaternion.identity();
        const oldMaterials = Array.isArray(visual.mesh.material) ? visual.mesh.material : [visual.mesh.material];
        oldMaterials.forEach((material) => material.dispose());
        visual.mesh.material = stickerMaterials(cubie);
    }
}

function stickerMaterials(cubie: RenderCubie) {
    return materialsForCubie(cubie).map((face) => new THREE.MeshStandardMaterial({color: face ? COLORS[face] : 0x151b24, roughness: .7, metalness: .05}));
}
