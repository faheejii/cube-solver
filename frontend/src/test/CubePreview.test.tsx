import {render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";

const orbit = vi.hoisted(() => ({instances: [] as Array<Record<string, unknown>>}));

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
        },
    };
});

import CubePreview from "../CubePreview";

afterEach(() => {
    orbit.instances.length = 0;
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
});
