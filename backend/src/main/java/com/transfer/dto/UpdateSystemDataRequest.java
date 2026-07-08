package com.transfer.dto;

import com.transfer.enums.SystemDataCategory;

public record UpdateSystemDataRequest(
        SystemDataCategory category,
        String code,
        String name,
        String value,
        String description,
        Boolean enabled,
        Integer sortOrder
) {
}
