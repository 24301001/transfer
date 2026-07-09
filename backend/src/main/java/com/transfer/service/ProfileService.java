package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.PasswordUtils;
import com.transfer.common.UnauthorizedException;
import com.transfer.dto.ChangePasswordRequest;
import com.transfer.dto.CurrentUserResponse;
import com.transfer.dto.EmailCodeResponse;
import com.transfer.dto.MessageResponse;
import com.transfer.dto.ProfileEmailCodeRequest;
import com.transfer.dto.UpdateProfileNameRequest;
import com.transfer.enums.VerificationPurpose;
import com.transfer.model.UserAccount;
import com.transfer.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final AuthService authService;
    private final UserAccountRepository userAccountRepository;
    private final OperationLogService operationLogService;
    private final VerificationCodeService verificationCodeService;

    public ProfileService(
            AuthService authService,
            UserAccountRepository userAccountRepository,
            OperationLogService operationLogService,
            VerificationCodeService verificationCodeService
    ) {
        this.authService = authService;
        this.userAccountRepository = userAccountRepository;
        this.operationLogService = operationLogService;
        this.verificationCodeService = verificationCodeService;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse profile(String authorizationHeader) {
        return CurrentUserResponse.from(authService.loadCurrentUser(authorizationHeader));
    }

    @Transactional
    public CurrentUserResponse updateName(String authorizationHeader, UpdateProfileNameRequest request) {
        UserAccount user = authService.loadCurrentUser(authorizationHeader);
        String fullName = normalizeRequired(request.fullName(), "fullName");
        user.setFullName(fullName);
        UserAccount saved = userAccountRepository.save(user);
        operationLogService.record(
                saved.getId(),
                "UPDATE_PROFILE_NAME",
                "UserAccount",
                saved.getId().toString(),
                null,
                saved.getUsername()
        );
        return CurrentUserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EmailCodeResponse sendChangePasswordEmailCode(
            String authorizationHeader,
            ProfileEmailCodeRequest request
    ) {
        UserAccount user = authService.loadCurrentUser(authorizationHeader);
        verificationCodeService.validateCaptcha(request.captchaId(), request.captchaCode());
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("当前账号未绑定邮箱，无法发送邮箱验证码");
        }
        return verificationCodeService.sendEmailCode(
                VerificationPurpose.CHANGE_PASSWORD,
                authService.changePasswordTargetKey(user),
                user.getEmail()
        );
    }

    @Transactional
    public MessageResponse changePassword(String authorizationHeader, ChangePasswordRequest request) {
        UserAccount user = authService.loadCurrentUser(authorizationHeader);
        verificationCodeService.validateCaptcha(request.captchaId(), request.captchaCode());

        if (!PasswordUtils.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("原密码错误");
        }
        if (PasswordUtils.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("新密码不能与原密码相同");
        }

        verificationCodeService.validateEmailCode(
                VerificationPurpose.CHANGE_PASSWORD,
                authService.changePasswordTargetKey(user),
                request.emailCode()
        );

        user.setPasswordHash(PasswordUtils.hash(request.newPassword()));
        userAccountRepository.save(user);
        operationLogService.record(
                user.getId(),
                "CHANGE_PASSWORD",
                "UserAccount",
                user.getId().toString(),
                null,
                user.getUsername()
        );

        return new MessageResponse("密码修改成功，请重新登录");
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }
}
