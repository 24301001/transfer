package com.transfer.controller;

import com.transfer.dto.CreateUserRequest;
import com.transfer.dto.UpdateUserRequest;
import com.transfer.dto.UserResponse;
import com.transfer.model.OperationLog;
import com.transfer.service.OperationLogService;
import com.transfer.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserService userService;
    private final OperationLogService operationLogService;

    public AdminController(UserService userService, OperationLogService operationLogService) {
        this.userService = userService;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/users")
    public Page<UserResponse> findUsers(Pageable pageable) {
        return userService.findAll(pageable);
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/operation-logs")
    public Page<OperationLog> findOperationLogs(Pageable pageable) {
        return operationLogService.findAll(pageable);
    }
}
