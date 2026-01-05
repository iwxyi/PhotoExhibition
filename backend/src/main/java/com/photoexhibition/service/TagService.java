package com.photoexhibition.service;

import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.TagRepository;
import com.photoexhibition.repository.TagRepository.TagCountProjection;
import com.photoexhibition.repository.TagRepository.TagIdCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public List<TagDTO> findAll() {
        return tagRepository.findAllWithCount().stream()
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

    public TagDTO toDTO(TagCountProjection projection) {
        TagDTO dto = new TagDTO();
        dto.setId(projection.getId());
        dto.setName(projection.getName());
        dto.setColor(projection.getColor());
        dto.setPhotoCount(projection.getPhotoCount() == null ? 0L : projection.getPhotoCount());
        return dto;
    }

    public TagDTO toDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        dto.setPhotoCount(0L);
        return dto;
    }

    public List<TagDTO> toDTOWithCounts(List<Tag> tagList) {
        List<Long> ids = tagList.stream().map(Tag::getId).collect(Collectors.toList());
        Map<Long, Long> countMap = tagRepository.findCountsByIds(ids).stream()
            .collect(Collectors.toMap(TagIdCountProjection::getId, TagIdCountProjection::getPhotoCount));
        return tagList.stream().map(tag -> {
            TagDTO dto = toDTO(tag);
            dto.setPhotoCount(countMap.getOrDefault(tag.getId(), 0L));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 清空所有智能标签关联（保留手动添加的标签）
     * 注意：这个方法会删除所有图片-标签关联，但保留标签本身
     */
    @Transactional
    public void clearAllSmartTags() {
        // 删除所有图片-标签关联
        tagRepository.clearAllPhotoTagAssociations();
    }
}

