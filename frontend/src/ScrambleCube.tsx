import DeferredCubePreview from "./DeferredCubePreview";

export default function ScrambleCube({scramble}: { scramble: string }) {
    return (
        <div className="scramble-cube" aria-label="Current scrambled cube">
            <div className="scramble-cube-glow"/>
            <DeferredCubePreview setupAlgorithm={scramble}/>
        </div>
    );
}
