package com.photoexhibition.controller;

import com.photoexhibition.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/admin/folders")
@RequiredArgsConstructor
public class FolderController {

    @Value("${photo.scan.base-path}")
    private String basePath;

    private final FolderService folderService;

    @GetMapping("/base-path")
    public ResponseEntity<Map<String, Object>> getBasePath() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("basePath", basePath);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/move")
    public ResponseEntity<Map<String, Object>> moveFolder(@RequestParam String source, @RequestParam String target) {
        Map<String, Object> resp = new HashMap<>();
        try {
            folderService.moveFolder(source, target);
            resp.put("message", "移动完成");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteFolder(@RequestParam String path) {
        Map<String, Object> resp = new HashMap<>();
        try {
            folderService.deleteFolder(path);
            resp.put("message", "删除完成");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String path) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String target = (path == null || path.isEmpty()) ? basePath : path;
            List<String> dirs = folderService.listDirectories(target);
            resp.put("base", target);
            resp.put("dirs", dirs);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 文件浏览器：列出指定路径下的文件和文件夹
     */
    @GetMapping("/browser/list")
    public ResponseEntity<Map<String, Object>> browserList(@RequestParam(required = false) String path) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String target = (path == null || path.isEmpty()) ? basePath : path;
            Map<String, Object> result = folderService.listFilesAndDirectories(target);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 创建文件夹
     */
    @PostMapping("/browser/create")
    public ResponseEntity<Map<String, Object>> createDirectory(@RequestParam String path, @RequestParam String name) {
        Map<String, Object> resp = new HashMap<>();
        try {
            java.nio.file.Path parentPath = java.nio.file.Paths.get(path);
            java.nio.file.Path fullPath = parentPath.resolve(name);
            folderService.createDirectory(fullPath.toString());
            resp.put("message", "创建成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 重命名文件或文件夹
     */
    @PostMapping("/browser/rename")
    public ResponseEntity<Map<String, Object>> renameItem(@RequestParam String path, @RequestParam String newName) {
        Map<String, Object> resp = new HashMap<>();
        try {
            folderService.renameItem(path, newName);
            resp.put("message", "重命名成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 删除文件或文件夹（复用现有方法）
     */
    @DeleteMapping("/browser/delete")
    public ResponseEntity<Map<String, Object>> deleteItem(@RequestParam String path) {
        Map<String, Object> resp = new HashMap<>();
        try {
            // 检查是文件还是文件夹
            java.nio.file.Path itemPath = java.nio.file.Paths.get(path);
            if (java.nio.file.Files.isDirectory(itemPath)) {
                folderService.deleteFolder(path);
            } else {
                java.nio.file.Files.deleteIfExists(itemPath);
            }
            resp.put("message", "删除成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 批量删除文件或文件夹
     */
    @DeleteMapping("/browser/delete-items")
    public ResponseEntity<Map<String, Object>> deleteItems(@RequestParam List<String> paths) {
        Map<String, Object> resp = new HashMap<>();
        try {
            folderService.deleteItems(paths);
            resp.put("message", "删除成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 批量移动文件或文件夹
     */
    @PostMapping("/browser/move-items")
    public ResponseEntity<Map<String, Object>> moveItems(@RequestParam List<String> paths, @RequestParam String target) {
        Map<String, Object> resp = new HashMap<>();
        try {
            folderService.moveItems(paths, target);
            resp.put("message", "移动成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 上传文件或文件夹（支持文件夹内路径）
     */
    @PostMapping("/browser/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("files") List<MultipartFile> files,
                                                      @RequestParam String target,
                                                      @RequestParam(required = false) List<String> relativePaths) {
        Map<String, Object> resp = new HashMap<>();
        try {
            int saved = folderService.uploadFiles(files, target, relativePaths);
            resp.put("message", "已保存 " + saved + " 个文件，后台处理中");
            resp.put("saved", saved);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }
}

