# Emberfall — Phase 2.1 (graphics pass)

A lightweight 2D top-down open-world survival RPG for Android, built with
**Kotlin and the Android Canvas/SurfaceView stack** — no game engine, no
AndroidX, no native libraries.

Phase 1 delivered the infinite world, movement and render loop. Phase 2 added
the survival layer: items and inventory, wildlife and predators, loot chests,
campfire checkpoints, XP/leveling, a minimap, five sealed bosses, death and
respawn. **2.1 adds textured materials, AGSL night lighting, sprite-sheet
animation and frame-paced 60 FPS rendering.**

<p>
  <img src="docs/screenshot-day.png" width="49%" alt="Textured plains with campfire and chest" />
  <img src="docs/screenshot-night.png" width="49%" alt="AGSL night lighting with player and campfire light pools" />
</p>

*Captured on an Android 15 (API 35) emulator at 50–57 FPS (SwiftShader). The
debug APK is ~2.1 MB, including a 1.2 MB texture atlas.*

## What is implemented

| System | Status |
| --- | --- |
| Open world | Infinite seeded chunks (forest / plains / highlands / shore / ocean / ruins), streamed and evicted around the player |
| Movement | Analog acceleration, wall sliding, circle-vs-tile collision, sprint at full stick |
| Touch | Floating joystick, attack, contextual interact, inventory and settings buttons |
| Survival | Health, mana, hunger, regeneration, starvation damage, death and campfire respawn |
| Items | 11 item types: wood, stone, fiber, iron, hide, magic stones, raw/cooked meat, elixir, wooden/iron swords |
| Inventory | 18 slots, stacking, tap-to-consume, automatic best-weapon equip |
| Wildlife | Rabbits, deer, boars (flee when hit, drop meat/hide) and wolves (chase and bite) |
| Loot | Eight hand-placed chests with deterministic loot, plus ground pickups with auto-collect |
| Progression | XP, levels (+8 max HP, +4 max mana), boss bonuses |
| Bosses | Five named bosses in sealed arenas, telegraphed dash attacks, permanent rewards |
| Checkpoints | Five campfires; resting heals, saves and sets the respawn point |
| Minimap | North-up terrain sample with player arrow, boss/chest/campfire/wildlife markers |
| Textures | 1024×1024 atlas, 12 material types × 4 variants at 128 px, bilinear-filtered |
| Lighting | AGSL RuntimeShader (API 33+) with ambient darkness and three dynamic light pools; legacy fallback |
| Animation | SpriteAnimation frame parser; 8-frame flame sheet; shimmering water variants |
| Loop | `lockHardwareCanvas`, `System.nanoTime` fixed 1/60 s step, `update(deltaTime)` + `render(Canvas)`, sleep-based cap |
| Graphics options | LOW/MEDIUM/HIGH quality, smooth textures, anti-aliasing, particles — applied live |
| Save | Versioned JSON, atomic write, autosave + save on pause; v1 files still load |

## Graphics & performance pass

**Textured materials.** `tools/gen_tileset.py` procedurally paints a 1024×1024
atlas: 12 material types × 4 variants at 128×128 (grass, forest floor, flowers,
dirt, sand, water, deep water, tree, rock, ruin floor, ruin wall, forest tree).
Tiles are drawn into 32-world-unit cells with reused `RectF`s, so the renderer
still issues one `drawBitmap` per tile. The atlas is generated from code, so it
can be regenerated or replaced with a Kenney/itch tileset.

**AGSL night lighting.** On Android 13+ a single `RuntimeShader` fragment pass
computes ambient darkness and soft circular falloff for three lights (player
torch + up to two nearby campfires), with a warm tint and dithering to prevent
8-bit banding. Older devices automatically use the previous overlay path.

**Sprite animation.** `SpriteAnimation` slices a sheet by row/column, advances on
a frame duration, and supports looping or one-shot clips. `AnimationSet` maps
IDLE/WALK/ATTACK states. The campfire flame is an 8-frame generated sheet; water
cycles atlas variants over time.

