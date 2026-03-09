#!/usr/bin/env python3
"""
图片处理工具 - 支持EXIF复制和压缩

用法:
    python3 exif_copyer.py "原图文件夹" "生成文件夹" "输出文件夹" [选项]

参数:
    src_folder: 原图文件夹，包含EXIF数据来源
    gen_folder: 生成文件夹，AI生成的图片（图片数据来源）
    out_folder: 输出文件夹，处理后的图片

选项:
    -r          递归处理子文件夹
    -nr         不递归处理子文件夹（默认）
    -f          强制覆盖输出文件夹
    -nf         不覆盖输出文件夹（默认）
    -e          复制EXIF数据（默认）
    -ne         不复制EXIF数据（默认）
    -q QUALITY  JPEG输出质量，1-100（默认85）
    -v          显示详细输出
    -so         保存原始生成图片到输出文件夹，命名为 原文件名_ori.后缀（用于对比压缩效果）

说明:
    查找原图和生成图中相同文件名的图片（允许不同扩展名），
    将原图的EXIF数据复制到生成图（除尺寸外），保存到输出文件夹并保留文件夹结构。
    非JPEG图片会自动转换为JPEG格式。
"""

import os
import sys
import shutil
import argparse
from pathlib import Path
from typing import Optional, Dict, Set

try:
    import piexif
except ImportError:
    print("错误: 需要安装 piexif 库。安装命令: pip install piexif")
    sys.exit(1)

SUPPORTED_EXTENSIONS = {'.jpg', '.jpeg', '.tif', '.tiff', '.png', '.webp'}


def get_image_files(folder: Path, recursive: bool = False) -> Dict[str, Set[Path]]:
    """获取文件夹中所有图片文件，按小写文件名索引。"""
    files_by_stem: Dict[str, Set[Path]] = {}

    if not folder.exists():
        return files_by_stem

    if recursive:
        iterator = folder.rglob('*')
    else:
        iterator = folder.glob('*')

    for file_path in iterator:
        if file_path.is_file() and file_path.suffix.lower() in SUPPORTED_EXTENSIONS:
            stem = file_path.stem.lower()
            if stem not in files_by_stem:
                files_by_stem[stem] = set()
            files_by_stem[stem].add(file_path)

    return files_by_stem


def get_relative_path(file_path: Path, base_path: Path) -> Path:
    """获取相对于基础路径的路径。"""
    try:
        return file_path.relative_to(base_path)
    except ValueError:
        return Path(file_path.name)


def process_image(src_path: Path, dst_path: Path, copy_exif: bool, quality: int) -> bool:
    """处理图片：转换格式、复制EXIF、压缩。"""
    from PIL import Image

    dst_ext = dst_path.suffix.lower()
    temp_jpeg = dst_path.with_suffix('.jpg')

    # 如果目标不是JPEG，先转换为JPEG
    if dst_ext not in {'.jpg', '.jpeg'}:
        try:
            with Image.open(dst_path) as img:
                # 处理透明通道
                if img.mode in ('RGBA', 'P'):
                    background = Image.new('RGB', img.size, (255, 255, 255))
                    if img.mode == 'P':
                        img = img.convert('RGBA')
                    background.paste(img, mask=img.split()[3] if img.mode == 'RGBA' else None)
                    img = background
                elif img.mode != 'RGB':
                    img = img.convert('RGB')

                # 保存为JPEG（使用最终质量）
                img.save(temp_jpeg, 'JPEG', quality=quality)

            # 删除原始非JPEG文件
            if dst_path.exists():
                dst_path.unlink()

            dst_path = temp_jpeg
        except Exception as e:
            print(f"警告: 转换 {dst_path} 为JPEG失败: {e}")
            return False
    else:
        # 已经是JPEG，重新压缩
        try:
            with Image.open(dst_path) as img:
                if img.mode in ('RGBA', 'P'):
                    background = Image.new('RGB', img.size, (255, 255, 255))
                    if img.mode == 'P':
                        img = img.convert('RGBA')
                    background.paste(img, mask=img.split()[3] if img.mode == 'RGBA' else None)
                    img = background
                elif img.mode != 'RGB':
                    img = img.convert('RGB')
                img.save(dst_path, 'JPEG', quality=quality)
        except Exception as e:
            print(f"警告: 压缩 {dst_path} 失败: {e}")
            return False

    # 复制EXIF数据
    if copy_exif:
        if dst_path.suffix.lower() in {'.jpg', '.jpeg'}:
            try:
                exif_dict = piexif.load(str(src_path))
            except Exception:
                exif_dict = {'0th': {}, 'Exif': {}, 'GPS': {}, '1st': {}, 'Interop': {}, 'thumbnail': None}

            # 移除尺寸相关标签
            dimension_tags_bytes = {
                b'PixelXDimension': True,
                b'PixelYDimension': True,
                b'ImageWidth': True,
                b'ImageLength': True,
            }

            for ifd_key in ['0th', 'Exif', '1st']:
                if ifd_key in exif_dict:
                    tags_to_remove = [
                        tag for tag in exif_dict[ifd_key].keys()
                        if tag in dimension_tags_bytes or piexif.TAGS[ifd_key].get(tag, {}).get('name', '') in dimension_tags_bytes
                    ]
                    for tag in tags_to_remove:
                        del exif_dict[ifd_key][tag]

            try:
                exif_bytes = piexif.dump(exif_dict)
                piexif.insert(exif_bytes, str(dst_path))
            except Exception:
                pass

    return True


