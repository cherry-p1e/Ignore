package violetarchive.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import violetarchive.Keys;

/**
 * Orbit, volley, and jump for the library guardian only.
 * The tower sentry keeps a normal shot and a normal teleport.
 */
public final class WatcherMind implements Listener {
    private static final double ORBIT_RADIUS = 0.8;
    private static final int ORBIT_TICKS = 80;
    private static final double NEAR_PLAYER = 32;
    private static final double JUMP_RANGE = 6;

    private final JavaPlugin plugin;
    private final Map<UUID, Watcher> watchers = new HashMap<>();
    private BukkitTask scanner;
    private BukkitTask ticker;

    public WatcherMind(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        scanner = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 20L, 20L);
    }

    public void shutdown() {
        if (scanner != null) {
            scanner.cancel();
            scanner = null;
        }
        stopTicker();
        for (Watcher watcher : new ArrayList<>(watchers.values())) {
            restoreAll(watcher);
            removeOrbit(watcher);
            removeRing(watcher);
        }
        watchers.clear();
    }

    public void forget(UUID id) {
        Watcher watcher = watchers.get(id);
        if (watcher == null) {
            return;
        }
        restoreAll(watcher);
        removeOrbit(watcher);
        removeRing(watcher);
        watchers.remove(id);
        if (watchers.isEmpty()) {
            stopTicker();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ShulkerBullet bullet)) {
            return;
        }
        if (!(bullet.getShooter() instanceof Shulker shulker) || !isWatcher(shulker)) {
            return;
        }
        event.setCancelled(true);
        Watcher watcher = watchers.computeIfAbsent(shulker.getUniqueId(), ignored -> new Watcher());
        ensureTicker();
        if (watcher.jump != null || watcher.volley != null) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (now - watcher.lastVolley < 60) {
            return;
        }
        UUID victim = null;
        if (bullet.getTarget() instanceof Player player) {
            victim = player.getUniqueId();
        } else if (shulker.getTarget() instanceof Player player) {
            victim = player.getUniqueId();
        }
        if (startVolley(watcher, shulker, victim)) {
            watcher.lastVolley = now;
        }
    }

    /**
     * Starts the same volley a cancelled watcher bullet would start.
     * The random shot and the short cooldown stay on the gameplay path.
     */
    public boolean forceVolley(Shulker shulker, Player player) {
        if (shulker == null || player == null || !shulker.isValid() || !isWatcher(shulker)) {
            return false;
        }
        Watcher watcher = watchers.computeIfAbsent(shulker.getUniqueId(), ignored -> new Watcher());
        ensureTicker();
        if (watcher.jump != null || watcher.volley != null) {
            return false;
        }
        if (!startVolley(watcher, shulker, player.getUniqueId())) {
            return false;
        }
        watcher.lastVolley = Bukkit.getCurrentTick();
        return true;
    }

    /**
     * Starts the same jump, including the seat lift and the later shockwave.
     * Skips only the 25 percent roll.
     */
    public boolean forceJump(Shulker shulker) {
        if (shulker == null || !shulker.isValid() || !isWatcher(shulker)) {
            return false;
        }
        Watcher watcher = watchers.computeIfAbsent(shulker.getUniqueId(), ignored -> new Watcher());
        ensureTicker();
        if (watcher.volley != null || watcher.jump != null) {
            return false;
        }
        if (!startJump(watcher, shulker)) {
            return false;
        }
        watcher.nextJumpRoll = Bukkit.getCurrentTick() + 200;
        return true;
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        if (watchers.isEmpty()) {
            return;
        }
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        UUID worldId = event.getWorld().getUID();
        for (Watcher watcher : watchers.values()) {
            if (watcher.volley != null) {
                for (Shot shot : watcher.volley) {
                    if (shot.lifted && worldId.equals(shot.worldId) && (shot.x >> 4) == chunkX && (shot.z >> 4) == chunkZ) {
                        restoreShot(shot);
                    }
                }
            }
            Jump jump = watcher.jump;
            if (jump != null && worldId.equals(jump.worldId) && (jump.seatX >> 4) == chunkX && (jump.seatZ >> 4) == chunkZ) {
                finishJump(watcher, jump, false);
            }
        }
    }

    private void scan() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            for (Entity entity : world.getNearbyEntities(player.getLocation(), NEAR_PLAYER, NEAR_PLAYER, NEAR_PLAYER)) {
                if (entity instanceof Shulker shulker && isWatcher(shulker)) {
                    watchers.computeIfAbsent(shulker.getUniqueId(), ignored -> new Watcher());
                }
            }
        }
        if (!watchers.isEmpty()) {
            ensureTicker();
        }
    }

    private void tick() {
        if (watchers.isEmpty()) {
            stopTicker();
            return;
        }
        int now = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Watcher>> iterator = watchers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Watcher> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Shulker shulker) || !shulker.isValid() || shulker.isDead()) {
                restoreAll(entry.getValue());
                removeOrbit(entry.getValue());
                removeRing(entry.getValue());
                iterator.remove();
                continue;
            }
            Watcher watcher = entry.getValue();
            boolean near = playerWithin(shulker, NEAR_PLAYER);
            if (near) {
                ensureOrbit(watcher, shulker);
                orbit(watcher, shulker);
            } else if (watcher.volley == null && watcher.jump == null) {
                removeOrbit(watcher);
                iterator.remove();
                continue;
            }
            tickVolley(watcher, shulker);
            tickJump(watcher, shulker);
            tickRing(watcher);
            if (near) {
                considerJump(watcher, shulker, now);
            }
        }
    }

    private void considerJump(Watcher watcher, Shulker shulker, int now) {
        if (watcher.volley != null || watcher.jump != null) {
            return;
        }
        if (!playerWithin(shulker, JUMP_RANGE)) {
            return;
        }
        if (now < watcher.nextJumpRoll) {
            return;
        }
        watcher.nextJumpRoll = now + 40;
        if (ThreadLocalRandom.current().nextDouble() < 0.25 && startJump(watcher, shulker)) {
            watcher.nextJumpRoll = now + 200;
        }
    }

    private boolean startVolley(Watcher watcher, Shulker shulker, UUID victim) {
        List<Block> candidates = solidBlocks(shulker);
        if (candidates.size() < 2) {
            return false;
        }
        java.util.Collections.shuffle(candidates, ThreadLocalRandom.current());
        ensureOrbit(watcher, shulker);
        if (watcher.orbit[0] == null || watcher.orbit[1] == null) {
            return false;
        }
        List<Shot> shots = new ArrayList<>(2);
        for (int index = 0; index < 2; index++) {
            Block block = candidates.get(index);
            Shot shot = new Shot();
            shot.shard = watcher.orbit[index];
            shot.busy = index;
            shot.destX = block.getX();
            shot.destY = block.getY();
            shot.destZ = block.getZ();
            shot.worldId = shulker.getWorld().getUID();
            shot.victim = victim;
            shots.add(shot);
        }
        watcher.volley = shots;
        return true;
    }

    private void tickVolley(Watcher watcher, Shulker shulker) {
        if (watcher.volley == null) {
            return;
        }
        if (shulker.getTarget() instanceof Player player) {
            for (Shot shot : watcher.volley) {
                if (!shot.done) {
                    shot.victim = player.getUniqueId();
                }
            }
        }
        boolean pending = false;
        for (Shot shot : watcher.volley) {
            if (shot.done) {
                continue;
            }
            stepShot(shot, shulker);
            if (!shot.done) {
                pending = true;
            }
        }
        if (!pending) {
            watcher.volley = null;
        }
    }

    private void stepShot(Shot shot, Shulker shulker) {
        if (shot.shard == null || !shot.shard.isValid()) {
            if (shot.lifted) {
                restoreShot(shot);
            }
            shot.done = true;
            return;
        }
        World world = shulker.getWorld();
        if (shot.phase == 0) {
            Location dest = center(world, shot.destX, shot.destY, shot.destZ);
            if (moveToward(shot.shard, dest, 0.45)) {
                if (!lift(shot, world)) {
                    shot.done = true;
                    return;
                }
                shot.phase = 1;
                shot.ticks = 0;
            }
            return;
        }
        if (!world.isChunkLoaded(shot.x >> 4, shot.z >> 4)) {
            restoreShot(shot);
            return;
        }
        shot.airTicks++;
        if (shot.airTicks >= 60) {
            restoreShot(shot);
            return;
        }
        if (shot.phase == 1) {
            shot.ticks++;
            Location raised = corner(world, shot.x, shot.y, shot.z);
            raised.add(0, shot.ticks * 0.1, 0);
            teleport(shot.display, raised);
            moveToward(shot.shard, raised.clone().add(0.5, 0.5, 0.5), 1);
            if (shot.ticks >= 10) {
                shot.phase = 2;
                shot.ticks = 0;
            }
            return;
        }
        if (shot.phase == 2) {
            shot.ticks++;
            if (shot.ticks >= 10) {
                shot.phase = 3;
            }
            return;
        }
        Player player = shot.victim == null ? null : Bukkit.getPlayer(shot.victim);
        if (player == null || !player.isValid() || player.isDead() || player.getWorld() != world) {
            restoreShot(shot);
            return;
        }
        Location aim = player.getLocation().add(0, player.getHeight() * 0.5, 0);
        Location displayCenter = shot.display.getLocation().add(0.5, 0.5, 0.5);
        moveToward(shot.display, aim.clone().subtract(0.5, 0.5, 0.5), 0.5);
        moveToward(shot.shard, shot.display.getLocation().clone().add(0.5, 0.5, 0.5), 1);
        BoundingBox box = player.getBoundingBox().expand(0.8);
        Location after = shot.display.getLocation().clone().add(0.5, 0.5, 0.5);
        if (box.contains(displayCenter.toVector()) || box.contains(after.toVector())) {
            player.damage(4, shulker);
            restoreShot(shot);
        }
    }

    private boolean lift(Shot shot, World world) {
        if (!world.isChunkLoaded(shot.destX >> 4, shot.destZ >> 4)) {
            return false;
        }
        Block block = world.getBlockAt(shot.destX, shot.destY, shot.destZ);
        if (!usable(block)) {
            return false;
        }
        shot.x = block.getX();
        shot.y = block.getY();
        shot.z = block.getZ();
        shot.data = block.getBlockData().clone();
        block.setType(Material.AIR, false);
        shot.lifted = true;
        Location corner = block.getLocation();
        shot.display = world.spawn(corner, BlockDisplay.class, display -> {
            display.setBlock(shot.data.clone());
            display.setTeleportDuration(2);
            display.setPersistent(false);
            display.setInvulnerable(true);
        });
        if (shot.display == null) {
            block.setBlockData(shot.data, false);
            shot.lifted = false;
            return false;
        }
        return true;
    }

    private void restoreShot(Shot shot) {
        if (shot.done) {
            return;
        }
        shot.done = true;
        if (shot.display != null && shot.display.isValid()) {
            shot.display.remove();
        }
        shot.display = null;
        if (!shot.lifted || shot.data == null) {
            return;
        }
        World world = Bukkit.getWorld(shot.worldId);
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(shot.x, shot.y, shot.z);
        if (block.getType().isAir()) {
            block.setBlockData(shot.data, false);
        }
        shot.lifted = false;
    }

    private boolean startJump(Watcher watcher, Shulker shulker) {
        Block seat = shulker.getLocation().getBlock().getRelative(shulker.getAttachedFace());
        Material type = seat.getType();
        if (type == Material.BEDROCK || type == Material.BARRIER || isChest(type)) {
            return false;
        }
        if (!type.isSolid()) {
            return false;
        }
        Jump jump = new Jump();
        jump.worldId = shulker.getWorld().getUID();
        jump.origin = shulker.getLocation().clone();
        jump.seatX = seat.getX();
        jump.seatY = seat.getY();
        jump.seatZ = seat.getZ();
        jump.data = seat.getBlockData().clone();
        shulker.setAware(false);
        seat.setType(Material.AIR, false);
        jump.display = shulker.getWorld().spawn(seat.getLocation(), BlockDisplay.class, display -> {
            display.setBlock(jump.data.clone());
            display.setTeleportDuration(2);
            display.setPersistent(false);
            display.setInvulnerable(true);
        });
        shulker.getWorld().playSound(shulker.getLocation(), Sound.ENTITY_SHULKER_TELEPORT, 1f, 1f);
        watcher.jump = jump;
        return true;
    }

    private void tickJump(Watcher watcher, Shulker shulker) {
        Jump jump = watcher.jump;
        if (jump == null) {
            return;
        }
        jump.tick++;
        double raised = 3;
        double offset;
        if (jump.tick <= 8) {
            offset = raised * (jump.tick / 8.0);
        } else if (jump.tick <= 16) {
            offset = raised;
        } else if (jump.tick <= 22) {
            offset = raised * (1 - ((jump.tick - 16) / 6.0));
        } else {
            finishJump(watcher, jump, true);
            return;
        }
        Location body = jump.origin.clone();
        body.setY(jump.origin.getY() + offset);
        shulker.teleport(body);
        if (jump.display != null && jump.display.isValid()) {
            Location seat = new Location(shulker.getWorld(), jump.seatX, jump.seatY + offset, jump.seatZ);
            jump.display.teleport(seat);
        }
    }

    private void finishJump(Watcher watcher, Jump jump, boolean scar) {
        if (watcher.jump != jump) {
            return;
        }
        watcher.jump = null;
        World world = Bukkit.getWorld(jump.worldId);
        Entity entity = null;
        for (Map.Entry<UUID, Watcher> entry : watchers.entrySet()) {
            if (entry.getValue() == watcher) {
                entity = Bukkit.getEntity(entry.getKey());
                break;
            }
        }
        if (entity instanceof Shulker shulker && shulker.isValid()) {
            shulker.teleport(jump.origin);
            shulker.setAware(true);
        }
        if (jump.display != null && jump.display.isValid()) {
            jump.display.remove();
        }
        if (world != null) {
            Block seat = world.getBlockAt(jump.seatX, jump.seatY, jump.seatZ);
            if (seat.getType().isAir() && jump.data != null) {
                seat.setBlockData(jump.data, false);
            }
            if (scar) {
                world.playSound(seat.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                launchRing(watcher, world, jump);
                scar(world, jump);
            }
        }
    }

    private void launchRing(Watcher watcher, World world, Jump jump) {
        removeRing(watcher);
        Location origin = new Location(world, jump.seatX + 0.5, jump.seatY + 1.2, jump.seatZ + 0.5);
        List<ItemDisplay> ring = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            double angle = index * Math.PI * 2 / 12;
            ItemDisplay display = spawnShard(world, origin);
            if (display != null) {
                ring.add(display);
            }
            watcher.ringAngle.add(angle);
        }
        watcher.ring = ring;
        watcher.ringTicks = 0;
        watcher.ringOrigin = origin;
        for (Player player : world.getNearbyPlayers(origin, 3.5)) {
            Vector push = player.getLocation().toVector().subtract(origin.toVector());
            push.setY(0);
            if (push.lengthSquared() < 1.0E-4) {
                push = new Vector(0.35, 0, 0);
            } else {
                push.normalize().multiply(0.35);
            }
            Vector velocity = player.getVelocity();
            velocity.setX(velocity.getX() + push.getX());
            velocity.setZ(velocity.getZ() + push.getZ());
            player.setVelocity(velocity);
        }
    }

    private void tickRing(Watcher watcher) {
        if (watcher.ring == null) {
            return;
        }
        watcher.ringTicks++;
        double radius = Math.min(3, watcher.ringTicks * (3.0 / 8.0));
        Location origin = watcher.ringOrigin;
        for (int index = 0; index < watcher.ring.size(); index++) {
            ItemDisplay display = watcher.ring.get(index);
            if (display == null || !display.isValid() || origin == null) {
                continue;
            }
            double angle = watcher.ringAngle.get(index);
            Location at = origin.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            display.teleport(at);
        }
        if (watcher.ringTicks >= 8) {
            removeRing(watcher);
        }
    }

    private void scar(World world, Jump jump) {
        List<Block> tops = new ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double distance = Math.sqrt(dx * (double) dx + dz * (double) dz);
                if (distance < 2.2 || distance > 3.2) {
                    continue;
                }
                Block top = null;
                for (int y = jump.seatY + 3; y >= jump.seatY - 3; y--) {
                    Block candidate = world.getBlockAt(jump.seatX + dx, y, jump.seatZ + dz);
                    if (!candidate.getType().isSolid() || forbidden(candidate.getType())) {
                        continue;
                    }
                    top = candidate;
                    break;
                }
                if (top != null && !(top.getX() == jump.seatX && top.getY() == jump.seatY && top.getZ() == jump.seatZ)) {
                    tops.add(top);
                }
            }
        }
        int buds = 0;
        for (Block top : tops) {
            if (ThreadLocalRandom.current().nextDouble() >= 0.40) {
                continue;
            }
            top.setType(Material.AMETHYST_BLOCK, false);
            if (buds >= 6) {
                continue;
            }
            for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block side = top.getRelative(face);
                if (!side.getType().isAir()) {
                    continue;
                }
                BlockData bud = Material.SMALL_AMETHYST_BUD.createBlockData(data -> {
                    if (data instanceof Directional directional && directional.getFaces().contains(face)) {
                        directional.setFacing(face);
                    }
                });
                side.setBlockData(bud, false);
                buds++;
                break;
            }
        }
    }

    private void restoreAll(Watcher watcher) {
        if (watcher.volley != null) {
            for (Shot shot : watcher.volley) {
                restoreShot(shot);
            }
            watcher.volley = null;
        }
        if (watcher.jump != null) {
            finishJump(watcher, watcher.jump, false);
        }
    }

    private void ensureOrbit(Watcher watcher, Shulker shulker) {
        for (int index = 0; index < 4; index++) {
            ItemDisplay display = watcher.orbit[index];
            if (display == null || !display.isValid()) {
                watcher.orbit[index] = spawnShard(shulker.getWorld(), shulker.getLocation().add(0, 0.5, 0));
            }
        }
    }

    private void orbit(Watcher watcher, Shulker shulker) {
        watcher.angle += Math.PI * 2 / ORBIT_TICKS;
        Location center = shulker.getLocation().clone().add(0, 0.5, 0);
        for (int index = 0; index < 4; index++) {
            if (watcher.volley != null && index < watcher.volley.size() && !watcher.volley.get(index).done) {
                continue;
            }
            ItemDisplay display = watcher.orbit[index];
            if (display == null || !display.isValid()) {
                continue;
            }
            double angle = watcher.angle + index * Math.PI / 2;
            Location at = center.clone().add(Math.cos(angle) * ORBIT_RADIUS, 0, Math.sin(angle) * ORBIT_RADIUS);
            display.teleport(at);
        }
    }

    private void removeOrbit(Watcher watcher) {
        for (int index = 0; index < watcher.orbit.length; index++) {
            ItemDisplay display = watcher.orbit[index];
            if (display != null && display.isValid()) {
                display.remove();
            }
            watcher.orbit[index] = null;
        }
    }

    private void removeRing(Watcher watcher) {
        if (watcher.ring != null) {
            for (ItemDisplay display : watcher.ring) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
        }
        watcher.ring = null;
        watcher.ringAngle.clear();
        watcher.ringOrigin = null;
    }

    private ItemDisplay spawnShard(World world, Location location) {
        return world.spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.AMETHYST_SHARD));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setTeleportDuration(2);
            display.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f(0.4f, 0.4f, 0.4f),
                    new AxisAngle4f()));
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
        });
    }

    private static boolean moveToward(Entity entity, Location dest, double speed) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        Location from = entity.getLocation();
        Vector delta = dest.toVector().subtract(from.toVector());
        double distance = delta.length();
        if (distance <= speed) {
            entity.teleport(dest);
            return true;
        }
        delta.multiply(speed / distance);
        entity.teleport(from.add(delta));
        return false;
    }

    private static void teleport(Entity entity, Location location) {
        if (entity != null && entity.isValid()) {
            entity.teleport(location);
        }
    }

    private static Location center(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    private static Location corner(World world, int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    private List<Block> solidBlocks(Shulker shulker) {
        List<Block> found = new ArrayList<>();
        Location origin = shulker.getLocation();
        Block occupied = origin.getBlock();
        Block attached = occupied.getRelative(shulker.getAttachedFace());
        World world = shulker.getWorld();
        int cx = origin.getBlockX();
        int cy = origin.getBlockY();
        int cz = origin.getBlockZ();
        for (int x = cx - 5; x <= cx + 5; x++) {
            for (int y = cy - 5; y <= cy + 5; y++) {
                for (int z = cz - 5; z <= cz + 5; z++) {
                    double dx = (x + 0.5) - origin.getX();
                    double dy = (y + 0.5) - (origin.getY() + 0.5);
                    double dz = (z + 0.5) - origin.getZ();
                    if (dx * dx + dy * dy + dz * dz > 25) {
                        continue;
                    }
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (block.equals(occupied) || block.equals(attached) || !usable(block)) {
                        continue;
                    }
                    found.add(block);
                }
            }
        }
        return found;
    }

    private static boolean usable(Block block) {
        Material type = block.getType();
        return type.isSolid() && !type.isAir() && !forbidden(type);
    }

    private static boolean forbidden(Material type) {
        return type == Material.BEDROCK || type == Material.BARRIER || isChest(type);
    }

    private static boolean isChest(Material type) {
        return type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.ENDER_CHEST
                || Tag.COPPER_CHESTS.isTagged(type);
    }

    private static boolean playerWithin(Shulker shulker, double range) {
        double rangeSquared = range * range;
        for (Player player : shulker.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(shulker.getLocation()) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWatcher(Shulker shulker) {
        return shulker.getPersistentDataContainer().has(Keys.WATCHER, PersistentDataType.BYTE);
    }

    private void ensureTicker() {
        if (ticker == null) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private static final class Watcher {
        private final ItemDisplay[] orbit = new ItemDisplay[4];
        private double angle;
        private int lastVolley = -10000;
        private int nextJumpRoll;
        private List<Shot> volley;
        private Jump jump;
        private List<ItemDisplay> ring;
        private final List<Double> ringAngle = new ArrayList<>();
        private Location ringOrigin;
        private int ringTicks;
    }

    private static final class Shot {
        private ItemDisplay shard;
        private int busy;
        private int destX;
        private int destY;
        private int destZ;
        private UUID worldId;
        private UUID victim;
        private int phase;
        private int ticks;
        private int airTicks;
        private boolean lifted;
        private boolean done;
        private int x;
        private int y;
        private int z;
        private BlockData data;
        private BlockDisplay display;
    }

    private static final class Jump {
        private UUID worldId;
        private Location origin;
        private int seatX;
        private int seatY;
        private int seatZ;
        private BlockData data;
        private BlockDisplay display;
        private int tick;
    }
}
