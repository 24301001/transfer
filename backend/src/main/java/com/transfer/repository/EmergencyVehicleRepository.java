package com.transfer.repository;

import com.transfer.enums.VehicleStatus;
import com.transfer.enums.VehicleType;
import com.transfer.model.EmergencyVehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyVehicleRepository
        extends JpaRepository<EmergencyVehicle, Long> {

    boolean existsByVehicleNo(String vehicleNo);

    long countByStatus(VehicleStatus status);

    List<EmergencyVehicle>
    findByVehicleTypeOrderByVehicleNoAsc(
            VehicleType vehicleType
    );

    List<EmergencyVehicle>
    findByStatusOrderByVehicleNoAsc(
            VehicleStatus status
    );

    List<EmergencyVehicle>
    findByVehicleTypeAndStatusOrderByVehicleNoAsc(
            VehicleType vehicleType,
            VehicleStatus status
    );
}
