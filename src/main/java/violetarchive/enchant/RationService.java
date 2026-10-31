package violetarchive.enchant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import io.papermc.paper.block.TileStateInventoryHolder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.datacomponent.item.UseRemainder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;

/**
 * Opens a carried Ration box only for the players who have one, and spends
 * at most one item per check.
 */
public final class RationService implements Listener {
    private final JavaPlugin plugin;
    private final Set<UUID> carriers = new HashSet<>();

    public RationService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void notice(Player player) {
        if (player != null && player.isOnline()) {
            refresh(player);
        }
    }

    public void tick() {
        for (UUID id : new ArrayList<>(carriers)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                carriers.remove(id);
                continue;
            }
            if (!hasRation(player)) {
                carriers.remove(id);
                continue;
            }
            if (player.getOpenInventory().getType() == InventoryType.SHULKER_BOX) {
                continue;
            }
            boolean lowHealth = player.getHealth() <= player.getMaxHealth() * 0.4;
            boolean harmful = harmful(player);
            boolean hungry = player.getFoodLevel() < 20;
            if (player.getFoodLevel() >= 20 && !lowHealth && !harmful) {
                continue;
            }
            if ((lowHealth || harmful) && drink(player, lowHealth, harmful)) {
                continue;
            }
            if (hungry) {
                eat(player);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        carriers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        queue(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        queue(event.getPlayer());
    }

    private void queue(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }

    private void refresh(Player player) {
        if (hasRation(player)) {
            carriers.add(player.getUniqueId());
        } else {
            carriers.remove(player.getUniqueId());
        }
    }

    private boolean drink(Player player, boolean lowHealth, boolean harmful) {
        boolean drank = edit(player, (inventory, slot, stack) -> {
            if (stack.getType() != Material.POTION) {
                return false;
            }
            PotionContents contents = stack.getData(DataComponentTypes.POTION_CONTENTS);
            if (contents == null || !qualifies(player, contents, lowHealth)) {
                return false;
            }
            ItemStack remainder = new ItemStack(Material.GLASS_BOTTLE);
            UseRemainder use = stack.getData(DataComponentTypes.USE_REMAINDER);
            if (use != null && use.transformInto() != null && !use.transformInto().getType().isAir()) {
                remainder = use.transformInto().clone();
            }
            inventory.setItem(slot, remainder);
            for (PotionEffect effect : contents.allEffects()) {
                player.addPotionEffect(effect);
            }
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
            return true;
        });
        if (drank || !harmful) {
            return drank;
        }
        return edit(player, (inventory, slot, stack) -> {
            if (stack.getType() != Material.MILK_BUCKET) {
                return false;
            }
            inventory.setItem(slot, new ItemStack(Material.BUCKET));
            player.clearActivePotionEffects();
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
            return true;
        });
    }

    private void eat(Player player) {
        int gap = 20 - player.getFoodLevel();
        if (gap <= 0) {
            return;
        }
        for (int boxSlot : rationSlots(player)) {
            Held held = read(player.getInventory().getItem(boxSlot));
            if (held == null) {
                continue;
            }
            List<Integer> foodSlots = new ArrayList<>();
            List<Integer> nutrition = new ArrayList<>();
            List<Float> saturation = new ArrayList<>();
            for (int slot = 0; slot < held.inventory.getSize(); slot++) {
                ItemStack stack = held.inventory.getItem(slot);
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                var food = stack.getData(DataComponentTypes.FOOD);
                if (food == null || food.nutrition() <= 0) {
                    continue;
                }
                foodSlots.add(slot);
                nutrition.add(food.nutrition());
                saturation.add(food.saturation());
            }
            int[] nutritionArray = nutrition.stream().mapToInt(Integer::intValue).toArray();
            float[] saturationArray = new float[saturation.size()];
            for (int index = 0; index < saturation.size(); index++) {
                saturationArray[index] = saturation.get(index);
            }
            int choice = RationChoice.choose(gap, nutritionArray, saturationArray);
            if (choice < 0) {
                continue;
            }
            int slot = foodSlots.get(choice);
            ItemStack foodStack = held.inventory.getItem(slot);
            var food = foodStack.getData(DataComponentTypes.FOOD);
            consumeOne(held.inventory, slot, foodStack);
            int foodLevel = Math.min(20, Math.max(0, player.getFoodLevel() + food.nutrition()));
            float filled = Math.min(foodLevel, Math.max(0, player.getSaturation() + food.saturation()));
            player.setFoodLevel(foodLevel);
            player.setSaturation(filled);
            write(player, boxSlot, held);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
            return;
        }
    }

    private boolean edit(Player player, ContentsVisitor visitor) {
        for (int boxSlot : rationSlots(player)) {
            Held held = read(player.getInventory().getItem(boxSlot));
            if (held == null) {
                continue;
            }
            for (int slot = 0; slot < held.inventory.getSize(); slot++) {
                ItemStack stack = held.inventory.getItem(slot);
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                if (visitor.visit(held.inventory, slot, stack)) {
                    write(player, boxSlot, held);
                    return true;
                }
            }
        }
        return false;
    }

    private static void consumeOne(Inventory inventory, int slot, ItemStack stack) {
        ItemStack remainder = null;
        UseRemainder use = stack.getData(DataComponentTypes.USE_REMAINDER);
        if (use != null && use.transformInto() != null && !use.transformInto().getType().isAir()) {
            remainder = use.transformInto().clone();
        }
        if (stack.getAmount() <= 1) {
            inventory.setItem(slot, remainder);
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        inventory.setItem(slot, stack);
        if (remainder != null) {
            inventory.addItem(remainder);
        }
    }

    private static boolean qualifies(Player player, PotionContents contents, boolean lowHealth) {
        List<PotionEffect> effects = contents.allEffects();
        boolean harmfulEffect = false;
        boolean beneficial = false;
        boolean already = false;
        boolean instantHeal = contents.potion() == PotionType.HEALING || contents.potion() == PotionType.STRONG_HEALING;
        for (PotionEffect effect : effects) {
            if (effect.getType().equals(PotionEffectType.INSTANT_HEALTH)) {
                instantHeal = true;
            }
            if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                harmfulEffect = true;
            }
            if (effect.getType().getCategory() == PotionEffectTypeCategory.BENEFICIAL) {
                beneficial = true;
            }
            if (!effect.getType().isInstant() && player.hasPotionEffect(effect.getType())) {
                already = true;
            }
        }
        if (harmfulEffect) {
            return false;
        }
        if (instantHeal) {
            return lowHealth;
        }
        return beneficial && !already;
    }

    private static boolean harmful(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRation(Player player) {
        return !rationSlots(player).isEmpty();
    }

    private static List<Integer> rationSlots(Player player) {
        List<Integer> slots = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isRation(stack)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static boolean isRation(ItemStack stack) {
        return stack != null
                && Tag.SHULKER_BOXES.isTagged(stack.getType())
                && ArchiveEnchants.has(stack, ArchiveEnchants.RATION);
    }

    private static Held read(ItemStack box) {
        if (!(box.getItemMeta() instanceof BlockStateMeta meta)) {
            return null;
        }
        if (!(meta.getBlockState() instanceof TileStateInventoryHolder holder)) {
            return null;
        }
        return new Held(box, meta, holder, holder.getSnapshotInventory());
    }

    private static void write(Player player, int slot, Held held) {
        held.meta.setBlockState((BlockState) held.holder);
        held.box.setItemMeta(held.meta);
        player.getInventory().setItem(slot, held.box);
    }

    private interface ContentsVisitor {
        boolean visit(Inventory inventory, int slot, ItemStack stack);
    }

    private record Held(ItemStack box, BlockStateMeta meta, TileStateInventoryHolder holder, Inventory inventory) {
    }
}
