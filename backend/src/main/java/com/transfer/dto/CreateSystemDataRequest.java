package com.transfer.dto;

import com.transfer.enums.SystemDataCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSystemDataRequest(
        @NotNull SystemDataCategory category,
        @NotBlank String code,
        @NotBlank String name,
        String value,
        String description,
        Boolean enabled,
        Integer sortOrder
) {
}
