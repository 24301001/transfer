package com.transfer.dto;

import com.transfer.enums.CoordinateType;
import com.transfer.enums.VehicleStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record UpdateVehicleLocationRequest(

        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        Double longitude,

        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        Double latitude,

        CoordinateType coordinateType,

        Double speedKmh,

        VehicleStatus status,

        @Size(max = 255)
        String currentAddress
) {
}
