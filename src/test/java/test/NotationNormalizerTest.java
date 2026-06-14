package test;

import org.junit.jupiter.api.Test;
import util.NotationNormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NotationNormalizerTest {
    @Test
    void normalizeLastLayerAlgorithm_shouldExpandStandardRightWideNotation() {
        assertEquals("R M' U R' M", NotationNormalizer.normalizeLastLayerAlgorithm("r U r'"));
    }

    @Test
    void normalizeLastLayerAlgorithm_shouldExpandStandardLeftWideNotation() {
        assertEquals("L M U L' M'", NotationNormalizer.normalizeLastLayerAlgorithm("l U l'"));
    }

    @Test
    void normalizeLastLayerAlgorithm_shouldUseRepositoryEDirectionForWideUAndD() {
        assertEquals("U E U' E'", NotationNormalizer.normalizeLastLayerAlgorithm("u u'"));
        assertEquals("D E' D' E", NotationNormalizer.normalizeLastLayerAlgorithm("d d'"));
    }

    @Test
    void normalizeLastLayerAlgorithm_shouldExpandFrontAndBackWideNotation() {
        assertEquals("F S F' S'", NotationNormalizer.normalizeLastLayerAlgorithm("f f'"));
        assertEquals("B S' B' S", NotationNormalizer.normalizeLastLayerAlgorithm("b b'"));
    }
}
