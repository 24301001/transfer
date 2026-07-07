package com.transfer.controller;

import com.transfer.dto.MapClientConfigResponse;
import com.transfer.dto.MapLocationResponse;
import com.transfer.dto.MapPointResponse;
import com.transfer.enums.CoordinateType;
import com.transfer.service.MapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maps")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    /**
     * 给前端返回百度地图 JS API 配置。
     */
    @GetMapping("/client-config")
    public MapClientConfigResponse clientConfig() {
        return mapService.clientConfig();
    }

    /**
     * 文字地址转换为百度坐标。
     *
     * 示例：
     * /api/v1/maps/geocode
     * ?address=上海市人民广场
     * &city=上海市
     */
    @GetMapping("/geocode")
    public MapLocationResponse geocode(
            @RequestParam
            String address,

            @RequestParam(required = false)
            String city
    ) {
        return mapService.geocode(
                address,
                city
        );
    }

    /**
     * 坐标转换为文字地址。
     */
    @GetMapping("/reverse-geocode")
    public MapLocationResponse reverseGeocode(
            @RequestParam
            Double longitude,

            @RequestParam
            Double latitude,

            @RequestParam(defaultValue = "WGS84")
            CoordinateType coordinateType
    ) {
        return mapService.reverseGeocode(
                longitude,
                latitude,
                coordinateType
        );
    }

    /**
     * 把 WGS84 或 GCJ02 转换为百度 BD09。
     */
    @GetMapping("/convert")
    public MapPointResponse convert(
            @RequestParam
            Double longitude,

            @RequestParam
            Double latitude,

            @RequestParam(defaultValue = "WGS84")
            CoordinateType coordinateType
    ) {
        return mapService.convertToBaidu(
                longitude,
                latitude,
                coordinateType
        );
    }
}
