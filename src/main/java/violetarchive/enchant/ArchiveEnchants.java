package violetarchive.enchant;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ArchiveEnchants {
    public static final NamespacedKey VINDICTA = new NamespacedKey("violetarchive", "vindicta");
    public static final NamespacedKey RATION = new NamespacedKey("violetarchive", "ration");
    public static final NamespacedKey ANIMA = new NamespacedKey("violetarchive", "anima");

    private ArchiveEnchants() {
    }

    public static Enchantment vindicta() {
        return Registry.ENCHANTMENT.get(VINDICTA);
    }

    public static Enchantment ration() {
        return Registry.ENCHANTMENT.get(RATION);
    }

    public static Enchantment anima() {
        return Registry.ENCHANTMENT.get(ANIMA);
    }

    public static boolean has(ItemStack stack, NamespacedKey key) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(key);
        if (enchantment == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasEnchant(enchantment);
    }
}
