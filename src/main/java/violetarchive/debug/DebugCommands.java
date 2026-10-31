package violetarchive.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.block.TileStateInventoryHolder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.PotionContents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lockable;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.util.Vector;
import violetarchive.Keys;
import violetarchive.VioletArchivePlugin;
import violetarchive.enchant.ArchiveEnchants;
import violetarchive.item.ArchiveItems;
import violetarchive.structure.StructureListener;
import violetarchive.world.ForestNoise;

/**
 * Player commands that reach archive behavior a normal session cannot force.
 * Each trigger calls the gameplay service. Unused, they change nothing.
 */
public final class DebugCommands {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private DebugCommands() {
    }

    public static void register(Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("violetarchive")
                .requires(Commands.restricted(source -> source.getSender().hasPermission("violetarchive.debug")))
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("give")
                        .then(Commands.literal("blade").executes(ctx -> giveBlade(ctx.getSource())))
                        .then(Commands.literal("key").executes(ctx -> giveKey(ctx.getSource())))
                        .then(Commands.literal("decoy").executes(ctx -> giveDecoy(ctx.getSource())))
                        .then(Commands.literal("vindicta").executes(ctx -> giveBook(ctx.getSource())))
                        .then(Commands.literal("chestplate").executes(ctx -> giveWearable(ctx.getSource(), Material.DIAMOND_CHESTPLATE)))
                        .then(Commands.literal("elytra").executes(ctx -> giveWearable(ctx.getSource(), Material.ELYTRA)))
                        .then(Commands.literal("ration").executes(ctx -> giveRation(ctx.getSource())))
                        .then(Commands.literal("anima").executes(ctx -> giveAnima(ctx.getSource()))))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("watcher").executes(ctx -> spawn(ctx.getSource(), true)))
                        .then(Commands.literal("sentry").executes(ctx -> spawn(ctx.getSource(), false))))
                .then(Commands.literal("locate")
                        .then(Commands.literal("library").executes(ctx -> locate(ctx.getSource(), "library", false)))
                        .then(Commands.literal("tower").executes(ctx -> locate(ctx.getSource(), "tower", false)))
                        .then(Commands.literal("forest").executes(ctx -> locate(ctx.getSource(), "forest", false))))
                .then(Commands.literal("tp")
                        .then(Commands.literal("library").executes(ctx -> locate(ctx.getSource(), "library", true)))
                        .then(Commands.literal("tower").executes(ctx -> locate(ctx.getSource(), "tower", true)))
                        .then(Commands.literal("forest").executes(ctx -> locate(ctx.getSource(), "forest", true))))
                .then(Commands.literal("trigger")
                        .then(Commands.literal("volley").executes(ctx -> triggerVolley(ctx.getSource())))
                        .then(Commands.literal("jump").executes(ctx -> triggerJump(ctx.getSource())))
                        .then(Commands.literal("vindicta").executes(ctx -> triggerVindicta(ctx.getSource())))
                        .then(Commands.literal("ration").executes(ctx -> triggerRation(ctx.getSource())))
                        .then(Commands.literal("anima").executes(ctx -> triggerAnima(ctx.getSource()))))
                .then(Commands.literal("loot").then(Commands.literal("chest")
                        .then(lootTable("swords"))
                        .then(lootTable("bows"))
                        .then(lootTable("armor"))
                        .then(lootTable("tools"))
                        .then(lootTable("depths"))
                        .then(lootTable("mending"))
                        .then(lootTable("curses"))
                        .then(lootTable("supplies"))
                        .then(lootTable("vault"))
                        .then(lootTable("tower"))))
                .then(Commands.literal("inspect").executes(ctx -> inspect(ctx.getSource())));
        commands.register(root.build(), "Violet Archive debug checks");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lootTable(String name) {
        return Commands.literal(name)
                .executes(ctx -> loot(ctx.getSource(), name, 1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> loot(ctx.getSource(), name, IntegerArgumentType.getInteger(ctx, "count"))));
    }

    private static int help(CommandSourceStack stack) {
        return play(stack, player -> say(player, "give, spawn, locate, tp, trigger, loot chest, inspect"));
    }

    private static int giveBlade(CommandSourceStack stack) {
        return play(stack, player -> {
            ItemStack blade = ArchiveItems.blade();
            render(player, blade);
            offer(player, blade);
            say(player, "blade " + describeItem(blade));
        });
    }

    private static int giveKey(CommandSourceStack stack) {
        return play(stack, player -> {
            Block chest = placeChest(player, 0);
            if (chest == null) {
                say(player, "key failed: no chest spot");
                return;
            }
            UUID lockId = plugin().structures().lockNewChest(chest);
            if (lockId == null) {
                say(player, "key failed: chest did not lock");
                return;
            }
            ItemStack key = ArchiveItems.key(lockId);
            render(player, key);
            offer(player, key);
            say(player, "key lock " + lockId + " chest " + pos(chest) + " " + describeItem(key));
        });
    }

    private static int giveDecoy(CommandSourceStack stack) {
        return play(stack, player -> {
            VioletArchivePlugin plugin = plugin();
            ItemStack decoy = new ItemStack(Material.AMETHYST_SHARD);
            decoy.editMeta(meta -> {
                meta.itemName(plugin.translations().render("item.violetarchive.archive_key", player.locale()));
                meta.lore(List.of(plugin.translations().render("item.violetarchive.archive_key.lore", player.locale())));
            });
            offer(player, decoy);
            say(player, "decoy " + describeItem(decoy));
        });
    }

    private static int giveBook(CommandSourceStack stack) {
        return play(stack, player -> {
            Enchantment vindicta = ArchiveEnchants.vindicta();
            if (vindicta == null) {
                say(player, "vindicta is not registered");
                return;
            }
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(vindicta, 1, true));
            int stored = book.getItemMeta() instanceof EnchantmentStorageMeta meta ? meta.getStoredEnchantLevel(vindicta) : 0;
            offer(player, book);
            say(player, "vindicta book stored " + stored);
        });
    }

    private static int giveWearable(CommandSourceStack stack, Material material) {
        return play(stack, player -> {
            Enchantment vindicta = ArchiveEnchants.vindicta();
            if (vindicta == null) {
                say(player, "vindicta is not registered");
                return;
            }
            ItemStack wearable = new ItemStack(material);
            wearable.addUnsafeEnchantment(vindicta, 1);
            offer(player, wearable);
            say(player, material.name().toLowerCase() + " vindicta " + wearable.getEnchantmentLevel(vindicta));
        });
    }

    private static int giveRation(CommandSourceStack stack) {
        return play(stack, player -> {
            Enchantment ration = ArchiveEnchants.ration();
            if (ration == null) {
                say(player, "ration is not registered");
                return;
            }
            ItemStack box = new ItemStack(Material.SHULKER_BOX);
            box.addUnsafeEnchantment(ration, 1);
            box.editMeta(BlockStateMeta.class, meta -> {
                BlockState state = meta.getBlockState();
                if (state instanceof TileStateInventoryHolder holder) {
                    Inventory inventory = holder.getSnapshotInventory();
                    inventory.setItem(0, new ItemStack(Material.BREAD, 4));
                    inventory.setItem(1, new ItemStack(Material.COOKIE, 4));
                    inventory.setItem(2, new ItemStack(Material.COOKED_BEEF, 4));
                    inventory.setItem(3, potion(Material.POTION, PotionType.HEALING));
                    inventory.setItem(4, potion(Material.SPLASH_POTION, PotionType.HEALING));
                    inventory.setItem(5, new ItemStack(Material.MILK_BUCKET));
                    meta.setBlockState(state);
                }
            });
            offer(player, box);
            plugin().ration().notice(player);
            say(player, "ration box " + contents(box));
        });
    }

    private static int giveAnima(CommandSourceStack stack) {
        return play(stack, player -> {
            Enchantment anima = ArchiveEnchants.anima();
            if (anima == null) {
                say(player, "anima is not registered");
                return;
            }
            ItemStack box = new ItemStack(Material.PURPLE_SHULKER_BOX);
            box.addUnsafeEnchantment(anima, 1);
            offer(player, box);
            say(player, "anima box follow " + plugin().followDistance());
        });
    }

    private static int spawn(CommandSourceStack stack, boolean watcher) {
        return play(stack, player -> {
            BlockFace face = facing(player);
            int x = player.getLocation().getBlockX() + face.getModX() * 6;
            int y = player.getLocation().getBlockY() - 1;
            int z = player.getLocation().getBlockZ() + face.getModZ() * 6;
            World world = player.getWorld();
            if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) {
                say(player, "spawn failed: height");
                return;
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    world.getBlockAt(x + dx, y, z + dz).setType(Material.END_STONE, false);
                    world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR, false);
                    world.getBlockAt(x + dx, y + 2, z + dz).setType(Material.AIR, false);
                }
            }
            world.getBlockAt(x + 3, y, z).setType(Material.BEDROCK, false);
            world.getBlockAt(x, y, z + 3).setType(Material.BARRIER, false);
            world.getBlockAt(x - 3, y, z).setType(Material.CHEST, false);
            Block chest = world.getBlockAt(x + 2, y + 1, z);
            chest.setType(Material.CHEST, false);
            Shulker shulker = world.spawn(new Location(world, x + 0.5, y + 1, z + 0.5), Shulker.class, entity -> {
                entity.setAttachedFace(BlockFace.DOWN);
                entity.setAware(true);
                entity.setRemoveWhenFarAway(false);
                entity.setPersistent(true);
            });
            StructureListener.ArmResult armed = plugin().structures().arm(shulker, chest, watcher);
            if (armed == StructureListener.ArmResult.FAILED) {
                say(player, "spawn failed: lock");
                return;
            }
            UUID lockId = lockId(chest);
            if (lockId != null) {
                ItemStack key = ArchiveItems.key(lockId);
                render(player, key);
                offer(player, key);
            }
            String kind = watcher ? "watcher" : "sentry";
            say(player, kind + " " + pos(shulker)
                    + " health " + shulker.getHealth() + "/" + shulker.getMaxHealth()
                    + " name " + plain(shulker.customName())
                    + " color " + shulker.getColor()
                    + " persistent " + shulker.isPersistent()
                    + " lock " + lockId
                    + " chest " + pos(chest)
                    + " ring-bedrock " + (x + 3) + " " + y + " " + z
                    + " ring-barrier " + x + " " + y + " " + (z + 3)
                    + " ring-chest " + (x - 3) + " " + y + " " + z
                    + " pdc " + pdc(shulker.getPersistentDataContainer()));
        });
    }

    private static int locate(CommandSourceStack stack, String kind, boolean teleport) {
        return play(stack, player -> {
            if ("forest".equals(kind)) {
                forest(player, teleport);
                return;
            }
            World end = end(player);
            if (end == null) {
                say(player, kind + " failed: the End did not load");
                return;
            }
            Structure structure = Registry.STRUCTURE.get(NamespacedKey.fromString("violetarchive:" + kind));
            if (structure == null) {
                say(player, kind + " is not in the structure registry");
                return;
            }
            Location found = findStructure(end, structure);
            if (found == null) {
                say(player, kind + " was not found outside 1024");
                return;
            }
            Block chest = archiveChest(end, found, "library".equals(kind)
                    ? "violetarchive:archive/vault"
                    : "violetarchive:archive/tower");
            Location spot = chest != null ? chest.getLocation() : found;
            int x = spot.getBlockX();
            int z = spot.getBlockZ();
            int y = end.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            if (chest != null) {
                y = Math.max(y, chest.getY() + 1);
            }
            double distance = Math.hypot(x, z);
            say(player, kind + " " + x + " " + y + " " + z
                    + " origin-distance " + Math.round(distance)
                    + (chest == null ? " no-chest" : " chest " + pos(chest) + " loot " + lootKey(chest)));
            if (teleport) {
                boolean moved = player.teleport(new Location(end, x + 0.5, y + 1, z + 0.5));
                say(player, "teleport " + moved);
            }
        });
    }

    private static void forest(Player player, boolean teleport) {
        World end = end(player);
        if (end == null) {
            say(player, "forest failed: the End did not load");
            return;
        }
        int preferX = player.getWorld() == end ? player.getLocation().getBlockX() : 0;
        int preferZ = player.getWorld() == end ? player.getLocation().getBlockZ() : 0;
        List<int[]> chunks = forestChunks(end.getSeed(), preferX, preferZ);
        if (chunks.isEmpty()) {
            say(player, "forest cell was not found");
            return;
        }
        int[] first = chunks.get(0);
        long centerX = first[0] * 16L + 8L;
        long centerZ = first[1] * 16L + 8L;
        say(player, "forest chunk " + first[0] + " " + first[1]
                + " block " + centerX + " " + centerZ
                + " origin-distance " + Math.round(Math.hypot(centerX, centerZ)));
        if (!teleport) {
            return;
        }
        for (int[] chunk : chunks) {
            end.getChunkAt(chunk[0], chunk[1]);
            int x = chunk[0] * 16 + 8;
            int z = chunk[1] * 16 + 8;
            int y = end.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            Material surface = end.getBlockAt(x, y, z).getType();
            if (surface.isAir() || y <= end.getMinHeight()) {
                continue;
            }
            boolean moved = player.teleport(new Location(end, x + 0.5, y + 1, z + 0.5));
            say(player, "forest surface " + surface.name().toLowerCase()
                    + " biome " + end.getBiome(x, y, z).getKey()
                    + " at " + x + " " + (y + 1) + " " + z
                    + " teleport " + moved);
            return;
        }
        say(player, "forest chunks had no ground");
    }

    private static int triggerVolley(CommandSourceStack stack) {
        return play(stack, player -> {
            Shulker shulker = markedShulker(player, Keys.WATCHER);
            if (shulker == null) {
                say(player, "volley failed: no watcher");
                return;
            }
            boolean started = plugin().watcherMind().forceVolley(shulker, player);
            say(player, "volley " + (started ? "started" : "did-not-start") + " watcher " + pos(shulker));
        });
    }

    private static int triggerJump(CommandSourceStack stack) {
        return play(stack, player -> {
            Shulker shulker = markedShulker(player, Keys.WATCHER);
            if (shulker == null) {
                say(player, "jump failed: no watcher");
                return;
            }
            Block seat = shulker.getLocation().getBlock().getRelative(shulker.getAttachedFace());
            Material before = seat.getType();
            boolean started = plugin().watcherMind().forceJump(shulker);
            say(player, "jump " + (started ? "started" : "did-not-start")
                    + " seat " + pos(seat) + " " + before.name().toLowerCase()
                    + " -> " + seat.getType().name().toLowerCase()
                    + " aware " + shulker.isAware());
        });
    }

    private static int triggerVindicta(CommandSourceStack stack) {
        return play(stack, player -> {
            LivingEntity target = mob(player);
            if (target == null) {
                say(player, "vindicta failed: no mob");
                return;
            }
            double before = target.getHealth();
            ShulkerBullet bullet = plugin().vindicta().launch(player, target);
            if (bullet == null) {
                say(player, "vindicta did not fire");
                return;
            }
            boolean marked = bullet.getPersistentDataContainer().has(Keys.VINDICTA_BULLET, PersistentDataType.BYTE);
            say(player, "vindicta bullet " + bullet.getUniqueId()
                    + " marked " + marked
                    + " target " + plain(target.customName())
                    + " health " + before);
            VioletArchivePlugin plugin = plugin();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid()) {
                    say(player, "vindicta target gone");
                    return;
                }
                say(player, "vindicta next health " + target.getHealth()
                        + " effects " + effects(target)
                        + " velocity " + vector(target.getVelocity()));
            }, 2L);
        });
    }

    private static int triggerRation(CommandSourceStack stack) {
        return play(stack, player -> {
            plugin().ration().notice(player);
            int food = player.getFoodLevel();
            double health = player.getHealth();
            String before = rationBoxes(player);
            boolean open = player.getOpenInventory().getType().name().equals("SHULKER_BOX");
            plugin().ration().tick();
            say(player, "ration open " + open
                    + " food " + food + "->" + player.getFoodLevel()
                    + " health " + health + "->" + player.getHealth()
                    + " effects " + effects(player)
                    + " before [" + before + "] after [" + rationBoxes(player) + "]");
        });
    }

    private static int triggerAnima(CommandSourceStack stack) {
        return play(stack, player -> {
            List<String> before = animaSpots(player.getWorld());
            plugin().anima().tick();
            List<String> after = animaSpots(player.getWorld());
            say(player, "anima follow " + plugin().followDistance()
                    + " before " + String.join(" | ", before)
                    + " after " + String.join(" | ", after));
        });
    }

    private static int loot(CommandSourceStack stack, String name, int count) {
        return play(stack, player -> {
            LootTable table = Bukkit.getLootTable(NamespacedKey.fromString("violetarchive:archive/" + name));
            if (table == null) {
                say(player, "loot table missing " + name);
                return;
            }
            World world = player.getWorld();
            int x = player.getLocation().getBlockX() + 2;
            int y = player.getLocation().getBlockY();
            int z = player.getLocation().getBlockZ();
            for (int index = 0; index < count; index++) {
                Block block = world.getBlockAt(x + index, y, z);
                block.setType(Material.CHEST, false);
                BlockState state = block.getState();
                if (state instanceof Lootable lootable) {
                    lootable.setLootTable(table, world.getSeed() + index * 17L + name.hashCode());
                    state.update(true, false);
                }
            }
            say(player, "loot chests " + count + " " + name + " from " + x + " " + y + " " + z);
        });
    }

    private static int inspect(CommandSourceStack stack) {
        return play(stack, player -> {
            say(player, "locale " + player.locale()
                    + " follow " + plugin().followDistance()
                    + " biome " + player.getLocation().getBlock().getBiome().getKey()
                    + " recipes " + archiveRecipes());
            say(player, "hand " + describeItem(player.getInventory().getItemInMainHand()));
            Block block = player.getTargetBlockExact(8);
            if (block == null || block.getType().isAir()) {
                block = nearestChest(player, 5);
            }
            if (block != null) {
                say(player, "block " + block.getType().name().toLowerCase() + " " + pos(block)
                        + " " + lockText(block)
                        + " loot " + lootKey(block));
            }
            Entity entity = player.getTargetEntity(8, false);
            if (!(entity instanceof Item) && !(entity instanceof LivingEntity)) {
                entity = markedShulker(player, Keys.WATCHER);
                if (entity == null) {
                    entity = markedShulker(player, Keys.SENTRY);
                }
            }
            if (entity instanceof Item item) {
                say(player, "dropped " + describeItem(item.getItemStack()));
            } else if (entity instanceof LivingEntity living) {
                String color = living instanceof Shulker shulker ? " color " + shulker.getColor() : "";
                say(player, "entity " + living.getType().name().toLowerCase()
                        + " " + pos(living)
                        + " name " + plain(living.customName())
                        + " health " + living.getHealth() + "/" + living.getMaxHealth()
                        + " aware " + (living instanceof org.bukkit.entity.Mob mob ? mob.isAware() : false)
                        + " persistent " + living.isPersistent()
                        + color
                        + " effects " + effects(living)
                        + " velocity " + vector(living.getVelocity())
                        + " pdc " + pdc(living.getPersistentDataContainer()));
            }
            reportEnchant(player, ArchiveEnchants.vindicta());
            reportEnchant(player, ArchiveEnchants.ration());
            reportEnchant(player, ArchiveEnchants.anima());
        });
    }

    private static int play(CommandSourceStack stack, java.util.function.Consumer<Player> action) {
        if (!(stack.getSender() instanceof Player player)) {
            stack.sendFailure(Component.text("[violetarchive] a player has to run this"));
            return 0;
        }
        if (!player.hasPermission("violetarchive.debug")) {
            stack.sendFailure(Component.text("[violetarchive] missing violetarchive.debug"));
            return 0;
        }
        VioletArchivePlugin plugin = plugin();
        if (plugin == null || !plugin.isEnabled()) {
            stack.sendFailure(Component.text("[violetarchive] plugin is not enabled"));
            return 0;
        }
        Runnable task = () -> {
            try {
                action.accept(player);
            } catch (RuntimeException exception) {
                say(player, "error " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                plugin.getLogger().log(Level.WARNING, "debug command failed", exception);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
        return 1;
    }

    private static VioletArchivePlugin plugin() {
        return VioletArchivePlugin.get();
    }

    private static void render(Player player, ItemStack stack) {
        ArchiveItems.render(stack, player.locale(), plugin().translations());
    }

    private static void offer(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), extra);
        }
    }

    private static ItemStack potion(Material material, PotionType type) {
        ItemStack stack = new ItemStack(material);
        stack.setData(DataComponentTypes.POTION_CONTENTS, PotionContents.potionContents().potion(type));
        return stack;
    }

    private static Block placeChest(Player player, int along) {
        BlockFace face = facing(player);
        World world = player.getWorld();
        int x = player.getLocation().getBlockX() + face.getModX() * (2 + along);
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ() + face.getModZ() * (2 + along);
        if (y <= world.getMinHeight() || y >= world.getMaxHeight()) {
            return null;
        }
        Block ground = world.getBlockAt(x, y - 1, z);
        if (!ground.getType().isSolid()) {
            ground.setType(Material.END_STONE, false);
        }
        Block chest = world.getBlockAt(x, y, z);
        chest.setType(Material.CHEST, false);
        return chest;
    }

    private static World end(Player player) {
        World world = Bukkit.getWorld("world_the_end");
        if (world != null) {
            return world;
        }
        say(player, "creating the End");
        return new WorldCreator("world_the_end").environment(World.Environment.THE_END).createWorld();
    }

    private static Location findStructure(World world, Structure structure) {
        Location origin = new Location(world, 0, 80, 0);
        for (int step = 0; step < 6; step++) {
            StructureSearchResult result = world.locateNearestStructure(origin, structure, 192, false);
            if (result == null || result.getLocation() == null) {
                return null;
            }
            Location found = result.getLocation();
            if (Math.hypot(found.getX(), found.getZ()) > 1024) {
                return found;
            }
            double distance = Math.max(1, Math.hypot(found.getX(), found.getZ()));
            double push = 1400 + step * 500.0;
            origin = new Location(world, found.getX() * push / distance, 80, found.getZ() * push / distance);
        }
        return null;
    }

    private static Block archiveChest(World world, Location origin, String preferred) {
        int centerX = origin.getBlockX() >> 4;
        int centerZ = origin.getBlockZ() >> 4;
        Block fallback = null;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Chunk chunk = world.getChunkAt(centerX + dx, centerZ + dz);
                for (BlockState state : chunk.getTileEntities()) {
                    if (!(state instanceof Lootable) || state.getType() != Material.CHEST) {
                        continue;
                    }
                    String key = lootKey(state.getBlock());
                    if (preferred.equals(key)) {
                        return state.getBlock();
                    }
                    if (fallback == null && key.startsWith("violetarchive:")) {
                        fallback = state.getBlock();
                    }
                }
            }
        }
        return fallback;
    }

    private static List<int[]> forestChunks(long seed, int preferX, int preferZ) {
        List<int[]> found = new ArrayList<>();
        int originX = preferX >> 4;
        int originZ = preferZ >> 4;
        for (int radius = 0; radius <= 280 && found.size() < 8; radius++) {
            for (int dx = -radius; dx <= radius && found.size() < 8; dx++) {
                for (int dz = -radius; dz <= radius && found.size() < 8; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int chunkX = originX + dx;
                    int chunkZ = originZ + dz;
                    if (ForestNoise.isForestChunk(seed, chunkX, chunkZ)) {
                        found.add(new int[] {chunkX, chunkZ});
                    }
                }
            }
        }
        found.sort(Comparator.comparingLong(chunk -> {
            long dx = chunk[0] * 16L + 8L - preferX;
            long dz = chunk[1] * 16L + 8L - preferZ;
            return dx * dx + dz * dz;
        }));
        return found;
    }

    private static Shulker markedShulker(Player player, NamespacedKey key) {
        Entity looked = player.getTargetEntity(24, false);
        if (looked instanceof Shulker shulker && shulker.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return shulker;
        }
        Shulker best = null;
        double bestDistance = 32 * 32;
        for (Entity entity : player.getNearbyEntities(32, 16, 32)) {
            if (!(entity instanceof Shulker shulker) || !shulker.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < bestDistance) {
                best = shulker;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static LivingEntity mob(Player player) {
        Entity looked = player.getTargetEntity(24, false);
        if (looked instanceof LivingEntity living && usable(living, player)) {
            return living;
        }
        LivingEntity marked = null;
        LivingEntity any = null;
        double markedDistance = Double.MAX_VALUE;
        double anyDistance = Double.MAX_VALUE;
        for (Entity entity : player.getNearbyEntities(16, 8, 16)) {
            if (!(entity instanceof LivingEntity living) || !usable(living, player)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            boolean archive = living.getPersistentDataContainer().has(Keys.WATCHER, PersistentDataType.BYTE)
                    || living.getPersistentDataContainer().has(Keys.SENTRY, PersistentDataType.BYTE);
            if (archive && distance < markedDistance) {
                marked = living;
                markedDistance = distance;
            }
            if (distance < anyDistance) {
                any = living;
                anyDistance = distance;
            }
        }
        return marked != null ? marked : any;
    }

    private static boolean usable(LivingEntity living, Player player) {
        return living != player && living.isValid() && !living.isDead() && living.getType() != org.bukkit.entity.EntityType.ARMOR_STAND;
    }

    private static Block nearestChest(Player player, int radius) {
        Block best = null;
        double bestDistance = Double.MAX_VALUE;
        Location origin = player.getLocation();
        int x = origin.getBlockX();
        int y = origin.getBlockY();
        int z = origin.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = origin.getWorld().getBlockAt(x + dx, y + dy, z + dz);
                    if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
                        continue;
                    }
                    double distance = block.getLocation().distanceSquared(origin);
                    if (distance < bestDistance) {
                        best = block;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static List<String> animaSpots(World world) {
        List<String> spots = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (!Tag.SHULKER_BOXES.isTagged(state.getType()) || !(state instanceof TileState tile)) {
                    continue;
                }
                if (!tile.getPersistentDataContainer().has(Keys.ANIMA_OWNER, PersistentDataType.STRING)) {
                    continue;
                }
                String facing = "none";
                if (state.getBlockData() instanceof Directional directional) {
                    facing = directional.getFacing().name().toLowerCase();
                }
                spots.add(state.getX() + " " + state.getY() + " " + state.getZ() + " " + facing);
            }
        }
        spots.sort(Comparator.naturalOrder());
        return spots;
    }

    private static String rationBoxes(Player player) {
        StringBuilder builder = new StringBuilder();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (!ArchiveEnchants.has(stack, ArchiveEnchants.RATION)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" ; ");
            }
            builder.append(contents(stack));
        }
        return builder.toString();
    }

    private static String contents(ItemStack box) {
        if (box == null || !(box.getItemMeta() instanceof BlockStateMeta meta)) {
            return "";
        }
        if (!(meta.getBlockState() instanceof TileStateInventoryHolder holder)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ItemStack stack : holder.getSnapshotInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(stack.getType().name().toLowerCase()).append('x').append(stack.getAmount());
            PotionContents potion = stack.getData(DataComponentTypes.POTION_CONTENTS);
            if (potion != null && potion.potion() != null) {
                builder.append(':').append(potion.potion().name().toLowerCase());
            }
        }
        return builder.toString();
    }

    private static void reportEnchant(Player player, Enchantment enchantment) {
        if (enchantment == null) {
            say(player, "enchant missing");
            return;
        }
        say(player, "enchant " + enchantment.getKey()
                + " max " + enchantment.getMaxLevel()
                + " discoverable " + enchantment.isDiscoverable()
                + " tradeable " + enchantment.isTradeable()
                + " description " + plain(enchantment.description())
                + " exclusive " + setText(enchantment.getExclusiveWith())
                + " primary " + setText(enchantment.getPrimaryItems())
                + " chestplate " + enchantment.canEnchantItem(new ItemStack(Material.DIAMOND_CHESTPLATE))
                + " elytra " + enchantment.canEnchantItem(new ItemStack(Material.ELYTRA))
                + " shulker " + enchantment.canEnchantItem(new ItemStack(Material.SHULKER_BOX))
                + " sword " + enchantment.canEnchantItem(new ItemStack(Material.DIAMOND_SWORD)));
    }

    private static String setText(Object set) {
        return set == null ? "null" : set.toString();
    }

    private static int archiveRecipes() {
        int count = 0;
        var recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (recipe instanceof org.bukkit.Keyed keyed
                    && keyed.getKey() != null
                    && "violetarchive".equals(keyed.getKey().getNamespace())) {
                count++;
            }
        }
        return count;
    }

    private static String describeItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "air";
        }
        ItemMeta meta = stack.getItemMeta();
        Component itemName = meta == null ? null : meta.itemName();
        Component custom = meta != null && meta.hasDisplayName() ? meta.displayName() : null;
        String lore = "none";
        if (meta != null && meta.hasLore() && meta.lore() != null) {
            StringBuilder builder = new StringBuilder();
            for (Component line : meta.lore()) {
                if (!builder.isEmpty()) {
                    builder.append(" / ");
                }
                builder.append(plain(line))
                        .append(" italic=")
                        .append(line.decoration(TextDecoration.ITALIC))
                        .append(" type=")
                        .append(line instanceof TranslatableComponent ? "translatable" : "text");
            }
            lore = builder.toString();
        }
        String nameType = itemName == null ? "none" : itemName instanceof TranslatableComponent ? "translatable" : "text";
        return stack.getType().name().toLowerCase()
                + " itemName " + plain(itemName)
                + " nameType " + nameType
                + " custom " + plain(custom)
                + " lore " + lore
                + " pdc " + pdc(stack.getPersistentDataContainer())
                + " enchants " + stack.getEnchantments();
    }

    private static String lockText(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Lockable lockable) || !(state instanceof TileState tile)) {
            return "unlocked-container false";
        }
        return "locked " + lockable.isLocked()
                + " lock " + lockable.getLock()
                + " pdc " + pdc(tile.getPersistentDataContainer());
    }

    private static String lootKey(Block block) {
        if (block.getState() instanceof Lootable lootable && lootable.getLootTable() != null) {
            return lootable.getLootTable().getKey().toString();
        }
        return "none";
    }

    private static UUID lockId(Block chest) {
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

    private static String pdc(PersistentDataContainerView container) {
        if (container.getKeys().isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (NamespacedKey key : container.getKeys()) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            String value;
            if (container.has(key, PersistentDataType.STRING)) {
                value = container.get(key, PersistentDataType.STRING);
            } else if (container.has(key, PersistentDataType.BYTE)) {
                value = Byte.toString(container.get(key, PersistentDataType.BYTE));
            } else {
                value = "?";
            }
            builder.append(key).append('=').append(value);
        }
        return builder.toString();
    }

    private static String effects(LivingEntity living) {
        if (living.getActivePotionEffects().isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (PotionEffect effect : living.getActivePotionEffects()) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(effect.getType().getKey())
                    .append(" dur ").append(effect.getDuration())
                    .append(" amp ").append(effect.getAmplifier())
                    .append(" particles ").append(effect.hasParticles())
                    .append(" ambient ").append(effect.isAmbient());
        }
        return builder.toString();
    }

    private static String vector(Vector velocity) {
        return String.format(java.util.Locale.US, "%.3f %.3f %.3f", velocity.getX(), velocity.getY(), velocity.getZ());
    }

    private static String pos(Block block) {
        return block.getX() + " " + block.getY() + " " + block.getZ();
    }

    private static String pos(Entity entity) {
        Location location = entity.getLocation();
        return location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private static String plain(Component component) {
        return component == null ? "none" : PLAIN.serialize(component);
    }

    private static BlockFace facing(Player player) {
        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        if (yaw < 45 || yaw >= 315) {
            return BlockFace.SOUTH;
        }
        if (yaw < 135) {
            return BlockFace.WEST;
        }
        if (yaw < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    private static void say(Player player, String text) {
        player.sendMessage(Component.text("[violetarchive] " + text));
    }
}
