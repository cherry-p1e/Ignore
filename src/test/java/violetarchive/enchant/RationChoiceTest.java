package violetarchive.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RationChoiceTest {
    @Test
    void smallestOverflowWins() {
        assertEquals(0, RationChoice.choose(4, new int[] {5, 9}, new float[] {1f, 1f}));
    }

    @Test
    void tieUsesSaturationClosestToTheGap() {
        assertEquals(1, RationChoice.choose(4, new int[] {6, 6}, new float[] {1f, 5f}));
    }

    @Test
    void skipsFoodThatDoesNotCoverTheGap() {
        assertEquals(-1, RationChoice.choose(0, new int[] {4}, new float[] {1f}));
        assertEquals(-1, RationChoice.choose(5, new int[] {1, 2, 4}, new float[] {1f, 1f, 1f}));
        assertEquals(-1, RationChoice.choose(3, new int[0], new float[0]));
    }
}
