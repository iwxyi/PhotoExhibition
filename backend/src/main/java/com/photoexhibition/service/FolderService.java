package com.photoexhibition.service;

import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;

    /**
     * 移动/重命名文件夹，同时更新数据库中的相册/照片路径
     */
    @Transactional
    public void moveFolder(String sourcePath, String targetPath) throws Exception {
        Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
        Path target = Paths.get(targetPath).toAbsolutePath().normalize();

        if (!Files.exists(source)) {
            throw new IllegalArgumentException("源目录不存在: " + source);
        }
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("源路径不是目录: " + source);
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("目标已存在: " + target);
        }

        // 先移动文件系统
        Files.createDirectories(target.getParent());
        Files.move(source, target);

        String oldPrefix = source.toString();
        String newPrefix = target.toString();

        // 更新相册路径
        List<Album> albums = albumRepository.findByPathStartingWith(oldPrefix);
        for (Album a : albums) {
            a.setPath(a.getPath().replaceFirst(oldPrefix, newPrefix));
        }
        albumRepository.saveAll(albums);

        // 更新照片路径
        List<Photo> photos = photoRepository.findByOriginalPathStartingWith(oldPrefix);
        for (Photo p : photos) {
            p.setOriginalPath(replacePrefix(p.getOriginalPath(), oldPrefix, newPrefix));
            if (p.getThumbnailPath() != null) {
                p.setThumbnailPath(replacePrefix(p.getThumbnailPath(), oldPrefix, newPrefix));
            }
            if (p.getWebpPath() != null) {
                p.setWebpPath(replacePrefix(p.getWebpPath(), oldPrefix, newPrefix));
            }
        }
        photoRepository.saveAll(photos);
    }

    /**
     * 删除目录，并删除其下关联的相册/照片记录
     */
    @Transactional
    public void deleteFolder(String folderPath) throws Exception {
        Path dir = Paths.get(folderPath).toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException("目录不存在: " + dir);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("路径不是目录: " + dir);
        }

        String prefix = dir.toString();

        // 找到匹配的相册，删除其照片与相册记录
        List<Album> albums = albumRepository.findByPathStartingWith(prefix);
        List<Long> albumIds = new ArrayList<>();
        for (Album a : albums) {
            albumIds.add(a.getId());
        }
        if (!albumIds.isEmpty()) {
            photoRepository.deleteByAlbumIdIn(albumIds);
            albumRepository.deleteAll(albums);
        }

        // 删除文件系统目录
        deleteDirectoryRecursive(dir);
    }

    /**
     * 列出指定目录下一级子目录
     */
    public List<String> listDirectories(String folderPath) throws Exception {
        Path dir = resolvePath(folderPath);
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                .filter(Files::isDirectory)
                .map(Path::toString)
                .collect(Collectors.toList());
        }
    }

    /**
     * 将相对路径转换为项目根目录下的绝对路径，若已是绝对路径则直接返回
     */
    private Path resolvePath(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) return null;
        Path path = Paths.get(pathStr);
        if (!path.isAbsolute()) {
            String projectRoot = System.getProperty("user.dir");
            if (projectRoot.endsWith("backend")) {
                projectRoot = Paths.get(projectRoot).getParent().toString();
            }
            String clean = pathStr.startsWith("./") ? pathStr.substring(2) : pathStr;
            path = Paths.get(projectRoot, clean).toAbsolutePath().normalize();
        } else {
            path = path.toAbsolutePath().normalize();
        }
        return path;
    }

    private void deleteDirectoryRecursive(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
            .sorted((a, b) -> b.compareTo(a)) // 先删除子文件
            .forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    log.warn("删除文件失败: {}", p, e);
                }
            });
    }

    private String replacePrefix(String path, String oldPrefix, String newPrefix) {
        if (path == null) return null;
        return path.replaceFirst(oldPrefix, newPrefix);
    }
}

