#!/usr/bin/env python3
"""
Generate test data for PhotoExhibition:
- 9 categories, 50 albums, 2-80 photos each
- Creates actual PNG image files with varied colors/gradients
"""

import os
import struct
import zlib
import random
import hashlib
import math

BASE_PATH = "/workspace/data/photos"

CATEGORIES = {
    "人像": [
        ("2024.01.15 新年人像写真", 25),
        ("2024.02.14 情人节街拍", 18),
        ("2024.03.20 春日和服写真", 35),
        ("2024.05.01 毕业照合集", 60),
        ("2024.06.18 夏日泳池派对", 40),
        ("2024.08.10 复古胶片风", 12),
        ("2024.10.05 秋季户外写真", 30),
    ],
    "风景": [
        ("2024.01.01 元旦日出", 8),
        ("2024.02.20 雪山之巅", 22),
        ("2024.03.25 樱花盛开", 45),
        ("2024.04.15 西湖春景", 15),
        ("2024.07.20 海边日落", 28),
        ("2024.09.10 秋叶红枫", 50),
        ("2024.11.05 层林尽染", 10),
    ],
    "美食": [
        ("2024.01.28 年夜饭", 20),
        ("2024.03.08 下午茶时光", 8),
        ("2024.04.20 日料探店", 35),
        ("2024.06.30 夏日甜品", 15),
        ("2024.08.15 烧烤之夜", 12),
        ("2024.10.01 国庆家宴", 25),
    ],
    "建筑": [
        ("2024.02.10 古镇老街", 30),
        ("2024.04.05 现代都市天际线", 18),
        ("2024.05.18 欧式教堂", 22),
        ("2024.07.12 日式庭院", 15),
        ("2024.09.20 废墟探险", 40),
        ("2024.11.15 摩天大楼夜景", 10),
    ],
    "街拍": [
        ("2024.01.20 胡同漫步", 20),
        ("2024.03.15 雨中街巷", 35),
        ("2024.06.08 夜市人间", 45),
        ("2024.08.25 老城区即景", 28),
        ("2024.10.20 秋日步行街", 15),
    ],
    "动物": [
        ("2024.02.05 动物园之行", 55),
        ("2024.04.10 猫咖日记", 18),
        ("2024.06.20 海洋馆探秘", 30),
        ("2024.09.05 鸟类摄影", 12),
        ("2024.11.10 宠物写真", 8),
    ],
    "花卉": [
        ("2024.03.01 梅花三弄", 15),
        ("2024.04.08 郁金香花海", 40),
        ("2024.05.25 玫瑰花园", 22),
        ("2024.07.15 荷塘月色", 30),
        ("2024.10.10 菊花展", 18),
    ],
    "旅行": [
        ("2024.01.25 丽江古城", 80),
        ("2024.04.01 大理洱海", 55),
        ("2024.06.15 青海湖环线", 65),
        ("2024.08.20 敦煌莫高窟", 35),
        ("2024.11.01 香港维多利亚港", 20),
    ],
    "活动": [
        ("2024.03.12 马拉松比赛", 70),
        ("2024.05.04 音乐节现场", 50),
        ("2024.06.01 儿童节联欢", 25),
        ("2024.09.15 中秋灯会", 35),
        ("2024.12.25 圣诞市集", 3),
    ],
}

# Color palettes per category for realistic-looking gradients
CATEGORY_PALETTES = {
    "人像": [(245, 218, 200), (210, 170, 150), (180, 140, 120), (255, 230, 210)],
    "风景": [(100, 180, 230), (50, 150, 80), (200, 220, 100), (80, 130, 200)],
    "美食": [(220, 160, 80), (200, 100, 60), (255, 200, 100), (180, 80, 50)],
    "建筑": [(150, 150, 160), (100, 100, 110), (180, 170, 160), (120, 130, 140)],
    "街拍": [(180, 180, 170), (120, 110, 100), (200, 190, 180), (80, 80, 90)],
    "动物": [(140, 180, 100), (180, 160, 100), (100, 160, 120), (200, 180, 140)],
    "花卉": [(230, 120, 150), (200, 100, 180), (255, 180, 200), (180, 230, 140)],
    "旅行": [(100, 190, 220), (220, 180, 120), (150, 200, 180), (200, 150, 100)],
    "活动": [(220, 200, 100), (200, 120, 120), (100, 180, 200), (230, 180, 150)],
}

