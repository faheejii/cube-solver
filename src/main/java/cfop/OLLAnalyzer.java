package cfop;

import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import cube.LogicalFaceletView;

public class OLLAnalyzer {
    public static boolean isOllSolved(CubeState cube) {
        return isOllSolved(cube, new CubeOrientation());
    }

    public static boolean isOllSolved(CubeState cube, CubeOrientation orientation) {
        var signature = extractSignature(cube, orientation);
        return signature.u0()
                && signature.u1()
                && signature.u2()
                && signature.u3()
                && signature.u5()
                && signature.u6()
                && signature.u7()
                && signature.u8();
    }

    public static OLLCaseSignature extractSignature(CubeState cube) {
        return extractSignature(cube, new CubeOrientation());
    }

    public static OLLCaseSignature extractSignature(CubeState cube, CubeOrientation orientation) {
        var view = LogicalFaceletView.of(cube, orientation);

        return new OLLCaseSignature(
                isU(view, Face.U, 0), isU(view, Face.U, 1), isU(view, Face.U, 2),
                isU(view, Face.U, 3), isU(view, Face.U, 5), isU(view, Face.U, 6),
                isU(view, Face.U, 7), isU(view, Face.U, 8), isU(view, Face.F, 0),
                isU(view, Face.F, 1), isU(view, Face.F, 2), isU(view, Face.R, 0),
                isU(view, Face.R, 1), isU(view, Face.R, 2), isU(view, Face.B, 0),
                isU(view, Face.B, 1), isU(view, Face.B, 2), isU(view, Face.L, 0),
                isU(view, Face.L, 1), isU(view, Face.L, 2)
        );
    }

    private static boolean isU(LogicalFaceletView view, Face face, int index) {
        return view.sticker(face, index) == Face.U;
    }
}
