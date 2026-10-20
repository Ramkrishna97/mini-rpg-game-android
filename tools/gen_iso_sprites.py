"""Generate the original 32px directional player sprite sheet.

Eight rows are screen directions; columns are four idle, four walk and four
attack frames. Boss sprites are exported from Unity so rerunning this older
generator never overwrites the newer 16-column boss sheets.
"""
from __future__ import annotations

import os
import struct
import zlib

SIZE = 32
WIDTH = SIZE * 12
HEIGHT = SIZE * 8
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "sprites")
os.makedirs(OUT, exist_ok=True)


def rgba(value):
    if isinstance(value, str):
        value = value.lstrip("#")
        return tuple(bytes.fromhex(value)) + (255,)
    return value


class Canvas:
    def __init__(self):
        self.pixels = bytearray(WIDTH * HEIGHT * 4)
        self.ox = self.oy = 0

    def dot(self, x, y, color):
        x, y = int(x) + self.ox, int(y) + self.oy
        if 0 <= x < WIDTH and 0 <= y < HEIGHT:
            i = (y * WIDTH + x) * 4
            self.pixels[i:i + 4] = bytes(rgba(color))

    def rect(self, x1, y1, x2, y2, color):
        for y in range(int(y1), int(y2)):
            for x in range(int(x1), int(x2)):
                self.dot(x, y, color)

    def oval(self, x1, y1, x2, y2, color):
        cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
        rx, ry = max(1, (x2 - x1) / 2), max(1, (y2 - y1) / 2)
        for y in range(int(y1), int(y2)):
            for x in range(int(x1), int(x2)):
                if ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2 <= 1:
                    self.dot(x, y, color)

    def line(self, x1, y1, x2, y2, color, width=1):
        steps = int(max(abs(x2 - x1), abs(y2 - y1))) + 1
        for i in range(steps + 1):
            t = i / max(1, steps)
            self.rect(x1 + (x2-x1)*t, y1 + (y2-y1)*t,
                      x1 + (x2-x1)*t + width, y1 + (y2-y1)*t + width, color)


def draw_player(c, direction, state, frame):
    front = direction in (2, 3, 4)
    back = direction in (0, 1, 7)
    left = direction in (5, 6, 7)
    walk = state == 1
    attack = state == 2
    bob = (0, 1, 0, -1)[frame] if walk else (0, 0, -1, 0)[frame] if attack else 0
    step = (-2, 1, 2, -1)[frame] if walk else 0
    c.rect(10, 23 + step, 15, 29 + step, "362820")
    c.rect(17, 23 - step, 22, 29 - step, "362820")
    c.rect(9, 13 + bob, 23, 25 + bob, "203E37")
    c.rect(11, 14 + bob, 21, 24 + bob, "3D7755")
    c.rect(12, 17 + bob, 20, 23 + bob, "4A9569")
    c.rect(9, 19 + bob, 23, 22 + bob, "B8904C")
    c.rect(8, 14 + bob, 11, 23 + bob, "2B5745")
    c.rect(21, 14 + bob, 24, 23 + bob, "2B5745")
    c.oval(10, 4 + bob, 22, 17 + bob, "DDB989")
    if back:
        c.oval(9, 3 + bob, 23, 15 + bob, "3E2C27")
        c.rect(12, 5 + bob, 20, 14 + bob, "735142")
    else:
        c.rect(9, 4 + bob, 23, 9 + bob, "4B352B")
        c.rect(9, 8 + bob, 12, 13 + bob, "4B352B")
        c.rect(20, 8 + bob, 23, 13 + bob, "4B352B")
        if front:
            c.rect(13, 11 + bob, 15, 13 + bob, "241E1B")
            c.rect(18, 11 + bob, 20, 13 + bob, "241E1B")
        else:
            x = 12 if left else 19
            c.rect(x, 11 + bob, x + 2, 13 + bob, "241E1B")
    c.rect(15, 20 + bob, 17, 23 + bob, "E8B763")
    if attack:
        side = -1 if left else 1
        handle_x = 6 if left else 25
        c.line(handle_x, 18 + bob, handle_x + side * (frame - 1) * 3, 10 + bob, "7D5B35", 2)
        c.line(handle_x + side * (frame - 1) * 3, 10 + bob,
               handle_x + side * (frame - 1) * 5, 2 + bob, "ECF4E9", 3)
        c.rect(handle_x - 3, 16 + bob, handle_x + 3, 18 + bob, "D7A849")


