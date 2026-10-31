# Violet Archive

Violet Archive is a server-side plugin for Purpur 26.3. Players do not need a resource pack or a client mod. The plugin does not replace the End dimension.

The same facts are written in Russian in [README.ru.md](README.ru.md).

## Requirements

- Java 25
- Purpur 26.3
- Maven

The API dependency is `org.purpurmc.purpur:purpur-api` with `scope` `provided`. Maven resolves it from `https://repo.purpurmc.org/snapshots`. The server supplies that API at runtime, so the jar does not bundle Purpur.

`paper-plugin.yml` sets `api-version` to `26.3`, the plugin version to `1.0.0`, and the bootstrapper to `violetarchive.bootstrap.VioletArchiveBootstrap`.

## Build

From a checkout of this repository:

```bash
mvn -q clean package
```

The plugin jar is `target/violetarchive-1.0.0.jar`.

`mvn test` runs the unit tests in this repo. Those tests do not start a Purpur server.

## Install

Copy `target/violetarchive-1.0.0.jar` into the server's `plugins/` directory and restart.

On first enable the plugin writes `plugins/VioletArchive/config.yml` from the default file. The only setting is Anima's follow distance:

```yaml
anima:
  follow-distance: 4.5
```

`4.5` is the default. A value from `0.5` through `64`, including both ends, is kept. If the key is missing, the value is not a number, or it is outside that range, the plugin logs a warning and uses `4.5`. The value is read when the plugin enables. There is no reload command; change the file and restart.

Leaving the debug command unused does not change survival play.

## What players find

Structures generate in the outer End biomes `minecraft:small_end_islands`, `minecraft:end_midlands`, `minecraft:end_highlands`, and `minecraft:end_barrens`. The central-island biome `minecraft:the_end` is not in that list. The structure set `violetarchive:archive` places `violetarchive:library` and `violetarchive:tower` at equal weight, with random spread, spacing 32, separation 16, and a triangular spread. An exclusion zone of 8 chunks keeps them away from `minecraft:end_cities`.

Both buildings are jigsaw structures in the datapack. The library template is a partly ruined building: the west side is open, and the palette includes cobwebs, cracked stone bricks, bookshelves, and cherry wood. The tower template is an intact 9×18×9 building. Its end-stone-brick foundation is the bottom of that template. `start_height.absolute` is `2` on the tower and `0` on the library, and both project onto `WORLD_SURFACE_WG`. New chunks use those heights. A chunk that already generated is not rewritten.

The library's guarded chest uses loot table `violetarchive:archive/vault`. The shulker beside it is named **Purpur Watcher**, dyed purple, made persistent, and given 45 health. While a player is within 32 blocks, four amethyst-shard item displays orbit the watcher. The plugin cancels the watcher's shulker bullet. A volley starts when the watcher is not already jumping or firing, 60 ticks have passed since the last volley, and at least two usable solid blocks lie within 5 blocks. Bedrock, barriers, and chests are not used. The volley lifts two of those blocks. A block that reaches the targeted player deals 4 damage, and then the block is put back. While a player stays within 6 blocks, about one try in four starts a jump. The jump lifts the block the watcher is sitting on and then puts that block back. On landing it pushes players within 3.5 blocks and can turn some ground in a ring around the seat into amethyst, with a few small buds. It will not jump from bedrock, a barrier, a chest, or a non-solid block. The tower sentry is not given this behavior.

The tower's chest uses `violetarchive:archive/tower`. Its shulker is named **Archive Sentry**, dyed purple, and made persistent. The plugin does not change the sentry's health, bullet, or teleport.

Each guarded chest and its shulker share one `violetarchive:lock_id`. The key is not placed in the chest. Killing the watcher or the sentry adds the matching archive key to that mob's drops.

