package violetarchive.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import violetarchive.enchant.ArchiveEnchants;
import violetarchive.item.ArchiveItems;

/**
 * Adds the vault blade and the separate Vindicta book roll.
 * The book is not part of the datapack loot tables.
 */
public final class ArchiveLoot implements Listener {
    private final JavaPlugin plugin;

    public ArchiveLoot(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (event.getLootTable() == null) {
            return;
        }
        String key = event.getLootTable().getKey().toString();
        boolean vault = "violetarchive:archive/vault".equals(key);
        boolean tower = "violetarchive:archive/tower".equals(key);
        if (!vault && !tower) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        if (vault && loot.stream().noneMatch(ArchiveItems::isBlade)) {
            loot.add(ArchiveItems.blade());
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.08) {
            Enchantment vindicta = ArchiveEnchants.vindicta();
            if (vindicta == null) {
                plugin.getLogger().warning("violetarchive:vindicta is not registered; the book was not added");
            } else {
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(vindicta, 1, true));
                loot.add(book);
            }
        }
        event.setLoot(loot);
    }
}
