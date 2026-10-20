# Emberfall content packs

This folder is the external, data-only contract for downloadable game content.
It is not compiled with Kotlin sources. Publish the files in a pack, together
with a JSON manifest following [manifest.example.json](./manifest.example.json),
at a stable HTTPS URL.

Build the Android release with the stable manifest endpoint:

```powershell
./gradlew bundleRelease -PcontentManifestUrl=https://cdn.example.com/emberfall/manifest.json
```

The app fetches the manifest in the background on launch. It stages every file
in private app storage, checks its exact byte size and SHA-256, then atomically
activates the pack. Failed or incomplete downloads leave the current pack and
bundled APK assets untouched.

## Stable asset IDs

| Asset ID | Required format |
| --- | --- |
| `tileset` | PNG/WebP atlas at least 1024×768; the existing 8-column, 128-pixel-cell layout is retained. |
| `sprite.player` | PNG/WebP sprite sheet exactly 384×256. |
| `sprite.gloomfang` | PNG/WebP sprite sheet exactly 384×256. |
| `sprite.stonewarden` | PNG/WebP sprite sheet exactly 384×256. |
| `sprite.mirewitch` | PNG/WebP sprite sheet exactly 384×256. |
| `sprite.ashtitan` | PNG/WebP sprite sheet exactly 384×256. |
| `sprite.hollowking` | PNG/WebP sprite sheet exactly 384×256. |

Every manifest asset must have a unique, stable `id`, a safe relative `path`,
its exact file length in `bytes`, and a lowercase SHA-256 checksum. Extra
assets may be downloaded now for future data-driven systems, but only declared
IDs used by the game replace a runtime asset.

Packs can change graphics without an app-store update. Kotlin/native code,
renderer layouts, and new gameplay behavior still require a normal Play Store
application update.