BOSSES = {
    "gloomfang": ("6D748A", "A9B2BF", "31364E", "F3C976"),
    "stonewarden": ("6E737B", "B6B7B0", "3B4047", "92DAFF"),
    "mirewitch": ("44765C", "8FC89A", "263A39", "D6FF9B"),
    "ashtitan": ("A45236", "E29555", "49312F", "FFE070"),
    "hollowking": ("806C9C", "C8B9D6", "352D54", "FF6B83"),
}


def draw_boss(c, kind, direction, state, frame):
    body, light, dark, glow = BOSSES[kind]
    walk, attack = state == 1, state == 2
    bob = (0, 1, 0, -1)[frame] if walk else (0, -1, -2, 0)[frame] if attack else 0
    spread = 2 if attack else 0
    left = direction in (5, 6, 7)
    back = direction in (0, 1, 7)
    c.rect(7, 24 + bob, 14, 30 + bob, dark)
    c.rect(18, 24 + bob, 25, 30 + bob, dark)
    c.rect(7, 12 + bob, 25, 25 + bob, dark)
    c.rect(9, 11 + bob, 23, 23 + bob, body)
    c.rect(11, 13 + bob, 20, 20 + bob, light)
    c.rect(4-spread, 13 + bob, 9-spread, 24 + bob, body)
    c.rect(23+spread, 13 + bob, 28+spread, 24 + bob, body)
    c.oval(8, 2 + bob, 24, 16 + bob, dark)
    c.oval(10, 4 + bob, 22, 15 + bob, body)
    if kind == "gloomfang":
        c.line(10, 7 + bob, 5, 1 + bob, light, 3)
        c.line(21, 7 + bob, 26, 1 + bob, light, 3)
        c.rect(11, 13 + bob, 21, 16 + bob, light)
    elif kind == "stonewarden":
        c.rect(8, 4 + bob, 24, 8 + bob, light)
        c.line(14, 7 + bob, 11, 18 + bob, glow, 1)
        c.line(16, 19 + bob, 22, 22 + bob, glow, 1)
    elif kind == "mirewitch":
        c.line(16, 0 + bob, 5, 9 + bob, dark, 5)
        c.line(16, 0 + bob, 27, 9 + bob, dark, 5)
        c.line(7, 9 + bob, 25, 9 + bob, light, 2)
        c.rect(13, 22 + bob, 19, 29 + bob, body)
    elif kind == "ashtitan":
        c.rect(6, 2 + bob, 26, 7 + bob, light)
        c.rect(9, 15 + bob, 23, 18 + bob, "FFBE69")
        c.rect(13, 11 + bob, 19, 15 + bob, glow)
    else:
        c.rect(11, 1 + bob, 21, 5 + bob, "E3D6A9")
        c.rect(8, 2 + bob, 11, 8 + bob, "E3D6A9")
        c.rect(21, 2 + bob, 24, 8 + bob, "E3D6A9")
        c.rect(13, 20 + bob, 19, 23 + bob, "DED6CB")
    if not back:
        c.rect(12 if not left else 10, 9 + bob, 15 if not left else 13, 12 + bob, glow)
        c.rect(18 if not left else 16, 9 + bob, 21 if not left else 19, 12 + bob, glow)
    if attack:
        c.line(29 if not left else 3, 19 + bob, 30 if not left else 2, 4 + bob, glow, 3)


def save_png(name, pixels):
    raw = b"".join(b"\x00" + pixels[y*WIDTH*4:(y+1)*WIDTH*4] for y in range(HEIGHT))
    def chunk(tag, payload):
        return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", zlib.crc32(tag + payload) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(os.path.join(OUT, name + ".png"), "wb") as file:
        file.write(data)


for name in ("player",):
    canvas = Canvas()
    for direction in range(8):
        for state in range(3):
            for frame in range(4):
                canvas.ox = (state * 4 + frame) * SIZE
                canvas.oy = direction * SIZE
                if name == "player":
                    draw_player(canvas, direction, state, frame)
                else:
                    draw_boss(canvas, name, direction, state, frame)
    save_png(name, canvas.pixels)
