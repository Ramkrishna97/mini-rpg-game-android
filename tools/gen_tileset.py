"""Generate Emberfall's 1024x1024 tile atlas (12 tile types x 4 variants).

Run with the bundled Python:
  python tools/gen_tileset.py
Output: app/src/main/assets/tileset.png
"""
from __future__ import annotations

import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "tileset.png"
PREVIEW = ROOT / "docs" / "tileset-preview.png"

CELL = 128
SS = 2  # supersampling factor
S = CELL * SS
COLS = 8
ROWS = 8

TYPES = 12
VARIANTS = 4

GRASS = (78, 122, 59)
GRASS_DARK = (56, 92, 43)
GRASS_LIGHT = (101, 148, 76)
FOREST = (63, 98, 55)
FOREST_DARK = (45, 73, 40)
DIRT = (122, 97, 66)
DIRT_DARK = (92, 72, 48)
DIRT_LIGHT = (148, 120, 85)
SAND = (199, 177, 124)
SAND_DARK = (172, 149, 100)
SAND_LIGHT = (219, 200, 154)
WATER = (43, 89, 119)
WATER_DARK = (30, 66, 88)
WATER_LIGHT = (108, 157, 176)
DEEP = (27, 58, 77)
ROCK = (108, 108, 116)
ROCK_DARK = (74, 74, 82)
ROCK_LIGHT = (142, 142, 150)
RUIN = (88, 88, 98)
RUIN_DARK = (61, 61, 70)
RUIN_LIGHT = (116, 116, 126)
MORTAR = (44, 44, 53)
MOSS = (78, 98, 66)


