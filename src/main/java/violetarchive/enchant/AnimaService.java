package violetarchive.enchant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.block.TileStateInventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import violetarchive.Keys;

/**
 * A placed Anima box walks toward its owner and rolls its lid one quarter turn
 * per block. The enchantment stays on the item; the tile only remembers the owner.
 */
public final class AnimaService implements Listener {
    private final Set<Pos> boxes = new HashSet<>();
    private final double followDistance;

    public AnimaService(double followDistance) {
        this.followDistance = followDistance;
    }

    public void catchUp() {
        for (World world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                inspect(chunk);
            }
        }
    }

    public void tick() {
        for (Pos pos : new ArrayList<>(boxes)) {
            World world = Bukkit.getWorld(pos.world);
            if (world == null) {
                boxes.remove(pos);
                continue;
            }
            if (!world.isChunkLoaded(pos.x >> 4, pos.z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(pos.x, pos.y, pos.z);
            if (!Tag.SHULKER_BOXES.isTagged(block.getType())) {
                boxes.remove(pos);
                continue;
            }
            if (!(block.getState() instanceof TileState tile)) {
                continue;
            }
            String raw = tile.getPersistentDataContainer().get(Keys.ANIMA_OWNER, PersistentDataType.STRING);
            if (raw == null) {
                boxes.remove(pos);
                continue;
            }
            UUID ownerId;
            try {
                ownerId = UUID.fromString(raw);
            } catch (IllegalArgumentException exception) {
                boxes.remove(pos);
                continue;
            }
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline() || owner.getWorld() != world) {
                continue;
            }
            if (distanceToBox(owner.getEyeLocation(), block.getBoundingBox()) > followDistance) {
                continue;
            }
            if (block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(owner.getLocation()) <= 4) {
                continue;
            }
            double dx = owner.getLocation().getX() - (block.getX() + 0.5);
            double dz = owner.getLocation().getZ() - (block.getZ() + 0.5);
            int stepX = 0;
            int stepZ = 0;
            if (Math.abs(dx) >= Math.abs(dz)) {
                stepX = dx >= 0 ? 1 : -1;
            } else {
                stepZ = dz >= 0 ? 1 : -1;
            }
            if (move(block, stepX, stepZ, 0) || move(block, stepX, stepZ, 1) || move(block, stepX, stepZ, -1)) {
                continue;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!Tag.SHULKER_BOXES.isTagged(block.getType())) {
            return;
        }
        if (!ArchiveEnchants.has(event.getItemInHand(), ArchiveEnchants.ANIMA)) {
            return;
        }
        if (!(block.getState() instanceof TileState tile)) {
            return;
        }
        tile.getPersistentDataContainer().set(
                Keys.ANIMA_OWNER, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
        tile.update(true, false);
        boxes.add(Pos.of(block));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        boxes.remove(Pos.of(event.getBlock()));
    }

    @EventHandler
    public void onDrop(BlockDropItemEvent event) {
        if (!(event.getBlockState() instanceof TileState tile)) {
            return;
        }
        if (!tile.getPersistentDataContainer().has(Keys.ANIMA_OWNER, PersistentDataType.STRING)) {
            return;
        }
        Enchantment anima = ArchiveEnchants.anima();
        if (anima == null) {
            return;
        }
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (!Tag.SHULKER_BOXES.isTagged(stack.getType())) {
                continue;
            }
            stack.addUnsafeEnchantment(anima, 1);
            item.setItemStack(stack);
        }
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        UUID worldId = event.getWorld().getUID();
        boxes.removeIf(pos -> pos.world.equals(worldId) && (pos.x >> 4) == chunkX && (pos.z >> 4) == chunkZ);
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        inspect(event.getChunk());
    }

    private void inspect(org.bukkit.Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (!Tag.SHULKER_BOXES.isTagged(state.getType()) || !(state instanceof TileState tile)) {
                continue;
            }
            if (tile.getPersistentDataContainer().has(Keys.ANIMA_OWNER, PersistentDataType.STRING)) {
                boxes.add(new Pos(chunk.getWorld().getUID(), state.getX(), state.getY(), state.getZ()));
            }
        }
    }

    private boolean move(Block block, int stepX, int stepZ, int stepY) {
        World world = block.getWorld();
        int x = block.getX() + stepX;
        int y = block.getY() + stepY;
        int z = block.getZ() + stepZ;
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || y - 1 < world.getMinHeight()) {
            return false;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        Block dest = world.getBlockAt(x, y, z);
        Block below = world.getBlockAt(x, y - 1, z);
        if (dest.getType() == Material.LAVA || (!dest.getType().isAir() && !dest.isReplaceable())) {
            return false;
        }
        if (!below.getType().isSolid() || below.getType() == Material.LAVA) {
            return false;
        }
        if (!(block.getState() instanceof TileStateInventoryHolder holder)) {
            return false;
        }
        Inventory live = holder.getInventory();
        ItemStack[] contents = new ItemStack[live.getSize()];
        for (int slot = 0; slot < live.getSize(); slot++) {
            ItemStack stack = live.getItem(slot);
            contents[slot] = stack == null || stack.getType().isAir() ? null : stack.clone();
        }
        ItemStack buffer = new ItemStack(Material.PAPER);
        buffer.editPersistentDataContainer(container -> holder.getPersistentDataContainer().copyTo(container, true));
        BlockData data = block.getBlockData().clone();
        AnimaRoll travel = stepX > 0 ? AnimaRoll.EAST : stepX < 0 ? AnimaRoll.WEST : stepZ > 0 ? AnimaRoll.SOUTH : AnimaRoll.NORTH;
        if (data instanceof Directional directional) {
            AnimaRoll current = roll(directional.getFacing());
            AnimaRoll next = current == null ? AnimaRoll.UP : AnimaRoll.next(travel, current);
            BlockFace face = face(next);
            if (directional.getFaces().contains(face)) {
                directional.setFacing(face);
            }
        }
        for (int slot = 0; slot < live.getSize(); slot++) {
            live.setItem(slot, null);
        }
        block.setType(Material.AIR, false);
        dest.setBlockData(data, false);
        if (!(dest.getState() instanceof TileStateInventoryHolder placed)) {
            return true;
        }
        Inventory restored = placed.getInventory();
        for (int slot = 0; slot < contents.length && slot < restored.getSize(); slot++) {
            restored.setItem(slot, contents[slot]);
        }
        buffer.getPersistentDataContainer().copyTo(placed.getPersistentDataContainer(), true);
        placed.update(true, false);
        boxes.remove(Pos.of(block));
        boxes.add(Pos.of(dest));
        world.playSound(dest.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_SHULKER_TELEPORT, 1f, 1f);
        return true;
    }

    private static double distanceToBox(org.bukkit.Location eyes, BoundingBox box) {
        double x = clamp(eyes.getX(), box.getMinX(), box.getMaxX());
        double y = clamp(eyes.getY(), box.getMinY(), box.getMaxY());
        double z = clamp(eyes.getZ(), box.getMinZ(), box.getMaxZ());
        double dx = eyes.getX() - x;
        double dy = eyes.getY() - y;
        double dz = eyes.getZ() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static AnimaRoll roll(BlockFace face) {
        return switch (face) {
            case UP -> AnimaRoll.UP;
            case DOWN -> AnimaRoll.DOWN;
            case NORTH -> AnimaRoll.NORTH;
            case SOUTH -> AnimaRoll.SOUTH;
            case EAST -> AnimaRoll.EAST;
            case WEST -> AnimaRoll.WEST;
            default -> null;
        };
    }

    private static BlockFace face(AnimaRoll roll) {
        return switch (roll) {
            case UP -> BlockFace.UP;
            case DOWN -> BlockFace.DOWN;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case EAST -> BlockFace.EAST;
            case WEST -> BlockFace.WEST;
        };
    }

    private record Pos(UUID world, int x, int y, int z) {
        private static Pos of(Block block) {
            return new Pos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
