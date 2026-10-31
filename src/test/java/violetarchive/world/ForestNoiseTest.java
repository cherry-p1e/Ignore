package violetarchive.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ForestNoiseTest {
    @Test
    void outerRingStartsPast1024() {
        assertFalse(ForestNoise.isOuter(63, 0));
        assertTrue(ForestNoise.isOuter(64, 0));
        assertTrue(ForestNoise.isOuter(-65, 0));
        assertFalse(ForestNoise.isOuter(0, 0));
    }

    @Test
    void nearbyChunksShareOneCell() {
        long seed = 0x5EEDL;
        assertEquals(ForestNoise.isForestChunk(seed, 100, 8), ForestNoise.isForestChunk(seed, 101, 8));
        assertEquals(ForestNoise.isForest(seed, -1, 10), ForestNoise.isForest(seed, -200, 10));
        assertEquals(ForestNoise.value(seed, Math.floorDiv(-1, ForestNoise.CELL), 0),
                ForestNoise.value(seed, Math.floorDiv(-200, ForestNoise.CELL), 0));
    }

    @Test
    void lowestFifthIsStable() {
        long seed = 8675309L;
        int forest = 0;
        int total = 0;
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                total++;
                if (ForestNoise.value(seed, x, z) < 0.20) {
                    forest++;
                }
            }
        }
        double rate = forest / (double) total;
        assertTrue(rate > 0.14 && rate < 0.26, "rate " + rate);
        assertEquals(ForestNoise.isForest(seed, 400, 600), ForestNoise.isForest(seed, 400, 600));
    }
}
