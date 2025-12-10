package com.photoexhibition.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AlbumDTO {
    private Long id;
    private String name;
    private String path;
    private Long coverImageId;
    private String description;
    private Integer photoCount;
    private List<TagDTO> tags;
    private CoverImagesDTO coverImages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

