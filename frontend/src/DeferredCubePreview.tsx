import {Component, lazy, Suspense, useEffect, useRef, useState, type ComponentProps, type ErrorInfo, type ReactNode} from "react";
import type CubePreview from "./CubePreview";
import {useCubePreviewMode, type CubePreviewMode} from "./CubePreviewModeContext";

const CubePreviewRenderer = lazy(() => import("./CubePreview"));
const CubeNetPreviewRenderer = lazy(() => import("./CubeNetPreview"));
const VIEWPORT_ROOT_MARGIN = "120px 0px";

type Props = ComponentProps<typeof CubePreview> & {
    displayMode?: CubePreviewMode;
    unloadWhenOutOfView?: boolean;
};

export default function DeferredCubePreview({displayMode, unloadWhenOutOfView = false, ...props}: Props) {
    const contextMode = useCubePreviewMode();
    const previewMode = displayMode ?? contextMode;
    const hostRef = useRef<HTMLDivElement>(null);
    const [shouldRender, setShouldRender] = useState(false);

    useEffect(() => {
        const element = hostRef.current;
        if (!element) {
            return;
        }

        if (typeof IntersectionObserver === "undefined") {
            setShouldRender(true);
            return;
        }

        const observer = new IntersectionObserver(([entry]) => {
            const isNearViewport = Boolean(entry?.isIntersecting);
            setShouldRender(isNearViewport);
            if (isNearViewport && !unloadWhenOutOfView) {
                observer.disconnect();
            }
        }, {rootMargin: VIEWPORT_ROOT_MARGIN});
        observer.observe(element);
        return () => observer.disconnect();
    }, [unloadWhenOutOfView]);

    return (
        <div ref={hostRef} className={props.compact ? "deferred-cube-preview-host compact" : "deferred-cube-preview-host"}>
            {shouldRender ? (
                <CubePreviewErrorBoundary compact={props.compact}>
                    <Suspense fallback={<PreviewPlaceholder {...props} mode={previewMode} loading/>}>
                        {previewMode === "2d" ? <CubeNetPreviewRenderer {...props}/> : <CubePreviewRenderer {...props}/>}
                    </Suspense>
                </CubePreviewErrorBoundary>
            ) : <PreviewPlaceholder {...props} mode={previewMode} loading={false}/>}
        </div>
    );
}

type PlaceholderProps = Omit<Props, "unloadWhenOutOfView"> & {loading: boolean; mode: "2d" | "3d"};

function PreviewPlaceholder({
    compact = false,
    interactiveView = false,
    loading,
    mode,
    ...props
}: PlaceholderProps) {
    const label = interactiveView
        ? mode === "3d" ? "Interactive cube preview. Drag to rotate the cube view." : "2D interactive cube net preview"
        : mode === "3d" ? "Cube preview" : "2D cube net preview";
    return (
        <div
            className={compact ? "custom-cube-preview compact deferred-cube-preview" : "custom-cube-preview deferred-cube-preview"}
            role="img"
            aria-label={loading ? `Loading ${label.toLowerCase()}` : label}
            aria-busy={loading}
            data-preview-setup={props.setupAlgorithm ?? ""}
            data-preview-alg={props.algorithm ?? ""}
            data-preview-stage={props.stage ?? ""}
            data-playback-alg={props["data-playback-alg"] ?? props.algorithm ?? ""}
            data-interactive-view={interactiveView ? "true" : "false"}
        >
            {loading ? <span className="cube-preview-loading-indicator">Loading {mode.toUpperCase()} preview…</span> : null}
        </div>
    );
}

class CubePreviewErrorBoundary extends Component<{children: ReactNode; compact?: boolean}, {failed: boolean}> {
    state = {failed: false};

    static getDerivedStateFromError() {
        return {failed: true};
    }

    componentDidCatch(_error: Error, _info: ErrorInfo) {
        // Keep a failed optional preview local to its own reserved area.
    }

    render() {
        if (this.state.failed) {
            return (
                <div className={this.props.compact ? "custom-cube-preview compact deferred-cube-preview" : "custom-cube-preview deferred-cube-preview"} role="status">
                    Cube preview failed to load.
                </div>
            );
        }
        return this.props.children;
    }
}
