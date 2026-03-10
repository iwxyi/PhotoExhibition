#!/usr/bin/env python3
"""
根据参考文件夹的结构，对原图文件夹中的图片进行重新排列。

用法：
    python sort_images_by_folder.py <原图文件夹> <参考文件夹> <输出文件夹>

参数：
    原图文件夹：   包含原始图片的文件夹
    参考文件夹：   包含相同图片但按不同子文件夹组织的文件夹
    输出文件夹：   重新组织后的图片目标文件夹

脚本功能：
1. 扫描原图文件夹中的所有图片
2. 在参考文件夹中查找每个图片的路径（通过匹配文件名）
3. 在输出文件夹中复制原图，保持与参考文件夹相同的目录结构
4. 优先级：精确匹配（文件名+后缀都相同），然后使用不同后缀作为后备
"""

import argparse
import os
import shutil
from pathlib import Path
from typing import Optional


def get_image_files(folder: Path) -> dict[str, list[Path]]:
    """获取文件夹中所有图片文件，按文件名（不含扩展名）建立索引。"""
    images: dict[str, list[Path]] = {}
    image_extensions = {'.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.tiff', '.tif'}

    for file_path in folder.rglob('*'):
        if file_path.is_file() and file_path.suffix.lower() in image_extensions:
            stem = file_path.stem
            if stem not in images:
                images[stem] = []
            images[stem].append(file_path)

    return images


def find_matching_file(
    stem: str,
    source_images: dict[str, list[Path]],
    reference_path: Path,
    source_folder: Path
) -> Optional[Path]:
    """根据参考路径在源文件夹中查找匹配的文件。"""
    ref_ext = reference_path.suffix.lower()

    # 优先级1：精确匹配（文件名+扩展名都相同）
    if stem in source_images:
        for img_path in source_images[stem]:
            if img_path.suffix.lower() == ref_ext:
                return img_path

    # 优先级2：文件名相同，扩展名不同
    if stem in source_images:
        return source_images[stem][0]

    return None


def build_relative_path(
    filename: str,
    reference_folder: Path,
    source_folder: Path
) -> Optional[Path]:
    """在参考文件夹中查找文件名对应的相对路径。"""
    # 在参考文件夹中搜索文件
    for ref_path in reference_folder.rglob('*'):
        if ref_path.is_file() and ref_path.name == filename:
            # 获取相对于参考文件夹的路径
            rel_path = ref_path.relative_to(reference_folder)
            # 返回父目录（不含文件名本身）
            return rel_path.parent

    return None


def sort_images(source_folder: Path, reference_folder: Path, output_folder: Path) -> None:
    """主函数：对图片进行排序。"""
    print(f"原图文件夹: {source_folder}")
    print(f"参考文件夹: {reference_folder}")
    print(f"输出文件夹: {output_folder}")
    print()

    # 从原图文件夹获取所有图片
    print("正在扫描原图文件夹...")
    source_images = get_image_files(source_folder)
    print(f"在原图文件夹中找到 {len(source_images)} 个唯一文件名")

    # 如果输出文件夹不存在则创建
    output_folder.mkdir(parents=True, exist_ok=True)

    # 统计信息
    matched = 0
    not_found = 0
    skipped_duplicates = 0
    used_fuzzy_match = 0

    # 处理源文件夹中的每个唯一文件名
    for stem, paths in source_images.items():
        if len(paths) > 1:
            print(f"  警告: '{stem}' 在源文件夹中有 {len(paths)} 个文件，使用第一个")
            skipped_duplicates += len(paths) - 1

        source_path = paths[0]

        # 从参考文件夹查找相对路径结构
        relative_path = build_relative_path(source_path.name, reference_folder, source_folder)

        if relative_path is None:
            print(f"  在参考文件夹中未找到: {source_path.name}")
            not_found += 1
            continue

        # 构建输出路径
        dest_dir = output_folder / relative_path
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest_path = dest_dir / source_path.name

        # 处理文件名冲突
        counter = 1
        original_name = source_path.stem
        original_ext = source_path.suffix
        while dest_path.exists():
            new_name = f"{original_name}_{counter}{original_ext}"
            dest_path = dest_dir / new_name
            counter += 1

        # 检查是否使用了模糊匹配（不同扩展名）
        ref_path_candidates = list(reference_folder.rglob(source_path.name))
        if not ref_path_candidates:
            # 尝试查找任意扩展名
            for ext in ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp']:
                candidates = list(reference_folder.rglob(f"{stem}{ext}"))
                if candidates:
                    if source_path.suffix.lower() != ext:
                        used_fuzzy_match += 1
                    break

        # 复制文件到目标位置
        shutil.copy2(source_path, dest_path)
        matched += 1
        print(f"  已复制: {source_path.name} -> {relative_path / source_path.name}")

    print()
    print("=" * 50)
    print("统计:")
    print(f"  成功匹配: {matched}")
    print(f"  在参考文件夹中未找到: {not_found}")
    print(f"  跳过重复文件: {skipped_duplicates}")
    print(f"  使用模糊匹配（不同扩展名）: {used_fuzzy_match}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description='根据参考文件夹的结构，对原图文件夹中的图片进行重新排列。'
    )
    parser.add_argument('source_folder', type=Path, help='包含原始图片的文件夹')
    parser.add_argument('reference_folder', type=Path, help='作为参考的文件夹结构')
    parser.add_argument('output_folder', type=Path, help='排序后图片的目标文件夹')

    args = parser.parse_args()

    # 验证文件夹
    if not args.source_folder.exists():
        print(f"错误: 原图文件夹不存在: {args.source_folder}")
        return

    if not args.reference_folder.exists():
        print(f"错误: 参考文件夹不存在: {args.reference_folder}")
        return

    if not args.source_folder.is_dir():
        print(f"错误: 原图路径不是文件夹: {args.source_folder}")
        return

    if not args.reference_folder.is_dir():
        print(f"错误: 参考路径不是文件夹: {args.reference_folder}")
        return

    sort_images(args.source_folder, args.reference_folder, args.output_folder)


if __name__ == '__main__':
    main()
