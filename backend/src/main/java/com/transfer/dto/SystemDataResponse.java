package com.transfer.dto;

import com.transfer.enums.SystemDataCategory;
import com.transfer.model.SystemData;

import java.time.LocalDateTime;

public record SystemDataResponse(
        Long id,
        SystemDataCategory category,
        String code,
        String name,
        String value,
        String description,
        Boolean enabled,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SystemDataResponse from(SystemData data) {
        return new SystemDataResponse(
                data.getId(),
                data.getCategory(),
                data.getCode(),
                data.getName(),
                data.getValue(),
                data.getDescription(),
                data.getEnabled(),
                data.getSortOrder(),
                data.getCreatedAt(),
                data.getUpdatedAt()
        );
    }
}
