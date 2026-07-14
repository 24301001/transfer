package com.transfer.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 将 YOLO/OpenCV 生成的 mp4v 视频转换为浏览器兼容的 H.264 MP4。
 *
 * <p>YOLO 返回的 URL 保持不变；本服务在挂载目录中原地替换对应视频，
 * 因此前端和数据库无需修改 URL。</p>
 */
@Service
public class VideoTranscodeService {

    private static final Logger log =
            LoggerFactory.getLogger(VideoTranscodeService.class);

    private static final String RUNS_API_PREFIX = "/runs/api/";

    private final boolean enabled;
    private final Path outputDir;
    private final String ffmpegPath;
    private final String ffprobePath;
    private final Duration timeout;

    public VideoTranscodeService(
            @Value("${app.video-transcode.enabled:true}")
            boolean enabled,
            @Value("${app.algorithm1.output-dir:../algorithm1/runs/api}")
            String outputDir,
            @Value("${app.ffmpeg.path:${FFMPEG_PATH:/usr/bin/ffmpeg}}")
            String ffmpegPath,
            @Value("${app.ffprobe.path:${FFPROBE_PATH:/usr/bin/ffprobe}}")
            String ffprobePath,
            @Value("${app.video-transcode.timeout-seconds:900}")
            long timeoutSeconds
    ) {
        this.enabled = enabled;
        this.outputDir = Path.of(outputDir)
                .toAbsolutePath()
                .normalize();
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
        this.timeout = Duration.ofSeconds(
                Math.max(timeoutSeconds, 30)
        );
    }

    /**
     * 确保 outputUrl 对应的视频是浏览器兼容的 H.264/yuv420p MP4。
     * 返回值仍然是原始 URL。
     */
    public String ensureBrowserCompatible(
            String outputUrl
    ) {
        if (!enabled
                || outputUrl == null
                || outputUrl.isBlank()
                || !isMp4(outputUrl)) {
            return outputUrl;
        }

        Path source = resolveMountedVideoPath(outputUrl);

        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException(
                    "YOLO 输出视频不存在于后端挂载目录："
                            + source
            );
        }

        ProbeResult probe = probe(source);

        if ("h264".equals(probe.codecName())
                && "yuv420p".equals(probe.pixelFormat())) {
            log.info(
                    "YOLO 视频已经兼容浏览器，无需转码：{}",
                    source
            );
            return outputUrl;
        }

        log.info(
                "开始将 YOLO 视频转码为 H.264：file={}, codec={}, pixFmt={}",
                source,
                probe.codecName(),
                probe.pixelFormat()
        );

        Path temp = source.resolveSibling(
                source.getFileName() + ".h264.tmp.mp4"
        );

        try {
            Files.deleteIfExists(temp);

            runProcess(
                    List.of(
                            ffmpegPath,
                            "-hide_banner",
                            "-loglevel",
                            "error",
                            "-y",
                            "-i",
                            source.toString(),
                            "-map",
                            "0:v:0",
                            "-map",
                            "0:a?",
                            "-c:v",
                            "libx264",
                            "-preset",
                            "veryfast",
                            "-crf",
                            "23",
                            "-pix_fmt",
                            "yuv420p",
                            "-movflags",
                            "+faststart",
                            "-c:a",
                            "aac",
                            "-b:a",
                            "128k",
                            temp.toString()
                    ),
                    "FFmpeg 视频转码"
            );

            ProbeResult converted = probe(temp);
            if (!"h264".equals(converted.codecName())) {
                throw new IllegalStateException(
                        "FFmpeg 已执行，但输出编码不是 H.264："
                                + converted.codecName()
                );
            }

            replaceAtomically(temp, source);

            log.info(
                    "YOLO 视频 H.264 转码完成并原地替换：{}",
                    source
            );

            return outputUrl;
        } catch (RuntimeException | IOException ex) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupEx) {
                log.warn(
                        "清理 FFmpeg 临时文件失败：{}",
                        temp,
                        cleanupEx
                );
            }
            throw new IllegalStateException(
                    "YOLO 标注视频转码失败："
                            + ex.getMessage(),
                    ex
            );
        }
    }

    private ProbeResult probe(
            Path file
    ) {
        String output = runProcess(
                List.of(
                        ffprobePath,
                        "-v",
                        "error",
                        "-select_streams",
                        "v:0",
                        "-show_entries",
                        "stream=codec_name,pix_fmt",
                        "-of",
                        "csv=p=0",
                        file.toString()
                ),
                "FFprobe 视频检查"
        ).trim();

        if (output.isBlank()) {
            throw new IllegalStateException(
                    "FFprobe 未返回视频编码信息："
                            + file
            );
        }

        String[] parts = output
                .split("\\R", 2)[0]
                .split(",", -1);

        String codec = parts.length > 0
                ? parts[0].trim().toLowerCase(Locale.ROOT)
                : "";
        String pixelFormat = parts.length > 1
                ? parts[1].trim().toLowerCase(Locale.ROOT)
                : "";

        return new ProbeResult(codec, pixelFormat);
    }

    private Path resolveMountedVideoPath(
            String outputUrl
    ) {
        String path;

        try {
            URI uri = URI.create(outputUrl.trim());
            path = uri.getRawPath();
        } catch (IllegalArgumentException ex) {
            path = outputUrl.trim();
        }

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "无效的 YOLO 输出视频 URL："
                            + outputUrl
            );
        }

        int prefixIndex = path.indexOf(RUNS_API_PREFIX);
        if (prefixIndex >= 0) {
            path = path.substring(
                    prefixIndex + RUNS_API_PREFIX.length()
            );
        } else {
            path = path.startsWith("/")
                    ? path.substring(1)
                    : path;
        }

        String decoded = URLDecoder.decode(
                path,
                StandardCharsets.UTF_8
        );

        Path resolved = outputDir
                .resolve(decoded)
                .normalize();

        if (!resolved.startsWith(outputDir)) {
            throw new IllegalArgumentException(
                    "YOLO 输出路径越界："
                            + outputUrl
            );
        }

        return resolved;
    }

    private String runProcess(
            List<String> command,
            String operation
    ) {
        Process process = null;

        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            boolean completed = process.waitFor(
                    timeout.toSeconds(),
                    TimeUnit.SECONDS
            );

            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        operation + "超时，限制为 "
                                + timeout.toSeconds()
                                + " 秒"
                );
            }

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        operation
                                + "失败，退出码="
                                + process.exitValue()
                                + "，输出="
                                + output
                );
            }

            return output;
        } catch (IOException ex) {
            throw new IllegalStateException(
                    operation
                            + "无法启动，请确认可执行文件存在："
                            + command.get(0),
                    ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    operation + "被中断",
                    ex
            );
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private void replaceAtomically(
            Path source,
            Path target
    ) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private boolean isMp4(
            String url
    ) {
        String value = url.toLowerCase(Locale.ROOT);
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        return value.endsWith(".mp4");
    }

    private record ProbeResult(
            String codecName,
            String pixelFormat
    ) {
    }
}
