package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.PasswordUtils;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.dto.CreateUserRequest;
import com.transfer.dto.UpdateUserRequest;
import com.transfer.dto.UserResponse;
import com.transfer.enums.UserStatus;
import com.transfer.model.UserAccount;
import com.transfer.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final OperationLogService operationLogService;

    public UserService(UserAccountRepository userAccountRepository, OperationLogService operationLogService) {
        this.userAccountRepository = userAccountRepository;
        this.operationLogService = operationLogService;
    }

    public Page<UserResponse> findAll(Pageable pageable) {
        return userAccountRepository.findAll(pageable).map(UserResponse::from);
    }

    public UserResponse create(CreateUserRequest request) {
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new BadRequestException("Username already exists: " + request.username());
        }
        UserAccount user = new UserAccount();
        user.setFullName(request.fullName());
        user.setUsername(request.username());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setStatus(UserStatus.ENABLED);
        user.setPasswordHash(hashPassword(request.password()));
        UserAccount saved = userAccountRepository.save(user);
        operationLogService.record(null, "CREATE_USER", "UserAccount", saved.getId().toString(), null, saved.getUsername());
        return UserResponse.from(saved);
    }

    public UserResponse update(Long id, UpdateUserRequest request) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(hashPassword(request.password()));
        }
        UserAccount saved = userAccountRepository.save(user);
        operationLogService.record(null, "UPDATE_USER", "UserAccount", saved.getId().toString(), null, saved.getUsername());
        return UserResponse.from(saved);
    }

    public void delete(Long id) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        userAccountRepository.delete(user);
        operationLogService.record(null, "DELETE_USER", "UserAccount", id.toString(), null, user.getUsername());
    }

    private String hashPassword(String password) {
        return PasswordUtils.sha256(password);
    }
}
