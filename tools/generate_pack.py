#!/usr/bin/env python3
"""Build-time generator for Violet Archive loot tables and structure NBT.

The running plugin does not paste these buildings. Static templates in the jar
are placed by the datapack structure set.
"""

from __future__ import annotations

import gzip
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "src/main/resources/pack/data/violetarchive"
LOOT = PACK / "loot_table/archive"
STRUCTURE = PACK / "structure"
DATA_VERSION = 5023


class Byte(int):
    pass


class Short(int):
    pass


class Float(float):
    pass


class Double(float):
    pass


class Lst:
    def __init__(self, kind: str, values):
        self.kind = kind
        self.values = list(values)


def _write_string(out: bytearray, text: str) -> None:
    raw = text.encode("utf-8")
    if len(raw) > 65535:
        raise ValueError("string too long")
    out += struct.pack(">H", len(raw))
    out += raw


def _write_payload(out: bytearray, value) -> None:
    if isinstance(value, dict):
        for key in value:
            _write_named(out, key, value[key])
        out.append(0)
        return
    if isinstance(value, Byte):
        out.append(int(value) & 0xFF)
        return
    if isinstance(value, Short):
        out += struct.pack(">h", int(value))
        return
    if isinstance(value, Float):
        out += struct.pack(">f", float(value))
        return
    if isinstance(value, Double):
        out += struct.pack(">d", float(value))
        return
    if isinstance(value, bool):
        raise TypeError("use Byte for NBT booleans")
    if isinstance(value, int) and not isinstance(value, bool):
        out += struct.pack(">i", int(value))
        return
    if isinstance(value, str):
        _write_string(out, value)
        return
    if isinstance(value, Lst):
        # An empty list has no element type. Vanilla writes TAG_End.
        if not value.values:
            out.append(0)
            out += struct.pack(">i", 0)
            return
        kind = _TYPE_ID[value.kind]
        out.append(kind)
        out += struct.pack(">i", len(value.values))
        for item in value.values:
            _write_payload(out, _coerce(value.kind, item))
        return
    raise TypeError(f"unsupported nbt value {type(value)}")


_TYPE_ID = {
    "byte": 1,
    "short": 2,
    "int": 3,
    "long": 4,
    "float": 5,
    "double": 6,
    "string": 8,
    "list": 9,
    "compound": 10,
}


def _coerce(kind: str, item):
    if kind == "int":
        return int(item)
    if kind == "double":
        return Double(item)
    if kind == "float":
        return Float(item)
    if kind == "string":
        return str(item)
    if kind == "byte":
        return Byte(item)
    if kind == "compound":
        return item
    raise TypeError(kind)


def _tag_type(value) -> int:
    if isinstance(value, dict):
        return 10
    if isinstance(value, Byte):
        return 1
    if isinstance(value, Short):
        return 2
    if isinstance(value, Float):
        return 5
    if isinstance(value, Double):
        return 6
    if isinstance(value, str):
        return 8
    if isinstance(value, Lst):
        return 9
    if isinstance(value, int) and not isinstance(value, bool):
        return 3
    raise TypeError(type(value))


def _write_named(out: bytearray, name: str, value) -> None:
    out.append(_tag_type(value))
    _write_string(out, name)
    _write_payload(out, value)


def write_nbt(path: Path, root: dict) -> None:
    payload = bytearray()
    _write_named(payload, "", root)
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.GzipFile(filename="", mode="wb", fileobj=open(path, "wb"), mtime=0) as handle:
        handle.write(payload)


