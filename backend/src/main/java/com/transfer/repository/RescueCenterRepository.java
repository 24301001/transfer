package com.transfer.repository;

import com.transfer.model.RescueCenter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RescueCenterRepository extends JpaRepository<RescueCenter, Long> {

    List<RescueCenter> findByStatus(String status);

    List<RescueCenter> findByCenterType(String centerType);
}
