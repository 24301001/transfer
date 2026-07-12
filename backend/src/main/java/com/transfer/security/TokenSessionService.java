package com.transfer.security;

import com.transfer.common.JwtTokenProvider;

/**
 * Token 会话存储接口 — 支持 Redis 和本地内存两种实现。
 */
public interface TokenSessionService {

    void store(JwtTokenProvider.JwtClaims claims);

    void validate(JwtTokenProvider.JwtClaims claims);

    void revoke(String tokenId);

    void revokeAll(Long userId);
}