def read_nbt(path: Path):
    data = gzip.decompress(path.read_bytes())
    offset = 0

    def take(n):
        nonlocal offset
        chunk = data[offset:offset + n]
        if len(chunk) != n:
            raise EOFError(path)
        offset += n
        return chunk

    def read_string():
        length = struct.unpack(">H", take(2))[0]
        return take(length).decode("utf-8")

    def read_value(kind):
        if kind == 0:
            return None
        if kind == 1:
            return struct.unpack(">b", take(1))[0]
        if kind == 2:
            return struct.unpack(">h", take(2))[0]
        if kind == 3:
            return struct.unpack(">i", take(4))[0]
        if kind == 4:
            return struct.unpack(">q", take(8))[0]
        if kind == 5:
            return struct.unpack(">f", take(4))[0]
        if kind == 6:
            return struct.unpack(">d", take(8))[0]
        if kind == 7:
            length = struct.unpack(">i", take(4))[0]
            return take(length)
        if kind == 8:
            return read_string()
        if kind == 9:
            elem = take(1)[0]
            length = struct.unpack(">i", take(4))[0]
            return [read_value(elem) for _ in range(length)]
        if kind == 10:
            compound = {}
            while True:
                tag = take(1)[0]
                if tag == 0:
                    break
                name = read_string()
                compound[name] = read_value(tag)
            return compound
        if kind == 11:
            length = struct.unpack(">i", take(4))[0]
            return list(struct.unpack(">" + "i" * length, take(4 * length)))
        if kind == 12:
            length = struct.unpack(">i", take(4))[0]
            return list(struct.unpack(">" + "q" * length, take(8 * length)))
        raise ValueError(f"bad tag {kind}")

    root_type = take(1)[0]
    if root_type != 10:
        raise ValueError("root is not a compound")
    if read_string() != "":
        raise ValueError("root name is not empty")
    value = read_value(10)
    if offset != len(data):
        raise ValueError("trailing nbt bytes")
    return value


# --- loot tables -----------------------------------------------------------

def item(name, weight=1, modifiers=None, condition=None):
    entry = {"type": "minecraft:item", "name": name}
    if weight != 1:
        entry["weight"] = weight
    if modifiers:
        entry["modifier"] = modifiers[0] if len(modifiers) == 1 else modifiers
    if condition:
        entry["condition"] = condition
    return entry


def uniform(low, high):
    return {"type": "minecraft:uniform", "min": low, "max": high}


def set_count(low, high=None):
    count = low if high is None else uniform(low, high)
    return {"type": "minecraft:set_count", "count": count}


def set_damage(low=0.25, high=0.8):
    return {"type": "minecraft:set_damage", "damage": uniform(low, high)}


def enchant(name, level):
    return {"type": "minecraft:set_enchantments", "enchantments": {f"minecraft:{name}": level}}


def rolls(value):
    if isinstance(value, int):
        return value
    return uniform(value[0], value[1])


def pool(roll, entries, condition=None):
    body = {"entries": entries, "rolls": rolls(roll)}
    if condition:
        body["condition"] = condition
    return body


def table(pools):
    return {"type": "minecraft:chest", "pools": pools}


def themed(material, enchants, levels, damaged=False):
    entries = []
    for name in enchants:
        allowed = levels
        if name in {"silk_touch", "infinity", "aqua_affinity", "flame", "mending", "binding_curse", "vanishing_curse"}:
            allowed = (1,)
        if name == "punch":
            allowed = tuple(level for level in levels if level <= 2)
        if name == "frost_walker":
            allowed = tuple(level for level in levels if level <= 2)
        for level in allowed:
            modifiers = [enchant(name, level)]
            if damaged:
                modifiers.insert(0, set_damage())
            entries.append(item(material, modifiers=modifiers))
    return entries


def books(enchants, levels):
    return themed("minecraft:book", enchants, levels, damaged=False)


SWORD = ["sharpness", "smite", "bane_of_arthropods", "looting", "sweeping_edge"]
BOW = ["power", "punch", "flame", "infinity"]
ARMOR = ["protection", "blast_protection", "projectile_protection", "fire_protection", "thorns"]
TOOL = ["efficiency", "silk_touch", "fortune", "unbreaking"]
DEPTH = ["respiration", "aqua_affinity", "depth_strider", "frost_walker"]

ARMOR_PIECES = [
    "minecraft:iron_helmet",
    "minecraft:iron_chestplate",
    "minecraft:iron_leggings",
    "minecraft:iron_boots",
    "minecraft:diamond_helmet",
    "minecraft:diamond_chestplate",
    "minecraft:diamond_leggings",
    "minecraft:diamond_boots",
]
TOOLS = [
    "minecraft:iron_pickaxe",
    "minecraft:iron_axe",
    "minecraft:iron_shovel",
    "minecraft:iron_hoe",
]


