package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.model.DispatchDecision;
import com.transfer.model.DispatchTask;
import com.transfer.model.Incident;
import com.transfer.model.RescueCenter;
import com.transfer.model.UserAccount;
import com.transfer.repository.DispatchDecisionRepository;
import com.transfer.repository.DispatchTaskRepository;
import com.transfer.repository.IncidentRepository;
import com.transfer.repository.RescueCenterRepository;
import com.transfer.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 指挥中心调度决策服务。
 * 负责创建、查询指挥调度决策记录，包含 AI Agent 分析内容。
 */
@Service
public class DispatchDecisionService {

    private final DispatchDecisionRepository decisionRepository;
    private final IncidentRepository incidentRepository;
    private final UserAccountRepository userAccountRepository;
    private final RescueCenterRepository rescueCenterRepository;
    private final DispatchTaskRepository dispatchTaskRepository;
    private final OperationLogService operationLogService;
    private final RealtimeService realtimeService;

    public DispatchDecisionService(
            DispatchDecisionRepository decisionRepository,
            IncidentRepository incidentRepository,
            UserAccountRepository userAccountRepository,
            RescueCenterRepository rescueCenterRepository,
            DispatchTaskRepository dispatchTaskRepository,
            OperationLogService operationLogService,
            RealtimeService realtimeService
    ) {
        this.decisionRepository = decisionRepository;
        this.incidentRepository = incidentRepository;
        this.userAccountRepository = userAccountRepository;
        this.rescueCenterRepository = rescueCenterRepository;
        this.dispatchTaskRepository = dispatchTaskRepository;
        this.operationLogService = operationLogService;
        this.realtimeService = realtimeService;
    }

    /**
     * 创建指挥调度决策（包含 Agent 分析内容）。
     * <p>
     * 关联关系：
     * incidentId   → incidents (事故现场事件)
     * commandUserId → app_users (指挥人员)
     * rescueUserId  → app_users (清障人员)
     * rescueCenterId → rescue_centers (清障中心)
     * dispatchTaskId → dispatch_tasks (调度任务，可选)
     */
    @Transactional
    public DispatchDecision create(DispatchDecision decision) {
        if (decision.getIncidentId() == null) {
            throw new BadRequestException("incidentId is required");
        }
        if (decision.getCommandUserId() == null) {
            throw new BadRequestException("commandUserId is required");
        }

        // 验证各外键存在
        incidentRepository.findById(decision.getIncidentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Incident not found: " + decision.getIncidentId()));

        userAccountRepository.findById(decision.getCommandUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Command user not found: " + decision.getCommandUserId()));

        if (decision.getRescueUserId() != null) {
            userAccountRepository.findById(decision.getRescueUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Rescue user not found: " + decision.getRescueUserId()));
        }

        if (decision.getRescueCenterId() != null) {
            rescueCenterRepository.findById(decision.getRescueCenterId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Rescue center not found: " + decision.getRescueCenterId()));
        }

        if (decision.getDispatchTaskId() != null) {
            dispatchTaskRepository.findById(decision.getDispatchTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Dispatch task not found: " + decision.getDispatchTaskId()));
        }

        if (decision.getDecisionType() == null || decision.getDecisionType().isBlank()) {
            decision.setDecisionType("HYBRID");
        }
        if (decision.getStatus() == null || decision.getStatus().isBlank()) {
            decision.setStatus("DRAFT");
        }

        DispatchDecision saved = decisionRepository.save(decision);

        operationLogService.record(
                decision.getCommandUserId(),
                "CREATE_DISPATCH_DECISION",
                "DispatchDecision",
                saved.getId().toString(),
                null,
                "Incident #" + decision.getIncidentId()
        );

        realtimeService.publish("DISPATCH_DECISION_CREATED",
                java.util.Map.of(
                        "decisionId", saved.getId(),
                        "incidentId", decision.getIncidentId()
                ));

        return saved;
    }

    /**
     * 根据事故 ID 查询所有调度决策（按时间倒序）。
     */
    @Transactional(readOnly = true)
    public List<DispatchDecision> findByIncidentId(Long incidentId) {
        return decisionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
    }

    /**
     * 根据指挥人员查询其创建的决策。
     */
    @Transactional(readOnly = true)
    public List<DispatchDecision> findByCommandUserId(Long commandUserId) {
        return decisionRepository.findByCommandUserIdOrderByCreatedAtDesc(commandUserId);
    }

    /**
     * 根据清障人员查询相关的决策。
     */
    @Transactional(readOnly = true)
    public List<DispatchDecision> findByRescueUserId(Long rescueUserId) {
        return decisionRepository.findByRescueUserIdOrderByCreatedAtDesc(rescueUserId);
    }

    /**
     * 更新决策状态。
     */
    @Transactional
    public DispatchDecision updateStatus(Long decisionId, String status) {
        DispatchDecision decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DispatchDecision not found: " + decisionId));
        decision.setStatus(status);
        return decisionRepository.save(decision);
    }
}
