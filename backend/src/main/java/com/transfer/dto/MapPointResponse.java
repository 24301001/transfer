package com.transfer.dto;

import com.transfer.enums.CoordinateType;

public record MapPointResponse(
        Double longitude,
        Double latitude,
        CoordinateType coordinateType
) {
}
