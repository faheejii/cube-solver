package solver;

import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import cube.LogicalFaceletView;
import io.CubeConverter;
import io.FaceletState;

/** Converts a physical cubie state into the canonical logical frame used by solver tables. */
final class LogicalFrameNormalizer {
    private LogicalFrameNormalizer() {
    }

    static CubeState normalize(CubeState state, CubeOrientation orientation) {
        var view = LogicalFaceletView.of(state, orientation);
        var stickers = new Face[FaceletState.FACE_COUNT][FaceletState.FACELET_COUNT_PER_FACE];
        for (var face : Face.values()) {
            for (var index = 0; index < FaceletState.FACELET_COUNT_PER_FACE; index++) {
                stickers[face.ordinal()][index] = view.sticker(face, index);
            }
        }
        return CubeConverter.toCubeState(new FaceletState(stickers));
    }
}