def write_loot() -> None:
    LOOT.mkdir(parents=True, exist_ok=True)
    tables = {
        "swords": table([
            pool(1, themed("minecraft:diamond_sword", SWORD, (1, 2, 3), damaged=True)),
            pool((2, 5), books(SWORD, (1, 2, 3))),
        ]),
        "bows": table([
            pool(1, themed("minecraft:bow", BOW, (1, 2, 3), damaged=False)),
            pool((2, 5), books(BOW, (1, 2, 3))),
        ]),
        "armor": table([
            pool((2, 3), [entry for piece in ARMOR_PIECES for entry in themed(piece, ARMOR, (1, 2, 3), damaged=True)]),
            pool((1, 3), books(ARMOR, (1, 2, 3))),
        ]),
        "tools": table([
            pool(1, [entry for piece in TOOLS for entry in themed(piece, TOOL, (1, 2, 3), damaged=True)]),
            pool((2, 5), books(TOOL, (1, 2, 3))),
        ]),
        "depths": table([
            pool(1, themed("minecraft:iron_boots", DEPTH, (1, 2), damaged=True)
                 + themed("minecraft:diamond_boots", DEPTH, (1, 2), damaged=True)),
            pool((2, 5), books(DEPTH, (1, 2))),
        ]),
        "mending": table([
            pool((3, 6), [
                item("minecraft:lapis_lazuli", modifiers=[set_count(2, 8)]),
                item("minecraft:experience_bottle", modifiers=[set_count(1, 4)]),
                item("minecraft:book", modifiers=[set_count(1, 3)]),
            ]),
            pool(1, [item("minecraft:book", modifiers=[enchant("mending", 1)])],
                 condition={"type": "minecraft:random_chance", "chance": 0.15}),
        ]),
        "curses": table([
            pool(1, [item("minecraft:golden_helmet", modifiers=[enchant("binding_curse", 1)])]),
            pool(1, [item("minecraft:iron_chestplate", modifiers=[set_damage(), enchant("vanishing_curse", 1)])]),
            pool((1, 4), [
                item("minecraft:book"),
                item("minecraft:string", modifiers=[set_count(2, 6)]),
            ]),
        ]),
        "supplies": table([
            pool((3, 6), [
                item("minecraft:paper", modifiers=[set_count(2, 8)]),
                item("minecraft:book", modifiers=[set_count(1, 3)]),
                item("minecraft:leather", modifiers=[set_count(2, 6)]),
                item("minecraft:string", modifiers=[set_count(2, 6)]),
                item("minecraft:black_dye", modifiers=[set_count(1, 4)]),
                item("minecraft:name_tag"),
            ]),
        ]),
        "vault": table([
            pool(1, [item("minecraft:amethyst_shard", modifiers=[set_count(4, 8)])]),
            pool((2, 4), [item("minecraft:book", modifiers=[set_count(1, 3)])]),
        ]),
        "tower": table([
            pool(1, [item("minecraft:amethyst_shard", modifiers=[set_count(4, 8)])]),
            pool(1, [item("minecraft:chorus_fruit", modifiers=[set_count(2, 6)])]),
            pool(1, [item("minecraft:ender_pearl", modifiers=[set_count(1, 2)])]),
            pool(1, [item("minecraft:book", modifiers=[set_count(2, 6)])]),
            pool(1, [item("minecraft:crying_obsidian", modifiers=[set_count(1, 4)])]),
        ]),
    }
    for name, body in tables.items():
        text = json.dumps(body, indent=2) + "\n"
        blob = text.lower()
        if "netherite" in blob or "vindicta" in blob or "watchers" in blob:
            raise SystemExit(f"loot {name} contains a forbidden item")
        (LOOT / f"{name}.json").write_text(text, encoding="utf-8")


# --- structures ------------------------------------------------------------

def block(name, props=None, nbt=None):
    return name, props or {}, nbt


def stairs(facing):
    return block("minecraft:purpur_stairs", {
        "facing": facing, "half": "bottom", "shape": "straight", "waterlogged": "false",
    })


def slab():
    return block("minecraft:purpur_slab", {"type": "bottom", "waterlogged": "false"})


def pillar(axis="y"):
    return block("minecraft:purpur_pillar", {"axis": axis})


