package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.CommandDispatchRequest;
import com.transfer.dto.CreateDispatchTaskRequest;
import com.transfer.dto.DispatchAssignmentRequest;
import com.transfer.dto.UpdateTaskStatusRequest;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.NotificationChannel;
import com.transfer.enums.TaskStatus;
import com.transfer.enums.TaskType;
import com.transfer.enums.UserStatus;
import com.transfer.model.DispatchTask;
import com.transfer.model.Incident;
import com.transfer.model.UserAccount;
import com.transfer.repository.DispatchTaskRepository;
import com.transfer.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DispatchTaskService {

    private static final List<TaskStatus>
            ACTIVE_STATUSES = List.of(
            TaskStatus.DISPATCHED,
            TaskStatus.DEPARTED,
            TaskStatus.ARRIVED,
            TaskStatus.PROCESSING
    );

    private final DispatchTaskRepository
            dispatchTaskRepository;

    private final UserAccountRepository
            userAccountRepository;

    private final IncidentService incidentService;
    private final NotificationService notificationService;
    private final OperationLogService operationLogService;
    private final RealtimeService realtimeService;

    public DispatchTaskService(
            DispatchTaskRepository dispatchTaskRepository,
            UserAccountRepository userAccountRepository,
            IncidentService incidentService,
            NotificationService notificationService,
            OperationLogService operationLogService,
            RealtimeService realtimeService
    ) {
        this.dispatchTaskRepository =
                dispatchTaskRepository;

        this.userAccountRepository =
                userAccountRepository;

        this.incidentService = incidentService;
        this.notificationService = notificationService;
        this.operationLogService = operationLogService;
        this.realtimeService = realtimeService;
    }

    /**
     * 原有的单任务创建接口。
     */
    @Transactional
    public DispatchTask create(
            CreateDispatchTaskRequest request
    ) {
        Incident incident =
                incidentService.findIncident(
                        request.incidentId()
                );

        DispatchTask task = createOne(
                incident,
                request.taskType(),
                request.receiverUserId(),
                request.assignedByUserId(),
                request.vehicleRequired(),
                request.vehicleType(),
                request.advice()
        );

        incidentService.updateStatus(
                incident.getId(),
                IncidentStatus.DISPATCHED
        );

        return task;
    }

    /**
     * 指挥中心批量调度。
     */
    @Transactional
    public List<DispatchTask> createBatch(
            Long incidentId,
            CommandDispatchRequest request
    ) {
        Incident incident =
                incidentService.findIncident(
                        incidentId
                );

        if (request.assignments() == null
                || request.assignments().isEmpty()) {
            throw new BadRequestException(
                    "At least one dispatch assignment is required"
            );
        }

        List<DispatchTask> tasks =
                new ArrayList<>();

        for (DispatchAssignmentRequest assignment
                : request.assignments()) {

            String advice = firstNonBlank(
                    assignment.advice(),
                    request.commonAdvice(),
                    incident.getSuggestion()
            );

            tasks.add(
                    createOne(
                            incident,
                            assignment.taskType(),
                            assignment.receiverUserId(),
                            request.assignedByUserId(),
                            assignment.vehicleRequired(),
                            assignment.vehicleType(),
                            advice
                    )
            );
        }

        incidentService.updateStatus(
                incident.getId(),
                IncidentStatus.DISPATCHED
        );

        return tasks;
    }

    @Transactional(readOnly = true)
    public Page<DispatchTask> findAll(
            Pageable pageable
    ) {
        return dispatchTaskRepository.findAll(
                pageable
        );
    }

    @Transactional(readOnly = true)
    public List<DispatchTask> findMyTasks(
            Long receiverUserId
    ) {
        if (receiverUserId == null) {
            return dispatchTaskRepository.findAll();
        }

        return dispatchTaskRepository
                .findByReceiverUserIdOrderByCreatedAtDesc(
                        receiverUserId
                );
    }

    @Transactional(readOnly = true)
    public List<DispatchTask> findByIncidentId(
            Long incidentId
    ) {
        incidentService.findIncident(incidentId);

        return dispatchTaskRepository
                .findByIncidentIdOrderByCreatedAtDesc(
                        incidentId
                );
    }

    @Transactional
    public DispatchTask updateStatus(
            Long taskId,
            UpdateTaskStatusRequest request
    ) {
        DispatchTask task =
                dispatchTaskRepository
                        .findById(taskId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Dispatch task not found: "
                                                + taskId
                                )
                        );

        task.setStatus(request.status());
        task.setFeedback(request.feedback());

        if (request.status()
                == TaskStatus.PROCESSING) {
            incidentService.updateStatus(
                    task.getIncidentId(),
                    IncidentStatus.PROCESSING
            );
        }

        if (request.status()
                == TaskStatus.COMPLETED) {
            task.setCompletedAt(
                    LocalDateTime.now()
            );

            incidentService.updateStatus(
                    task.getIncidentId(),
                    IncidentStatus.CLEARED
            );
        }

        DispatchTask saved =
                dispatchTaskRepository.save(task);

        operationLogService.record(
                task.getReceiverUserId(),
                "UPDATE_TASK_STATUS",
                "DispatchTask",
                saved.getId().toString(),
                null,
                saved.getStatus().name()
        );

        realtimeService.publish(
                "DISPATCH_TASK_UPDATED",
                Map.of(
                        "taskId",
                        saved.getId(),

                        "incidentId",
                        saved.getIncidentId(),

                        "status",
                        saved.getStatus()
                )
        );

        return saved;
    }

    private DispatchTask createOne(
            Incident incident,
            TaskType taskType,
            Long receiverUserId,
            Long assignedByUserId,
            Boolean vehicleRequired,
            String vehicleType,
            String advice
    ) {
        if (taskType == null) {
            throw new BadRequestException(
                    "taskType is required"
            );
        }

        if (receiverUserId != null) {
            validateReceiver(receiverUserId);

            boolean duplicate =
                    dispatchTaskRepository
                            .existsByIncidentIdAndTaskTypeAndReceiverUserIdAndStatusIn(
                                    incident.getId(),
                                    taskType,
                                    receiverUserId,
                                    ACTIVE_STATUSES
                            );

            if (duplicate) {
                throw new BadRequestException(
                        "该人员已经有同类型的未完成调度任务，"
                                + "receiverUserId="
                                + receiverUserId
                );
            }
        }

        DispatchTask task =
                new DispatchTask();

        task.setTaskNo(generateTaskNo());

        task.setIncidentId(
                incident.getId()
        );

        task.setTaskType(taskType);

        task.setReceiverUserId(
                receiverUserId
        );

        task.setAssignedByUserId(
                assignedByUserId
        );

        task.setVehicleRequired(
                Boolean.TRUE.equals(vehicleRequired)
        );

        task.setVehicleType(
                trimToNull(vehicleType)
        );

        task.setLocationName(
                incident.getLocationName()
        );

        task.setRiskLevel(
                incident.getRiskLevel()
        );

        task.setAdvice(
                firstNonBlank(
                        advice,
                        incident.getSuggestion()
                )
        );

        task.setStatus(
                TaskStatus.DISPATCHED
        );

        DispatchTask saved =
                dispatchTaskRepository.save(task);

        if (receiverUserId != null) {
            notificationService.send(
                    receiverUserId,
                    NotificationChannel.SYSTEM,
                    "新的调度任务",
                    "任务 "
                            + saved.getTaskNo()
                            + "，事故编号 "
                            + incident.getIncidentNo()
            );
        }

        operationLogService.record(
                assignedByUserId,
                "CREATE_DISPATCH_TASK",
                "DispatchTask",
                saved.getId().toString(),
                null,
                saved.getTaskNo()
        );

        realtimeService.publish(
                "DISPATCH_TASK_CREATED",
                Map.of(
                        "taskId",
                        saved.getId(),

                        "incidentId",
                        incident.getId(),

                        "status",
                        saved.getStatus()
                )
        );

        return saved;
    }

    private UserAccount validateReceiver(
            Long receiverUserId
    ) {
        UserAccount user =
                userAccountRepository
                        .findById(receiverUserId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Receiver user not found: "
                                                + receiverUserId
                                )
                        );

        if (user.getStatus()
                != UserStatus.ENABLED) {
            throw new BadRequestException(
                    "Receiver user is disabled: "
                            + receiverUserId
            );
        }

        return user;
    }

    private String generateTaskNo() {
        return "TASK"
                + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern(
                                "yyyyMMddHHmmss"
                        )
                )
                + UUID.randomUUID()
                .toString()
                .substring(0, 6);
    }

    private String firstNonBlank(
            String... values
    ) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null
                    && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private String trimToNull(String value) {
        return value == null
                || value.trim().isEmpty()
                ? null
                : value.trim();
    }
}