The library template also has ordinary chests for `swords`, `bows`, `armor`, `tools`, `depths`, `mending`, `curses`, and `supplies`. Those tables stay in the datapack JSON. They use vanilla gear and books, including a golden helmet with Curse of Binding, an iron chestplate with Curse of Vanishing, and a 15% chance of a Mending book. They do not contain netherite, the Watcher's Blade, or Vindicta. Only the vault chest and the tower chest are locked by the plugin.

When the vault chest generates loot, the plugin adds one Watcher's Blade if the table did not already include one. The blade is a diamond sword with persistent data `violetarchive:watchers_blade`. It has no dash, blink, bonus damage, or other attack. For the vault chest and the tower chest, the plugin also adds a Vindicta enchanted book on 8% of loot rolls. That book is not written into the loot JSON. A dropped Vindicta book drifts upward for a short time.

Vindicta, Ration, and Anima are the three registered enchantments. Each has a maximum level of 1.

- **Vindicta** goes on leather, copper, chainmail, iron, gold, diamond, and netherite chestplates, and on elytra. It is exclusive with Thorns, and Thorns is exclusive with it. When a living entity damages a wearer, there is a 20% chance to fire a marked shulker bullet. A hit from that bullet deals 6 damage, applies Levitation for 60 ticks at amplifier 0 without particles, and adds a horizontal push of 0.12 away from the wearer. Other shulker bullets, including the sentry's, stay vanilla.
- **Ration** goes on any shulker box and is exclusive with Anima. It is not in a loot table. Once a second, if a player is carrying a Ration box and does not have a shulker box open, the plugin spends at most one item from it. At or below 40% health, or while a harmful effect is active, it can drink one drinkable potion, or a milk bucket if a harmful effect is still there. Splash and lingering potions are left alone. A healing potion is used only at low health. Another beneficial potion is used only when the player does not already have that effect. Below full hunger it eats one food item that covers the missing hunger, preferring the smallest overflow.
- **Anima** goes on any shulker box and is exclusive with Ration. It is not in a loot table. A placed Anima box remembers the player who placed it. Every 10 ticks, if that player is online in the same world, the box is farther than 2 blocks away, and the player's eyes are within the follow distance, the box steps one block along the larger horizontal axis. It tries the same height, then one block up, then one block down, and turns its lid one quarter turn. Breaking the box puts the enchantment back on the dropped item.

Item names are rendered on the server from `lang/en_us.json` and `lang/ru_ru.json`. English uses "Watcher's Blade" and "Archive Key". Russian uses "Клинок Смотрителя" and "Ключ архива". The text is plain, not a client translation key, and the lore is not italic. The name is cleared when the item leaves the player's inventory. Mob names stay the English strings above.

Past 1024 blocks from the origin, about one in five outer cells can become an amethyst forest. A cell is 200 blocks on a side. A chunk is eligible when the center of the chunk is more than 1024 blocks from `x=0, z=0` and that cell's value is in the lowest 20%. The forest is not a structure. On first generation, the populator paints the top end stone in a calcite and amethyst checkerboard, sets the column's biome to `violetarchive:amethyst_forest`, and may place calcite trunks, amethyst and calcite canopy, buds, and end rods. Saved chunks are not decorated again. A chunk that already contains a purpur block, a purpur pillar, magenta stained glass, or end stone bricks is skipped. The biome JSON sets fog `#4A3270`, end-rod and purple dust particles, and enderman spawns. Its feature list is empty; the trees come from the populator. End stone under the surface is left in place.

## Locks

The chest lock is a nameless amethyst shard. Its only extra data is the persistent string `violetarchive:lock_id`. The shard has no item name and no lore.

The archive key is also an amethyst shard. It stores `violetarchive:archive_key` and the same `lock_id`. After a player holds it, the key may show a rendered name. That name is not part of the lock.

Either hand may hold the key. The chest opens only when the key's `lock_id` is the chest's `lock_id`. A key for another chest, a named shard with no plugin data, and a plain amethyst shard do not match. Those clicks keep the vanilla locked-container denial (`container.isLocked`). The lock stays on the chest.

