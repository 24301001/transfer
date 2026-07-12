package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.PasswordUtils;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.RolePermissionResponse;
import com.transfer.dto.UpdateUserRequest;
import com.transfer.dto.UserResponse;
import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.model.UserAccount;
import com.transfer.repository.UserAccountRepository;
import com.transfer.security.TokenSessionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final OperationLogService operationLogService;
    private final TokenSessionService tokenSessionService;

    public UserService(
            UserAccountRepository userAccountRepository,
            OperationLogService operationLogService,
            TokenSessionService tokenSessionService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.operationLogService = operationLogService;
        this.tokenSessionService = tokenSessionService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userAccountRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        return update(id, request, null, null);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long operatorUserId, String ipAddress) {
        UserAccount user = findUser(id);

        if (request.username() != null) {
            String username = normalizeRequired(request.username(), "username");
            userAccountRepository.findByUsername(username)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new BadRequestException("Username already exists: " + username);
                    });
            user.setUsername(username);
        }
        if (request.fullName() != null) {
            user.setFullName(normalizeRequired(request.fullName(), "fullName"));
        }
        if (request.phone() != null) {
            user.setPhone(normalizeOptional(request.phone()));
        }
        if (request.email() != null) {
            String email = normalizeOptionalEmail(request.email());
            if (email != null) {
                userAccountRepository.findByEmail(email)
                        .filter(existing -> !existing.getId().equals(id))
                        .ifPresent(existing -> {
                            throw new BadRequestException("Email already exists: " + email);
                        });
            }
            if ((email == null && user.getEmail() != null) || (email != null && !email.equalsIgnoreCase(user.getEmail()))) {
                user.setEmailVerified(false);
            }
            user.setEmail(email);
        }
        if (request.role() != null) {
            validateLastEnabledAdmin(
                    user,
                    request.role(),
                    request.status() == null ? user.getStatus() : request.status()
            );
            user.setRole(request.role());
        }
        if (request.status() != null) {
            validateLastEnabledAdmin(user, request.role() == null ? user.getRole() : request.role(), request.status());
            user.setStatus(request.status());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(hashPassword(request.password()));
        }

        UserAccount saved = userAccountRepository.save(user);
        tokenSessionService.revokeAll(saved.getId());
        operationLogService.record(
                operatorUserId,
                "UPDATE_USER",
                "UserAccount",
                saved.getId().toString(),
                ipAddress,
                saved.getUsername() + ", role=" + saved.getRole() + ", status=" + saved.getStatus()
        );
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateStatus(Long id, UserStatus status, Long operatorUserId, String ipAddress) {
        if (status == null) {
            throw new BadRequestException("status is required");
        }
        UserAccount user = findUser(id);
        validateLastEnabledAdmin(user, user.getRole(), status);
        user.setStatus(status);
        UserAccount saved = userAccountRepository.save(user);
        tokenSessionService.revokeAll(saved.getId());
        operationLogService.record(
                operatorUserId,
                "UPDATE_USER_STATUS",
                "UserAccount",
                saved.getId().toString(),
                ipAddress,
                saved.getUsername() + ", status=" + saved.getStatus()
        );
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse disable(Long id, Long operatorUserId, String ipAddress) {
        return updateStatus(id, UserStatus.DISABLED, operatorUserId, ipAddress);
    }

    @Transactional
    public UserResponse enable(Long id, Long operatorUserId, String ipAddress) {
        return updateStatus(id, UserStatus.ENABLED, operatorUserId, ipAddress);
    }

    @Transactional
    public void delete(Long id) {
        delete(id, null, null);
    }

    @Transactional
    public void delete(Long id, Long operatorUserId, String ipAddress) {
        UserAccount user = findUser(id);
        validateLastEnabledAdmin(user, null, UserStatus.DISABLED);
        userAccountRepository.delete(user);
        tokenSessionService.revokeAll(id);
        operationLogService.record(
                operatorUserId,
                "DELETE_USER",
                "UserAccount",
                id.toString(),
                ipAddress,
                user.getUsername()
        );
    }

    @Transactional(readOnly = true)
    public List<RolePermissionResponse> findRolePermissions() {
        List<RolePermissionResponse> responses = new ArrayList<>();
        responses.add(new RolePermissionResponse(
                UserRole.FIELD_OFFICER,
                "现场交警/巡查人员",
                List.of("事故上报", "事故附件上传", "查看本人上报记录")
        ));
        responses.add(new RolePermissionResponse(
                UserRole.COMMAND_CENTER,
                "交警指挥中心",
                List.of("查看事故列表", "查看事故详情", "处置支援判断", "创建调度任务", "指挥中心大屏")
        ));
        responses.add(new RolePermissionResponse(
                UserRole.RESCUE_WORKER,
                "道路清障/救援人员",
                List.of("查看本人任务", "更新任务状态", "填写处置反馈")
        ));
        responses.add(new RolePermissionResponse(
                UserRole.ADMIN,
                "系统管理员",
                List.of("用户账号管理", "事故历史查询", "系统数据维护", "操作日志查看", "系统运行检查")
        ));
        return responses;
    }

    private UserAccount findUser(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private void validateLastEnabledAdmin(UserAccount user, UserRole targetRole, UserStatus targetStatus) {
        boolean currentlyEnabledAdmin = user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ENABLED;
        boolean willRemainEnabledAdmin = targetRole == UserRole.ADMIN && targetStatus == UserStatus.ENABLED;
        if (!currentlyEnabledAdmin || willRemainEnabledAdmin) {
            return;
        }
        long enabledAdminCount = userAccountRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ENABLED);
        if (enabledAdminCount <= 1) {
            throw new BadRequestException("At least one enabled administrator account must be retained");
        }
    }

    private String hashPassword(String password) {
        return PasswordUtils.hash(password);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeOptionalEmail(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
