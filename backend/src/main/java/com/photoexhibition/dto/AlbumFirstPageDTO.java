package com.photoexhibition.dto;

import lombok.Data;
import java.util.List;

/** 首屏详情所需的相册、照片和人物栏数据。 */
@Data
public class AlbumFirstPageDTO {
    private AlbumDTO album;
    private List<PhotoDTO> photos;
    private List<PersonSummaryDTO> persons;
    private boolean last;
}
