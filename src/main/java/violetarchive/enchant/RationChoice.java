package violetarchive.enchant;

/**
 * Picks one food for a missing hunger gap.
 * Foods that do not cover the gap are ignored. The smallest overflow wins,
 * and a tie uses the saturation closest to the gap.
 */
public final class RationChoice {
    private RationChoice() {
    }

    public static int choose(int gap, int[] nutrition, float[] saturation) {
        if (gap <= 0 || nutrition.length == 0) {
            return -1;
        }
        int best = -1;
        int bestOverflow = Integer.MAX_VALUE;
        float bestDistance = Float.MAX_VALUE;
        for (int index = 0; index < nutrition.length; index++) {
            if (nutrition[index] < gap) {
                continue;
            }
            int overflow = nutrition[index] - gap;
            float distance = Math.abs(saturation[index] - gap);
            if (overflow < bestOverflow || (overflow == bestOverflow && distance < bestDistance)) {
                best = index;
                bestOverflow = overflow;
                bestDistance = distance;
            }
        }
        return best;
    }
}
