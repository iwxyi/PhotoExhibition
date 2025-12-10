package com.photoexhibition.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${photo.scan.base-path}")
    private String photoBasePath;

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
        // 处理图片路径配置
        String basePath = photoBasePath;
        
        // 如果是相对路径，转换为绝对路径
        if (!Paths.get(basePath).isAbsolute()) {
            String projectRoot = System.getProperty("user.dir");
            if (projectRoot.endsWith("backend")) {
                projectRoot = new File(projectRoot).getParent();
            }
            String cleanPath = basePath.startsWith("./") 
                ? basePath.substring(2) 
                : basePath;
            basePath = new File(projectRoot, cleanPath).getAbsolutePath();
        }
        
        // 确保路径以/结尾（macOS/Linux）或\结尾（Windows）
        if (!basePath.endsWith("/") && !basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        
        // 配置静态资源访问
        // /files/** 映射到图片目录
        // 注意：前端访问时，路径应该是 /api/files/相对路径
        registry.addResourceHandler("/files/**")
            .addResourceLocations("file:" + basePath)
            .setCachePeriod(3600); // 缓存1小时
    }
}
