package com.transfer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "dispatch_decisions")
public class DispatchDecision extends AuditableEntity {

    /** 事故现场事件主键 */
    @Column(nullable = false)
    private Long incidentId;

    /** 指挥人员主键 */
    @Column(nullable = false)
    private Long commandUserId;

    /** 清障人员主键 */
    private Long rescueUserId;

    /** 清障中心主键 */
    private Long rescueCenterId;

    /** 关联调度任务 */
    private Long dispatchTaskId;

    /**
     * Agent 内容：AI 生成的指挥调度分析建议。
     * 包含风险评估、资源推荐、路线规划、处置优先级等决策支持内容。
     */
    @Lob
    private String agentContent;

    /** 人工决策摘要 */
    @Column(length = 1000)
    private String decisionSummary;

    /** 决策类型: AUTO / MANUAL / HYBRID */
    @Column(nullable = false, length = 16)
    private String decisionType = "HYBRID";

    /** 决策状态: DRAFT / ISSUED / EXECUTED / CLOSED */
    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    // ====== getters & setters ======

    public Long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(Long incidentId) {
        this.incidentId = incidentId;
    }

    public Long getCommandUserId() {
        return commandUserId;
    }

    public void setCommandUserId(Long commandUserId) {
        this.commandUserId = commandUserId;
    }

    public Long getRescueUserId() {
        return rescueUserId;
    }

    public void setRescueUserId(Long rescueUserId) {
        this.rescueUserId = rescueUserId;
    }

    public Long getRescueCenterId() {
        return rescueCenterId;
    }

    public void setRescueCenterId(Long rescueCenterId) {
        this.rescueCenterId = rescueCenterId;
    }

    public Long getDispatchTaskId() {
        return dispatchTaskId;
    }

    public void setDispatchTaskId(Long dispatchTaskId) {
        this.dispatchTaskId = dispatchTaskId;
    }

    public String getAgentContent() {
        return agentContent;
    }

    public void setAgentContent(String agentContent) {
        this.agentContent = agentContent;
    }

    public String getDecisionSummary() {
        return decisionSummary;
    }

    public void setDecisionSummary(String decisionSummary) {
        this.decisionSummary = decisionSummary;
    }

    public String getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(String decisionType) {
        this.decisionType = decisionType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
