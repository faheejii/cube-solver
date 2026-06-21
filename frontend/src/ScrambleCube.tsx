import "cubing/twisty";

export default function ScrambleCube({ scramble }: { scramble: string }) {
  return (
    <div className="scramble-cube" aria-label="Current scrambled cube">
      <div className="scramble-cube-glow" />
      <twisty-player
        key={scramble}
        puzzle="3x3x3"
        experimental-setup-alg={scramble}
        alg=""
        background="none"
        control-panel="none"
        hint-facelets="none"
        camera-latitude="25"
        camera-longitude="34"
      />
    </div>
  );
}
