package violetarchive.bootstrap;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemType;

/**
 * Registers the datapack and the three enchantments before worlds load.
 * A failure here is left uncaught so the server stops instead of stripping
 * enchantments off items players already own.
 */
public final class VioletArchiveBootstrap implements PluginBootstrap {
    public static final TypedKey<Enchantment> VINDICTA = EnchantmentKeys.create(Key.key("violetarchive", "vindicta"));
    public static final TypedKey<Enchantment> RATION = EnchantmentKeys.create(Key.key("violetarchive", "ration"));
    public static final TypedKey<Enchantment> ANIMA = EnchantmentKeys.create(Key.key("violetarchive", "anima"));

    @Override
    public void bootstrap(BootstrapContext context) {
        var manager = context.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                event.registrar().discoverPack(packUri(), "violetarchive");
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        manager.registerEventHandler(RegistryEvents.ENCHANTMENT.compose(), this::registerEnchantments);
        manager.registerEventHandler(RegistryEvents.ENCHANTMENT.entryAdd().newHandler(event -> {
            EnchantmentRegistryEntry.Builder builder = event.builder();
            builder.exclusiveWith(including(builder.exclusiveWith(), VINDICTA));
        }).filter(EnchantmentKeys.THORNS));
    }

    private void registerEnchantments(RegistryComposeEvent<Enchantment, EnchantmentRegistryEntry.Builder> event) {
        // Item tags are not bound yet while enchantments are composed, so the
        // chest-armor and shulker-box sets are the vanilla tag members themselves.
        RegistryKeySet<ItemType> chestArmor = items(
                ItemTypeKeys.LEATHER_CHESTPLATE,
                ItemTypeKeys.COPPER_CHESTPLATE,
                ItemTypeKeys.CHAINMAIL_CHESTPLATE,
                ItemTypeKeys.GOLDEN_CHESTPLATE,
                ItemTypeKeys.IRON_CHESTPLATE,
                ItemTypeKeys.DIAMOND_CHESTPLATE,
                ItemTypeKeys.NETHERITE_CHESTPLATE,
                ItemTypeKeys.ELYTRA);
        RegistryKeySet<ItemType> shulkerBoxes = items(
                ItemTypeKeys.SHULKER_BOX,
                ItemTypeKeys.WHITE_SHULKER_BOX,
                ItemTypeKeys.ORANGE_SHULKER_BOX,
                ItemTypeKeys.MAGENTA_SHULKER_BOX,
                ItemTypeKeys.LIGHT_BLUE_SHULKER_BOX,
                ItemTypeKeys.YELLOW_SHULKER_BOX,
                ItemTypeKeys.LIME_SHULKER_BOX,
                ItemTypeKeys.PINK_SHULKER_BOX,
                ItemTypeKeys.GRAY_SHULKER_BOX,
                ItemTypeKeys.LIGHT_GRAY_SHULKER_BOX,
                ItemTypeKeys.CYAN_SHULKER_BOX,
                ItemTypeKeys.PURPLE_SHULKER_BOX,
                ItemTypeKeys.BLUE_SHULKER_BOX,
                ItemTypeKeys.BROWN_SHULKER_BOX,
                ItemTypeKeys.GREEN_SHULKER_BOX,
                ItemTypeKeys.RED_SHULKER_BOX,
                ItemTypeKeys.BLACK_SHULKER_BOX);
        EnchantmentRegistryEntry.EnchantmentCost minimum = EnchantmentRegistryEntry.EnchantmentCost.of(1, 1);
        EnchantmentRegistryEntry.EnchantmentCost maximum = EnchantmentRegistryEntry.EnchantmentCost.of(5, 1);
        event.registry().register(VINDICTA, builder -> builder
                .description(Component.text("Vindicta"))
                .supportedItems(chestArmor)
                .weight(1)
                .maxLevel(1)
                .minimumCost(minimum)
                .maximumCost(maximum)
                .anvilCost(1)
                .activeSlots(EquipmentSlotGroup.CHEST)
                .exclusiveWith(RegistrySet.keySet(RegistryKey.ENCHANTMENT, EnchantmentKeys.THORNS)));
        event.registry().register(RATION, builder -> builder
                .description(Component.text("Ration"))
                .supportedItems(shulkerBoxes)
                .weight(1)
                .maxLevel(1)
                .minimumCost(minimum)
                .maximumCost(maximum)
                .anvilCost(1)
                .activeSlots(EquipmentSlotGroup.ANY)
                .exclusiveWith(RegistrySet.keySet(RegistryKey.ENCHANTMENT, ANIMA)));
        event.registry().register(ANIMA, builder -> builder
                .description(Component.text("Anima"))
                .supportedItems(shulkerBoxes)
                .weight(1)
                .maxLevel(1)
                .minimumCost(minimum)
                .maximumCost(maximum)
                .anvilCost(1)
                .activeSlots(EquipmentSlotGroup.ANY)
                .exclusiveWith(RegistrySet.keySet(RegistryKey.ENCHANTMENT, RATION)));
    }

    @SafeVarargs
    private static RegistryKeySet<ItemType> items(TypedKey<ItemType>... keys) {
        return RegistrySet.keySet(RegistryKey.ITEM, keys);
    }

    private static RegistryKeySet<Enchantment> including(RegistryKeySet<Enchantment> current, TypedKey<Enchantment> extra) {
        List<TypedKey<Enchantment>> keys = new ArrayList<>();
        if (current != null) {
            keys.addAll(current.values());
        }
        if (!keys.contains(extra)) {
            keys.add(extra);
        }
        return RegistrySet.keySet(RegistryKey.ENCHANTMENT, keys);
    }

    private static URI packUri() {
        var resource = VioletArchiveBootstrap.class.getResource("/pack");
        if (resource == null) {
            throw new IllegalStateException("Violet Archive datapack /pack is missing from the jar");
        }
        try {
            return resource.toURI();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Violet Archive datapack URI is invalid", exception);
        }
    }
}
