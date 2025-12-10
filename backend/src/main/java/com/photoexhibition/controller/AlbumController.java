package com.photoexhibition.controller;

import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.CoverImagesDTO;
import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/albums")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AlbumController {

    private final AlbumService albumService;

    /**
     * 获取所有相册（相册模式）
     */
    @GetMapping
    public ResponseEntity<Page<AlbumDTO>> getAllAlbums(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AlbumDTO> albums = albumService.getAllAlbumsWithCover(pageable);
        return ResponseEntity.ok(albums);
    }

    /**
     * 根据标签筛选相册
     */
    @PostMapping("/filter")
    public ResponseEntity<Page<AlbumDTO>> filterAlbums(@RequestBody FilterRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<AlbumDTO> albums = albumService.filterAlbums(request, pageable);
        return ResponseEntity.ok(albums);
    }

    /**
     * 获取相册详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<AlbumDTO> getAlbumById(@PathVariable Long id) {
        AlbumDTO album = albumService.getAlbumById(id);
        return ResponseEntity.ok(album);
    }

    /**
     * 获取相册的封面图片组合
     */
    @GetMapping("/{id}/cover")
    public ResponseEntity<CoverImagesDTO> getAlbumCover(@PathVariable Long id) {
        CoverImagesDTO cover = albumService.getAlbumCoverImages(id);
        return ResponseEntity.ok(cover);
    }

    /**
     * 更新相册（名称/描述）
     */
    @PutMapping("/{id}")
    public ResponseEntity<AlbumDTO> updateAlbum(@PathVariable Long id, @RequestBody AlbumDTO dto) {
        return ResponseEntity.ok(albumService.updateAlbum(id, dto));
    }

    /**
     * 删除相册
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlbum(@PathVariable Long id) {
        albumService.deleteAlbum(id);
        return ResponseEntity.ok().build();
    }
}

