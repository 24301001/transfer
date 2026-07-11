package com.transfer.prediction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * 预测模块 HTTP 客户端的默认实现。
 *
 * <p>使用 Spring 6.1+ 的 {@link RestClient} 发送同步 HTTP 请求。</p>
 */
@Component
public class DefaultPredictionModelClient implements PredictionModelClient {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultPredictionModelClient.class);

    private final RestClient restClient;
    private final boolean configured;

    public DefaultPredictionModelClient(
            @Value("${app.prediction-module.base-url:}")
            String baseUrl,
            @Value("${app.prediction-module.predict-path:/predict}")
            String predictPath,
            @Value("${app.prediction-module.health-path:/health}")
            String healthPath,
            @Value("${app.prediction-module.connect-timeout:5000}")
            int connectTimeout,
            @Value("${app.prediction-module.read-timeout:60000}")
            int readTimeout
    ) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        this.configured = !trimmed.isBlank();

        if (configured) {
            this.restClient = RestClient.builder()
                    .baseUrl(trimmed)
                    .defaultHeaders(headers ->
                            headers.setContentType(MediaType.APPLICATION_JSON))
                    .requestInterceptor((request, body, execution) -> {
                        log.debug("预测模块请求: {} {}", request.getMethod(), request.getURI());
                        return execution.execute(request, body);
                    })
                    .build();
            log.info("预测模块客户端已配置: baseUrl={}, connectTimeout={}ms, readTimeout={}ms",
                    trimmed, connectTimeout, readTimeout);
        } else {
            this.restClient = null;
            log.warn("预测模块 URL 未配置 (app.prediction-module.base-url)，"
                    + "预测功能将以降级模式运行");
        }
    }

    @Override
    public PredictionModuleResponse predict(PredictionModuleRequest request) {
        if (!configured) {
            log.warn("预测模块未配置，返回降级响应");
            return new PredictionModuleResponse(
                    null,
                    "NOT_CONFIGURED",
                    null,
                    "Prediction module base-url is not configured"
            );
        }

        try {
            PredictionModuleResponse response = restClient.post()
                    .uri("/predict")
                    .body(request)
                    .retrieve()
                    .body(PredictionModuleResponse.class);

            log.info("预测模块响应: traceId={}, status={}",
                    response.traceId(), response.status());
            return response;

        } catch (RestClientResponseException ex) {
            log.error("预测模块返回 HTTP {}: {}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return new PredictionModuleResponse(
                    null,
                    "HTTP_ERROR",
                    null,
                    "预测模块返回 HTTP " + ex.getStatusCode().value()
            );
        } catch (ResourceAccessException ex) {
            log.error("预测模块不可达: {}", ex.getMessage());
            return new PredictionModuleResponse(
                    null,
                    "UNAVAILABLE",
                    null,
                    "预测模块连接失败: " + ex.getMessage()
            );
        }
    }

    @Override
    public boolean healthCheck() {
        if (!configured || restClient == null) {
            return false;
        }

        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.debug("预测模块健康检查失败: {}", ex.getMessage());
            return false;
        }
    }
}
