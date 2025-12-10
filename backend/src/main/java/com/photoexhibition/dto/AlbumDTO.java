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
    /**
     * 展示用标题（去掉日期前缀、合并子目录），便于前端显示
     */
    private String displayTitle;
    private List<TagDTO> tags;
    private CoverImagesDTO coverImages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /**
     * 相册的拍摄日期（取最早的照片拍摄时间），用于前端显示
     */
    private LocalDateTime takenAt;
}

