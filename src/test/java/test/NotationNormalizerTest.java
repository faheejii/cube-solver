package test;

import org.junit.jupiter.api.Test;
import util.NotationNormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NotationNormalizerTest {
    @Test
    void normalizeLastLayerAlgorithm_shouldPreserveStandardRightWideNotation() {
        assertEquals("r U r'", NotationNormalizer.normalizeLastLayerAlgorithm("r U r'"));
    }

    @Test
    void normalizeLastLayerAlgorithm_shouldPreserveStandardLeftWideNotation() {
        assertEquals("l U l'", NotationNormalizer.normalizeLastLayerAlgorithm("l U l'"));
    }

    @Test
    void normalizeLastLayerAlgorithm_shouldPreserveWideUAndDNotation() {
        assertEquals("u u'", NotationNormalizer.normalizeLastLayerAlgorithm("u u'"));
        assertEquals("d d'", NotationNormalizer.normalizeLastLayerAlgorithm("d d'"));
    }

    @Test
    void normalizeLastLayerAlgorithm_shouldPreserveFrontAndBackWideNotation() {
        assertEquals("f f'", NotationNormalizer.normalizeLastLayerAlgorithm("f f'"));
        assertEquals("b b'", NotationNormalizer.normalizeLastLayerAlgorithm("b b'"));
    }
}
