package violetarchive.enchant;

/**
 * Quarter-turns of a shulker lid as it rolls along one horizontal direction.
 */
public enum AnimaRoll {
    UP,
    DOWN,
    NORTH,
    SOUTH,
    EAST,
    WEST;

    public static AnimaRoll next(AnimaRoll travel, AnimaRoll current) {
        AnimaRoll[] cycle = switch (travel) {
            case EAST -> new AnimaRoll[] {UP, EAST, DOWN, WEST};
            case WEST -> new AnimaRoll[] {UP, WEST, DOWN, EAST};
            case SOUTH -> new AnimaRoll[] {UP, SOUTH, DOWN, NORTH};
            case NORTH -> new AnimaRoll[] {UP, NORTH, DOWN, SOUTH};
            case UP, DOWN -> null;
        };
        if (cycle == null) {
            return UP;
        }
        for (int index = 0; index < cycle.length; index++) {
            if (cycle[index] == current) {
                return cycle[(index + 1) % cycle.length];
            }
        }
        return UP;
    }
}
