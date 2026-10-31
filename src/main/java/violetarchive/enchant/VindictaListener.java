package violetarchive.enchant;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import violetarchive.Keys;

/**
 * A marked bullet from Vindicta. Unmarked shulker bullets, including the
 * sentry's, stay vanilla.
 */
public final class VindictaListener implements Listener {
    private final JavaPlugin plugin;

    public VindictaListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack chest = player.getInventory().getChestplate();
        if (!ArchiveEnchants.has(chest, ArchiveEnchants.VINDICTA)) {
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent by) || !(by.getDamager() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker == player || Math.random() >= 0.20) {
            return;
        }
        launch(player, attacker);
    }

    /**
     * Fires the same marked bullet the damage listener fires after a successful roll.
     */
    public ShulkerBullet launch(Player player, LivingEntity attacker) {
        ItemStack chest = player.getInventory().getChestplate();
        if (!ArchiveEnchants.has(chest, ArchiveEnchants.VINDICTA)) {
            return null;
        }
        if (attacker == null || attacker == player || !attacker.isValid()) {
            return null;
        }
        Vector direction = attacker.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector());
        if (direction.lengthSquared() < 1.0E-6) {
            direction = new Vector(0, 0, 1);
        } else {
            direction.normalize();
        }
        return player.launchProjectile(ShulkerBullet.class, direction, bullet -> {
            bullet.setTarget(attacker);
            bullet.getPersistentDataContainer().set(Keys.VINDICTA_BULLET, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBullet(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof ShulkerBullet bullet)) {
            return;
        }
        if (!bullet.getPersistentDataContainer().has(Keys.VINDICTA_BULLET, PersistentDataType.BYTE)) {
            return;
        }
        event.setDamage(6);
        UUID targetId = event.getEntity().getUniqueId();
        UUID wearerId = bullet.getShooter() instanceof Entity shooter ? shooter.getUniqueId() : null;
        Bukkit.getScheduler().runTask(plugin, () -> apply(targetId, wearerId));
    }

    private static void apply(UUID targetId, UUID wearerId) {
        if (!(Bukkit.getEntity(targetId) instanceof LivingEntity living) || !living.isValid()) {
            return;
        }
        living.removePotionEffect(PotionEffectType.LEVITATION);
        living.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 0, false, false, true));
        Entity wearer = wearerId == null ? null : Bukkit.getEntity(wearerId);
        if (wearer == null) {
            return;
        }
        Vector away = living.getLocation().toVector().subtract(wearer.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0E-6) {
            return;
        }
        away.normalize().multiply(0.12);
        Vector velocity = living.getVelocity();
        velocity.setX(velocity.getX() + away.getX());
        velocity.setZ(velocity.getZ() + away.getZ());
        living.setVelocity(velocity);
    }
}
