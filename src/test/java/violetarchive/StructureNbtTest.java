package violetarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StructureNbtTest {
    @Test
    void libraryMatchesTheRoomList() throws Exception {
        Map<String, Object> root = NbtStructures.read(bytes("/pack/data/violetarchive/structure/library.nbt"));
        assertEquals(5023, ((Number) root.get("DataVersion")).intValue());
        assertEquals(List.of(34, 13, 15), ints(root.get("size")));
        List<Object> palette = NbtStructures.list(root.get("palette"));
        Set<String> ids = new HashSet<>();
        for (Object entry : palette) {
            ids.add((String) NbtStructures.compound(entry).get("id"));
        }
        assertFalse(ids.stream().anyMatch(id -> id.contains("netherrack") || id.contains("netherite")));
        for (String required : List.of(
                "minecraft:end_stone_bricks",
                "minecraft:purpur_stairs",
                "minecraft:cobweb",
                "minecraft:cherry_log",
                "minecraft:stripped_cherry_log",
                "minecraft:cherry_leaves",
                "minecraft:bookshelf",
                "minecraft:chiseled_bookshelf",
                "minecraft:anvil",
                "minecraft:purple_stained_glass",
                "minecraft:chest")) {
            assertTrue(ids.contains(required), required);
        }
        Map<List<Integer>, Block> blocks = index(root);
        Set<String> tables = new HashSet<>();
        for (var entry : blocks.entrySet()) {
            if (!"minecraft:chest".equals(entry.getValue().id())) {
                continue;
            }
            String loot = (String) entry.getValue().nbt().get("LootTable");
            tables.add(loot);
            List<Integer> above = List.of(entry.getKey().get(0), entry.getKey().get(1) + 1, entry.getKey().get(2));
            assertEquals("minecraft:air", blocks.get(above).id(), loot);
        }
        assertEquals(Set.of(
                "violetarchive:archive/swords",
                "violetarchive:archive/bows",
                "violetarchive:archive/armor",
                "violetarchive:archive/tools",
                "violetarchive:archive/depths",
                "violetarchive:archive/mending",
                "violetarchive:archive/curses",
                "violetarchive:archive/supplies",
                "violetarchive:archive/vault"), tables);
        assertEquals("minecraft:cracked_stone_bricks", blocks.get(List.of(26, 2, 7)).id());
        assertEquals("minecraft:air", blocks.get(List.of(3, 2, 11)).id());
        for (int x = 22; x <= 26; x++) {
            assertEquals("minecraft:air", blocks.get(List.of(x, 3, 7)).id());
            assertEquals("minecraft:air", blocks.get(List.of(x, 4, 7)).id());
        }
        long lecterns = blocks.values().stream().filter(block -> "minecraft:lectern".equals(block.id())).count();
        assertEquals(3, lecterns);
        List<Object> entities = NbtStructures.list(root.get("entities"));
        assertEquals(1, entities.size());
        Map<String, Object> entity = NbtStructures.compound(entities.get(0));
        assertEquals(List.of(26, 3, 7), ints(entity.get("blockPos")));
        assertEquals("minecraft:shulker", NbtStructures.compound(entity.get("nbt")).get("id"));
        assertTrue(blocks.keySet().stream().noneMatch(pos ->
                pos.get(0) < 8 && "minecraft:purpur_stairs".equals(blocks.get(pos).id())));
    }

    @Test
    void towerIsTheNarrowIntactBuilding() throws Exception {
        Map<String, Object> root = NbtStructures.read(bytes("/pack/data/violetarchive/structure/tower.nbt"));
        assertEquals(List.of(9, 18, 9), ints(root.get("size")));
        Map<List<Integer>, Block> blocks = index(root);
        assertEquals("violetarchive:archive/tower", blocks.get(List.of(5, 14, 3)).nbt().get("LootTable"));
        assertEquals("minecraft:air", blocks.get(List.of(5, 15, 3)).id());
        assertEquals("minecraft:end_rod", blocks.get(List.of(4, 17, 4)).id());
        assertEquals("minecraft:end_stone_bricks", blocks.get(List.of(4, 0, 4)).id());
        long lanterns = blocks.values().stream().filter(block -> "minecraft:lantern".equals(block.id())).count();
        assertEquals(4, lanterns);
        List<Object> entities = NbtStructures.list(root.get("entities"));
        assertEquals(1, entities.size());
        assertEquals(List.of(5, 15, 5), ints(NbtStructures.compound(entities.get(0)).get("blockPos")));
        assertFalse(blocks.values().stream().anyMatch(block -> block.id().contains("netherrack")));
    }

    private static Map<List<Integer>, Block> index(Map<String, Object> root) {
        List<Object> palette = NbtStructures.list(root.get("palette"));
        Map<List<Integer>, Block> blocks = new HashMap<>();
        for (Object body : NbtStructures.list(root.get("blocks"))) {
            Map<String, Object> compound = NbtStructures.compound(body);
            List<Integer> pos = ints(compound.get("pos"));
            int state = ((Number) compound.get("state")).intValue();
            String id = (String) NbtStructures.compound(palette.get(state)).get("id");
            Object nbt = compound.get("nbt");
            blocks.put(pos, new Block(id, nbt == null ? Map.of() : NbtStructures.compound(nbt)));
        }
        return blocks;
    }

    private static List<Integer> ints(Object value) {
        List<Integer> numbers = new java.util.ArrayList<>();
        for (Object item : NbtStructures.list(value)) {
            numbers.add(((Number) item).intValue());
        }
        return numbers;
    }

    private static byte[] bytes(String resource) throws Exception {
        try (InputStream input = StructureNbtTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing " + resource);
            }
            return input.readAllBytes();
        }
    }

    private record Block(String id, Map<String, Object> nbt) {
    }
}
