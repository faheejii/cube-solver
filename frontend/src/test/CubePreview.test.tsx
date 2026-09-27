import {fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {parseAlgorithm} from "../cube/notation";
import {applyRenderMoves, renderStateSignature, solvedRenderCube} from "../cube/renderCubeState";

const orbit = vi.hoisted(() => ({instances: [] as Array<Record<string, unknown>>}));
const renderers = vi.hoisted(() => ({instances: [] as Array<Record<string, unknown>>}));

vi.mock("three/addons/controls/OrbitControls.js", () => ({
    OrbitControls: class {
        enableDamping = false;
        dampingFactor = 0;
        enableZoom = true;
        enablePan = true;
        minPolarAngle = 0;
        maxPolarAngle = 0;
        target = {set: vi.fn()};
        update = vi.fn();
        dispose = vi.fn();

        constructor() {
            orbit.instances.push(this as unknown as Record<string, unknown>);
        }
    },
}));

vi.mock("three", async (importOriginal) => {
    const actual = await importOriginal<typeof import("three")>();
    return {
        ...actual,
        WebGLRenderer: class {
            domElement = document.createElement("canvas");
            setPixelRatio = vi.fn();
            setClearColor = vi.fn();
            setSize = vi.fn();
            render = vi.fn();
            dispose = vi.fn();
            forceContextLoss = vi.fn();

            constructor() {
                renderers.instances.push(this as unknown as Record<string, unknown>);
            }
        },
    };
});

import CubePreview from "../CubePreview";

afterEach(() => {
    orbit.instances.length = 0;
    renderers.instances.length = 0;
});

describe("CubePreview interactive view", () => {
    it("creates and configures orbit controls only when interaction is enabled", () => {
        const {unmount} = render(<CubePreview interactiveView isPlaying={false}/>);

        expect(screen.getByLabelText("Interactive cube preview. Drag to rotate the cube view.")).toHaveAttribute("data-interactive-view", "true");
        expect(orbit.instances).toHaveLength(1);
        const controls = orbit.instances[0] as {
            enableDamping: boolean;
            dampingFactor: number;
            enableZoom: boolean;
            enablePan: boolean;
            minPolarAngle: number;
            maxPolarAngle: number;
            dispose: ReturnType<typeof vi.fn>;
        };
        expect(controls.enableDamping).toBe(true);
        expect(controls.dampingFactor).toBe(0.08);
        expect(controls.enableZoom).toBe(false);
        expect(controls.enablePan).toBe(false);
        expect(controls.minPolarAngle).toBe(0.2);
        expect(controls.maxPolarAngle).toBeCloseTo(Math.PI - 0.2);

        unmount();
        expect(controls.dispose).toHaveBeenCalledOnce();
    });

    it("leaves fixed previews without orbit controls", () => {
        render(<CubePreview isPlaying={false}/>);

        expect(screen.getByLabelText("Cube preview")).toHaveAttribute("data-interactive-view", "false");
        expect(orbit.instances).toHaveLength(0);
    });

    it("draws static scramble previews on mount and resize without an animation loop", () => {
        const requestFrame = vi.spyOn(window, "requestAnimationFrame");
        const {unmount} = render(<CubePreview setupAlgorithm="R U" compact staticPreview/>);
        const renderer = renderers.instances[0] as {
            render: ReturnType<typeof vi.fn>;
            dispose: ReturnType<typeof vi.fn>;
            forceContextLoss: ReturnType<typeof vi.fn>;
        };

        expect(renderer.render).toHaveBeenCalledTimes(1);
        expect(requestFrame).not.toHaveBeenCalled();
        const stateBeforeResize = screen.getByLabelText("Cube preview").getAttribute("data-render-state");
        const expectedState = renderStateSignature(applyRenderMoves(solvedRenderCube(), parseAlgorithm("R U")));
        expect(stateBeforeResize).toBe(expectedState);

        fireEvent(window, new Event("resize"));
        expect(renderer.render).toHaveBeenCalledTimes(2);
        expect(screen.getByLabelText("Cube preview")).toHaveAttribute("data-render-state", stateBeforeResize);

        unmount();
        expect(renderer.dispose).toHaveBeenCalledOnce();
        expect(renderer.forceContextLoss).toHaveBeenCalledOnce();
    });

    it("releases the renderer context whenever a preview unmounts", () => {
        const {unmount} = render(<CubePreview isPlaying={false}/>);
        const renderer = renderers.instances[0] as {
            dispose: ReturnType<typeof vi.fn>;
            forceContextLoss: ReturnType<typeof vi.fn>;
        };

        unmount();

        expect(renderer.dispose).toHaveBeenCalledOnce();
        expect(renderer.forceContextLoss).toHaveBeenCalledOnce();
    });
});