def chest(facing, loot):
    return block("minecraft:chest", {
        "facing": facing, "type": "single", "waterlogged": "false",
    }, {
        "LootTable": f"violetarchive:archive/{loot}",
        "id": "minecraft:chest",
        "components": {},
    })


def lantern(hanging):
    return block("minecraft:lantern", {
        "hanging": "true" if hanging else "false", "waterlogged": "false",
    })


def candle():
    return block("minecraft:candle", {"candles": "1", "lit": "false", "waterlogged": "false"})


def leaves():
    return block("minecraft:cherry_leaves", {
        "distance": "1", "persistent": "true", "waterlogged": "false",
    })


def log(kind, axis="y"):
    return block(f"minecraft:{kind}", {"axis": axis})


def pane():
    return block("minecraft:purple_stained_glass_pane", {
        "east": "false", "north": "false", "south": "false", "waterlogged": "false", "west": "false",
    })


def written_book(title, page):
    return {
        "id": "minecraft:written_book",
        "count": 1,
        "components": {
            "minecraft:written_book_content": {
                "title": title,
                "author": "Archive",
                "generation": 0,
                "resolved": Byte(1),
                "pages": Lst("string", [page]),
            }
        },
    }


def lectern(facing, title, page):
    return block("minecraft:lectern", {
        "facing": facing, "has_book": "true", "powered": "false",
    }, {
        "id": "minecraft:lectern",
        "Page": 0,
        "Book": written_book(title, page),
    })


def shelf(facing, occupied):
    props = {"facing": facing}
    items = []
    for slot in range(6):
        filled = slot in occupied
        props[f"slot_{slot}_occupied"] = "true" if filled else "false"
        if filled:
            items.append({
                "Slot": Byte(slot),
                "id": "minecraft:book",
                "count": 1,
            })
    nbt = {
        "id": "minecraft:chiseled_bookshelf",
        "Items": Lst("compound", items),
        "last_interacted_slot": 0 if items else -1,
    }
    return block("minecraft:chiseled_bookshelf", props, nbt)


def shulker_entity(x, y, z, yaw, health):
    return {
        "blockPos": Lst("int", [x, y, z]),
        "pos": Lst("double", [x + 0.5, float(y), z + 0.5]),
        "nbt": {
            "id": "minecraft:shulker",
            "PersistenceRequired": Byte(1),
            "Health": Float(health),
            "Color": Byte(10),
            "AttachFace": Byte(0),
            "Peek": Byte(0),
            "Air": Short(300),
            "OnGround": Byte(1),
            "FallDistance": Float(0),
            "Fire": Short(0),
            "Invulnerable": Byte(0),
            "PortalCooldown": 0,
            "AbsorptionAmount": Float(0),
            "DeathTime": Short(0),
            "HurtTime": Short(0),
            "FallFlying": Byte(0),
            "CanPickUpLoot": Byte(0),
            "LeftHanded": Byte(0),
            "Pos": Lst("double", [x + 0.5, float(y), z + 0.5]),
            "Motion": Lst("double", [0.0, 0.0, 0.0]),
            "Rotation": Lst("float", [yaw, 0.0]),
        },
    }


class Grid:
    def __init__(self, size_x, size_y, size_z):
        self.size = (size_x, size_y, size_z)
        self.blocks = {}
        self.entities = []

    def inside(self, x, y, z):
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def put(self, x, y, z, value):
        if not self.inside(x, y, z):
            raise ValueError(f"out of bounds {(x, y, z)} size {self.size}")
        self.blocks[(x, y, z)] = value

    def fill(self, x0, x1, y0, y1, z0, z1, value):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, value)

    def air(self, x, y, z):
        self.put(x, y, z, block("minecraft:air"))

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    def to_nbt(self):
        palette = []
        index = {}
        blocks = []
        for (x, y, z) in sorted(self.blocks):
            name, props, nbt = self.blocks[(x, y, z)]
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                entry = {"id": name}
                if props:
                    entry["properties"] = dict(sorted(props.items()))
                index[key] = len(palette)
                palette.append(entry)
            body = {
                "pos": Lst("int", [x, y, z]),
                "state": index[key],
            }
            if nbt is not None:
                body["nbt"] = nbt
            blocks.append(body)
        sx, sy, sz = self.size
        return {
            "DataVersion": DATA_VERSION,
            "size": Lst("int", [sx, sy, sz]),
            "palette": Lst("compound", palette),
            "blocks": Lst("compound", blocks),
            "entities": Lst("compound", self.entities),
        }


