import {act, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {CubePreviewModeContext} from "../CubePreviewModeContext";

const observerInstances = vi.hoisted(() => ({items: [] as MockIntersectionObserver[]}));

vi.mock("../CubePreview", () => ({
    default: ({setupAlgorithm = "", algorithm = "", stage = "", compact = false}: {
        setupAlgorithm?: string;
        algorithm?: string;
        stage?: string;
        compact?: boolean;
    }) => (
        <div
            data-testid="loaded-cube-preview"
            data-preview-setup={setupAlgorithm}
            data-preview-alg={algorithm}
            data-preview-stage={stage}
            data-compact={String(compact)}
        />
    ),
}));

import DeferredCubePreview from "../DeferredCubePreview";

class MockIntersectionObserver {
    static instances: MockIntersectionObserver[] = observerInstances.items;
    target: Element | null = null;
    disconnected = false;

    constructor(private readonly callback: IntersectionObserverCallback, readonly options?: IntersectionObserverInit) {
        MockIntersectionObserver.instances.push(this);
    }

    observe = vi.fn((target: Element) => {
        this.target = target;
    });
    disconnect = vi.fn(() => {
        this.disconnected = true;
    });
    unobserve = vi.fn();
    takeRecords = vi.fn((): IntersectionObserverEntry[] => []);

    notify(isIntersecting: boolean) {
        if (!this.target) throw new Error("Observer has no target");
        this.callback([{
            isIntersecting,
            target: this.target,
            boundingClientRect: this.target.getBoundingClientRect(),
            intersectionRatio: isIntersecting ? 1 : 0,
            intersectionRect: this.target.getBoundingClientRect(),
            rootBounds: null,
            time: performance.now(),
        }], this as unknown as IntersectionObserver);
    }
}

describe("DeferredCubePreview", () => {
    beforeEach(() => {
        MockIntersectionObserver.instances.length = 0;
        vi.stubGlobal("IntersectionObserver", MockIntersectionObserver);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("waits until near the viewport, then keeps the renderer mounted", async () => {
        const {container} = render(<DeferredCubePreview setupAlgorithm="R U" compact/>);

        expect(screen.getByRole("img", {name: "Cube preview"})).toHaveAttribute("aria-busy", "false");
        expect(screen.queryByTestId("loaded-cube-preview")).not.toBeInTheDocument();
        expect(MockIntersectionObserver.instances).toHaveLength(1);
        const observer = MockIntersectionObserver.instances[0];
        expect(observer.options?.rootMargin).toBe("120px 0px");

        await act(async () => {
            observer.notify(true);
        });

        const preview = await screen.findByTestId("loaded-cube-preview");
        expect(preview).toHaveAttribute("data-preview-setup", "R U");
        expect(preview).toHaveAttribute("data-compact", "true");
        expect(observer.disconnected).toBe(true);

        act(() => observer.notify(false));
        expect(screen.getByTestId("loaded-cube-preview")).toBeInTheDocument();
        expect(container.querySelector(".deferred-cube-preview")).not.toBeInTheDocument();
    });

    it("loads immediately when IntersectionObserver is unavailable", async () => {
        vi.stubGlobal("IntersectionObserver", undefined);

        render(<DeferredCubePreview algorithm="F R U"/>);

        expect(await screen.findByTestId("loaded-cube-preview")).toHaveAttribute("data-preview-alg", "F R U");
    });

    it("loads the project-owned 2D net renderer when that mode is selected", async () => {
        render(
            <CubePreviewModeContext.Provider value="2d">
                <DeferredCubePreview setupAlgorithm="R U" isPlaying={false}/>
            </CubePreviewModeContext.Provider>,
        );

        await act(async () => {
            MockIntersectionObserver.instances[0].notify(true);
        });

        expect(await screen.findByRole("img", {name: "2D cube net preview"})).toBeInTheDocument();
    });

    it("observes the replacement placeholder when the mode changes before entering the viewport", async () => {
        const {rerender} = render(
            <CubePreviewModeContext.Provider value="3d">
                <DeferredCubePreview isPlaying={false}/>
            </CubePreviewModeContext.Provider>,
        );
        const oldObserver = MockIntersectionObserver.instances[0];

        rerender(
            <CubePreviewModeContext.Provider value="2d">
                <DeferredCubePreview isPlaying={false}/>
            </CubePreviewModeContext.Provider>,
        );

        expect(oldObserver.disconnected).toBe(true);
        expect(MockIntersectionObserver.instances).toHaveLength(2);
        await act(async () => MockIntersectionObserver.instances[1].notify(true));
        expect(await screen.findByRole("img", {name: "2D cube net preview"})).toBeInTheDocument();
    });
});