def find_matching_file(target_stem: str, source_files: Dict[str, Set[Path]]) -> Optional[Path]:
    """在源文件夹中查找匹配的文件（不区分大小写）。"""
    target_lower = target_stem.lower()

    if target_lower in source_files:
        paths = source_files[target_lower]
        for p in paths:
            if p.stem.lower() == target_stem:
                return p
        return next(iter(paths))

    for stem, paths in source_files.items():
        if stem.lower() == target_lower:
            return next(iter(paths))

    return None


def process_folders(src_folder: Path, gen_folder: Path, out_folder: Path,
                   recursive: bool = False, force: bool = False,
                   copy_exif: bool = True, quality: int = 85,
                   verbose: bool = False, save_original: bool = False) -> None:
    """处理原图和生成文件夹中的所有图片。"""

    if not src_folder.exists():
        print(f"错误: 原图文件夹不存在: {src_folder}")
        sys.exit(1)

    if not gen_folder.exists():
        print(f"错误: 生成文件夹不存在: {gen_folder}")
        sys.exit(1)

    # 检查输出文件夹是否非空
    if out_folder.exists() and not force:
        if any(out_folder.iterdir()):
            print(f"错误: 输出文件夹非空，请使用 -f 强制覆盖")
            sys.exit(1)

    if force and out_folder.exists():
        shutil.rmtree(out_folder)

    out_folder.mkdir(parents=True, exist_ok=True)

    files_src = get_image_files(src_folder, recursive)
    files_gen = get_image_files(gen_folder, recursive)

    if verbose:
        print(f"原图文件夹中找到 {len(files_src)} 个唯一文件名")
        print(f"生成文件夹中找到 {len(files_gen)} 个唯一文件名")

    processed = 0
    skipped = 0
    errors = 0

    for stem_src, paths_src in files_src.items():
        for path_src in paths_src:
            rel_path = get_relative_path(path_src, src_folder)

            matching_gen = find_matching_file(stem_src, files_gen)

            if not matching_gen:
                skipped += 1
                if verbose:
                    print(f"跳过（无匹配）: {rel_path}")
                continue

            # 输出路径始终使用.jpg扩展名
            rel_dir = rel_path.parent
            output_stem = rel_path.stem
            output_path = out_folder / rel_dir / (str(output_stem) + '.jpg')
            output_path.parent.mkdir(parents=True, exist_ok=True)

            # 先从生成图复制到输出文件夹
            shutil.copy2(matching_gen, output_path)

            # 如果需要保存原始图片（压缩前），保存为 原文件名_ori.jpg
            if save_original:
                ori_path = out_folder / rel_dir / (str(output_stem) + '_ori.jpg')

                # 如果原始文件不是JPEG，需要转换
                if matching_gen.suffix.lower() not in {'.jpg', '.jpeg'}:
                    from PIL import Image
                    try:
                        with Image.open(matching_gen) as img:
                            if img.mode in ('RGBA', 'P'):
                                background = Image.new('RGB', img.size, (255, 255, 255))
                                if img.mode == 'P':
                                    img = img.convert('RGBA')
                                background.paste(img, mask=img.split()[3] if img.mode == 'RGBA' else None)
                                img = background
                            elif img.mode != 'RGB':
                                img = img.convert('RGB')
                            img.save(ori_path, 'JPEG', quality=100)
                    except Exception as e:
                        print(f"警告: 转换原始图片失败: {e}")
                        # 回退：直接复制
                        shutil.copy2(matching_gen, ori_path)
                else:
                    shutil.copy2(matching_gen, ori_path)

            # 然后处理图片（转换、压缩、复制EXIF）
            try:
                success = process_image(path_src, output_path, copy_exif, quality)
                if success:
                    processed += 1
                    if verbose:
                        exif_status = "复制EXIF" if copy_exif else "不复制EXIF"
                        original_status = " [保留原始]" if save_original else ""
                        print(f"已处理: {rel_path} -> {output_path} ({exif_status}, 质量{quality}){original_status}")
                else:
                    errors += 1
                    if verbose:
                        print(f"处理失败: {rel_path}")
            except Exception as e:
                errors += 1
                print(f"处理错误 {rel_path}: {e}")

    print(f"\n处理完成:")
    print(f"  已处理: {processed}")
    print(f"  跳过（无匹配）: {skipped}")
    print(f"  错误: {errors}")
    print(f"  输出文件夹: {out_folder}")


