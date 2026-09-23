import {Component, lazy, Suspense, useEffect, useRef, useState, type ComponentProps, type ErrorInfo, type ReactNode, type Ref} from "react";
import type CubePreview from "./CubePreview";

const CubePreviewRenderer = lazy(() => import("./CubePreview"));
const VIEWPORT_ROOT_MARGIN = "120px 0px";

type Props = ComponentProps<typeof CubePreview>;

export default function DeferredCubePreview(props: Props) {
    const placeholderRef = useRef<HTMLDivElement>(null);
    const [nearViewport, setNearViewport] = useState(false);

    useEffect(() => {
        if (nearViewport) {
            return;
        }

        const element = placeholderRef.current;
        if (!element) {
            return;
        }

        if (typeof IntersectionObserver === "undefined") {
            setNearViewport(true);
            return;
        }

        const observer = new IntersectionObserver(([entry]) => {
            if (entry?.isIntersecting) {
                setNearViewport(true);
                observer.disconnect();
            }
        }, {rootMargin: VIEWPORT_ROOT_MARGIN});
        observer.observe(element);
        return () => observer.disconnect();
    }, [nearViewport]);

    if (nearViewport) {
        return (
            <CubePreviewErrorBoundary>
                <Suspense fallback={<PreviewPlaceholder {...props} loading/>}>
                    <CubePreviewRenderer {...props}/>
                </Suspense>
            </CubePreviewErrorBoundary>
        );
    }

    return <PreviewPlaceholder {...props} ref={placeholderRef} loading={false}/>;
}

type PlaceholderProps = Props & {loading: boolean; ref?: Ref<HTMLDivElement>};

function PreviewPlaceholder({
    compact = false,
    interactiveView = false,
    loading,
    ...props
}: PlaceholderProps) {
    const label = interactiveView
        ? "Interactive cube preview. Drag to rotate the cube view."
        : "Cube preview";
    return (
        <div
            ref={props.ref}
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
            {loading ? <span className="cube-preview-loading-indicator">Loading 3D preview…</span> : null}
        </div>
    );
}

class CubePreviewErrorBoundary extends Component<{children: ReactNode}, {failed: boolean}> {
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
                <div className="custom-cube-preview deferred-cube-preview" role="status">
                    3D preview failed to load.
                </div>
            );
        }
        return this.props.children;
    }
}
