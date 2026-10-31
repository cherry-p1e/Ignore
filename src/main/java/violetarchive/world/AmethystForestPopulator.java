package violetarchive.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paints and builds the amethyst forest while a chunk generates.
 * Saved chunks are not decorated again. This class does not call World.getChunk
 * and does not keep the limited region.
 */
public final class AmethystForestPopulator extends BlockPopulator {
    private static final Material[] BUD_TYPES = {
            Material.SMALL_AMETHYST_BUD,
            Material.MEDIUM_AMETHYST_BUD,
            Material.LARGE_AMETHYST_BUD,
            Material.AMETHYST_CLUSTER
    };
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final JavaPlugin plugin;
    private final Biome biome;
    private final BlockData calcite;
    private final BlockData amethyst;
    private final BlockData budding;
    private final BlockData endRod;
    private final BlockData[][] buds;
    private final AtomicBoolean warned = new AtomicBoolean();

    public AmethystForestPopulator(JavaPlugin plugin) {
        this.plugin = plugin;
        this.biome = Registry.BIOME.get(NamespacedKey.fromString("violetarchive:amethyst_forest"));
        this.calcite = Material.CALCITE.createBlockData();
        this.amethyst = Material.AMETHYST_BLOCK.createBlockData();
        this.budding = Material.BUDDING_AMETHYST.createBlockData();
        this.endRod = Material.END_ROD.createBlockData(data -> {
            if (data instanceof Directional directional) {
                directional.setFacing(BlockFace.UP);
            }
        });
        this.buds = new BlockData[BUD_TYPES.length][SIDES.length];
        for (int type = 0; type < BUD_TYPES.length; type++) {
            for (int side = 0; side < SIDES.length; side++) {
                BlockFace face = SIDES[side];
                buds[type][side] = BUD_TYPES[type].createBlockData(data -> {
                    if (data instanceof Directional directional && directional.getFaces().contains(face)) {
                        directional.setFacing(face);
                    }
                });
            }
        }
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        if (!ForestNoise.isForestChunk(worldInfo.getSeed(), chunkX, chunkZ)) {
            return;
        }
        if (containsCity(worldInfo, region, chunkX, chunkZ)) {
            return;
        }
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        List<int[]> surface = new ArrayList<>();
        boolean paint = biome != null;
        if (!paint && warned.compareAndSet(false, true)) {
            plugin.getLogger().warning("violetarchive:amethyst_forest is not registered; skipping biome paint");
        }
        for (int x = baseX; x < baseX + 16; x++) {
            for (int z = baseZ; z < baseZ + 16; z++) {
                // WorldGenLevel.getHeight returns minY for a chunk still being
                // generated, and one above the surface once the chunk exists.
                // Walk the column so the top end stone is the block we mean.
                int y = topEndStone(region, worldInfo, x, z);
                if (y == Integer.MIN_VALUE) {
                    continue;
                }
                if (paint) {
                    int minY = worldInfo.getMinHeight();
                    int maxY = worldInfo.getMaxHeight();
                    for (int column = minY; column < maxY; column++) {
                        if (region.isInRegion(x, column, z)) {
                            region.setBiome(x, column, z, biome);
                        }
                    }
                }
                BlockData cover = Math.floorMod(x + z, 2) == 0 ? calcite : amethyst;
                region.setBlockData(x, y, z, cover.clone());
                if (region.isInRegion(x, y + 1, z) && region.getType(x, y + 1, z).isAir()) {
                    surface.add(new int[] {x, y, z});
                }
            }
        }
        if (surface.size() < 16) {
            return;
        }
        placeTrees(region, random, surface);
    }

