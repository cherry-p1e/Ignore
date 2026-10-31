package violetarchive.item;

import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Lockable;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import violetarchive.Keys;

/**
 * Opens an archive chest when either hand holds the matching key.
 * A rendered name stays on the key; the lock item itself has none.
 * Any other item keeps the vanilla locked-chest message.
 */
public final class ArchiveLock implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Container container) || !(container instanceof Lockable lockable)) {
            return;
        }
        if (!lockable.isLocked() || !(container instanceof TileState tile)) {
            return;
        }
        String raw = tile.getPersistentDataContainer().get(Keys.LOCK_ID, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        UUID lockId;
        try {
            lockId = UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (!ArchiveItems.isKeyFor(main, lockId) && !ArchiveItems.isKeyFor(off, lockId)) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        player.openInventory(container.getInventory());
    }
}
