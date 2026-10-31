package violetarchive.world;

/**
 * Pure forest decision. One lattice cell is 200 blocks across, so nearby
 * chunks of an island share a value. The lowest 20 percent of those values
 * is forest. Nothing is read from disk.
 */
public final class ForestNoise {
    public static final int CELL = 200;
    private static final long ORIGIN_LIMIT = 1024L;

    private ForestNoise() {
    }

    public static boolean isOuter(int chunkX, int chunkZ) {
        long centerX = chunkX * 16L + 8L;
        long centerZ = chunkZ * 16L + 8L;
        return centerX * centerX + centerZ * centerZ > ORIGIN_LIMIT * ORIGIN_LIMIT;
    }

    public static boolean isForestChunk(long seed, int chunkX, int chunkZ) {
        if (!isOuter(chunkX, chunkZ)) {
            return false;
        }
        return isForest(seed, chunkX * 16 + 8, chunkZ * 16 + 8);
    }

    public static boolean isForest(long seed, int blockX, int blockZ) {
        int cellX = Math.floorDiv(blockX, CELL);
        int cellZ = Math.floorDiv(blockZ, CELL);
        return value(seed, cellX, cellZ) < 0.20;
    }

    static double value(long seed, int cellX, int cellZ) {
        long mixed = seed ^ (cellX * 341873128712L) ^ (cellZ * 132897987541L);
        mixed = (mixed ^ (mixed >>> 33)) * 0xff51afd7ed558ccdL;
        mixed = (mixed ^ (mixed >>> 33)) * 0xc4ceb9fe1a85ec53L;
        mixed = mixed ^ (mixed >>> 33);
        return (mixed & 0xffffffffL) / (double) 0x100000000L;
    }
}
