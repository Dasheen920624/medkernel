package com.medkernel.engine.security.bootstrap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 首发部署 init token 服务：登记、校验和一次性消费均只处理 hash 后的 token。
 */
@Service
public class BootstrapInitTokenService {

    private final BootstrapInitTokenRepository tokens;
    private final Clock clock;

    @Autowired
    public BootstrapInitTokenService(BootstrapInitTokenRepository tokens) {
        this(tokens, Clock.systemUTC());
    }

    BootstrapInitTokenService(BootstrapInitTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public BootstrapInitToken registerDeploymentToken(String rawToken,
                                                     Duration ttl,
                                                     String actor,
                                                     String traceId) {
        Instant now = Instant.now(clock);
        String tokenHash = sha256(requireToken(rawToken));
        java.util.Optional<BootstrapInitToken> existing = tokens.findFirstByTokenHashOrderByCreatedAtDesc(tokenHash);
        if (existing.isPresent()) {
            return existing.get();
        }
        BootstrapInitToken token = new BootstrapInitToken(
            null,
            "init-" + UUID.randomUUID(),
            tokenHash,
            BootstrapInitTokenStatus.ACTIVE.name(),
            now.plus(ttl),
            null,
            null,
            now,
            actor,
            now,
            actor,
            traceId);
        return tokens.save(token);
    }

    @Transactional
    public BootstrapInitToken validate(String rawToken) {
        Instant now = Instant.now(clock);
        BootstrapInitToken token = tokens.findFirstByTokenHashOrderByCreatedAtDesc(sha256(requireToken(rawToken)))
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_007));
        if (!token.active()) {
            throw new ApiException(ErrorCode.ENG_AUTH_009);
        }
        if (!token.expiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.ENG_AUTH_008);
        }
        return token;
    }

    @Transactional
    public BootstrapInitToken consume(String rawToken, String usedBy, String traceId) {
        Instant now = Instant.now(clock);
        BootstrapInitToken token = validate(rawToken);
        return tokens.save(token.withStatus(BootstrapInitTokenStatus.USED, now, usedBy, traceId));
    }

    static String sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }

    private String requireToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(ErrorCode.ENG_AUTH_007);
        }
        return rawToken.trim();
    }
}
