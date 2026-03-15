package com.photoexhibition.dto;

import lombok.Data;

@Data
public class FaceDTO {
    private Long id;
    private Long photoId;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
    private Double confidence;
    private Long personId;
    private String personName;
    private String personDescription;
    private String photoFilename;
    private String photoThumbnailPath;
    private String photoMediumThumbPath;
    private String photoSmallThumbPath;
    private String photoOriginalPath;
    private String photoLargeThumbPath;
    private String photoWebpPath;
    private Integer photoWidth;   // 照片宽度
    private Integer photoHeight;  // 照片高度
    private String photoTakenAt;  // 拍摄时间
    private Long albumId;         // 相册ID
    private String photoFolderPath; // 照片所在文件夹
    // EXIF 信息
    private String photoLocation;
    private String photoCameraModel;
    private String photoLensModel;
    private String photoAperture;
    private String photoShutterSpeed;
    private String photoIso;
    private String photoFocalLength;
    private Double similarity; // 相似度（可选）
    private Boolean isConfirmed; // 是否已确认
}

