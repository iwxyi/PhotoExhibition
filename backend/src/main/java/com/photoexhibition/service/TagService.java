package com.photoexhibition.service;

import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public List<TagDTO> findAll() {
        return tagRepository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public TagDTO create(TagDTO dto) {
        Tag tag = new Tag();
        tag.setName(dto.getName());
        tag.setColor(dto.getColor());
        return toDTO(tagRepository.save(tag));
    }

    @Transactional
    public TagDTO update(Long id, TagDTO dto) {
        Tag tag = tagRepository.findById(id).orElseThrow(() -> new RuntimeException("标签不存在"));
        if (dto.getName() != null) tag.setName(dto.getName());
        if (dto.getColor() != null) tag.setColor(dto.getColor());
        return toDTO(tagRepository.save(tag));
    }

    @Transactional
    public void delete(Long id) {
        // 先解除关联
        tagRepository.removeFromAlbums(id);
        tagRepository.deleteById(id);
    }

    private TagDTO toDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        return dto;
    }
}

