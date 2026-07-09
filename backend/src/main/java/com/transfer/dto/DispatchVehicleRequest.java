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
         * 指挥中心必须从 ETA 列表中手动选择车辆。
         * 后端不再自动选择最快车辆。
         */
        @NotNull
        Long vehicleId,

        /**
         * 接收该任务的清障/救援人员ID。
         */
        Long receiverUserId,

        /**
         * 指挥中心调度人员ID。
         */
        Long assignedByUserId,

        @Size(max = 1000)
        String advice
) {
}
