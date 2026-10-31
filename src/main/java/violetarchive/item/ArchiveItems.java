package violetarchive.item;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import violetarchive.Keys;

/**
 * Marked items. The Watcher's Blade is only a marked diamond sword:
 * it has no dash, blink, bonus damage, or other attack.
 */
public final class ArchiveItems {
    private ArchiveItems() {
    }

    public static ItemStack blade() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        stack.editPersistentDataContainer(container ->
                container.set(Keys.WATCHERS_BLADE, PersistentDataType.BYTE, (byte) 1));
        return stack;
    }

    /**
     * A key that stores only its lock id. {@link #render} adds the visible name
     * later. The chest lock item never receives that name.
     */
    public static ItemStack key(UUID lockId) {
        ItemStack stack = new ItemStack(Material.AMETHYST_SHARD);
        stack.editPersistentDataContainer(container -> {
            container.set(Keys.ARCHIVE_KEY, PersistentDataType.BYTE, (byte) 1);
            container.set(Keys.LOCK_ID, PersistentDataType.STRING, lockId.toString());
        });
        return stack;
    }

    /**
     * Lock template. The only extra data is the lock id: no name and no lore.
     */
    public static ItemStack lockTemplate(UUID lockId) {
        ItemStack stack = new ItemStack(Material.AMETHYST_SHARD);
        stack.editPersistentDataContainer(container ->
                container.set(Keys.LOCK_ID, PersistentDataType.STRING, lockId.toString()));
        return stack;
    }

    public static boolean isBlade(ItemStack stack) {
        return marked(stack, Keys.WATCHERS_BLADE);
    }

    public static boolean isKey(ItemStack stack) {
        return marked(stack, Keys.ARCHIVE_KEY);
    }

    public static boolean isMarked(ItemStack stack) {
        return isBlade(stack) || isKey(stack);
    }

    public static boolean isKeyFor(ItemStack stack, UUID lockId) {
        return lockId != null && isKey(stack) && lockId.equals(lockId(stack));
    }

    public static UUID lockId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        String raw = stack.getPersistentDataContainer().get(Keys.LOCK_ID, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static void render(ItemStack stack, Locale locale, Translations translations) {
        if (!isMarked(stack)) {
            return;
        }
        Locale resolved = locale == null ? Locale.US : locale;
        String code = resolved.toString();
        String stored = stack.getPersistentDataContainer().get(Keys.NAME_LOCALE, PersistentDataType.STRING);
        if (code.equals(stored)) {
            return;
        }
        String nameKey = isBlade(stack)
                ? "item.violetarchive.watchers_blade"
                : "item.violetarchive.archive_key";
        Component name = translations.render(nameKey, resolved);
        Component lore = translations.render(nameKey + ".lore", resolved);
        stack.editMeta(meta -> {
            meta.itemName(name);
            meta.lore(List.of(lore));
            meta.getPersistentDataContainer().set(Keys.NAME_LOCALE, PersistentDataType.STRING, code);
        });
    }

    public static void clearDisplay(ItemStack stack) {
        if (!isMarked(stack)) {
            return;
        }
        stack.unsetData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_NAME);
        stack.unsetData(io.papermc.paper.datacomponent.DataComponentTypes.LORE);
        stack.editPersistentDataContainer(container -> container.remove(Keys.NAME_LOCALE));
    }

    private static boolean marked(ItemStack stack, org.bukkit.NamespacedKey key) {
        return stack != null
                && !stack.getType().isAir()
                && stack.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
