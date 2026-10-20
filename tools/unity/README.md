# Unity resource pipeline

The game currently has seven replaceable visual resources. The generator in
[EmberfallResourceBuilder.cs](./EmberfallArtProject/Assets/Editor/EmberfallResourceBuilder.cs)
creates an initial coherent, hand-tunable fantasy art pack for all of them:

| Runtime ID | Export | Contract |
| --- | --- | --- |
| `tileset` | `art/tileset.png` | 1024×1024 PNG. The first 48 cells are 12 tile types × 4 variants, ordered by `TileType`. Each cell is 128×128; eight cells per row. |
| `sprite.player` | `art/player.png` | 384×256 PNG: eight direction rows × 12 columns. Columns 0–3 idle, 4–7 walk, 8–11 attack; every cell is 32×32. |
| `sprite.gloomfang` through `sprite.hollowking` | matching PNGs | The same 384×256 directional-sprite contract. |

The app keeps procedural props, animal art, HUD, particles, campfire flames,
and music in code for now; adding those as downloadable resources needs an
additional runtime asset ID before Unity exports would have any effect.

## Ready-to-run command

When Unity finishes installing, run this from the repository root:

```powershell
.\tools\unity\Build-EmberfallArt.ps1
```

The script finds the newest Unity Hub editor. If yours is installed elsewhere:

```powershell
.\tools\unity\Build-EmberfallArt.ps1 -UnityPath "D:\Unity\Hub\Editor\6000.0.0f1\Editor\Unity.exe" -ContentVersion "art-001"
```

It launches Unity headlessly with `-batchmode`, `-projectPath`, and
`-executeMethod`, as supported by the [Unity command-line documentation](https://docs.unity3d.com/6000.0/Manual/build-command-line.html). It writes ready-to-host files to [generated/](../../content-pack/generated/):

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
```

Host that directory on the HTTPS endpoint configured when the Android release
is built. The app will validate every file's exact byte length and SHA-256
before it activates the pack.
