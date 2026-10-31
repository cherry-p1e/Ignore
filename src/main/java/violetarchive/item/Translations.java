package violetarchive.item;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;

/**
 * Server-side item strings. The client never receives a raw translation key.
 */
public final class Translations {
    private static final Pattern ENTRY = Pattern.compile(
            "\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final TranslationStore<Component> store;

    public Translations() {
        store = TranslationStore.component(Key.key("violetarchive", "lang"));
        store.defaultLocale(Locale.US);
        load("/lang/en_us.json", Locale.US);
        load("/lang/en_us.json", Locale.ENGLISH);
        load("/lang/ru_ru.json", Locale.forLanguageTag("ru-RU"));
        load("/lang/ru_ru.json", Locale.forLanguageTag("ru"));
    }

    public void register() {
        GlobalTranslator.translator().addSource(store);
    }

    public void unregister() {
        GlobalTranslator.translator().removeSource(store);
    }

    public Component render(String key, Locale locale) {
        Locale resolved = locale == null ? Locale.US : locale;
        return GlobalTranslator.render(Component.translatable(key), resolved)
                .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private void load(String resource, Locale locale) {
        try (InputStream input = Translations.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing translation file " + resource);
            }
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = ENTRY.matcher(text);
            while (matcher.find()) {
                store.register(unescape(matcher.group(1)), locale, Component.text(unescape(matcher.group(2))));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + resource, exception);
        }
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
    }
}
