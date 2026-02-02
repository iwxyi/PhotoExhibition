package com.photoexhibition.dto;

import lombok.Data;

@Data
public class CoverImagesDTO {
    /**
     * 封面1 - 左侧竖图
     */
    private PhotoDTO cover1;
    /**
     * 封面2 - 右侧上方横图
     */
    private PhotoDTO cover2;
    /**
     * 封面3 - 右侧下方横图
     */
    private PhotoDTO cover3;
    /**
     * 封面4 - 预留
     */
    private PhotoDTO cover4;
    
    // 兼容旧字段名
    public PhotoDTO getLeftVertical() {
        return cover1;
    }
    
    public void setLeftVertical(PhotoDTO photo) {
        this.cover1 = photo;
    }
    
    public PhotoDTO getRightTop() {
        return cover2;
    }
    
    public void setRightTop(PhotoDTO photo) {
        this.cover2 = photo;
    }
    
    public PhotoDTO getRightBottom() {
        return cover3;
    }
    
    public void setRightBottom(PhotoDTO photo) {
        this.cover3 = photo;
    }
}

