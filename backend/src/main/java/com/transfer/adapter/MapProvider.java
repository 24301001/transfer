package com.transfer.adapter;

public interface MapProvider {
    String reverseGeocode(Double longitude, Double latitude);

    String navigationUrl(Double longitude, Double latitude);
}