STONE = block("minecraft:stone_bricks")
CRACKED = block("minecraft:cracked_stone_bricks")
MOSSY = block("minecraft:mossy_stone_bricks")
END_BRICK = block("minecraft:end_stone_bricks")
CALCITE = block("minecraft:calcite")
PURPUR = block("minecraft:purpur_block")
GLASS = block("minecraft:purple_stained_glass")
BOOKSHELF = block("minecraft:bookshelf")
COBWEB = block("minecraft:cobweb")
CRYING = block("minecraft:crying_obsidian")
AMETHYST = block("minecraft:amethyst_block")


def floor_mix(x, z):
    if (x * 3 + z) % 7 == 0:
        return CALCITE
    if (x + z) % 5 == 0:
        return MOSSY
    if (x * 2 + z) % 4 == 0:
        return CRACKED
    return STONE


def build_library() -> Grid:
    grid = Grid(34, 13, 15)
    # Clear the readable interior and the yard's eye line. Missing cells stay
    # structure void so the foundation does not carve the island around it.
    grid.fill(1, 21, 3, 11, 1, 13, block("minecraft:air"))
    grid.fill(23, 33, 3, 6, 2, 12, block("minecraft:air"))

    for x in range(0, 23):
        for z in range(0, 15):
            grid.put(x, 0, z, END_BRICK)
            grid.put(x, 1, z, END_BRICK)
    for x in range(23, 34):
        for z in range(2, 13):
            grid.put(x, 0, z, END_BRICK)
            grid.put(x, 1, z, END_BRICK)

    for x in range(1, 22):
        for z in range(1, 14):
            grid.put(x, 2, z, floor_mix(x, z))
    for x in range(23, 34):
        for z in range(2, 13):
            grid.put(x, 2, z, CRACKED if (x + z) % 2 == 0 else CALCITE)
    grid.put(26, 2, 7, CRACKED)

    def wall_top(x, z):
        if 8 <= x <= 22:
            return 12 - abs(z - 7)
        return 4

    for x in range(0, 23):
        for z in (0, 14):
            for y in range(2, wall_top(x, z) + 1):
                grid.put(x, y, z, CRACKED if (x + y) % 6 == 0 else STONE)
    for z in range(0, 15):
        for x in (0, 22):
            for y in range(2, wall_top(x, z) + 1):
                grid.put(x, y, z, MOSSY if (z + y) % 8 == 0 else STONE)

    # Pitched roof over the intact two thirds. The west third stays open.
    for x in range(8, 23):
        for z in range(0, 15):
            y = 12 - abs(z - 7)
            if z < 7:
                grid.put(x, y, z, stairs("north"))
            elif z > 7:
                grid.put(x, y, z, stairs("south"))
            else:
                grid.put(x, y, z, PURPUR)
    for z in range(1, 14):
        y = 12 - abs(z - 7)
        grid.put(7, max(3, y - 1), z, COBWEB)

    # East wall hole, large enough to see the forecourt from the aisle.
    for z in range(4, 11):
        for y in range(3, 7):
            grid.air(22, y, z)
    # West ruin into the back room. No door block; the wall is simply gone.
    for z in range(10, 13):
        for y in range(3, 6):
            grid.air(0, y, z)

    # Windows: bookshelves, purple glass, panes, and several missing panes.
    for y in (4, 5, 6):
        grid.put(5, y, 0, BOOKSHELF)
    for x, z, ys in (
        (8, 0, (4, 5)),
        (9, 0, (4,)),
        (16, 0, (4, 5)),
        (12, 14, (4, 5)),
        (15, 14, (4, 5)),
        (18, 14, (5,)),
    ):
        for y in ys:
            grid.put(x, y, z, GLASS)
    grid.put(4, 4, 0, pane())
    grid.put(10, 4, 0, pane())
    grid.put(6, 5, 0, pane())
    # x=12, y=4 on the north wall is a missing pane: stone stays, no pane.
    grid.put(14, 4, 14, pane())
    grid.put(17, 5, 14, pane())

    # Back room partition. Doorless; the west hole is the only opening.
    grid.fill(5, 5, 3, 6, 9, 13, STONE)
    grid.air(3, 2, 11)

    # Curse corner.
    grid.put(1, 2, 2, CRYING)
    grid.put(2, 2, 2, CRYING)
    grid.put(1, 3, 2, COBWEB)
    grid.put(2, 4, 2, COBWEB)
    grid.put(3, 3, 2, COBWEB)
    grid.put(2, 3, 4, COBWEB)
    grid.put(1, 4, 1, COBWEB)
    grid.put(21, 5, 1, COBWEB)
    grid.put(21, 5, 13, COBWEB)
    grid.put(20, 11, 7, COBWEB)

    # Fallen pillar, scattered drums, and a lantern on the floor.
    grid.put(11, 3, 6, pillar("y"))
    grid.put(12, 3, 6, pillar("x"))
    grid.put(13, 3, 6, pillar("x"))
    grid.put(14, 3, 6, pillar("x"))
    grid.put(15, 3, 5, pillar("z"))
    grid.put(12, 3, 7, pillar("x"))
    grid.put(13, 3, 4, lantern(False))

    # Fallen shelves beside the supply chest, not on top of it.
    grid.put(9, 3, 5, BOOKSHELF)
    grid.put(10, 3, 5, BOOKSHELF)

    grid.put(4, 3, 1, shelf("south", {0, 1}))
    grid.put(5, 3, 1, shelf("south", set()))
    grid.put(7, 3, 13, shelf("north", {0}))

    grid.put(5, 2, 8, AMETHYST)
    grid.put(5, 3, 8, block("minecraft:amethyst_cluster", {"facing": "up", "waterlogged": "false"}))
    grid.put(4, 3, 8, block("minecraft:small_amethyst_bud", {"facing": "west", "waterlogged": "false"}))

    # Surviving upper floor and the stair that still stands, with one broken step.
    grid.fill(10, 16, 6, 6, 6, 10, STONE)
    grid.put(17, 3, 10, stairs("east"))
    grid.air(16, 4, 10)
    grid.put(15, 5, 10, stairs("east"))
    grid.put(14, 6, 10, stairs("east"))
    grid.put(14, 1, 10, chest("north", "tools"))
    grid.air(14, 2, 10)

    books = (
        (18, 6, "south", "The Ledger",
         "The library once lent enchanted books to the crews of end ships. They signed the ledger, took a volume for the voyage, and returned it when the ship came home."),
        (18, 8, "north", "The Vault",
         "The vault was locked after the roof fell. The key was not left on the desk. What remains inside is the blade and whatever the shelves still remember."),
        (10, 7, "east", "The Watcher",
         "The watcher outside was left to keep the blade. It was told to stay in the yard, and it has."),
    )
    for x, z, facing, title, page in books:
        grid.put(x, 3, z, lectern(facing, title, page))
    grid.put(18, 4, 6, candle())
    grid.put(8, 3, 1, STONE)
    grid.put(8, 4, 1, candle())

    grid.put(15, 11, 7, lantern(True))
    grid.put(12, 11, 7, lantern(True))

    chests = {
        (19, 2, 3): ("south", "swords"),
        (21, 8, 9): ("west", "bows"),
        (6, 2, 3): ("south", "armor"),
        (8, 2, 12): ("north", "depths"),
        (11, 6, 8): ("east", "mending"),
        (2, 2, 3): ("south", "curses"),
        (9, 2, 6): ("west", "supplies"),
        (2, 2, 11): ("east", "vault"),
    }
    for (x, y, z), (facing, loot) in chests.items():
        grid.put(x, y, z, chest(facing, loot))
        grid.air(x, y + 1, z)
    grid.put(12, 6, 8, block("minecraft:anvil", {"facing": "north"}))
    grid.put(21, 7, 9, PURPUR)
    grid.put(23, 8, 9, slab())
    grid.put(23, 8, 10, slab())
    grid.put(22, 8, 10, slab())

    # One cherry tree, leaning toward the door. Gaps in a thin cap.
    grid.put(25, 3, 4, log("stripped_cherry_log"))
    grid.put(25, 4, 4, log("stripped_cherry_log"))
    grid.put(24, 5, 4, log("cherry_log"))
    grid.put(24, 6, 4, log("cherry_log"))
    grid.put(23, 7, 4, log("cherry_log", "x"))
    for pos in ((23, 8, 4), (22, 7, 4), (24, 7, 5), (23, 7, 3), (22, 8, 5)):
        grid.put(*pos, leaves())

    # Yard sightline from the guardian back through the entrance.
    for x in range(22, 27):
        for y in range(3, 6):
            grid.air(x, y, 7)
    grid.entities.append(shulker_entity(26, 3, 7, 90.0, 45.0))
    return grid


