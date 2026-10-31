package violetarchive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DatapackTest {
    @Test
    void packAndPlacementStayInsideTheArchive() throws Exception {
        String meta = text("/pack/pack.mcmeta");
        assertTrue(meta.contains("121.0"));
        assertTrue(meta.contains("\"min_format\": 121"));
        String set = text("/pack/data/violetarchive/worldgen/structure_set/archive.json");
        assertTrue(set.contains("\"spacing\": 32"));
        assertTrue(set.contains("\"separation\": 16"));
        assertTrue(set.contains("triangular"));
        assertTrue(set.contains("minecraft:end_cities"));
        assertTrue(set.contains("\"chunk_count\": 8"));
        assertTrue(set.contains("violetarchive:library"));
        assertTrue(set.contains("violetarchive:tower"));
        String library = text("/pack/data/violetarchive/worldgen/structure/library.json");
        assertTrue(library.contains("WORLD_SURFACE_WG"));
        assertTrue(library.contains("minecraft:end_highlands"));
        assertFalse(library.contains("minecraft:the_end"));
        assertNull(DatapackTest.class.getResource("/pack/data/minecraft/dimension/the_end.json"));
    }

    @Test
    void biomeKeepsTheEndPictureWithoutChorus() throws Exception {
        String biome = text("/pack/data/violetarchive/worldgen/biome/amethyst_forest.json");
        assertTrue(biome.contains("\"features\": []"));
        assertTrue(biome.contains("minecraft:enderman"));
        assertTrue(biome.contains("#4A3270"));
        assertTrue(biome.contains("\"has_precipitation\": false"));
        assertTrue(biome.contains("end_rod"));
        assertTrue(biome.contains("0.60"));
        assertFalse(biome.contains("sky_light_level"));
    }

    @Test
    void openChestsStayVanillaThemed() throws Exception {
        String[] tables = {
                "swords", "bows", "armor", "tools", "depths", "mending", "curses", "supplies", "vault", "tower"
        };
        for (String table : tables) {
            String json = text("/pack/data/violetarchive/loot_table/archive/" + table + ".json");
            assertFalse(json.contains("netherite"), table);
            assertFalse(json.contains("vindicta"), table);
            assertFalse(json.contains("watchers"), table);
            if (!"supplies".equals(table) && !"vault".equals(table) && !"tower".equals(table) && !"mending".equals(table)) {
                assertTrue(json.contains("set_enchantments") || json.contains("binding_curse") || json.contains("vanishing_curse"), table);
            }
        }
        String mending = text("/pack/data/violetarchive/loot_table/archive/mending.json");
        assertTrue(mending.contains("minecraft:mending"));
        assertTrue(mending.contains("0.15"));
        String supplies = text("/pack/data/violetarchive/loot_table/archive/supplies.json");
        assertFalse(supplies.contains("set_enchantments"));
        String vault = text("/pack/data/violetarchive/loot_table/archive/vault.json");
        assertTrue(vault.contains("amethyst_shard"));
        String swords = text("/pack/data/violetarchive/loot_table/archive/swords.json");
        assertTrue(swords.contains("set_enchantments"));
        assertFalse(swords.contains("\"functions\""));
    }

    private static String text(String resource) throws Exception {
        try (InputStream input = DatapackTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
