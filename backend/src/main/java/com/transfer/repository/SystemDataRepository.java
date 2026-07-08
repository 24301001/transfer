package com.transfer.repository;

import com.transfer.enums.SystemDataCategory;
import com.transfer.model.SystemData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SystemDataRepository extends JpaRepository<SystemData, Long>, JpaSpecificationExecutor<SystemData> {

    boolean existsByCategoryAndCode(SystemDataCategory category, String code);

    Optional<SystemData> findByCategoryAndCode(SystemDataCategory category, String code);
}
