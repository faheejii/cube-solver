package cfop;

import cube.Corner;
import cube.Edge;

public enum F2LSlot {
    FR(Corner.DFR, Edge.FR),
    FL(Corner.DLF, Edge.FL),
    BL(Corner.DBL, Edge.BL),
    BR(Corner.DRB, Edge.BR);

    private final Corner corner;
    private final Edge edge;

    F2LSlot(Corner corner, Edge edge) {
        this.corner = corner;
        this.edge = edge;
    }

    public Corner corner() {
        return corner;
    }

    public Edge edge() {
        return edge;
    }
}