def build_tower() -> Grid:
    grid = Grid(9, 18, 9)
    for y in (0, 1):
        grid.fill(1, 7, y, y, 1, 7, END_BRICK)
    for y in range(2, 14):
        for x in range(1, 8):
            for z in range(1, 8):
                edge = x in (1, 7) or z in (1, 7)
                if not edge:
                    grid.air(x, y, z)
                    continue
                corner = x in (1, 7) and z in (1, 7)
                if corner:
                    grid.put(x, y, z, pillar("y"))
                elif (x + y + z) % 9 == 0:
                    grid.put(x, y, z, CRACKED)
                else:
                    grid.put(x, y, z, STONE)
    for pos in (
        (1, 6, 4), (1, 10, 4), (7, 6, 4), (7, 10, 4),
        (4, 6, 1), (4, 10, 1), (4, 6, 7), (4, 10, 7),
    ):
        grid.put(*pos, GLASS)
    # Doorway so the upper chest can be reached.
    for x in (3, 4):
        grid.air(x, 2, 1)
        grid.air(x, 3, 1)

    positions = [(5, 4), (5, 5), (4, 5), (3, 5), (3, 4), (3, 3), (4, 3), (5, 3)]
    for y in range(2, 14):
        x, z = positions[(y - 2) % len(positions)]
        nxt = positions[(y - 1) % len(positions)]
        dx, dz = nxt[0] - x, nxt[1] - z
        if dx == 1:
            facing = "west"
        elif dx == -1:
            facing = "east"
        elif dz == 1:
            facing = "north"
        else:
            facing = "south"
        grid.put(x, y, z, stairs(facing))

    grid.fill(1, 7, 14, 14, 1, 7, STONE)
    for x in range(0, 9):
        for z in range(0, 9):
            if x in (0, 8) or z in (0, 8):
                grid.put(x, 14, z, slab())
    for pos in ((0, 0), (0, 8), (8, 0), (8, 8)):
        grid.put(pos[0], 15, pos[1], lantern(False))
    grid.put(5, 14, 3, chest("south", "tower"))
    grid.air(5, 15, 3)
    grid.air(5, 15, 4)
    grid.put(4, 15, 4, pillar("y"))
    grid.put(4, 16, 4, pillar("y"))
    grid.put(4, 17, 4, block("minecraft:end_rod", {"facing": "up"}))
    grid.entities.append(shulker_entity(5, 15, 5, 180.0, 30.0))
    return grid


