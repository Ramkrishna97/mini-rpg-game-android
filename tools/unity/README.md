# Unity resource pipeline

The game currently has eight replaceable visual resources. The generator in
[EmberfallResourceBuilder.cs](./EmberfallArtProject/Assets/Editor/EmberfallResourceBuilder.cs)
creates an initial coherent, hand-tunable fantasy art pack for all of them:

| Runtime ID | Export | Contract |
| --- | --- | --- |
| `tileset` | `art/tileset.png` | 1024×1024 PNG. The first 48 cells are 12 tile types × 4 variants, ordered by `TileType`. Each cell is 128×128; eight cells per row. |
| `sprite.player` | `art/player.png` | 384×256 PNG: eight direction rows × 12 columns. Columns 0–3 idle, 4–7 walk, 8–11 attack; every cell is 32×32. |
| `sprite.gloomfang` through `sprite.dragon` | matching PNGs | 512×256: eight direction rows × 16 columns. Four frames each for idle, movement, attack, and death. |

The six boss designs and their animation poses live in
[EmberfallBossArtBuilder.cs](./EmberfallArtProject/Assets/Editor/EmberfallBossArtBuilder.cs).
The [concept sheet](./EmberfallArtProject/Assets/ArtReferences/boss-concepts.png)
is a visual reference. The game uses the exported PNG sheets, not Unity at runtime.
The five original bosses already have distinct combat moves; this pipeline
changes their appearance and adds the same visual treatment to the dragon.

The app keeps procedural props, animal art, HUD, particles, campfire flames,
and music in code for now; adding those as downloadable resources needs an
additional runtime asset ID before Unity exports would have any effect.

## Ready-to-run command

Run this from the repository root with the installed Unity project:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\unity\Build-EmberfallArt.ps1 -ProjectPath "F:\Apps\Unity engine\Unity projects\Emberfall"
```

The script finds the newest Unity Hub editor, including the one on `F:`. If
yours is installed elsewhere:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\unity\Build-EmberfallArt.ps1 -UnityPath "D:\Unity\Hub\Editor\6000.0.0f1\Editor\Unity.exe" -ContentVersion "art-001"
```

The script also ships with a small source project in this repository. To use
that project instead:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\unity\Build-EmberfallArt.ps1
```

It launches Unity headlessly with `-batchmode`, `-projectPath`, and
`-executeMethod`, as supported by the [Unity command-line documentation](https://docs.unity3d.com/6000.0/Manual/build-command-line.html). It writes ready-to-host files to [generated/](../../content-pack/generated/):

The manifest produced by this command includes only the six boss sprites;
the generated tileset and player sheet are available for future art packs.

```text
generated/
  manifest.json
  art/
    tileset.png
    player.png
    gloomfang.png
    stonewarden.png
    mirewitch.png
    ashtitan.png
    hollowking.png
    dragon.png
```

For future art changes, put a transparent PNG with one of the names above in
`Assets/EmberfallArtOverrides/` inside the Unity project. The exporter validates
its dimensions and substitutes it for the generated sheet. You can paint every
frame in Unity or another editor and rebuild the content pack without changing
the Android gameplay code. Bundled app art can be updated by copying the exported
PNG files into `app/src/main/assets/sprites/` (tileset into `app/src/main/assets/`).

Host that directory on the HTTPS endpoint configured when the Android release
is built. The app will validate every file's exact byte length and SHA-256
before it activates the pack.
