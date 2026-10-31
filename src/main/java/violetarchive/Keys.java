package violetarchive;

import org.bukkit.NamespacedKey;

/**
 * Every persistent data key used by the plugin.
 */
public final class Keys {
    public static final NamespacedKey WATCHERS_BLADE = key("watchers_blade");
    public static final NamespacedKey ARCHIVE_KEY = key("archive_key");
    public static final NamespacedKey LOCK_ID = key("lock_id");
    public static final NamespacedKey WATCHER = key("watcher");
    public static final NamespacedKey SENTRY = key("sentry");
    public static final NamespacedKey VINDICTA_BULLET = key("vindicta_bullet");
    public static final NamespacedKey ANIMA_OWNER = key("anima_owner");
    public static final NamespacedKey NAME_LOCALE = key("name_locale");

    private Keys() {
    }

    private static NamespacedKey key(String path) {
        return new NamespacedKey("violetarchive", path);
    }
}
