package cfop;

import cube.Corner;
import cube.Edge;

public record PLLCaseSignature(
        Corner urfPiece,
        Corner uflPiece,
        Corner ulbPiece,
        Corner ubrPiece,
        Edge urPiece,
        Edge ufPiece,
        Edge ulPiece,
        Edge ubPiece
) {
    public PLLCaseSignature {
        if (urfPiece == null || uflPiece == null || ulbPiece == null || ubrPiece == null
                || urPiece == null || ufPiece == null || ulPiece == null || ubPiece == null) {
            throw new IllegalArgumentException("PLL signature pieces cannot be null");
        }
    }
}