def palette_ids(root):
    return [entry["id"] for entry in root["palette"]]


def block_index(root):
    found = {}
    for body in root["blocks"]:
        pos = tuple(body["pos"])
        state = root["palette"][body["state"]]
        found[pos] = (state, body.get("nbt"))
    return found


def assert_library(path: Path) -> None:
    root = read_nbt(path)
    if root["DataVersion"] != DATA_VERSION:
        raise SystemExit("library data version")
    if root["size"] != [34, 13, 15]:
        raise SystemExit(f"library size {root['size']}")
    ids = set(palette_ids(root))
    if any("netherrack" in name or "netherite" in name for name in ids):
        raise SystemExit("library uses a forbidden block")
    for required in (
        "minecraft:end_stone_bricks", "minecraft:purpur_stairs", "minecraft:cobweb",
        "minecraft:cherry_log", "minecraft:stripped_cherry_log", "minecraft:cherry_leaves",
        "minecraft:bookshelf", "minecraft:chiseled_bookshelf", "minecraft:anvil",
        "minecraft:purple_stained_glass", "minecraft:purple_stained_glass_pane",
        "minecraft:lantern", "minecraft:candle", "minecraft:crying_obsidian",
        "minecraft:amethyst_block", "minecraft:calcite", "minecraft:chest",
    ):
        if required not in ids:
            raise SystemExit(f"library missing {required}")
    blocks = block_index(root)
    tables = {}
    for pos, (state, nbt) in blocks.items():
        if state["id"] != "minecraft:chest":
            continue
        loot = nbt["LootTable"]
        above = blocks.get((pos[0], pos[1] + 1, pos[2]))
        if above is None or above[0]["id"] != "minecraft:air":
            raise SystemExit(f"chest {pos} is covered")
        tables[loot] = pos
    expected = {f"violetarchive:archive/{name}" for name in (
        "swords", "bows", "armor", "tools", "depths", "mending", "curses", "supplies", "vault",
    )}
    if set(tables) != expected:
        raise SystemExit(f"library chests {sorted(tables)}")
    if blocks[(3, 2, 11)][0]["id"] != "minecraft:air":
        raise SystemExit("back room foundation hole missing")
    if blocks[(26, 2, 7)][0]["id"] != "minecraft:cracked_stone_bricks":
        raise SystemExit("guardian seat is not cracked stone bricks")
    for x in range(22, 27):
        for y in (3, 4):
            if blocks[(x, y, 7)][0]["id"] != "minecraft:air":
                raise SystemExit(f"sightline blocked at {(x, y, 7)}")
    lecterns = [pos for pos, (state, _) in blocks.items() if state["id"] == "minecraft:lectern"]
    if len(lecterns) != 3:
        raise SystemExit(f"lecterns {len(lecterns)}")
    if len(root["entities"]) != 1:
        raise SystemExit("library shulker count")
    entity = root["entities"][0]
    if entity["nbt"]["id"] != "minecraft:shulker" or entity["blockPos"] != [26, 3, 7]:
        raise SystemExit("library shulker pose")
    roof = [pos for pos, (state, _) in blocks.items() if state["id"] == "minecraft:purpur_stairs" and pos[0] < 8]
    if roof:
        raise SystemExit("roof continues into the ruined third")
    print("library", len(blocks), "blocks", len(ids), "palette")


