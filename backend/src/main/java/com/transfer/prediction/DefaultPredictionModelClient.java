package com.transfer.prediction;

import com.transfer.dto.WeatherFeaturePayload;
import com.transfer.enums.CoordinateType;
import com.transfer.service.BaiduWeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * 预测模块 HTTP 客户端默认实现。
 *
 * <p>发送预测请求前，会按事故经纬度查询百度地图实时天气：</p>
 *
 * <ol>
 *     <li>将天气现象写入原 weather 字段；</li>
 *     <li>将结构化天气写入 weatherDetail；</li>
 *     <li>天气服务失败时不阻断预测。</li>
 * </ol>
 */
@Component
public class DefaultPredictionModelClient
        implements PredictionModelClient {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultPredictionModelClient.class);

    private final RestClient restClient;
    private final BaiduWeatherService weatherService;

    private final boolean configured;
    private final String predictPath;
    private final String healthPath;

    public DefaultPredictionModelClient(
            BaiduWeatherService weatherService,

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
        this.weatherService = weatherService;
        this.predictPath = normalizePath(predictPath, "/predict");
        this.healthPath = normalizePath(healthPath, "/health");

        String trimmedBaseUrl =
                baseUrl == null ? "" : baseUrl.trim();

        this.configured = !trimmedBaseUrl.isBlank();

        if (configured) {
            int effectiveConnectTimeout =
                    Math.max(500, connectTimeout);

            int effectiveReadTimeout =
                    Math.max(500, readTimeout);

            SimpleClientHttpRequestFactory requestFactory =
                    new SimpleClientHttpRequestFactory();

            requestFactory.setConnectTimeout(
                    effectiveConnectTimeout
            );

            requestFactory.setReadTimeout(
                    effectiveReadTimeout
            );

            this.restClient =
                    RestClient.builder()
                            .baseUrl(trimmedBaseUrl)
                            .requestFactory(requestFactory)
                            .defaultHeaders(headers ->
                                    headers.setContentType(
                                            MediaType.APPLICATION_JSON
                                    )
                            )
                            .requestInterceptor(
                                    (request, body, execution) -> {
                                        log.debug(
                                                "预测模块请求: {} {}",
                                                request.getMethod(),
                                                request.getURI()
                                        );

                                        return execution.execute(
                                                request,
                                                body
                                        );
                                    }
                            )
                            .build();

            log.info(
                    "预测模块客户端已配置: "
                            + "baseUrl={}, "
                            + "predictPath={}, "
                            + "healthPath={}, "
                            + "connectTimeout={}ms, "
                            + "readTimeout={}ms",
                    trimmedBaseUrl,
                    this.predictPath,
                    this.healthPath,
                    effectiveConnectTimeout,
                    effectiveReadTimeout
            );
        } else {
            this.restClient = null;

            log.warn(
                    "预测模块 URL 未配置 "
                            + "(app.prediction-module.base-url)，"
                            + "预测功能将以降级模式运行"
            );
        }
    }

    @Override
    public PredictionModuleResponse predict(
            PredictionModuleRequest request
    ) {
        if (!configured || restClient == null) {
            log.warn("预测模块未配置，返回降级响应");

            return new PredictionModuleResponse(
                    null,
                    "NOT_CONFIGURED",
                    null,
                    "Prediction module base-url is not configured"
            );
        }

        PredictionModuleRequest enrichedRequest =
                enrichWeather(request);

        try {
            PredictionModuleResponse response =
                    restClient
                            .post()
                            .uri(predictPath)
                            .body(enrichedRequest)
                            .retrieve()
                            .body(PredictionModuleResponse.class);

            if (response == null) {
                return new PredictionModuleResponse(
                        null,
                        "EMPTY_RESPONSE",
                        null,
                        "预测模块返回空响应"
                );
            }

            log.info(
                    "预测模块响应: traceId={}, status={}",
                    response.traceId(),
                    response.status()
            );

            return response;

        } catch (RestClientResponseException ex) {
            log.error(
                    "预测模块返回 HTTP {}: {}",
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString()
            );

            return new PredictionModuleResponse(
                    null,
                    "HTTP_ERROR",
                    null,
                    "预测模块返回 HTTP "
                            + ex.getStatusCode().value()
            );

        } catch (ResourceAccessException ex) {
            log.error(
                    "预测模块不可达: {}",
                    ex.getMessage()
            );

            return new PredictionModuleResponse(
                    null,
                    "UNAVAILABLE",
                    null,
                    "预测模块连接失败: "
                            + ex.getMessage()
            );

        } catch (RestClientException ex) {
            log.error(
                    "预测模块调用失败: {}",
                    ex.getMessage()
            );

            return new PredictionModuleResponse(
                    null,
                    "CLIENT_ERROR",
                    null,
                    "预测模块调用失败: "
                            + ex.getMessage()
            );
        }
    }

    @Override
    public boolean healthCheck() {
        if (!configured || restClient == null) {
            return false;
        }

        try {
            restClient
                    .get()
                    .uri(healthPath)
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (RestClientException ex) {
            log.debug(
                    "预测模块健康检查失败: {}",
                    ex.getMessage()
            );

            return false;
        }
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    /**
     * 为预测请求补充实时天气数据。
     *
     * <p>当请求没有坐标、已经包含天气详情，或者天气服务调用失败时，
     * 直接返回原请求，避免天气功能影响主预测流程。</p>
     */
    private PredictionModuleRequest enrichWeather(
            PredictionModuleRequest request
    ) {
        if (request == null
                || request.weatherDetail() != null
                || request.longitude() == null
                || request.latitude() == null) {
            return request;
        }

        try {
            /*
             * 当前事故上报页面明确传入 coordinateType=WGS84，
             * 因此这里按 WGS84 查询天气。
             */
            Optional<WeatherFeaturePayload> weather =
                    weatherService.tryGetCurrentWeather(
                            request.longitude(),
                            request.latitude(),
                            CoordinateType.WGS84
                    );

            if (weather.isEmpty()) {
                return request;
            }

            WeatherFeaturePayload detail =
                    weather.get();

            log.info(
                    "已为预测请求补充百度天气: "
                            + "incidentId={}, "
                            + "weather={}, "
                            + "temp={}℃",
                    request.incidentId(),
                    detail.text(),
                    detail.temperatureC()
            );

            return request.withWeatherDetail(
                    detail.text(),
                    detail
            );

        } catch (RuntimeException ex) {
            log.warn(
                    "获取实时天气失败，将使用原始预测请求: "
                            + "incidentId={}, error={}",
                    request.incidentId(),
                    ex.getMessage()
            );

            return request;
        }
    }

    private String normalizePath(
            String value,
            String fallback
    ) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String trimmed = value.trim();

        return trimmed.startsWith("/")
                ? trimmed
                : "/" + trimmed;
    }
}