    private static int topEndStone(LimitedRegion region, WorldInfo world, int x, int z) {
        int min = world.getMinHeight();
        for (int y = world.getMaxHeight() - 1; y >= min; y--) {
            if (!region.isInRegion(x, y, z)) {
                continue;
            }
            Material type = region.getType(x, y, z);
            if (type.isAir()) {
                continue;
            }
            if (type == Material.END_STONE) {
                return y;
            }
            if (type == Material.CHORUS_PLANT || type == Material.CHORUS_FLOWER) {
                continue;
            }
            return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private void placeTrees(LimitedRegion region, Random random, List<int[]> surface) {
        List<int[]> spots = new ArrayList<>(surface);
        Collections.shuffle(spots, random);
        int wanted = 2 + random.nextInt(3);
        List<int[]> planted = new ArrayList<>();
        for (int[] spot : spots) {
            if (planted.size() >= wanted) {
                break;
            }
            if (!separated(planted, spot[0], spot[2]) || voidEdge(region, spot[0], spot[1], spot[2])) {
                continue;
            }
            boolean tall = planted.isEmpty() || (planted.size() != 1 && random.nextBoolean());
            int height = tall ? 4 + random.nextInt(3) : 3;
            if (!roomForTrunk(region, spot[0], spot[1], spot[2], height)) {
                continue;
            }
            List<int[]> grown = new ArrayList<>();
            for (int step = 1; step <= height; step++) {
                int y = spot[1] + step;
                region.setBlockData(spot[0], y, spot[2], calcite.clone());
                grown.add(new int[] {spot[0], y, spot[2]});
            }
            if (tall) {
                canopy(region, random, spot[0], spot[1] + height, spot[2], grown, false);
            } else {
                int top = spot[1] + height + 1;
                if (region.isInRegion(spot[0], top, spot[2]) && region.getType(spot[0], top, spot[2]).isAir()) {
                    region.setBlockData(spot[0], top, spot[2], budding.clone());
                    grown.add(new int[] {spot[0], top, spot[2]});
                }
                canopy(region, random, spot[0], spot[1] + height, spot[2], grown, true);
            }
            buds(region, random, grown);
            rods(region, random, spot[0], spot[1], spot[2]);
            planted.add(spot);
        }
    }

    private void canopy(LimitedRegion region, Random random, int x, int y, int z, List<int[]> grown, boolean small) {
        int radius = small ? 1 : 2;
        int limit = small ? 2 : 5;
        int layers = small ? 1 : 2;
        for (int layer = 0; layer < layers; layer++) {
            int at = y + layer;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (dx * dx + dz * dz > limit) {
                        continue;
                    }
                    if (random.nextFloat() < 0.40f) {
                        continue;
                    }
                    int placeX = x + dx;
                    int placeZ = z + dz;
                    if (!region.isInRegion(placeX, at, placeZ)) {
                        continue;
                    }
                    if (!region.getType(placeX, at, placeZ).isAir()) {
                        continue;
                    }
                    BlockData cover = Math.floorMod(placeX + placeZ, 2) == 0 ? amethyst : calcite;
                    region.setBlockData(placeX, at, placeZ, cover.clone());
                    grown.add(new int[] {placeX, at, placeZ});
                }
            }
        }
    }

    private void buds(LimitedRegion region, Random random, List<int[]> grown) {
        for (int[] block : grown) {
            for (int side = 0; side < SIDES.length; side++) {
                if (random.nextFloat() > 0.35f) {
                    continue;
                }
                BlockFace face = SIDES[side];
                int x = block[0] + face.getModX();
                int y = block[1] + face.getModY();
                int z = block[2] + face.getModZ();
                if (!region.isInRegion(x, y, z) || !region.getType(x, y, z).isAir()) {
                    continue;
                }
                int type = random.nextInt(BUD_TYPES.length);
                region.setBlockData(x, y, z, buds[type][side].clone());
            }
        }
    }

    private void rods(LimitedRegion region, Random random, int x, int y, int z) {
        int count = 1 + random.nextInt(2);
        List<BlockFace> faces = new ArrayList<>(List.of(SIDES));
        Collections.shuffle(faces, random);
        int placed = 0;
        for (BlockFace face : faces) {
            if (placed >= count) {
                break;
            }
            int rx = x + face.getModX();
            int rz = z + face.getModZ();
            if (!region.isInRegion(rx, y, rz) || !region.isInRegion(rx, y + 1, rz)) {
                continue;
            }
            if (!region.getType(rx, y, rz).isSolid() || !region.getType(rx, y + 1, rz).isAir()) {
                continue;
            }
            region.setBlockData(rx, y + 1, rz, endRod.clone());
            placed++;
        }
    }

    private static boolean separated(List<int[]> planted, int x, int z) {
        for (int[] tree : planted) {
            int dx = tree[0] - x;
            int dz = tree[2] - z;
            if (dx * dx + dz * dz < 16) {
                return false;
            }
        }
        return true;
    }

    private static boolean roomForTrunk(LimitedRegion region, int x, int surface, int z, int height) {
        if (!region.isInRegion(x, surface, z) || !region.isInRegion(x, surface - 1, z)) {
            return false;
        }
        if (!region.getType(x, surface, z).isSolid() || !region.getType(x, surface - 1, z).isSolid()) {
            return false;
        }
        for (int step = 1; step <= height; step++) {
            if (!region.isInRegion(x, surface + step, z) || !region.getType(x, surface + step, z).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean voidEdge(LimitedRegion region, int x, int y, int z) {
        for (BlockFace face : SIDES) {
            int nx = x + face.getModX();
            int nz = z + face.getModZ();
            if (!region.isInRegion(nx, y, nz) || !region.isInRegion(nx, y - 1, nz)) {
                return true;
            }
            if (!region.getType(nx, y, nz).isSolid() && !region.getType(nx, y - 1, nz).isSolid()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCity(WorldInfo worldInfo, LimitedRegion region, int chunkX, int chunkZ) {
        int buffer = region.getBuffer();
        int minX = (chunkX << 4) - buffer;
        int maxX = (chunkX << 4) + 16 + buffer;
        int minZ = (chunkZ << 4) - buffer;
        int maxZ = (chunkZ << 4) + 16 + buffer;
        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight();
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = minY; y < maxY; y++) {
                    if (!region.isInRegion(x, y, z)) {
                        continue;
                    }
                    Material type = region.getType(x, y, z);
                    if (type == Material.PURPUR_BLOCK
                            || type == Material.PURPUR_PILLAR
                            || type == Material.MAGENTA_STAINED_GLASS
                            || type == Material.END_STONE_BRICKS) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
