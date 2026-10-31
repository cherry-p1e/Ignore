package violetarchive.structure;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lockable;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.AsyncStructureGenerateEvent;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import net.kyori.adventure.text.Component;
import violetarchive.Keys;
import violetarchive.item.ArchiveItems;

/**
 * Finishes a generated archive on the main thread: one lock id shared by the
 * chest and its guard. The key is never placed in the chest.
 */
public final class StructureListener implements Listener {
    private static final int RETRIES = 10;

    private final JavaPlugin plugin;
    private final WatcherMind watcherMind;

    public StructureListener(JavaPlugin plugin, WatcherMind watcherMind) {
        this.plugin = plugin;
        this.watcherMind = watcherMind;
    }

    @EventHandler
    public void onStructure(AsyncStructureGenerateEvent event) {
        NamespacedKey key = event.getStructure().getKey();
        if (!"violetarchive".equals(key.getNamespace())) {
            return;
        }
        if (event.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        String structure = key.toString();
        if (!"violetarchive:library".equals(structure) && !"violetarchive:tower".equals(structure)) {
            return;
        }
        UUID worldId = event.getWorld().getUID();
        BoundingBox box = event.getBoundingBox();
        double minX = box.getMinX();
        double minY = box.getMinY();
        double minZ = box.getMinZ();
        double maxX = box.getMaxX();
        double maxY = box.getMaxY();
        double maxZ = box.getMaxZ();
        int chunkX = event.getChunkX();
        int chunkZ = event.getChunkZ();
        Bukkit.getScheduler().runTask(plugin, () ->
                finish(worldId, structure, minX, minY, minZ, maxX, maxY, maxZ, chunkX, chunkZ, 0));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Shulker)) {
            return;
        }
        var container = entity.getPersistentDataContainer();
        boolean watcher = container.has(Keys.WATCHER, PersistentDataType.BYTE);
        boolean sentry = container.has(Keys.SENTRY, PersistentDataType.BYTE);
        if (!watcher && !sentry) {
            return;
        }
        if (watcher) {
            watcherMind.forget(entity.getUniqueId());
        }
        String raw = container.get(Keys.LOCK_ID, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        try {
            event.getDrops().add(ArchiveItems.key(UUID.fromString(raw)));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Archive guard " + entity.getUniqueId() + " had a broken lock id");
        }
    }

