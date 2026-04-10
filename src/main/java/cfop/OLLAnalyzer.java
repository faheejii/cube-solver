package cfop;

import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.Face;

public class OLLAnalyzer {
    public static boolean isOllSolved(CubeState cube) {
        return isOllSolved(cube, new CubeOrientation());
    }

    public static boolean isOllSolved(CubeState cube, CubeOrientation orientation) {
        var signature = extractSignature(cube, orientation);
        return signature.urfCornerOrientation() == 0
                && signature.uflCornerOrientation() == 0
                && signature.ulbCornerOrientation() == 0
                && signature.ubrCornerOrientation() == 0
                && signature.urEdgeOrientation() == 0
                && signature.ufEdgeOrientation() == 0
                && signature.ulEdgeOrientation() == 0
                && signature.ubEdgeOrientation() == 0;
    }

    public static OLLCaseSignature extractSignature(CubeState cube) {
        return extractSignature(cube, new CubeOrientation());
    }

    public static OLLCaseSignature extractSignature(CubeState cube, CubeOrientation orientation) {
        var urf = cornerForFaces(orientation.faceAt(Face.U), orientation.faceAt(Face.R), orientation.faceAt(Face.F));
        var ufl = cornerForFaces(orientation.faceAt(Face.U), orientation.faceAt(Face.F), orientation.faceAt(Face.L));
        var ulb = cornerForFaces(orientation.faceAt(Face.U), orientation.faceAt(Face.L), orientation.faceAt(Face.B));
        var ubr = cornerForFaces(orientation.faceAt(Face.U), orientation.faceAt(Face.B), orientation.faceAt(Face.R));

        var ur = edgeForFaces(orientation.faceAt(Face.U), orientation.faceAt(Face.R));
        var uf = edgeForFaces(orientation.faceAt(Face.U), orientation.faceAt(Face.F));
        var ul = edgeForFaces(orientation.faceAt(Face.U), orientation.faceAt(Face.L));
        var ub = edgeForFaces(orientation.faceAt(Face.U), orientation.faceAt(Face.B));

        return new OLLCaseSignature(
                Corner.values()[cube.cornerPerm[urf.ordinal()]],
                cube.cornerOri[urf.ordinal()],
                Corner.values()[cube.cornerPerm[ufl.ordinal()]],
                cube.cornerOri[ufl.ordinal()],
                Corner.values()[cube.cornerPerm[ulb.ordinal()]],
                cube.cornerOri[ulb.ordinal()],
                Corner.values()[cube.cornerPerm[ubr.ordinal()]],
                cube.cornerOri[ubr.ordinal()],
                Edge.values()[cube.edgePerm[ur.ordinal()]],
                cube.edgeOri[ur.ordinal()],
                Edge.values()[cube.edgePerm[uf.ordinal()]],
                cube.edgeOri[uf.ordinal()],
                Edge.values()[cube.edgePerm[ul.ordinal()]],
                cube.edgeOri[ul.ordinal()],
                Edge.values()[cube.edgePerm[ub.ordinal()]],
                cube.edgeOri[ub.ordinal()]
        );
    }

    private static Edge edgeForFaces(Face first, Face second) {
        if (matches(first, second, Face.U, Face.R)) return Edge.UR;
        if (matches(first, second, Face.U, Face.F)) return Edge.UF;
        if (matches(first, second, Face.U, Face.L)) return Edge.UL;
        if (matches(first, second, Face.U, Face.B)) return Edge.UB;
        if (matches(first, second, Face.D, Face.R)) return Edge.DR;
        if (matches(first, second, Face.D, Face.F)) return Edge.DF;
        if (matches(first, second, Face.D, Face.L)) return Edge.DL;
        if (matches(first, second, Face.D, Face.B)) return Edge.DB;
        if (matches(first, second, Face.F, Face.R)) return Edge.FR;
        if (matches(first, second, Face.F, Face.L)) return Edge.FL;
        if (matches(first, second, Face.B, Face.R)) return Edge.BR;
        if (matches(first, second, Face.B, Face.L)) return Edge.BL;
        throw new IllegalArgumentException("Faces do not form an edge: " + first + ", " + second);
    }

    private static Corner cornerForFaces(Face a, Face b, Face c) {
        if (matchesAll(a, b, c, Face.U, Face.R, Face.F)) return Corner.URF;
        if (matchesAll(a, b, c, Face.U, Face.F, Face.L)) return Corner.UFL;
        if (matchesAll(a, b, c, Face.U, Face.L, Face.B)) return Corner.ULB;
        if (matchesAll(a, b, c, Face.U, Face.B, Face.R)) return Corner.UBR;
        if (matchesAll(a, b, c, Face.D, Face.F, Face.R)) return Corner.DFR;
        if (matchesAll(a, b, c, Face.D, Face.L, Face.F)) return Corner.DLF;
        if (matchesAll(a, b, c, Face.D, Face.B, Face.L)) return Corner.DBL;
        if (matchesAll(a, b, c, Face.D, Face.R, Face.B)) return Corner.DRB;
        throw new IllegalArgumentException("Faces do not form a corner: " + a + ", " + b + ", " + c);
    }

    private static boolean matches(Face first, Face second, Face expectedA, Face expectedB) {
        return (first == expectedA && second == expectedB) || (first == expectedB && second == expectedA);
    }

    private static boolean matchesAll(Face a, Face b, Face c, Face x, Face y, Face z) {
        return contains(a, b, c, x) && contains(a, b, c, y) && contains(a, b, c, z);
    }

    private static boolean contains(Face a, Face b, Face c, Face target) {
        return a == target || b == target || c == target;
    }
}
