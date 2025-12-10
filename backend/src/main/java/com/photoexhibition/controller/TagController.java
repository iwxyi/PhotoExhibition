package com.photoexhibition.controller;

import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.TagRepository;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRepository tagRepository;
    private final TagService tagService;

    @GetMapping
    public ResponseEntity<?> listTags(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        if (page != null && size != null) {
            Page<Tag> tagPage = tagRepository.findAll(PageRequest.of(page, size));
            Page<TagDTO> dtoPage = tagPage.map(this::toDTO);
            return ResponseEntity.ok(dtoPage);
        } else {
            List<TagDTO> tags = tagRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
            return ResponseEntity.ok(tags);
        }
    }

    @PostMapping
    public ResponseEntity<TagDTO> createTag(@RequestBody TagDTO dto) {
        return ResponseEntity.ok(tagService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagDTO> updateTag(@PathVariable Long id, @RequestBody TagDTO dto) {
        return ResponseEntity.ok(tagService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        tagService.delete(id);
        return ResponseEntity.ok().build();
    }

    private TagDTO toDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        return dto;
    }
}

