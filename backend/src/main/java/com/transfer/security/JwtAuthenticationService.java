package com.transfer.security;

import com.transfer.common.JwtTokenProvider;
import com.transfer.common.UnauthorizedException;
import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.model.UserAccount;
import com.transfer.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JwtAuthenticationService {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenSessionService tokenSessionService;
    private final UserAccountRepository userAccountRepository;

    public JwtAuthenticationService(
            JwtTokenProvider jwtTokenProvider,
            TokenSessionService tokenSessionService,
            UserAccountRepository userAccountRepository
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenSessionService = tokenSessionService;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser authenticate(String token) {
        JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseAndValidate(token);
        tokenSessionService.validate(claims);

        UserAccount user = userAccountRepository.findById(claims.userId())
                .orElseThrow(() -> new UnauthorizedException("Token user no longer exists"));

        if (user.getStatus() != UserStatus.ENABLED) {
            tokenSessionService.revoke(claims.tokenId());
            throw new UnauthorizedException("User account is disabled");
        }

        if (!user.getUsername().equals(claims.username())
                || !user.getRole().name().equals(claims.role())) {
            tokenSessionService.revoke(claims.tokenId());
            throw new UnauthorizedException("Token user information is stale; please sign in again");
        }

        UserRole role;
        try {
            role = UserRole.valueOf(claims.role());
        } catch (Exception ex) {
            tokenSessionService.revoke(claims.tokenId());
            throw new UnauthorizedException("Invalid role in token");
        }

        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                role,
                claims.tokenId(),
                user
        );
    }
}
