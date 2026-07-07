package com.transfer.adapter;

import org.springframework.stereotype.Component;

@Component
public class BaiduMapProvider implements MapProvider {

    @Override
    public String reverseGeocode(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            return "";
        }
        return "BaiduMap reverse geocode placeholder: " + longitude + "," + latitude;
    }

    @Override
    public String navigationUrl(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            return "";
        }
        return "https://api.map.baidu.com/marker?location=" + latitude + "," + longitude + "&output=html";
    }
}
