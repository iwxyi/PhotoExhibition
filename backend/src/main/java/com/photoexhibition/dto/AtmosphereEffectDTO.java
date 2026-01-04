package com.photoexhibition.dto;

import lombok.Data;

@Data
public class AtmosphereEffectDTO {
    private String type;        // 特效类型：snow, cherry_blossom, birthday, meteor, starry_sky 等
    private String intensity;   // 强度：low, medium, high
    private String layer;       // 显示层级：above, background
    private Object config;      // 特效配置（JSON对象）

    public AtmosphereEffectDTO() {}

    public AtmosphereEffectDTO(String type, String intensity) {
        this.type = type;
        this.intensity = intensity;
    }

    public AtmosphereEffectDTO(String type, String intensity, Object config) {
        this.type = type;
        this.intensity = intensity;
        this.config = config;
    }

    public AtmosphereEffectDTO(String type, String intensity, String layer, Object config) {
        this.type = type;
        this.intensity = intensity;
        this.layer = layer;
        this.config = config;
    }
}


