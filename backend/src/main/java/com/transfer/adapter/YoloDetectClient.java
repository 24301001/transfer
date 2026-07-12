package com.transfer.adapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 调用 YOLOv5 FastAPI 推理服务进行事故类型检测。
 * 排除 "car" 类别，仅保留事故相关类别：
 * car damage, fire, car flip, car crash
 */
@Component
public class YoloDetectClient {

    private static final Logger log = LoggerFactory.getLogger(YoloDetectClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String yoloBaseUrl;

    /**
     * 排除的类别——"car"只是车辆本身，不是事故类型。
     */
    private static final List<String> EXCLUDED_CLASSES = List.of("car");

    public YoloDetectClient(
            @Value("${app.yolo.base-url:http://localhost:8000}") String yoloBaseUrl
    ) {
        this.yoloBaseUrl = yoloBaseUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String getYoloBaseUrl() {
        return yoloBaseUrl;
    }

    /**
     * 通过文件路径检测图片（用于已保存的文件）。
     */
    public YoloImageResult detectImageByPath(java.io.File file, float confThreshold) {
        try {
            String contentType = java.nio.file.Files.probeContentType(file.toPath());
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            MockMultipartFileAdapter mf = new MockMultipartFileAdapter(
                    "file", file.getName(), contentType, bytes);
            return detectImage(mf, confThreshold);
        } catch (Exception e) {
            log.error("YOLOv5 图片检测异常: {}", e.getMessage());
            return YoloImageResult.failed(e.getMessage());
        }
    }

    /**
     * 通过文件路径检测视频（用于已保存的文件）。
     */
    public YoloVideoResult detectVideoByPath(java.io.File file, float confThreshold) {
        try {
            String contentType = java.nio.file.Files.probeContentType(file.toPath());
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            MockMultipartFileAdapter mf = new MockMultipartFileAdapter(
                    "file", file.getName(), contentType, bytes);
            return detectVideo(mf, confThreshold);
        } catch (Exception e) {
            log.error("YOLOv5 视频检测异常: {}", e.getMessage());
            return YoloVideoResult.failed(e.getMessage());
        }
    }

    /**
     * 内部 MultipartFile 适配器，避免依赖 spring-test。
     */
    private static class MockMultipartFileAdapter implements MultipartFile {
        private final String name, originalFilename, contentType;
        private final byte[] bytes;

        MockMultipartFileAdapter(String name, String originalFilename, String contentType, byte[] bytes) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }

    /**
     * 对单张图片进行事故检测。
     *
     * @return 检测结果（包含事故类型列表、完整 JSON、输出图片 URL）
     */
    public YoloImageResult detectImage(MultipartFile file, float confThreshold) {
        String url = yoloBaseUrl + "/predict/image?conf=" + confThreshold;
        log.info("调用 YOLOv5 图片检测: url={}, filename={}", url, file.getOriginalFilename());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> respBody = response.getBody();

            return parseImageResult(respBody);
        } catch (IOException e) {
            log.error("YOLOv5 图片检测失败: {}", e.getMessage(), e);
            return YoloImageResult.failed("IO error: " + e.getMessage());
        } catch (Exception e) {
            log.error("YOLOv5 图片检测异常: {}", e.getMessage(), e);
            return YoloImageResult.failed(e.getMessage());
        }
    }

    /**
     * 对视频进行事故检测。
     *
     * @return 检测结果（含统计信息、事故类型、输出视频 URL）
     */
    public YoloVideoResult detectVideo(MultipartFile file, float confThreshold) {
        String url = yoloBaseUrl + "/predict/video?conf=" + confThreshold;
        log.info("调用 YOLOv5 视频检测: url={}, filename={}", url, file.getOriginalFilename());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> respBody = response.getBody();

            return parseVideoResult(respBody);
        } catch (IOException e) {
            log.error("YOLOv5 视频检测失败: {}", e.getMessage(), e);
            return YoloVideoResult.failed("IO error: " + e.getMessage());
        } catch (Exception e) {
            log.error("YOLOv5 视频检测异常: {}", e.getMessage(), e);
            return YoloVideoResult.failed(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private YoloImageResult parseImageResult(Map<String, Object> body) {
        if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
            return YoloImageResult.failed("YOLOv5 returned non-success");
        }

        List<Map<String, Object>> detections = (List<Map<String, Object>>) body.get("detections");
        List<String> accidentTypes = new ArrayList<>();
        if (detections != null) {
            for (Map<String, Object> det : detections) {
                String className = (String) det.get("class_name");
                if (className != null && !EXCLUDED_CLASSES.contains(className)) {
                    accidentTypes.add(className);
                }
            }
        }

        // 去重
        accidentTypes = accidentTypes.stream().distinct().collect(Collectors.toList());

        String outputUrl = (String) body.get("output_image_url");
        if (outputUrl != null && !outputUrl.startsWith("http")) {
            outputUrl = yoloBaseUrl + outputUrl;
        }

        return new YoloImageResult(true, accidentTypes, outputUrl, toJson(body));
    }

    @SuppressWarnings("unchecked")
    private YoloVideoResult parseVideoResult(Map<String, Object> body) {
        if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
            return YoloVideoResult.failed("YOLOv5 returned non-success");
        }

        Map<String, Object> stats = (Map<String, Object>) body.get("stats");
        List<String> accidentTypes = new ArrayList<>();
        Map<String, Object> perClass = null;
        if (stats != null) {
            perClass = (Map<String, Object>) stats.get("per_class");
            if (perClass != null) {
                for (Map.Entry<String, Object> entry : perClass.entrySet()) {
                    if (!EXCLUDED_CLASSES.contains(entry.getKey())) {
                        int count = entry.getValue() instanceof Number
                                ? ((Number) entry.getValue()).intValue() : 0;
                        if (count > 0) {
                            accidentTypes.add(entry.getKey());
                        }
                    }
                }
            }
        }

        String outputUrl = (String) body.get("output_video_url");
        if (outputUrl != null && !outputUrl.startsWith("http")) {
            outputUrl = yoloBaseUrl + outputUrl;
        }

        return new YoloVideoResult(true, accidentTypes, outputUrl, perClass, toJson(body));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 结果 DTO ----

    public record YoloImageResult(
            boolean success,
            List<String> accidentTypes,
            String outputUrl,
            String rawJson,
            String errorMessage
    ) {
        public YoloImageResult(boolean success, List<String> accidentTypes, String outputUrl, String rawJson) {
            this(success, accidentTypes, outputUrl, rawJson, null);
        }

        public static YoloImageResult failed(String msg) {
            return new YoloImageResult(false, List.of(), null, null, msg);
        }
    }

    public record YoloVideoResult(
            boolean success,
            List<String> accidentTypes,
            String outputUrl,
            Map<String, Object> perClassStats,
            String rawJson,
            String errorMessage
    ) {
        public YoloVideoResult(boolean success, List<String> accidentTypes, String outputUrl,
                               Map<String, Object> perClassStats, String rawJson) {
            this(success, accidentTypes, outputUrl, perClassStats, rawJson, null);
        }

        public static YoloVideoResult failed(String msg) {
            return new YoloVideoResult(false, List.of(), null, null, null, msg);
        }
    }
}
