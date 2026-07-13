package com.transfer.controller;

import com.transfer.dto.OperationLogResponse;
import com.transfer.dto.UpdateUserRequest;
import com.transfer.dto.UpdateUserStatusRequest;
import com.transfer.dto.UserResponse;
import com.transfer.dto.RolePermissionResponse;
import com.transfer.service.OperationLogService;
import com.transfer.service.UserService;
import com.transfer.enums.UserRole;
import com.transfer.security.RequireRoles;
import com.transfer.security.RequestSecurityAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequireRoles(UserRole.ADMIN)
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserService userService;
    private final OperationLogService operationLogService;

    public AdminController(
            UserService userService,
            OperationLogService operationLogService
    ) {
        this.userService = userService;
        this.operationLogService = operationLogService;
    }

    // ====== 用户管理 ======

    @GetMapping("/users")
    public Page<UserResponse> findUsers(Pageable pageable) {
        return userService.findAll(pageable);
    }

    @GetMapping("/users/{id}")
    public UserResponse findUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request,
            HttpServletRequest servletRequest
    ) {
        Long operatorUserId = RequestSecurityAttributes
                .requireAuthenticatedUser(servletRequest)
                .userId();
        return userService.update(id, request, operatorUserId, clientIp(servletRequest));
    }

    @PatchMapping("/users/{id}/status")
    public UserResponse updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        Long operatorUserId = RequestSecurityAttributes
                .requireAuthenticatedUser(servletRequest)
                .userId();
        return userService.updateStatus(id, request.status(), operatorUserId, clientIp(servletRequest));
    }

    @PostMapping("/users/{id}/disable")
    public UserResponse disableUser(
            @PathVariable Long id,
            HttpServletRequest servletRequest
    ) {
        Long operatorUserId = RequestSecurityAttributes
                .requireAuthenticatedUser(servletRequest)
                .userId();
        return userService.disable(id, operatorUserId, clientIp(servletRequest));
    }

    @PostMapping("/users/{id}/enable")
    public UserResponse enableUser(
            @PathVariable Long id,
            HttpServletRequest servletRequest
    ) {
        Long operatorUserId = RequestSecurityAttributes
                .requireAuthenticatedUser(servletRequest)
                .userId();
        return userService.enable(id, operatorUserId, clientIp(servletRequest));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            HttpServletRequest servletRequest
    ) {
        Long operatorUserId = RequestSecurityAttributes
                .requireAuthenticatedUser(servletRequest)
                .userId();
        userService.delete(id, operatorUserId, clientIp(servletRequest));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    public List<RolePermissionResponse> findRolePermissions() {
        return userService.findRolePermissions();
    }

    // ====== 操作日志（仅用户账号相关操作） ======

    @GetMapping("/operation-logs")
    public Page<OperationLogResponse> findOperationLogs(
            @RequestParam(required = false) Long operatorUserId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            Pageable pageable
    ) {
        return operationLogService.findByFilters(
                operatorUserId,
                operationType,
                "UserAccount",
                objectId,
                keyword,
                startTime,
                endTime,
                pageable
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
