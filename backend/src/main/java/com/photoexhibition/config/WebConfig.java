package com.photoexhibition.config;

import com.photoexhibition.service.UserPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserPathService userPathService;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = userPathService.resolvePhotoBasePath().toString();
        String userDataPath = userPathService.resolveUserDataBasePath().toString();
        
        // 确保路径以/结尾（macOS/Linux）或\结尾（Windows）
        if (!basePath.endsWith("/") && !basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        if (!userDataPath.endsWith("/") && !userDataPath.endsWith(File.separator)) {
            userDataPath += File.separator;
        }
        
        // 配置静态资源访问
        // /api/files/** 映射到图片目录 (相对于context-path /api)
        registry.addResourceHandler("/files/**")
            .addResourceLocations("file:" + basePath)
            .setCachePeriod(3600); // 缓存1小时

        // 支持 /api/photos/** 路径访问（通用图片路径）
        registry.addResourceHandler("/photos/**")
            .addResourceLocations("file:" + basePath)
            .setCachePeriod(3600);

        // 支持数据库中存储的直接路径访问（如 /api/人像/**, /api/游玩/** 等）
        // 这些路径相对于context-path /api
        registry.addResourceHandler("/人像/**", "/游玩/**", "/风景/**", "/活动/**", "/扫街/**", "/其他/**")
            .addResourceLocations("file:" + basePath)
            .setCachePeriod(3600);

        registry.addResourceHandler("/user-files/**")
            .addResourceLocations("file:" + userDataPath)
            .setCachePeriod(3600);
    }
}
