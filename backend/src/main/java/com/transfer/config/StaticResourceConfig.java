package com.transfer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 将 algorithm1 YOLO 输出目录映射为 HTTP 静态资源路径。
 * 前端通过 /runs/api/videos/ 和 /runs/api/images/ 访问分析后的标注媒体。
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceConfig.class);

    private final Path algoOutputPath;

    public StaticResourceConfig(
            @Value("${app.algorithm1.output-dir:../algorithm1/runs/api}")
            String algoOutputPath
    ) {
        this.algoOutputPath = Path.of(algoOutputPath)
                .toAbsolutePath()
                .normalize();
    }

    @PostConstruct
    void logPath() {
        String pathStr = algoOutputPath.toString().replace("\\", "/");
        boolean exists = Files.isDirectory(algoOutputPath);
        log.info("Algorithm1 输出目录映射 /runs/api/** → {} (存在: {})", pathStr, exists);
        if (!exists) {
            log.warn("⚠ Algorithm1 输出目录不存在！请确认 YOLO 服务已生成标注文件。路径: {}", pathStr);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + algoOutputPath.toString().replace("\\", "/") + "/";
        registry
                .addResourceHandler("/runs/api/**")
                .addResourceLocations(location)
                .setCachePeriod(0);
        log.info("已注册静态资源映射: /runs/api/** → {}", location);
    }
}
