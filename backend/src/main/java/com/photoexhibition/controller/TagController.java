package com.photoexhibition.controller;

import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<?> listTags(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UserAccount currentUser = requireCurrentUser(authorization);

        if (page != null && size != null) {
            Page<TagDTO> dtoPage = tagService.findPage(currentUser, PageRequest.of(page, size));
            return ResponseEntity.ok(dtoPage);
        } else {
            return ResponseEntity.ok(tagService.findAll(currentUser));
        }
    }

    @PostMapping
    public ResponseEntity<TagDTO> createTag(@RequestHeader("Authorization") String authorization,
                                            @RequestBody TagDTO dto) {
        return ResponseEntity.ok(tagService.create(dto, requireCurrentUser(authorization)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagDTO> updateTag(@RequestHeader("Authorization") String authorization,
                                            @PathVariable Long id,
                                            @RequestBody TagDTO dto) {
        return ResponseEntity.ok(tagService.update(id, dto, requireCurrentUser(authorization)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@RequestHeader("Authorization") String authorization,
                                          @PathVariable Long id) {
        tagService.delete(id, requireCurrentUser(authorization));
        return ResponseEntity.ok().build();
    }

    private UserAccount requireCurrentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未授权，请先登录");
        }
        return authService.getCurrentUserEntity(authorization.substring(7));
    }
}
