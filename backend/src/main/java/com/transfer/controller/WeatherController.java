package com.transfer.controller;

import com.transfer.dto.WeatherFeaturePayload;
import com.transfer.enums.CoordinateType;
import com.transfer.service.BaiduWeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final BaiduWeatherService weatherService;

    public WeatherController(
            BaiduWeatherService weatherService
    ) {
        this.weatherService = weatherService;
    }

    /**
     * 示例：
     *
     * GET /api/v1/weather/current
     *     ?longitude=116.40387
     *     &latitude=39.91489
     *     &coordinateType=WGS84
     */
    @GetMapping("/current")
    public WeatherFeaturePayload current(
            @RequestParam
            Double longitude,

            @RequestParam
            Double latitude,

            @RequestParam(defaultValue = "WGS84")
            CoordinateType coordinateType
    ) {
        return weatherService.getCurrentWeather(
                longitude,
                latitude,
                coordinateType
        );
    }
}
