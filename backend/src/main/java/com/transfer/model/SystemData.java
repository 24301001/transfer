package com.transfer.model;

import com.transfer.enums.SystemDataCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "system_data",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_system_data_category_code",
                columnNames = {"category", "code"}
        )
)
public class SystemData extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SystemDataCategory category;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    /**
     * 用于保存道路等级、风险阈值、事故类型扩展参数等结构化或半结构化配置。
     */
    @Column(length = 2000)
    private String value;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Boolean enabled = true;

    private Integer sortOrder = 0;

    public SystemDataCategory getCategory() {
        return category;
    }

    public void setCategory(SystemDataCategory category) {
        this.category = category;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