**Frame pacing.** The render thread calls
`SurfaceHolder.lockHardwareCanvas()` and runs a fixed 1/60 s accumulator from
`System.nanoTime()`, clamped to 250 ms to survive app switches. `Game` exposes
`update(deltaTime)` and `render(Canvas)`; the camera uses frame-rate-independent
exponential smoothing with a small look-ahead. The loop sleeps out the remainder
of each 16.6 ms budget instead of spinning.

## The five bosses

| Boss | Arena (tile coords) | HP | Contact damage | Reward |
| --- | --- | --- | --- | --- |
| Gloomfang, the Alpha | (0, −55) | 170 | 12 | +120 XP, +10 max HP, +4 max mana, elixir/meat/stone |
| Stonewarden | (95, 30) | 280 | 16 | +190 XP, +14 max HP, +5 max mana, iron/stone |
| Mirewitch of the Fen | (−120, 70) | 340 | 18 | +240 XP, +16 max HP, +8 max mana, elixirs/stones |
| Ash Titan | (40, 160) | 470 | 22 | +330 XP, +20 max HP, +6 max mana, iron sword |
| The Hollow King | (−180, −140) | 620 | 26 | +460 XP, +25 max HP, +10 max mana, elixirs/stones |

Stepping into an arena wakes its boss and seals the arena; the player is clamped
inside until either side dies. Dying resets the fight and returns the player to
the last campfire. Boss kills are saved permanently.

## Controls

| Input | Action |
| --- | --- |
| Drag on the left 60% | Floating joystick (push to the rim to sprint) |
| Sword button | Melee swing (best owned weapon is used automatically) |
| **E** button | Contextual interact: open chest, rest at campfire |
| Bag button | Inventory (pauses the world) |
| Gear button | Graphics settings |
| Tap outside a panel | Close it |

## Items

Wood, Stone, Fiber, Iron Ore, Hide, Magic Stone, Raw Meat, Cooked Meat, Elixir,
Wooden Sword, Iron Sword.

- Meat/elixir restore hunger, health and/or mana when tapped in the inventory.
- Weapons never stack and the highest-damage owned sword is equipped
  automatically.
- Chests roll 2–3 stacks from a deterministic table seeded by chest id + world
  seed; a couple of chests guarantee a sword.

## Build and run

Requirements: Android Studio (Ladybug+) or JDK 17+ with Gradle 8.9+, Android SDK
Platform 35, a device/emulator on Android 7.0+.

```bash
cd Emberfall

# Debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug

# Install + launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.emberfall.game/.MainActivity

# JVM unit tests (36 tests, no emulator)
./gradlew :app:testDebugUnitTest
```

On Windows use `gradlew.bat`. `local.properties` contains the SDK path of the
machine that generated this project — replace it with your own, or delete it and
let Android Studio regenerate it.

Regenerate the texture atlas after editing `tools/gen_tileset.py`:

```bash
python tools/gen_tileset.py
```

## Architecture

```
app/src/main/java/com/emberfall/game/
├── MainActivity.kt        Immersive fullscreen host, lifecycle → save
├── GameView.kt            SurfaceView, touch dispatch, render thread
├── core/
│   ├── Game.kt            Simulation: entities, combat, bosses, saves
│   ├── PlayerStats.kt     HP/mana/hunger/XP and level curves
│   ├── GraphicsSettings.kt
│   ├── Camera.kt
│   └── Config.kt
├── entity/
│   ├── Player.kt          Movement, facing, swing, damage/invulnerability
│   ├── Creature.kt        Shared HP, knockback, collision movement, facing
│   ├── Animal.kt          Rabbit/deer/boar wander + flee AI
│   ├── Wolf.kt            Aggro/chase/bite AI
│   ├── Boss.kt            Sealed-arena boss state machine (chase/telegraph/dash)
│   └── WorldObjects.kt    Pickup, Campfire, LootChest, FloatingText, Particle
├── item/
│   ├── ItemId.kt          Item stats and categories
│   └── Inventory.kt       Stacking, removal, best weapon
├── world/
│   ├── Noise.kt, WorldGenerator.kt, World.kt, TileType.kt, Biome.kt
│   ├── Bosses.kt          Boss definitions and arena coordinates
│   ├── WorldPoints.kt     Campfire and chest placements
│   └── Collision.kt
├── input/                 InputState, TouchController, UiCommand
├── render/
│   ├── TileAtlas.kt       Loads the 1024² atlas (procedural fallback)
│   ├── WorldRenderer.kt   Terrain, water shimmer, day/night + AGSL lights
│   ├── GpuLighting.kt     RuntimeShader (AGSL) night-lighting pass
│   ├── SpriteAnimation.kt Frame parser + state-based AnimationSet
│   ├── CreatureRenderer.kt, PlayerRenderer.kt, ObjectRenderer.kt
├── ui/
│   ├── HudRenderer.kt     Vitals, boss bar, prompts, joystick and buttons
│   ├── MinimapRenderer.kt
│   ├── InventoryPanelRenderer.kt, SettingsPanelRenderer.kt
│   └── UiLayout.kt        Single source of truth for all hit boxes
└── save/SaveManager.kt    Versioned JSON, atomic write, synchronized
```

