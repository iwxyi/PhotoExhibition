package com.photoexhibition.service;

import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.TagRepository;
import com.photoexhibition.repository.TagRepository.TagCountProjection;
import com.photoexhibition.repository.TagRepository.TagIdCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public List<TagDTO> findAll(UserAccount currentUser) {
        Long userId = requireUserId(currentUser);
        return toDTOWithCounts(tagRepository.findByUserIdOrderByNameAsc(userId), userId);
    }

    public Page<TagDTO> findPage(UserAccount currentUser, Pageable pageable) {
        Long userId = requireUserId(currentUser);
        Page<Tag> tagPage = tagRepository.findByUserIdOrderByIdDesc(userId, pageable);
        List<TagDTO> dtoList = toDTOWithCounts(tagPage.getContent(), userId);
        return new PageImpl<>(dtoList, pageable, tagPage.getTotalElements());
    }

    public TagDTO create(TagDTO dto, UserAccount currentUser) {
        Long userId = requireUserId(currentUser);
        String normalizedName = normalizeName(dto.getName());
        Tag existing = tagRepository.findByNameAndUserId(normalizedName, userId).orElse(null);
        if (existing != null) {
            if (dto.getColor() != null) {
                existing.setColor(dto.getColor());
                existing = tagRepository.save(existing);
            }
            return toDTO(existing);
        }
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(normalizedName);
        tag.setColor(dto.getColor());
        return toDTO(tagRepository.save(tag));
    }

    @Transactional
    public TagDTO update(Long id, TagDTO dto, UserAccount currentUser) {
        Long userId = requireUserId(currentUser);
        Tag tag = tagRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new RuntimeException("标签不存在"));
        if (dto.getName() != null) {
            String normalizedName = normalizeName(dto.getName());
            tagRepository.findByNameAndUserId(normalizedName, userId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RuntimeException("标签名称已存在");
                });
            tag.setName(normalizedName);
        }
        if (dto.getColor() != null) tag.setColor(dto.getColor());
        return toDTO(tagRepository.save(tag));
    }

    @Transactional
    public void delete(Long id, UserAccount currentUser) {
        Long userId = requireUserId(currentUser);
        Tag tag = tagRepository.findByIdAndUserId(id, userId).orElseThrow(() -> new RuntimeException("标签不存在"));
        // 先解除关联
        tagRepository.removeFromAlbums(tag.getId());
        tagRepository.removeFromPhotos(tag.getId());
        tagRepository.deleteById(tag.getId());
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
        return toDTOWithCounts(tagList, null);
    }

    public List<TagDTO> toDTOWithCounts(List<Tag> tagList, Long userId) {
        if (tagList == null || tagList.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tagList.stream().map(Tag::getId).collect(Collectors.toList());
        Map<Long, Long> countMap = (userId == null ? tagRepository.findCountsByIds(ids) : tagRepository.findCountsByIdsAndUserId(ids, userId)).stream()
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

    private Long requireUserId(UserAccount currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new RuntimeException("未授权，请先登录");
        }
        return currentUser.getId();
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("标签名称不能为空");
        }
        return name.trim();
    }
}
