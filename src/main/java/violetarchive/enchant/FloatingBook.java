package violetarchive.enchant;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * A dropped Vindicta book drifts upward for a short time. Nothing else does.
 */
public final class FloatingBook implements Listener {
    private final JavaPlugin plugin;

    public FloatingBook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        if (stack.getType() != Material.ENCHANTED_BOOK) {
            return;
        }
        Enchantment vindicta = ArchiveEnchants.vindicta();
        if (vindicta == null || !(stack.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return;
        }
        if (!meta.hasStoredEnchant(vindicta)) {
            return;
        }
        new BukkitRunnable() {
            private int left = 60;

            @Override
            public void run() {
                if (left-- <= 0 || !item.isValid() || item.isDead()) {
                    cancel();
                    return;
                }
                Vector velocity = item.getVelocity();
                velocity.setY(0.05);
                item.setVelocity(velocity);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
