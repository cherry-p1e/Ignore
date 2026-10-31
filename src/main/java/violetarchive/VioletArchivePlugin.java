package violetarchive;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import violetarchive.debug.DebugCommands;
import violetarchive.enchant.AnimaService;
import violetarchive.enchant.FloatingBook;
import violetarchive.enchant.RationService;
import violetarchive.enchant.VindictaListener;
import violetarchive.item.ArchiveLock;
import violetarchive.item.MarkedItems;
import violetarchive.item.Translations;
import violetarchive.structure.ArchiveLoot;
import violetarchive.structure.StructureListener;
import violetarchive.structure.WatcherMind;
import violetarchive.world.AmethystForestPopulator;
import violetarchive.world.ForestListener;

public final class VioletArchivePlugin extends JavaPlugin {
    private static VioletArchivePlugin instance;
    private Translations translations;
    private WatcherMind watcherMind;
    private RationService ration;
    private AnimaService anima;
    private VindictaListener vindicta;
    private StructureListener structures;
    private double followDistance = 4.5;

    public VioletArchivePlugin() {
        instance = this;
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                DebugCommands.register(event.registrar()));
    }

    public static VioletArchivePlugin get() {
        return instance;
    }

    public Translations translations() {
        return translations;
    }

    public WatcherMind watcherMind() {
        return watcherMind;
    }

    public RationService ration() {
        return ration;
    }

    public AnimaService anima() {
        return anima;
    }

    public VindictaListener vindicta() {
        return vindicta;
    }

    public StructureListener structures() {
        return structures;
    }

    public double followDistance() {
        return followDistance;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        translations = new Translations();
        translations.register();
        followDistance = readFollowDistance();
        AmethystForestPopulator populator = new AmethystForestPopulator(this);
        watcherMind = new WatcherMind(this);
        ration = new RationService(this);
        anima = new AnimaService(followDistance);
        vindicta = new VindictaListener(this);
        structures = new StructureListener(this, watcherMind);
        var events = getServer().getPluginManager();
        events.registerEvents(new MarkedItems(this, translations), this);
        events.registerEvents(new ArchiveLock(), this);
        events.registerEvents(new ArchiveLoot(this), this);
        events.registerEvents(structures, this);
        events.registerEvents(watcherMind, this);
        events.registerEvents(vindicta, this);
        events.registerEvents(new FloatingBook(this), this);
        events.registerEvents(ration, this);
        events.registerEvents(anima, this);
        events.registerEvents(new ForestListener(populator), this);
        for (World world : Bukkit.getWorlds()) {
            ForestListener.attach(world, populator);
        }
        anima.catchUp();
        Bukkit.getScheduler().runTaskTimer(this, ration::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, anima::tick, 10L, 10L);
        watcherMind.start();
    }

    @Override
    public void onDisable() {
        if (watcherMind != null) {
            watcherMind.shutdown();
        }
        getServer().getScheduler().cancelTasks(this);
        if (translations != null) {
            translations.unregister();
        }
    }

    private double readFollowDistance() {
        FileConfiguration config = getConfig();
        String path = "anima.follow-distance";
        if (!config.contains(path)) {
            getLogger().warning("anima.follow-distance is missing; using 4.5");
            return 4.5;
        }
        Object raw = config.get(path);
        if (!(raw instanceof Number number)) {
            getLogger().warning("anima.follow-distance is not a number; using 4.5");
            return 4.5;
        }
        double value = number.doubleValue();
        if (value < 0.5 || value > 64) {
            getLogger().warning("anima.follow-distance is outside 0.5..64; using 4.5");
            return 4.5;
        }
        return value;
    }
}
