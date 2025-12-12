package com.photoexhibition.dto;

import lombok.Data;
import java.util.List;

@Data
public class FaceClusterDTO {
    private List<FaceDTO> faces; // 该聚类中的人脸列表
    private int count; // 人脸数量
    private Double avgConfidence; // 平均置信度
    private Long representativeFaceId; // 代表脸ID（置信度最高的）
}

