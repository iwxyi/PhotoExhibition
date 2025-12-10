package com.photoexhibition.controller;

import com.photoexhibition.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}

