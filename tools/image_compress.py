#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
使用 ImageMagick 的图像压缩工具

此工具递归压缩文件夹中的图像，同时保持目录结构。
输出保存到与输入文件夹同级的 'slim' 文件夹中。

功能特性：
- 使用 ImageMagick 的高质量压缩设置
- 保留目录结构，包括隐藏文件夹
- 跳过已压缩的文件，避免重复处理
- 显示相对路径和压缩比率
- 支持 JPEG、PNG、GIF、BMP、TIFF、WebP 格式
- 可选保留原图副本用于对比检查

使用方法：
    python image_compress.py /path/to/photos          # 压缩图像
    python image_compress.py /path/to/photos --dry-run # 预览将要压缩的文件
    python image_compress.py /path/to/photos --output-dir /custom/output  # 自定义输出目录
    python image_compress.py /path/to/photos --keep-originals  # 保留原图副本用于对比

系统要求：
- Python 3.6+
- ImageMagick (macOS 下使用: brew install imagemagick)
"""

import os
import sys
import subprocess
import argparse
from pathlib import Path
from typing import Tuple, Optional


def get_file_size_mb(file_path: Path) -> float:
    """获取文件大小（MB）"""
    return file_path.stat().st_size / (1024 * 1024)


def get_relative_path(input_root: Path, file_path: Path) -> str:
    """获取相对于输入根目录的路径"""
    return str(file_path.relative_to(input_root))


def should_compress(input_file: Path, output_file: Path) -> bool:
    """根据文件存在性和修改时间检查是否需要压缩"""
    if not output_file.exists():
        return True

    # 如果输出文件存在但比输入文件旧，则重新压缩
    return output_file.stat().st_mtime < input_file.stat().st_mtime


def compress_image(input_file: Path, output_file: Path) -> Tuple[bool, str]:
    """
    使用 ImageMagick 的高质量设置压缩图像

    返回值: (成功标志, 错误信息)
    """
    try:
        # 如果输出目录不存在则创建
        output_file.parent.mkdir(parents=True, exist_ok=True)

        # ImageMagick magick convert 命令，使用高质量压缩设置
        cmd = [
            'magick',
            'convert',
            str(input_file),
            # 保留所有元数据 - 不使用 -strip 参数
            '-interlace', 'Plane',  # 渐进式 JPEG - 渐进加载，无质量损失
            '-gaussian-blur', '0.05',  # 极轻微模糊（几乎看不见）- 有助于压缩
            '-quality', '100',  # 高质量设置（对大多数照片近乎无损）
            '-define', 'jpeg:dct-method=float',  # 更好的压缩算法
            '-sampling-factor', '4:2:0',  # 标准色度子采样
            # 移除文件大小限制，优先保证质量而非严格的文件大小
            str(output_file)
        ]

        # 运行命令
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)

        if result.returncode == 0:
            return True, ""
        else:
            return False, f"ImageMagick 错误: {result.stderr}"

    except subprocess.TimeoutExpired:
        return False, "压缩超时"
    except Exception as e:
        return False, f"意外错误: {str(e)}"


def process_directory(input_dir: Path, output_dir: Path, dry_run: bool = False, keep_originals: bool = False) -> Tuple[int, int, float, float]:
    """
    递归处理目录中的所有图像

    返回值: (处理数量, 错误数量, 总输入大小, 总输出大小)
    """
    processed_count = 0
    error_count = 0
    total_input_size = 0.0
    total_output_size = 0.0

    # 支持的图像文件扩展名
    image_extensions = {'.jpg', '.jpeg', '.png', '.gif', '.bmp', '.tiff', '.tif', '.webp'}

    # 递归遍历所有文件
    for input_file in input_dir.rglob('*'):
        if not input_file.is_file():
            continue

        # 检查是否为图像文件
        if input_file.suffix.lower() not in image_extensions:
            continue

        # 计算相对路径和输出路径
        relative_path = input_file.relative_to(input_dir)
        output_file = output_dir / relative_path

        input_size = get_file_size_mb(input_file)
        total_input_size += input_size

        # 检查是否需要压缩
        if not should_compress(input_file, output_file):
            print(f"跳过: {relative_path} (已压缩)")
            continue

        if dry_run:
            print(f"将压缩: {relative_path} ({input_size:.2f} MB)")
            continue

        print(f"压缩中: {relative_path} ({input_size:.2f} MB)", end=' -> ')

        # 压缩图像
        success, error_msg = compress_image(input_file, output_file)

        if success:
            output_size = get_file_size_mb(output_file)
            total_output_size += output_size

            # 检查压缩效果：如果增大或压缩后超过90%原体积，则使用原文件
            compression_ratio = output_size / input_size
            if output_size >= input_size or compression_ratio > 0.9:
                # 压缩效果不佳，直接复制原文件
                compressed_size = output_size  # 保存压缩后的大小用于显示
                import shutil
                shutil.copy2(input_file, output_file)  # 保留元数据
                output_size = input_size  # 更新输出大小为原大小
                total_output_size = total_output_size - get_file_size_mb(output_file) + output_size  # 修正总数
                print(f"{compressed_size:.2f} MB (保留原文件)")
            else:
                # 压缩效果良好
                savings = ((input_size - output_size) / input_size) * 100
                print(f"{output_size:.2f} MB ({savings:.1f}% 节省)")

            # 如果启用保留原图功能，复制原文件用于对比
            if keep_originals and not dry_run:
                original_copy_path = output_file.parent / f"{output_file.stem}_原图{output_file.suffix}"
                import shutil
                shutil.copy2(input_file, original_copy_path)  # 保留元数据

            processed_count += 1
        else:
            print(f"错误: {error_msg}")
            error_count += 1

    return processed_count, error_count, total_input_size, total_output_size


def main():
    parser = argparse.ArgumentParser(
        description='使用 ImageMagick 压缩图像，同时保留目录结构',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
使用示例:
  python image_compress.py /path/to/photos
  python image_compress.py /path/to/photos --dry-run
  python image_compress.py /path/to/photos --output-dir /custom/output
        """
    )

    parser.add_argument('input_dir', help='包含图像的输入目录')
    parser.add_argument('--output-dir', help='自定义输出目录（默认：<输入目录>_slim）')
    parser.add_argument('--dry-run', action='store_true', help='显示将要压缩的文件但不实际执行')
    parser.add_argument('--keep-originals', action='store_true', help='保留原图副本用于对比检查（文件名添加_原图后缀）')

    args = parser.parse_args()

    # 验证输入目录
    input_dir = Path(args.input_dir).resolve()
    if not input_dir.exists() or not input_dir.is_dir():
        print(f"错误：输入目录 '{input_dir}' 不存在或不是目录")
        sys.exit(1)

    # 确定输出目录
    if args.output_dir:
        output_dir = Path(args.output_dir).resolve()
    else:
        output_dir = input_dir.parent / "slim"

    print(f"输入目录: {input_dir}")
    print(f"输出目录: {output_dir}")
    print(f"预览模式: {args.dry_run}")
    print("-" * 60)

    # 检查 ImageMagick 是否可用
    try:
        result = subprocess.run(['magick', '-version'], capture_output=True, text=True)
        if result.returncode != 0:
            print("错误：找不到 ImageMagick 'magick' 命令。请安装 ImageMagick。")
            sys.exit(1)
    except FileNotFoundError:
        print("错误：找不到 ImageMagick 'magick' 命令。请安装 ImageMagick。")
        sys.exit(1)

    # 处理目录
    processed, errors, input_size, output_size = process_directory(input_dir, output_dir, args.dry_run, args.keep_originals)

    if not args.dry_run:
        print("-" * 60)
        print("统计信息:")
        print(f"已处理图像: {processed}")
        print(f"错误数量: {errors}")
        print(f"总输入大小: {input_size:.2f} MB")
        print(f"总输出大小: {output_size:.2f} MB")
        if input_size > 0:
            total_savings = ((input_size - output_size) / input_size) * 100
            print(f"总节省: {total_savings:.2f}%")


if __name__ == '__main__':
    main()
