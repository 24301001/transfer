package com.transfer.controller;

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
import com.transfer.security.ClientFingerprintService;
import com.transfer.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientFingerprintService clientFingerprintService;

    public AuthController(
            AuthService authService,
            ClientFingerprintService clientFingerprintService
    ) {
        this.authService = authService;
        this.clientFingerprintService = clientFingerprintService;
    }

    @PostMapping("/slider-captcha/challenge")
    public SliderCaptchaChallengeResponse createSliderCaptcha(HttpServletRequest request) {
        return authService.createSliderCaptcha(clientFingerprintService.fingerprint(request));
    }

    @PostMapping("/slider-captcha/verify")
    public SliderCaptchaVerifyResponse verifySliderCaptcha(
            @Valid @RequestBody SliderCaptchaVerifyRequest request,
            HttpServletRequest servletRequest
    ) {
        return authService.verifySliderCaptcha(
                request,
                clientFingerprintService.fingerprint(servletRequest)
        );
    }

    @PostMapping("/email-code")
    public EmailCodeResponse sendEmailCode(
            @Valid @RequestBody EmailCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        return authService.sendEmailCode(
                request,
                clientFingerprintService.fingerprint(servletRequest)
        );
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return authService.resetPassword(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return authService.currentUser(authorizationHeader);
    }

    @PostMapping("/logout")
    public MessageResponse logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return authService.logout(authorizationHeader);
    }

    @PostMapping("/logout-all")
    public MessageResponse logoutAll(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return authService.logoutAll(authorizationHeader);
    }
}
