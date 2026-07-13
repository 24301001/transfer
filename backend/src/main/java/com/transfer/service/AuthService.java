package com.transfer.service;

import com.transfer.common.BadRequestException;
import com.transfer.common.JwtTokenProvider;
import com.transfer.common.PasswordUtils;
import com.transfer.common.UnauthorizedException;
import com.transfer.dto.CurrentUserResponse;
import com.transfer.dto.EmailCodeRequest;
import com.transfer.dto.EmailCodeResponse;
import com.transfer.dto.LoginRequest;
import com.transfer.dto.LoginResponse;
import com.transfer.dto.MessageResponse;
import com.transfer.dto.PasswordResetRequest;
import com.transfer.dto.RegisterRequest;
import com.transfer.dto.SliderCaptchaChallengeResponse;
import com.transfer.dto.SliderCaptchaVerifyRequest;
import com.transfer.dto.SliderCaptchaVerifyResponse;
import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.enums.VerificationPurpose;
import com.transfer.model.UserAccount;
import com.transfer.repository.UserAccountRepository;
import com.transfer.security.AuthenticatedUser;
import com.transfer.security.JwtAuthenticationService;
import com.transfer.security.TokenSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final OperationLogService operationLogService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationService jwtAuthenticationService;
    private final TokenSessionService tokenSessionService;
    private final VerificationCodeService verificationCodeService;
    private final SliderCaptchaService sliderCaptchaService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            OperationLogService operationLogService,
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationService jwtAuthenticationService,
            TokenSessionService tokenSessionService,
            VerificationCodeService verificationCodeService,
            SliderCaptchaService sliderCaptchaService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.operationLogService = operationLogService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtAuthenticationService = jwtAuthenticationService;
        this.tokenSessionService = tokenSessionService;
        this.verificationCodeService = verificationCodeService;
        this.sliderCaptchaService = sliderCaptchaService;
    }

    public SliderCaptchaChallengeResponse createSliderCaptcha(String fingerprintHash) {
        return sliderCaptchaService.createChallenge(fingerprintHash);
    }

    public SliderCaptchaVerifyResponse verifySliderCaptcha(
            SliderCaptchaVerifyRequest request,
            String fingerprintHash
    ) {
        return sliderCaptchaService.verify(
                request.captchaId(),
                request.sliderX(),
                fingerprintHash
        );
    }

    @Transactional(readOnly = true)
    public EmailCodeResponse sendEmailCode(
            EmailCodeRequest request,
            String fingerprintHash
    ) {
        VerificationPurpose purpose = request.purpose();
        if (purpose == VerificationPurpose.REGISTER) {
            String email = normalizeRequiredEmail(request.email());
            if (userAccountRepository.existsByEmail(email)) {
                throw new BadRequestException("Email already exists: " + email);
            }
            sliderCaptchaService.consumeVerificationToken(request.sliderToken(), fingerprintHash);
            return verificationCodeService.sendEmailCode(
                    purpose,
                    registerTargetKey(email),
                    email
            );
        }

        if (purpose == VerificationPurpose.LOGIN) {
            UserAccount user = findByUsernameForAuth(request.username());
            validateUserCanUseEmailCode(user);
            sliderCaptchaService.consumeVerificationToken(request.sliderToken(), fingerprintHash);
            return verificationCodeService.sendEmailCode(
                    purpose,
                    loginTargetKey(user),
                    user.getEmail()
            );
        }

        if (purpose == VerificationPurpose.RESET_PASSWORD) {
            UserAccount user = findByUsernameForAuth(request.username());
            String email = normalizeRequiredEmail(request.email());
            if (!email.equalsIgnoreCase(nullToEmpty(user.getEmail()))) {
                throw new BadRequestException("用户名与邮箱不匹配");
            }
            validateUserCanUseEmailCode(user);
            sliderCaptchaService.consumeVerificationToken(request.sliderToken(), fingerprintHash);
            return verificationCodeService.sendEmailCode(
                    purpose,
                    resetPasswordTargetKey(user),
                    user.getEmail()
            );
        }

        throw new BadRequestException("CHANGE_PASSWORD 邮箱验证码请调用 /api/v1/profile/password/email-code");
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = normalizeRequired(request.username(), "username");
        String email = normalizeRequiredEmail(request.email());

        if (userAccountRepository.existsByUsername(username)) {
            throw new BadRequestException("Username already exists: " + username);
        }
        if (userAccountRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already exists: " + email);
        }
        if (request.role() == UserRole.ADMIN) {
            throw new BadRequestException("Admin accounts must be created by an existing administrator");
        }

        verificationCodeService.validateEmailCode(
                VerificationPurpose.REGISTER,
                registerTargetKey(email),
                request.emailCode()
        );

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setFullName(normalizeRequired(request.fullName(), "fullName"));
        user.setPhone(normalizeOptional(request.phone()));
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setRole(request.role() == null ? UserRole.FIELD_OFFICER : request.role());
        user.setStatus(UserStatus.ENABLED);
        user.setPasswordHash(PasswordUtils.hash(request.password()));

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
    public LoginResponse login(LoginRequest request, String fingerprintHash) {
        String username = normalizeRequired(request.username(), "username");

        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (user.getStatus() != UserStatus.ENABLED) {
            throw new UnauthorizedException("User account is disabled");
        }
        if (!PasswordUtils.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        // 滑块验证码校验
        String sliderToken = request.sliderToken();
        if (sliderToken != null && !sliderToken.isBlank()) {
            sliderCaptchaService.consumeVerificationToken(sliderToken, fingerprintHash);
        } else {
            // 开发模式下无 sliderToken 也能登录（方便调试）
            // 生产环境应改为必填
        }

        // 邮箱验证码（可选）
        String emailCode = request.emailCode();
        if (emailCode != null && !emailCode.isBlank()) {
            verificationCodeService.validateEmailCode(
                    VerificationPurpose.LOGIN,
                    loginTargetKey(user),
                    emailCode
            );
        }

        return buildLoginResponse(user);
    }

    @Transactional
    public MessageResponse resetPassword(PasswordResetRequest request) {
        UserAccount user = findByUsernameForAuth(request.username());
        String email = normalizeRequiredEmail(request.email());
        if (!email.equalsIgnoreCase(nullToEmpty(user.getEmail()))) {
            throw new BadRequestException("用户名与邮箱不匹配");
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            throw new UnauthorizedException("User account is disabled");
        }

        verificationCodeService.validateEmailCode(
                VerificationPurpose.RESET_PASSWORD,
                resetPasswordTargetKey(user),
                request.emailCode()
        );

        user.setPasswordHash(PasswordUtils.hash(request.newPassword()));
        userAccountRepository.save(user);
        tokenSessionService.revokeAll(user.getId());
        operationLogService.record(
                user.getId(),
                "RESET_PASSWORD",
                "UserAccount",
                user.getId().toString(),
                null,
                user.getUsername()
        );

        return new MessageResponse("密码已重置，请使用新密码登录");
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(String authorizationHeader) {
        return CurrentUserResponse.from(loadCurrentUser(authorizationHeader));
    }

    @Transactional(readOnly = true)
    public UserAccount loadCurrentUser(String authorizationHeader) {
        String token = resolveBearerToken(authorizationHeader);
        return jwtAuthenticationService.authenticate(token).user();
    }

    public MessageResponse logout(String authorizationHeader) {
        String token = resolveBearerToken(authorizationHeader);
        AuthenticatedUser authenticated = jwtAuthenticationService.authenticate(token);
        tokenSessionService.revoke(authenticated.tokenId());
        return new MessageResponse("已退出登录");
    }

    public MessageResponse logoutAll(String authorizationHeader) {
        String token = resolveBearerToken(authorizationHeader);
        AuthenticatedUser authenticated = jwtAuthenticationService.authenticate(token);
        tokenSessionService.revokeAll(authenticated.userId());
        return new MessageResponse("已退出该账号的全部登录会话");
    }

    public String loginTargetKey(UserAccount user) {
        return verificationCodeService.emailTargetKey(
                VerificationPurpose.LOGIN,
                String.valueOf(user.getId())
        );
    }

    public String resetPasswordTargetKey(UserAccount user) {
        return verificationCodeService.emailTargetKey(
                VerificationPurpose.RESET_PASSWORD,
                String.valueOf(user.getId())
        );
    }

    public String changePasswordTargetKey(UserAccount user) {
        return verificationCodeService.emailTargetKey(
                VerificationPurpose.CHANGE_PASSWORD,
                String.valueOf(user.getId())
        );
    }

    private String registerTargetKey(String email) {
        return verificationCodeService.emailTargetKey(VerificationPurpose.REGISTER, email);
    }

    private LoginResponse buildLoginResponse(UserAccount user) {
        JwtTokenProvider.IssuedToken issuedToken = jwtTokenProvider.issueToken(user);
        tokenSessionService.store(issuedToken.claims());
        return new LoginResponse(
                issuedToken.token(),
                "Bearer",
                jwtTokenProvider.getExpirationSeconds(),
                CurrentUserResponse.from(user)
        );
    }

    private String resolveBearerToken(String authorizationHeader) {
        return jwtTokenProvider.resolveToken(authorizationHeader)
                .orElseThrow(() -> new UnauthorizedException("Missing Authorization bearer token"));
    }

    private UserAccount findByUsernameForAuth(String usernameValue) {
        String username = normalizeRequired(usernameValue, "username");
        return userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("用户不存在"));
    }

    private void validateUserCanUseEmailCode(UserAccount user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("当前账号未绑定邮箱，无法发送邮箱验证码");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeRequiredEmail(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException("email is required");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}