# Realistic image sizes (width, height)
IMAGE_SIZES = [
    (640, 480),   # 4:3 landscape
    (480, 640),   # 3:4 portrait
    (800, 600),   # 4:3 larger
    (600, 800),   # 3:4 larger portrait
    (720, 480),   # 3:2 landscape
    (480, 720),   # 2:3 portrait
    (640, 360),   # 16:9 wide
    (400, 400),   # 1:1 square
]


def create_png(filename, width, height, base_color, variation_seed):
    """Create a PNG file with gradient/pattern based on seed."""
    rng = random.Random(variation_seed)

    r0, g0, b0 = base_color
    dr = rng.randint(-40, 40)
    dg = rng.randint(-40, 40)
    db = rng.randint(-40, 40)

    pattern = rng.choice(["gradient_h", "gradient_v", "gradient_d", "radial", "blocks"])

    def chunk(chunk_type, data):
        c = chunk_type + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)

    header = b'\x89PNG\r\n\x1a\n'
    ihdr = chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))

    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)  # filter byte: None
        for x in range(width):
            if pattern == "gradient_h":
                t = x / max(width - 1, 1)
            elif pattern == "gradient_v":
                t = y / max(height - 1, 1)
            elif pattern == "gradient_d":
                t = (x + y) / max(width + height - 2, 1)
            elif pattern == "radial":
                cx, cy = width / 2, height / 2
                dist = math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
                max_dist = math.sqrt(cx ** 2 + cy ** 2)
                t = dist / max(max_dist, 1)
            else:  # blocks
                bx = (x * 4) // width
                by = (y * 4) // height
                t = ((bx + by) % 2) * 0.3 + 0.2

            r = max(0, min(255, int(r0 + dr * t)))
            g = max(0, min(255, int(g0 + dg * t)))
            b = max(0, min(255, int(b0 + db * t)))

            noise = rng.randint(-5, 5) if rng.random() < 0.3 else 0
            r = max(0, min(255, r + noise))
            g = max(0, min(255, g + noise))
            b = max(0, min(255, b + noise))

            raw_data.extend([r, g, b])

    compressed = zlib.compress(bytes(raw_data), 6)
    idat = chunk(b'IDAT', compressed)
    iend = chunk(b'IEND', b'')

    with open(filename, 'wb') as f:
        f.write(header + ihdr + idat + iend)


def main():
    total_albums = 0
    total_photos = 0

    # Clean existing test data (keep the Landscape/SampleAlbum if you want)
    # We'll just add new stuff

    for category, albums in CATEGORIES.items():
        palette = CATEGORY_PALETTES[category]
        cat_dir = os.path.join(BASE_PATH, category)
        os.makedirs(cat_dir, exist_ok=True)

        for album_name, photo_count in albums:
            album_dir = os.path.join(cat_dir, album_name)
            os.makedirs(album_dir, exist_ok=True)
            total_albums += 1

            for i in range(photo_count):
                seed = hash(f"{category}/{album_name}/{i}")
                rng = random.Random(seed)

                base_color = rng.choice(palette)
                w, h = rng.choice(IMAGE_SIZES)
                fname = f"IMG_{i + 1:04d}.png"
                fpath = os.path.join(album_dir, fname)

                if not os.path.exists(fpath):
                    create_png(fpath, w, h, base_color, seed)

                total_photos += 1

            print(f"  [{category}] {album_name}: {photo_count} photos")

    print(f"\nTotal: {total_albums} albums, {total_photos} photos")


if __name__ == "__main__":
    main()
