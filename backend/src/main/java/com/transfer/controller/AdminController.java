package com.transfer.controller;

import com.transfer.dto.AdminSystemStatusResponse;
import com.transfer.dto.CreateSystemDataRequest;
import com.transfer.dto.CreateUserRequest;
import com.transfer.dto.IncidentDetailResponse;
import com.transfer.dto.IncidentHistoryResponse;
import com.transfer.dto.OperationLogResponse;
import com.transfer.dto.NotificationRecordResponse;
import com.transfer.dto.RolePermissionResponse;
import com.transfer.dto.SystemDataResponse;
import com.transfer.dto.UpdateSystemDataRequest;
import com.transfer.dto.UpdateSystemDataStatusRequest;
import com.transfer.dto.UpdateUserRequest;
import com.transfer.dto.UpdateUserStatusRequest;
import com.transfer.dto.UserResponse;
import com.transfer.enums.IncidentStatus;
import com.transfer.enums.NotificationChannel;
import com.transfer.enums.NotificationStatus;
import com.transfer.enums.RiskLevel;
import com.transfer.enums.SystemDataCategory;
import com.transfer.service.AdminManagementService;
import com.transfer.service.IncidentService;
import com.transfer.service.OperationLogService;
import com.transfer.service.SystemDataService;
import com.transfer.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserService userService;
    private final OperationLogService operationLogService;
    private final SystemDataService systemDataService;
    private final AdminManagementService adminManagementService;
    private final IncidentService incidentService;

    public AdminController(
            UserService userService,
            OperationLogService operationLogService,
            SystemDataService systemDataService,
            AdminManagementService adminManagementService,
            IncidentService incidentService
    ) {
        this.userService = userService;
        this.operationLogService = operationLogService;
        this.systemDataService = systemDataService;
        this.adminManagementService = adminManagementService;
        this.incidentService = incidentService;
    }

    @GetMapping("/users")
    public Page<UserResponse> findUsers(Pageable pageable) {
        return userService.findAll(pageable);
    }

    @GetMapping("/users/{id}")
    public UserResponse findUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.create(request, operatorUserId, clientIp(servletRequest)));
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        return userService.update(id, request, operatorUserId, clientIp(servletRequest));
    }

    @PatchMapping("/users/{id}/status")
    public UserResponse updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        return userService.updateStatus(id, request.status(), operatorUserId, clientIp(servletRequest));
    }

    @PostMapping("/users/{id}/disable")
    public UserResponse disableUser(
            @PathVariable Long id,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        return userService.disable(id, operatorUserId, clientIp(servletRequest));
    }

    @PostMapping("/users/{id}/enable")
    public UserResponse enableUser(
            @PathVariable Long id,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        return userService.enable(id, operatorUserId, clientIp(servletRequest));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        userService.delete(id, operatorUserId, clientIp(servletRequest));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    public List<RolePermissionResponse> findRolePermissions() {
        return userService.findRolePermissions();
    }

    @GetMapping("/incidents/history")
    public Page<IncidentHistoryResponse> findIncidentHistory(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) String accidentType,
            @RequestParam(required = false) String roadName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            Pageable pageable
    ) {
        return adminManagementService.findIncidentHistory(
                status,
                riskLevel,
                accidentType,
                roadName,
                keyword,
                startTime,
                endTime,
                pageable
        );
    }

    @GetMapping("/incidents/{incidentId}")
    public IncidentDetailResponse findIncidentDetail(@PathVariable Long incidentId) {
        return incidentService.findDetail(incidentId);
    }

    @GetMapping("/system-data")
    public Page<SystemDataResponse> findSystemData(
            @RequestParam(required = false) SystemDataCategory category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        return systemDataService.findAll(category, enabled, keyword, pageable);
    }

    @GetMapping("/system-data/{id}")
    public SystemDataResponse findSystemDataById(@PathVariable Long id) {
        return systemDataService.findById(id);
    }

    @PostMapping("/system-data")
    public ResponseEntity<SystemDataResponse> createSystemData(
            @Valid @RequestBody CreateSystemDataRequest request,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(systemDataService.create(request, operatorUserId, clientIp(servletRequest)));
    }

    @PutMapping("/system-data/{id}")
    public SystemDataResponse updateSystemData(
            @PathVariable Long id,
            @RequestBody UpdateSystemDataRequest request,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        return systemDataService.update(id, request, operatorUserId, clientIp(servletRequest));
    }

    @PatchMapping("/system-data/{id}/status")
    public SystemDataResponse updateSystemDataStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSystemDataStatusRequest request,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        return systemDataService.updateEnabled(id, request.enabled(), operatorUserId, clientIp(servletRequest));
    }

    @DeleteMapping("/system-data/{id}")
    public ResponseEntity<Void> deleteSystemData(
            @PathVariable Long id,
            @RequestParam(value = "operatorUserId", required = false) Long operatorUserId,
            HttpServletRequest servletRequest
    ) {
        systemDataService.delete(id, operatorUserId, clientIp(servletRequest));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/operation-logs")
    public Page<OperationLogResponse> findOperationLogs(
            @RequestParam(required = false) Long operatorUserId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            Pageable pageable
    ) {
        return operationLogService.findByFilters(
                operatorUserId,
                operationType,
                objectType,
                objectId,
                keyword,
                startTime,
                endTime,
                pageable
        );
    }



    @GetMapping("/notification-records")
    public Page<NotificationRecordResponse> findNotificationRecords(
            @RequestParam(required = false) Long receiverUserId,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            Pageable pageable
    ) {
        return adminManagementService.findNotificationRecords(
                receiverUserId,
                channel,
                status,
                keyword,
                startTime,
                endTime,
                pageable
        );
    }

    @GetMapping("/system/status")
    public AdminSystemStatusResponse findSystemStatus() {
        return adminManagementService.findSystemStatus();
    }

    @GetMapping("/system/api-call-logs")
    public List<OperationLogResponse> findRecentApiCallLogs() {
        return operationLogService.findRecentByOperationType("API_CALL");
    }

    @GetMapping("/system/exception-logs")
    public List<OperationLogResponse> findRecentExceptionLogs() {
        return operationLogService.findRecentByOperationType("EXCEPTION");
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
