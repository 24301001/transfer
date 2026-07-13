package com.transfer.controller;

import com.transfer.dto.ClearanceRescueTaskResponse;
import com.transfer.dto.CreateDispatchTaskRequest;
import com.transfer.dto.UpdateTaskStatusRequest;
import com.transfer.model.DispatchTask;
import com.transfer.service.DispatchTaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dispatch-tasks")
public class DispatchTaskController {

    private final DispatchTaskService dispatchTaskService;

    public DispatchTaskController(DispatchTaskService dispatchTaskService) {
        this.dispatchTaskService = dispatchTaskService;
    }

    @PostMapping
    public ResponseEntity<DispatchTask> create(@Valid @RequestBody CreateDispatchTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dispatchTaskService.create(request));
    }

    @GetMapping
    public Page<DispatchTask> findAll(Pageable pageable) {
        return dispatchTaskService.findAll(pageable);
    }

    @GetMapping("/my")
    public List<DispatchTask> findMyTasks(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "receiverUserId", required = false) Long queryUserId
    ) {
        Long receiverUserId = queryUserId != null ? queryUserId : headerUserId;
        return dispatchTaskService.findMyTasks(receiverUserId);
    }

    @GetMapping("/my/current")
    public List<ClearanceRescueTaskResponse> findMyCurrentTasks(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "receiverUserId", required = false) Long queryUserId
    ) {
        Long receiverUserId = queryUserId != null ? queryUserId : headerUserId;
        return dispatchTaskService.findMyCurrentTasks(receiverUserId);
    }

    @GetMapping("/my/history")
    public List<ClearanceRescueTaskResponse> findMyHistoryTasks(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(value = "receiverUserId", required = false) Long queryUserId
    ) {
        Long receiverUserId = queryUserId != null ? queryUserId : headerUserId;
        return dispatchTaskService.findMyHistoryTasks(receiverUserId);
    }

    @GetMapping("/{taskId}")
    public DispatchTask findTask(@PathVariable Long taskId) {
        return dispatchTaskService.findTask(taskId);
    }

    @GetMapping("/{taskId}/clearance-rescue-detail")
    public ClearanceRescueTaskResponse findClearanceRescueTaskDetail(@PathVariable Long taskId) {
        return dispatchTaskService.findTaskDetail(taskId);
    }

    @PutMapping("/{taskId}/status")
    public DispatchTask updateStatus(@PathVariable Long taskId, @Valid @RequestBody UpdateTaskStatusRequest request) {
        return dispatchTaskService.updateStatus(taskId, request);
    }
}
