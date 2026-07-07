package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.JwtTokenProvider;
import com.transfer.common.PasswordUtils;
import com.transfer.common.ResourceNotFoundException;
import com.transfer.common.UnauthorizedException;
import com.transfer.dto.CurrentUserResponse;
import com.transfer.dto.LoginRequest;
import com.transfer.dto.LoginResponse;
import com.transfer.dto.RegisterRequest;
import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.model.UserAccount;
import com.transfer.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final OperationLogService operationLogService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserAccountRepository userAccountRepository,
            OperationLogService operationLogService,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userAccountRepository = userAccountRepository;
        this.operationLogService = operationLogService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = normalizeRequired(request.username(), "username");

        if (userAccountRepository.existsByUsername(username)) {
            throw new BadRequestException("Username already exists: " + username);
        }

        if (request.role() == UserRole.ADMIN) {
            throw new BadRequestException("Admin accounts must be created by an existing administrator");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setFullName(normalizeRequired(request.fullName(), "fullName"));
        user.setPhone(normalizeOptional(request.phone()));
        user.setEmail(normalizeOptional(request.email()));
        user.setRole(request.role() == null ? UserRole.FIELD_OFFICER : request.role());
        user.setStatus(UserStatus.ENABLED);
        user.setPasswordHash(PasswordUtils.sha256(request.password()));

        UserAccount saved = userAccountRepository.save(user);
        operationLogService.record(
                saved.getId(),
                "REGISTER",
                "UserAccount",
                saved.getId().toString(),
                null,
                saved.getUsername()
        );

        return buildLoginResponse(saved);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = normalizeRequired(request.username(), "username");

        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (user.getStatus() != UserStatus.ENABLED) {
            throw new UnauthorizedException("User account is disabled");
        }

        if (!PasswordUtils.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return buildLoginResponse(user);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(String authorizationHeader) {
        String token = jwtTokenProvider.resolveToken(authorizationHeader)
                .orElseThrow(() -> new UnauthorizedException("Missing Authorization bearer token"));

        JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseAndValidate(token);

        UserAccount user = userAccountRepository.findById(claims.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + claims.userId()));

        if (user.getStatus() != UserStatus.ENABLED) {
            throw new UnauthorizedException("User account is disabled");
        }

        return CurrentUserResponse.from(user);
    }

    private LoginResponse buildLoginResponse(UserAccount user) {
        return new LoginResponse(
                jwtTokenProvider.createToken(user),
                "Bearer",
                jwtTokenProvider.getExpirationSeconds(),
                CurrentUserResponse.from(user)
        );
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