The match is applied when the chest menu checks the lock. A rendered name on the key does not fail that check, because the check compares the lock id and not the item name.

## Debug command

`/violetarchive` is registered in Java when the plugin object is constructed. It is not declared under `commands` in `paper-plugin.yml`. The permission is `violetarchive.debug`, default `op`, described as "Run Violet Archive debug checks."

A player must run it. The console is rejected. If nobody runs the command, survival play is unchanged. The subcommands that exist are:

| Command | Effect |
| --- | --- |
| `/violetarchive` | Prints the subcommand names. |
| `give blade` | Gives a Watcher's Blade. |
| `give key` | Places a locked chest nearby and gives its key. |
| `give decoy` | Gives a named amethyst shard with no plugin data. |
| `give vindicta` | Gives a stored Vindicta book. |
| `give chestplate` | Gives a diamond chestplate with Vindicta. |
| `give elytra` | Gives elytra with Vindicta. |
| `give ration` | Gives a filled shulker box with Ration. |
| `give anima` | Gives a purple shulker box with Anima. |
| `spawn watcher` | Spawns a Purpur Watcher on a small pad and gives its key. |
| `spawn sentry` | Spawns an Archive Sentry on a small pad and gives its key. |
| `locate library` | Prints a library position outside 1024 blocks. |
| `locate tower` | Prints a tower position outside 1024 blocks. |
| `locate forest` | Prints a forest chunk. |
| `tp library` | Teleports to a library. |
| `tp tower` | Teleports to a tower. |
| `tp forest` | Teleports onto a forest surface. |
| `trigger volley` | Starts the watcher volley. |
| `trigger jump` | Starts the watcher jump. |
| `trigger vindicta` | Fires a Vindicta bullet at a nearby mob. |
| `trigger ration` | Runs one Ration check. |
| `trigger anima` | Runs one Anima step. |
| `loot chest <table> [count]` | Places 1 to 64 chests with a real `violetarchive:archive/<table>` table. |
| `inspect` | Prints locale, follow distance, held item, target, and enchantment facts. |

`<table>` is one of `swords`, `bows`, `armor`, `tools`, `depths`, `mending`, `curses`, `supplies`, `vault`, or `tower`. `locate` and `tp` load the world `world_the_end`, and create it if that world is missing. `trigger` calls the same gameplay methods the live watcher, Vindicta, Ration, and Anima use.

## What this plugin does not do

- The Watcher's Blade has no combat ability.
- The plugin registers no crafting recipes.
- The central island, the ender dragon, end cities, and end ships are not replaced. The datapack has no `data/minecraft/dimension/the_end.json`.
- Vindicta, Ration, and Anima are registered in Java during bootstrap. The datapack has no enchantment JSON. The registration does not mark them discoverable or tradeable.
- Ration and Anima are not added to generated loot. Survival play can find a Vindicta book in the vault or tower chest. It does not find a Ration or Anima box there.
- Structures and the `violetarchive:amethyst_forest` biome come from the datapack inside the jar. `pack.mcmeta` uses `pack_format` `121.0`. The bootstrap discovers that pack as `violetarchive`.

## Packages

| Package | Contents |
| --- | --- |
| `violetarchive` | Plugin enable path, follow distance, and persistent-data keys in `Keys`. |
| `violetarchive.bootstrap` | Datapack discovery and enchantment registration. |
| `violetarchive.item` | Archive lock, key, Watcher's Blade, and rendered names. |
| `violetarchive.structure` | Structure finish, Purpur Watcher, and the extra vault and tower loot. |
| `violetarchive.enchant` | Vindicta, Ration, and Anima. |
| `violetarchive.world` | Amethyst forest decision and chunk populator. |
| `violetarchive.debug` | The `/violetarchive` command. |

## License

No license file is present yet. This README does not grant one.