    private void finish(UUID worldId, String structure, double minX, double minY, double minZ,
                        double maxX, double maxY, double maxZ, int chunkX, int chunkZ, int attempt) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return;
        }
        boolean library = "violetarchive:library".equals(structure);
        String loot = library ? "violetarchive:archive/vault" : "violetarchive:archive/tower";
        // The event box is the pre-heightmap footprint, so its Y can sit far
        // above the blocks. Search tile entities in the loaded chunks instead.
        ChestSearch search = findChest(world, minX, minZ, maxX, maxZ, chunkX, chunkZ, loot);
        Block chest = search.chest;
        Shulker guard = chest == null ? null : findShulker(world, chest);
        if (chest == null || guard == null) {
            // A jigsaw fires once per chunk. Pieces that do not hold the guarded
            // chest are fully loaded and simply have nothing to finish.
            if (chest == null && search.fullyLoaded && attempt >= 2) {
                return;
            }
            retry(worldId, structure, minX, minY, minZ, maxX, maxY, maxZ, chunkX, chunkZ, attempt);
            return;
        }
        ArmResult armed = arm(guard, chest, library);
        if (armed == ArmResult.FAILED) {
            retry(worldId, structure, minX, minY, minZ, maxX, maxY, maxZ, chunkX, chunkZ, attempt);
            return;
        }
        if (armed == ArmResult.ARMED) {
            plugin.getLogger().info("Locked " + structure + " at "
                    + chest.getX() + " " + chest.getY() + " " + chest.getZ());
        }
    }

    public enum ArmResult {
        ARMED, ALREADY, FAILED
    }

    /**
     * Same lock, mark, name, color, and health the structure finisher applies.
     * Already-marked guards only repair a missing chest lock.
     */
    public ArmResult arm(Shulker guard, Block chest, boolean library) {
        NamespacedKey mark = library ? Keys.WATCHER : Keys.SENTRY;
        boolean marked = guard.getPersistentDataContainer().has(mark, PersistentDataType.BYTE);
        UUID lockId = existingLock(chest);
        if (lockId == null) {
            lockId = marked ? lockOn(guard) : null;
        }
        if (lockId == null) {
            lockId = UUID.randomUUID();
        }
        if (existingLock(chest) == null && !lockChest(chest, lockId)) {
            return ArmResult.FAILED;
        }
        if (marked) {
            return ArmResult.ALREADY;
        }
        guard.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1);
        guard.getPersistentDataContainer().set(Keys.LOCK_ID, PersistentDataType.STRING, lockId.toString());
        guard.setColor(DyeColor.PURPLE);
        guard.setPersistent(true);
        guard.setRemoveWhenFarAway(false);
        guard.setCustomNameVisible(true);
        if (library) {
            AttributeInstance health = guard.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(45);
            }
            guard.setHealth(45);
            guard.customName(Component.text("Purpur Watcher"));
        } else {
            guard.customName(Component.text("Archive Sentry"));
        }
        return ArmResult.ARMED;
    }

    /**
     * Locks a chest that has no guard and returns the new lock id.
     * Does not create a key. Returns null when the block cannot be locked.
     */
    public UUID lockNewChest(Block chest) {
        UUID lockId = UUID.randomUUID();
        if (!lockChest(chest, lockId)) {
            return null;
        }
        return lockId;
    }

    private void retry(UUID worldId, String structure, double minX, double minY, double minZ,
                       double maxX, double maxY, double maxZ, int chunkX, int chunkZ, int attempt) {
        if (attempt + 1 >= RETRIES) {
            plugin.getLogger().warning("Could not finish " + structure + " at chunk " + chunkX + ", " + chunkZ);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                finish(worldId, structure, minX, minY, minZ, maxX, maxY, maxZ, chunkX, chunkZ, attempt + 1), 10L);
    }

    private static ChestSearch findChest(World world, double minX, double minZ, double maxX, double maxZ,
                                         int chunkX, int chunkZ, String loot) {
        int[] span = chunkSpan(minX, minZ, maxX, maxZ, chunkX, chunkZ);
        ChestSearch search = new ChestSearch();
        search.fullyLoaded = true;
        for (int cx = span[0]; cx <= span[1]; cx++) {
            for (int cz = span[2]; cz <= span[3]; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    search.fullyLoaded = false;
                    continue;
                }
                Chunk chunk = world.getChunkAt(cx, cz);
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof Lootable lootable) || lootable.getLootTable() == null
                            || !loot.equals(lootable.getLootTable().getKey().toString())) {
                        continue;
                    }
                    if (!inside(state.getX(), state.getZ(), minX, minZ, maxX, maxZ)) {
                        continue;
                    }
                    search.chest = state.getBlock();
                    return search;
                }
            }
        }
        return search;
    }

    private static final class ChestSearch {
        private Block chest;
        private boolean fullyLoaded;
    }

    /**
     * Event chunk plus one chunk of margin, widened to the structure box when
     * that box is a few chunks across. A wild box falls back to the event chunk.
     */
    private static int[] chunkSpan(double minX, double minZ, double maxX, double maxZ,
                                   int chunkX, int chunkZ) {
        int cx0 = chunkX - 1;
        int cx1 = chunkX + 1;
        int cz0 = chunkZ - 1;
        int cz1 = chunkZ + 1;
        int boxCx0 = Math.floorDiv((int) Math.floor(minX), 16);
        int boxCx1 = Math.floorDiv((int) Math.floor(maxX), 16);
        int boxCz0 = Math.floorDiv((int) Math.floor(minZ), 16);
        int boxCz1 = Math.floorDiv((int) Math.floor(maxZ), 16);
        if (boxCx1 - boxCx0 <= 6 && boxCz1 - boxCz0 <= 6) {
            cx0 = Math.min(cx0, boxCx0 - 1);
            cx1 = Math.max(cx1, boxCx1 + 1);
            cz0 = Math.min(cz0, boxCz0 - 1);
            cz1 = Math.max(cz1, boxCz1 + 1);
        }
        return new int[] {cx0, cx1, cz0, cz1};
    }

    private static boolean inside(int x, int z, double minX, double minZ, double maxX, double maxZ) {
        int boxCx0 = Math.floorDiv((int) Math.floor(minX), 16);
        int boxCx1 = Math.floorDiv((int) Math.floor(maxX), 16);
        int boxCz0 = Math.floorDiv((int) Math.floor(minZ), 16);
        int boxCz1 = Math.floorDiv((int) Math.floor(maxZ), 16);
        if (boxCx1 - boxCx0 > 6 || boxCz1 - boxCz0 > 6) {
            return true;
        }
        return x >= Math.floor(minX) - 2 && x <= Math.ceil(maxX) + 2
                && z >= Math.floor(minZ) - 2 && z <= Math.ceil(maxZ) + 2;
    }

    private static Shulker findShulker(World world, Block chest) {
        int cx0 = Math.floorDiv(chest.getX() - 32, 16);
        int cx1 = Math.floorDiv(chest.getX() + 32, 16);
        int cz0 = Math.floorDiv(chest.getZ() - 32, 16);
        int cz1 = Math.floorDiv(chest.getZ() + 32, 16);
        Shulker nearest = null;
        double best = 32.0 * 32.0;
        double x = chest.getX() + 0.5;
        double y = chest.getY() + 0.5;
        double z = chest.getZ() + 0.5;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (!(entity instanceof Shulker shulker)) {
                        continue;
                    }
                    double dx = entity.getLocation().getX() - x;
                    double dy = entity.getLocation().getY() - y;
                    double dz = entity.getLocation().getZ() - z;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance <= best) {
                        best = distance;
                        nearest = shulker;
                    }
                }
            }
        }
        return nearest;
    }

    private static UUID lockOn(Shulker guard) {
        String raw = guard.getPersistentDataContainer().get(Keys.LOCK_ID, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static UUID existingLock(Block chest) {
        if (!(chest.getState() instanceof TileState tile)) {
            return null;
        }
        String raw = tile.getPersistentDataContainer().get(Keys.LOCK_ID, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean lockChest(Block chest, UUID lockId) {
        if (!(chest.getState() instanceof TileState tile) || !(tile instanceof Lockable lockable)) {
            return false;
        }
        tile.getPersistentDataContainer().set(Keys.LOCK_ID, PersistentDataType.STRING, lockId.toString());
        lockable.setLockItem(ArchiveItems.lockTemplate(lockId));
        return tile.update(true, false);
    }
}