def noise(seed: int, size: int, cells: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    small = rng.random((cells, cells)).astype(np.float32)
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((size, size), Image.BICUBIC)
    return np.asarray(img, dtype=np.float32) / 255.0


def texture(rgb, seed: int, contrast: float = 0.12, grain: float = 0.035) -> Image.Image:
    n = 0.6 * noise(seed, S, 6) + 0.4 * noise(seed + 1, S, 24)
    arr = np.empty((S, S, 3), np.float32)
    arr[..., 0] = rgb[0]
    arr[..., 1] = rgb[1]
    arr[..., 2] = rgb[2]
    arr *= (1.0 + contrast * (n - 0.5) * 2.0)[..., None]
    rng = np.random.default_rng(seed + 7)
    arr += rng.normal(0.0, grain * 255.0, (S, S, 1)).astype(np.float32)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert("RGBA")


def shade(rgb, factor: float):
    return tuple(max(0, min(255, int(c * factor))) for c in rgb)


def grass_blades(draw: ImageDraw.ImageDraw, rng: random.Random, count: int, colors):
    for _ in range(count):
        x = rng.uniform(4, S - 4)
        y = rng.uniform(10, S - 4)
        h = rng.uniform(8, 26) * SS / 2
        lean = rng.uniform(-6, 6)
        color = rng.choice(colors)
        draw.line([(x, y), (x + lean, y - h)], fill=color + (rng.randint(120, 220),), width=rng.randint(1, 3))


def flowers(draw: ImageDraw.ImageDraw, rng: random.Random, count: int):
    for _ in range(count):
        x = rng.uniform(10, S - 10)
        y = rng.uniform(14, S - 14)
        stem = rng.uniform(8, 20)
        draw.line([(x, y), (x, y + stem)], fill=GRASS_DARK + (220,), width=max(1, SS))
        color = rng.choice([(232, 200, 90), (237, 231, 214), (217, 139, 160), (200, 160, 230)])
        r = rng.uniform(2.5, 4.5) * SS / 1.5
        for ang in range(0, 360, 90):
            px = x + math.cos(math.radians(ang)) * r
            py = y + math.sin(math.radians(ang)) * r * 0.7
            draw.ellipse([px - r * 0.7, py - r * 0.7, px + r * 0.7, py + r * 0.7], fill=color + (235,))
        draw.ellipse([x - r * 0.5, y - r * 0.5, x + r * 0.5, y + r * 0.5], fill=(240, 210, 100, 255))


def pebbles(draw, rng, count, color):
    for _ in range(count):
        x = rng.uniform(4, S - 10)
        y = rng.uniform(4, S - 8)
        w = rng.uniform(4, 14) * SS / 2
        h = w * rng.uniform(0.5, 0.8)
        draw.ellipse([x, y, x + w, y + h], fill=color + (rng.randint(120, 200),))


def make_grass(seed):
    img = texture(GRASS, seed, 0.14, 0.04)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(seed)
    grass_blades(d, rng, 55, [GRASS_DARK, GRASS_LIGHT, shade(GRASS, 0.85)])
    return img


def make_forest(seed):
    img = texture(FOREST, seed, 0.13, 0.04)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(seed)
    for _ in range(26):
        x = rng.uniform(0, S)
        y = rng.uniform(0, S)
        r = rng.uniform(2, 6) * SS / 2
        color = rng.choice([(92, 74, 46), (52, 76, 42), (108, 84, 52), FOREST_DARK])
        d.ellipse([x, y, x + r * 1.4, y + r], fill=color + (rng.randint(100, 190),))
    grass_blades(d, rng, 16, [FOREST_DARK, shade(FOREST, 1.15)])
    return img


def make_flowers(seed):
    img = make_grass(seed + 3)
    d = ImageDraw.Draw(img, "RGBA")
    flowers(d, random.Random(seed), 4)
    return img


def make_dirt(seed):
    img = texture(DIRT, seed, 0.16, 0.05)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(seed)
    pebbles(d, rng, 22, rng.choice([DIRT_DARK, DIRT_LIGHT]))
    for _ in range(6):
        x = rng.uniform(0, S)
        y = rng.uniform(0, S)
        d.line([(x, y), (x + rng.uniform(10, 30), y + rng.uniform(-8, 8))], fill=DIRT_DARK + (90,), width=1)
    return img


def make_sand(seed):
    img = texture(SAND, seed, 0.10, 0.05)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(seed)
    for _ in range(9):
        y = rng.uniform(0, S)
        pts = []
        for x in range(0, S + 16, 16):
            pts.append((x, y + math.sin(x * 0.05 + seed) * 3))
        d.line(pts, fill=SAND_DARK + (80,), width=2)
    pebbles(d, rng, 14, SAND_LIGHT)
    return img


def water(seed, deep=False):
    base = DEEP if deep else WATER
    light = shade(WATER_LIGHT, 0.8 if deep else 1.0)
    img = texture(base, seed, 0.08, 0.02)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(seed)
    count = 3 if deep else 6
    for _ in range(count):
        y = rng.uniform(0, S)
        pts = []
        amp = rng.uniform(2, 5) * SS / 2
        for x in range(0, S + 20, 20):
            pts.append((x, y + math.sin(x * 0.03 + seed + y) * amp))
        d.line(pts, fill=light + (rng.randint(45, 95),), width=rng.randint(2, 4))
    for _ in range(6 if not deep else 2):
        x = rng.uniform(0, S)
        y = rng.uniform(0, S)
        d.ellipse([x, y, x + 8 * SS / 2, y + 3 * SS / 2], fill=(210, 235, 240, rng.randint(60, 120)))
    return img


def make_tree(seed, forest: bool):
    img = make_forest(seed + 5) if forest else make_grass(seed + 5)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(seed)

    # Ground shadow.
    d.ellipse([S * 0.33, S * 0.74, S * 0.67, S * 0.86], fill=(0, 0, 0, 26))

    # Trunk with a simple light/shadow split.
    tx = S * 0.46
    tw = S * 0.08
    d.rounded_rectangle([tx, S * 0.62, tx + tw, S * 0.90], radius=4 * SS, fill=(66, 46, 30, 255))
    d.rectangle([tx + tw * 0.55, S * 0.62, tx + tw, S * 0.90], fill=(48, 33, 21, 255))
    d.line([(tx + tw * 0.2, S * 0.60), (tx + tw * 0.2, S * 0.84)], fill=(96, 70, 46, 180), width=2 * SS)

    # Canopy: layered circles, dark base then lit clusters.
    cx, cy = S * 0.5, S * 0.33
    layers = [
        (0.42, (38, 72, 36)),
        (0.34, (48, 88, 43)),
        (0.24, (58, 102, 50)),
    ]
    for radius, color in layers:
        for _ in range(4):
            ox = rng.uniform(-0.12, 0.12) * S
            oy = rng.uniform(-0.10, 0.10) * S
            r = radius * S * rng.uniform(0.55, 0.8)
            d.ellipse([cx + ox - r, cy + oy - r, cx + ox + r, cy + oy + r], fill=color + (255,))

    for _ in range(9):
        ox = rng.uniform(-0.20, 0.20) * S
        oy = rng.uniform(-0.16, 0.14) * S
        r = rng.uniform(0.035, 0.07) * S
        d.ellipse([cx + ox - r, cy + oy - r, cx + ox + r, cy + oy + r], fill=(74, 122, 62, rng.randint(150, 230)))

    # Rim light on the upper-left.
    for _ in range(6):
        ox = rng.uniform(-0.22, -0.02) * S
        oy = rng.uniform(-0.20, -0.02) * S
        r = rng.uniform(0.02, 0.045) * S
        d.ellipse([cx + ox - r, cy + oy - r, cx + ox + r, cy + oy + r], fill=(112, 160, 88, 120))
    return img


def make_rock(seed):
    img = make_grass(seed + 9)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(seed)

    d.ellipse([S * 0.20, S * 0.70, S * 0.80, S * 0.86], fill=(0, 0, 0, 80))

    pts = [
        (S * 0.22, S * 0.78),
        (S * 0.16, S * 0.55),
        (S * 0.28, S * 0.30),
        (S * 0.52, S * 0.18),
        (S * 0.76, S * 0.28),
        (S * 0.84, S * 0.56),
        (S * 0.78, S * 0.80),
    ]
    d.polygon(pts, fill=ROCK + (255,))
    d.polygon([(S * 0.28, S * 0.31), (S * 0.52, S * 0.19), (S * 0.66, S * 0.30), (S * 0.36, S * 0.42)], fill=ROCK_LIGHT + (220,))
    d.polygon([(S * 0.22, S * 0.77), (S * 0.78, S * 0.79), (S * 0.72, S * 0.68), (S * 0.30, S * 0.66)], fill=ROCK_DARK + (220,))

    for _ in range(4):
        x0 = rng.uniform(S * 0.30, S * 0.60)
        y0 = rng.uniform(S * 0.32, S * 0.62)
        d.line([(x0, y0), (x0 + rng.uniform(-18, 22), y0 + rng.uniform(10, 30))], fill=(52, 52, 60, 160), width=2)
    return img


def make_ruin_floor(seed):
    img = texture(RUIN, seed, 0.10, 0.04)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(seed)
    # Sparse cracks instead of a dense grid.
    for _ in range(5):
        x = rng.uniform(0, S)
        y = rng.uniform(0, S)
        d.line([(x, y), (x + rng.uniform(6, 30), y + rng.uniform(-12, 12))], fill=(40, 40, 48, 120), width=1)
    for _ in range(4):
        x = rng.uniform(0, S)
        y = rng.uniform(0, S)
        r = rng.uniform(6, 20)
        d.ellipse([x, y, x + r * 2, y + r], fill=MOSS + (rng.randint(50, 110),))
    return img


def make_ruin_wall(seed):
    img = texture(MORTAR, seed, 0.06, 0.03)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(seed)
    row_h = S // 4
    brick_w = S // 2
    for row in range(4):
        offset = -brick_w // 2 if row % 2 else 0
        for col in range(-1, 3):
            x = offset + col * brick_w
            y = row * row_h
            pad = 3 * SS / 2
            d.rounded_rectangle([x + pad, y + pad, x + brick_w - pad, y + row_h - pad], radius=3, fill=RUIN + (255,))
            d.line([(x + pad, y + pad), (x + brick_w - pad, y + pad)], fill=RUIN_LIGHT + (110,), width=2)
            d.line([(x + pad, y + row_h - pad - 2), (x + brick_w - pad, y + row_h - pad - 2)], fill=RUIN_DARK + (180,), width=2)
    for _ in range(6):
        x = rng.uniform(0, S)
        y = rng.uniform(0, S)
        d.ellipse([x, y, x + 10, y + 5], fill=MOSS + (rng.randint(30, 80),))
    return img


def build_tile(type_id: int, variant: int) -> Image.Image:
    seed = 1000 + type_id * 37 + variant * 101
    if type_id == 0:
        return make_grass(seed)
    if type_id == 1:
        return make_forest(seed)
    if type_id == 2:
        return make_flowers(seed)
    if type_id == 3:
        return make_dirt(seed)
    if type_id == 4:
        return make_sand(seed)
    if type_id == 5:
        return water(seed)
    if type_id == 6:
        return water(seed, deep=True)
    if type_id == 7:
        return make_tree(seed, forest=False)
    if type_id == 8:
        return make_rock(seed)
    if type_id == 9:
        return make_ruin_floor(seed)
    if type_id == 10:
        return make_ruin_wall(seed)
    if type_id == 11:
        return make_tree(seed, forest=True)
    raise ValueError(type_id)


def main():
    atlas = Image.new("RGBA", (COLS * CELL, ROWS * CELL), (0, 0, 0, 255))
    for type_id in range(TYPES):
        for variant in range(VARIANTS):
            index = type_id * VARIANTS + variant
            img = build_tile(type_id, variant).resize((CELL, CELL), Image.LANCZOS)
            x = (index % COLS) * CELL
            y = (index // COLS) * CELL
            atlas.paste(img, (x, y))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUT, optimize=True)

    # Contact sheet preview: 8 columns at 128 px, downscaled to 1024 wide.
    preview = Image.new("RGBA", (COLS * CELL, (TYPES * VARIANTS // COLS + 1) * CELL), (20, 20, 20, 255))
    preview.alpha_composite(atlas.crop((0, 0, COLS * CELL, ((TYPES * VARIANTS + COLS - 1) // COLS) * CELL)))
    preview.resize((1024, preview.height * 1024 // preview.width), Image.LANCZOS).save(PREVIEW)

    print(f"wrote {OUT} ({OUT.stat().st_size / 1024:.0f} KB)")
    print(f"wrote {PREVIEW}")


if __name__ == "__main__":
    main()