### Loop and performance

- Fixed 1/60 s simulation on a dedicated thread (`THREAD_PRIORITY_DISPLAY`),
  one hardware-canvas draw per frame, sleep to the frame budget.
- Tiles are pre-rendered bitmaps: one `drawBitmap` per visible tile.
- Chunks are `ByteArray`s generated lazily and evicted past six chunks.
- Off-screen entities are not drawn or updated; bosses only tick within range.
- No per-frame allocations in the tile hot path; paints/rects/bitmaps are reused.

### Save format (v2)

```json
{
  "version": 2,
  "seed": 1763696017,
  "x": 16, "y": -1600,
  "minutes": 679.7, "day": 1,
  "level": 2, "xp": 65,
  "maxHealth": 118, "health": 118,
  "maxMana": 58, "mana": 58,
  "hunger": 69.2,
  "inventory": [{"id": "IRON_SWORD", "count": 1}],
  "campfireX": -176, "campfireY": 240,
  "defeatedBosses": ["gloomfang"],
  "openedChests": [1],
  "graphics": {"quality": "MEDIUM", "smoothTextures": true,
               "antiAliasing": true, "particles": true}
}
```

Writes go to a temp file and rename over the save, under a lock, so autosave and
the pause-time save cannot corrupt each other.

## Graphics notes

This pass uses real GPU-side effects available to Canvas: a 1024² textured
atlas with bilinear filtering and an AGSL `RuntimeShader` lighting pass on
API 33+. Direct **Vulkan** control, AGDK **Swappy** frame pacing and **ASTC**
texture upload still require a native renderer, because Canvas does not expose a
swapchain or command buffers; **LOD meshes** do not apply to 2D sprites. The 2D
equivalents that do apply are implemented: culling, distance-based AI updates,
quality tiers and per-object detail switches. See [docs/GRAPHICS.md](docs/GRAPHICS.md)
for the full breakdown, asset-import guide (Kenney / itch.io / OpenGameArt /
CraftPix) and the native upgrade path.

## Tests

36 JVM tests cover noise determinism, biome/tile rules, boss-arena carving,
chunking and eviction, spawn safety, collision/sliding, inventory stacking and
overflow, and XP/level/boss-reward math.

## Roadmap

| Phase | Scope |
| --- | --- |
| 1 | World streaming, render loop, movement, touch, save |
| 2 | Items/inventory, wildlife, wolves, chests, campfires, XP, minimap, 5 bosses, graphics settings |
| **2.1 (this)** | 1024² material atlas, AGSL night lighting, sprite animation, water shimmer, frame pacing |
| 3 | Harvestable resource nodes (trees/rocks/bushes), crafting recipes, shelter building, cooking |
| 4 | Ranged weapons and magic (spend mana), enemy variety, equipment durability, status effects |
| 5 | Perk tree and active abilities (dash, power strike), quests/waypoints |
| 6 | Additional boss attack patterns, loot rarity tiers, native Vulkan renderer experiment |
