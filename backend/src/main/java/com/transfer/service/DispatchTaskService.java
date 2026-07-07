package com.transfer.service;

import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.CreateDispatchTaskRequest;
import com.transfer.dto.UpdateTaskStatusRequest;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.NotificationChannel;
import com.transfer.enums.TaskStatus;
import com.transfer.model.DispatchTask;
import com.transfer.model.Incident;
import com.transfer.repository.DispatchTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DispatchTaskService {

    private final DispatchTaskRepository dispatchTaskRepository;
    private final IncidentService incidentService;
    private final NotificationService notificationService;
    private final OperationLogService operationLogService;
    private final RealtimeService realtimeService;

    public DispatchTaskService(
            DispatchTaskRepository dispatchTaskRepository,
            IncidentService incidentService,
            NotificationService notificationService,
            OperationLogService operationLogService,
            RealtimeService realtimeService
    ) {
        this.dispatchTaskRepository = dispatchTaskRepository;
        this.incidentService = incidentService;
        this.notificationService = notificationService;
        this.operationLogService = operationLogService;
        this.realtimeService = realtimeService;
    }

    public DispatchTask create(CreateDispatchTaskRequest request) {
        Incident incident = incidentService.findIncident(request.incidentId());
        DispatchTask task = new DispatchTask();
        task.setTaskNo(generateTaskNo());
        task.setIncidentId(incident.getId());
        task.setTaskType(request.taskType());
        task.setReceiverUserId(request.receiverUserId());
        task.setAssignedByUserId(request.assignedByUserId());
        task.setVehicleRequired(request.vehicleRequired());
        task.setVehicleType(request.vehicleType());
        task.setLocationName(incident.getLocationName());
        task.setRiskLevel(incident.getRiskLevel());
        task.setAdvice(request.advice() != null ? request.advice() : incident.getSuggestion());
        task.setStatus(TaskStatus.DISPATCHED);
        DispatchTask saved = dispatchTaskRepository.save(task);

        incidentService.updateStatus(incident.getId(), IncidentStatus.DISPATCHED);
        notificationService.send(
                request.receiverUserId(),
                NotificationChannel.SYSTEM,
                "New dispatch task",
                "Task " + saved.getTaskNo() + " for incident " + incident.getIncidentNo()
        );
        operationLogService.record(request.assignedByUserId(), "CREATE_DISPATCH_TASK", "DispatchTask", saved.getId().toString(), null, saved.getTaskNo());
        realtimeService.publish("DISPATCH_TASK_CREATED", Map.of("taskId", saved.getId(), "incidentId", incident.getId(), "status", saved.getStatus()));
        return saved;
    }

    public Page<DispatchTask> findAll(Pageable pageable) {
        return dispatchTaskRepository.findAll(pageable);
    }

    public List<DispatchTask> findMyTasks(Long receiverUserId) {
        if (receiverUserId == null) {
            return dispatchTaskRepository.findAll();
        }
        return dispatchTaskRepository.findByReceiverUserIdOrderByCreatedAtDesc(receiverUserId);
    }

    public DispatchTask updateStatus(Long taskId, UpdateTaskStatusRequest request) {
        DispatchTask task = dispatchTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch task not found: " + taskId));
        task.setStatus(request.status());
        task.setFeedback(request.feedback());
        if (request.status() == TaskStatus.PROCESSING) {
            incidentService.updateStatus(task.getIncidentId(), IncidentStatus.PROCESSING);
        }
        if (request.status() == TaskStatus.COMPLETED) {
            task.setCompletedAt(LocalDateTime.now());
            incidentService.updateStatus(task.getIncidentId(), IncidentStatus.CLEARED);
        }
        DispatchTask saved = dispatchTaskRepository.save(task);
        operationLogService.record(task.getReceiverUserId(), "UPDATE_TASK_STATUS", "DispatchTask", saved.getId().toString(), null, saved.getStatus().name());
        realtimeService.publish("DISPATCH_TASK_UPDATED", Map.of("taskId", saved.getId(), "incidentId", saved.getIncidentId(), "status", saved.getStatus()));
        return saved;
    }

    private String generateTaskNo() {
        return "TASK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + UUID.randomUUID().toString().substring(0, 6);
    }
}