def assert_tower(path: Path) -> None:
    root = read_nbt(path)
    if root["size"] != [9, 18, 9]:
        raise SystemExit(f"tower size {root['size']}")
    ids = set(palette_ids(root))
    if any("netherrack" in name for name in ids):
        raise SystemExit("tower netherrack")
    blocks = block_index(root)
    chests = [pos for pos, (state, nbt) in blocks.items() if state["id"] == "minecraft:chest"]
    if chests != [(5, 14, 3)] or blocks[chests[0]][1]["LootTable"] != "violetarchive:archive/tower":
        raise SystemExit(f"tower chests {chests}")
    if blocks[(5, 15, 3)][0]["id"] != "minecraft:air":
        raise SystemExit("tower chest covered")
    if blocks[(4, 17, 4)][0]["id"] != "minecraft:end_rod":
        raise SystemExit("tower spire")
    lanterns = [pos for pos, (state, _) in blocks.items() if state["id"] == "minecraft:lantern"]
    if len(lanterns) != 4:
        raise SystemExit("tower lanterns")
    if len(root["entities"]) != 1 or root["entities"][0]["blockPos"] != [5, 15, 5]:
        raise SystemExit("tower sentry")
    plinth = blocks[(4, 0, 4)][0]["id"]
    if plinth != "minecraft:end_stone_bricks":
        raise SystemExit("tower plinth")
    print("tower", len(blocks), "blocks")


def main() -> None:
    write_loot()
    library = STRUCTURE / "library.nbt"
    tower = STRUCTURE / "tower.nbt"
    write_nbt(library, build_library().to_nbt())
    write_nbt(tower, build_tower().to_nbt())
    assert_library(library)
    assert_tower(tower)
    print("wrote", library)
    print("wrote", tower)


if __name__ == "__main__":
    main()
