#!/usr/bin/env python3
"""Fast generator for remaining test photos - uses small solid-color PNGs."""

import os
import struct
import zlib
import random

BASE_PATH = "/workspace/data/photos"

MISSING = [
    ("旅行", "2024.08.20 敦煌莫高窟", 35, 6),
    ("旅行", "2024.11.01 香港维多利亚港", 20, 0),
    ("活动", "2024.03.12 马拉松比赛", 70, 0),
    ("活动", "2024.05.04 音乐节现场", 50, 0),
    ("活动", "2024.06.01 儿童节联欢", 25, 0),
    ("活动", "2024.09.15 中秋灯会", 35, 0),
    ("活动", "2024.12.25 圣诞市集", 3, 0),
]

PALETTES = {
    "旅行": [(100, 190, 220), (220, 180, 120), (150, 200, 180), (200, 150, 100)],
    "活动": [(220, 200, 100), (200, 120, 120), (100, 180, 200), (230, 180, 150)],
}

SIZES = [(200, 150), (150, 200), (200, 200), (250, 180), (180, 250)]


def make_fast_png(filename, w, h, r, g, b):
    """Create a simple solid-color PNG very fast."""
    def chunk(ctype, data):
        c = ctype + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)

    row = b'\x00' + bytes([r, g, b]) * w
    raw = row * h
    compressed = zlib.compress(raw, 1)

    with open(filename, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)))
        f.write(chunk(b'IDAT', compressed))
        f.write(chunk(b'IEND', b''))


count = 0
for cat, album, total, existing in MISSING:
    d = os.path.join(BASE_PATH, cat, album)
    os.makedirs(d, exist_ok=True)
    palette = PALETTES[cat]
    rng = random.Random(hash(f"{cat}/{album}"))

    for i in range(existing + 1, total + 1):
        r, g, b = rng.choice(palette)
        r = max(0, min(255, r + rng.randint(-30, 30)))
        g = max(0, min(255, g + rng.randint(-30, 30)))
        b = max(0, min(255, b + rng.randint(-30, 30)))
        w, h = rng.choice(SIZES)
        fname = os.path.join(d, f"IMG_{i:04d}.png")
        if not os.path.exists(fname):
            make_fast_png(fname, w, h, r, g, b)
        count += 1

    print(f"  [{cat}] {album}: generated {total - existing} photos")

print(f"\nDone: {count} photos generated")
