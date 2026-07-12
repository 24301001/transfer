package com.transfer.controller;

import com.transfer.dto.ChangePasswordRequest;
import com.transfer.dto.CurrentUserResponse;
import com.transfer.dto.EmailCodeResponse;
import com.transfer.dto.MessageResponse;
import com.transfer.dto.ProfileEmailCodeRequest;
import com.transfer.dto.UpdateProfileNameRequest;
import com.transfer.security.ClientFingerprintService;
import com.transfer.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final ClientFingerprintService clientFingerprintService;

    public ProfileController(
            ProfileService profileService,
            ClientFingerprintService clientFingerprintService
    ) {
        this.profileService = profileService;
        this.clientFingerprintService = clientFingerprintService;
    }

    @GetMapping
    public CurrentUserResponse profile(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return profileService.profile(authorizationHeader);
    }

    @PutMapping("/name")
    public CurrentUserResponse updateName(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody UpdateProfileNameRequest request
    ) {
        return profileService.updateName(authorizationHeader, request);
    }

    @PostMapping("/password/email-code")
    public EmailCodeResponse sendPasswordEmailCode(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ProfileEmailCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        return profileService.sendChangePasswordEmailCode(
                authorizationHeader,
                request,
                clientFingerprintService.fingerprint(servletRequest)
        );
    }

    @PutMapping("/password")
    public MessageResponse changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return profileService.changePassword(authorizationHeader, request);
    }
}
