package violetarchive.world;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

/**
 * Adds the forest populator to End worlds. One shared instance is used.
 */
public final class ForestListener implements Listener {
    private final AmethystForestPopulator populator;

    public ForestListener(AmethystForestPopulator populator) {
        this.populator = populator;
    }

    @EventHandler
    public void onInit(WorldInitEvent event) {
        attach(event.getWorld(), populator);
    }

    public static void attach(World world, AmethystForestPopulator populator) {
        if (world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        if (!world.getPopulators().contains(populator)) {
            world.getPopulators().add(populator);
        }
    }
}