def resolve_path(base_path: Path, relative_or_absolute: str) -> Path:
    """解析相对或绝对路径。"""
    path = Path(relative_or_absolute)
    if path.is_absolute():
        return path
    return (base_path / path).resolve()


def main():
    parser = argparse.ArgumentParser(
        description='复制EXIF元数据并压缩图片'
    )
    parser.add_argument('src_folder', help='原图文件夹，包含EXIF数据')
    parser.add_argument('gen_folder', help='生成文件夹（可相对于原图文件夹的父目录）')
    parser.add_argument('out_folder', help='输出文件夹（可相对于原图文件夹的父目录）')
    parser.add_argument('-r', dest='recursive', const=True, nargs='?', help='递归处理子文件夹')
    parser.add_argument('-nr', dest='recursive', action='store_false', help='不递归处理子文件夹')
    parser.add_argument('-f', dest='force', const=True, nargs='?', help='强制覆盖输出文件夹')
    parser.add_argument('-nf', dest='force', action='store_false', help='不覆盖输出文件夹')
    parser.add_argument('-e', dest='copy_exif', const=True, nargs='?', help='复制EXIF数据（默认）')
    parser.add_argument('-ne', dest='copy_exif', action='store_false', help='不复制EXIF数据')
    parser.add_argument('-q', '--quality', type=int, default=85, help='JPEG输出质量1-100（默认85）')
    parser.add_argument('-v', '--verbose', action='store_true', help='显示详细输出')
    parser.add_argument('-so', '--save-original', action='store_true', help='保存原始生成图片到输出文件夹，命名为 原文件名_ori.jpg')

    args = parser.parse_args()

    # 参数处理
    recursive = args.recursive if args.recursive is not None else False
    force = args.force if args.force is not None else False
    copy_exif = args.copy_exif if args.copy_exif is not None else True

    # 验证质量参数
    if not 1 <= args.quality <= 100:
        print("错误: 质量必须在1-100之间")
        sys.exit(1)

    src_folder = Path(args.src_folder).resolve()

    if Path(args.gen_folder).is_absolute():
        gen_folder = Path(args.gen_folder)
    else:
        gen_folder = src_folder.parent / args.gen_folder

    if Path(args.out_folder).is_absolute():
        out_folder = Path(args.out_folder)
    else:
        out_folder = src_folder.parent / args.out_folder

    print(f"原图文件夹: {src_folder}")
    print(f"生成文件夹: {gen_folder}")
    print(f"输出文件夹: {out_folder}")
    print(f"递归: {recursive}")
    print(f"强制覆盖: {force}")
    print(f"复制EXIF: {copy_exif}")
    print(f"压缩质量: {args.quality}")
    print(f"保留原始: {args.save_original}")
    print()

    process_folders(src_folder, gen_folder, out_folder,
                   recursive=recursive,
                   force=force,
                   copy_exif=copy_exif,
                   quality=args.quality,
                   verbose=args.verbose,
                   save_original=args.save_original)


if __name__ == '__main__':
    main()
