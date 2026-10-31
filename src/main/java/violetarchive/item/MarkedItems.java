package violetarchive.item;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Renders marked item text for the player who holds it, and strips that text
 * when the item leaves their inventory.
 */
public final class MarkedItems implements Listener {
    private final JavaPlugin plugin;
    private final Translations translations;
    private final Set<UUID> queued = new HashSet<>();

    public MarkedItems(JavaPlugin plugin, Translations translations) {
        this.plugin = plugin;
        this.translations = translations;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onLocale(PlayerLocaleChangeEvent event) {
        queue(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        queue(player);
        clearForeignLater(player, event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        queue(player);
        clearForeignLater(player, event.getView().getTopInventory());
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        clearEntity(event.getItemDrop());
        queue(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        clearEntity(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        ItemStack stack = event.getItem();
        if (!ArchiveItems.isMarked(stack)) {
            return;
        }
        ItemStack copy = stack.clone();
        ArchiveItems.clearDisplay(copy);
        event.setItem(copy);
    }

    private void queue(Player player) {
        if (!queued.add(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            queued.remove(player.getUniqueId());
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }

    private void refresh(Player player) {
        Locale locale = player.locale();
        renderInventory(player.getInventory(), locale);
        ItemStack cursor = player.getItemOnCursor();
        if (ArchiveItems.isMarked(cursor)) {
            ArchiveItems.render(cursor, locale, translations);
            player.setItemOnCursor(cursor);
        }
        if (player.getOpenInventory().getType() == InventoryType.CRAFTING) {
            renderInventory(player.getOpenInventory().getTopInventory(), locale);
        }
    }

    private void renderInventory(Inventory inventory, Locale locale) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ArchiveItems.isMarked(stack)) {
                continue;
            }
            ArchiveItems.render(stack, locale, translations);
            inventory.setItem(slot, stack);
        }
    }

    private void clearForeignLater(Player player, Inventory inventory) {
        if (inventory == null || isOwn(inventory, player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> clearMarked(inventory));
    }

    private void clearMarked(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!ArchiveItems.isMarked(stack)) {
                continue;
            }
            ArchiveItems.clearDisplay(stack);
            inventory.setItem(slot, stack);
        }
    }

    private static void clearEntity(Item item) {
        ItemStack stack = item.getItemStack();
        if (!ArchiveItems.isMarked(stack)) {
            return;
        }
        ArchiveItems.clearDisplay(stack);
        item.setItemStack(stack);
    }

    private static boolean isOwn(Inventory inventory, Player player) {
        if (inventory.equals(player.getInventory())) {
            return true;
        }
        InventoryType type = inventory.getType();
        return type == InventoryType.PLAYER || type == InventoryType.CRAFTING;
    }
}
