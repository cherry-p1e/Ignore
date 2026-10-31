package violetarchive.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnimaRollTest {
    @Test
    void eastCycle() {
        assertEquals(AnimaRoll.EAST, AnimaRoll.next(AnimaRoll.EAST, AnimaRoll.UP));
        assertEquals(AnimaRoll.DOWN, AnimaRoll.next(AnimaRoll.EAST, AnimaRoll.EAST));
        assertEquals(AnimaRoll.WEST, AnimaRoll.next(AnimaRoll.EAST, AnimaRoll.DOWN));
        assertEquals(AnimaRoll.UP, AnimaRoll.next(AnimaRoll.EAST, AnimaRoll.WEST));
    }

    @Test
    void otherDirections() {
        assertEquals(AnimaRoll.WEST, AnimaRoll.next(AnimaRoll.WEST, AnimaRoll.UP));
        assertEquals(AnimaRoll.EAST, AnimaRoll.next(AnimaRoll.WEST, AnimaRoll.DOWN));
        assertEquals(AnimaRoll.SOUTH, AnimaRoll.next(AnimaRoll.SOUTH, AnimaRoll.UP));
        assertEquals(AnimaRoll.NORTH, AnimaRoll.next(AnimaRoll.NORTH, AnimaRoll.UP));
        assertEquals(AnimaRoll.DOWN, AnimaRoll.next(AnimaRoll.NORTH, AnimaRoll.NORTH));
    }

    @Test
    void offCycleStartsUpright() {
        assertEquals(AnimaRoll.UP, AnimaRoll.next(AnimaRoll.EAST, AnimaRoll.NORTH));
        assertEquals(AnimaRoll.UP, AnimaRoll.next(AnimaRoll.UP, AnimaRoll.EAST));
        assertEquals(AnimaRoll.UP, AnimaRoll.next(AnimaRoll.DOWN, AnimaRoll.WEST));
    }
}
