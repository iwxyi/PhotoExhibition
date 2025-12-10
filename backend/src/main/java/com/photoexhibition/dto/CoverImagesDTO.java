package com.photoexhibition.dto;

import lombok.Data;

@Data
public class CoverImagesDTO {
    private PhotoDTO leftVertical;  // 左侧竖图
    private PhotoDTO rightTop;      // 右侧上方横图
    private PhotoDTO rightBottom;   // 右侧下方横图
}

