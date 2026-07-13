package com.transfer.controller;

import com.transfer.dto.AdminHealthResponse;
import com.transfer.enums.UserRole;
import com.transfer.security.RequireRoles;
import com.transfer.service.SystemHealthService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireRoles(UserRole.ADMIN)
@RequestMapping("/api/v1/admin")
public class AdminHealthController {

    private final SystemHealthService systemHealthService;

    public AdminHealthController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    /**
     * 管理员系统健康详情。建议管理端每 20～30 秒刷新一次。
     */
    @GetMapping("/health")
    public ResponseEntity<AdminHealthResponse> health() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(systemHealthService.getHealth());
    }
}
