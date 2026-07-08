package com.transfer.dto;

import com.transfer.enums.VehicleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DispatchVehicleRequest(

        /**
         * AMBULANCE 或 CLEARANCE_TRUCK。
         */
        @NotNull
        VehicleType vehicleType,

        /**
         * 可选。
         * 不传 vehicleId 时，后端自动选择预计最快到达的车辆。
         */
        Long vehicleId,

        /**
         * 指挥中心调度人员ID。
         */
        Long assignedByUserId,

        @Size(max = 1000)
        String advice
) {
}
