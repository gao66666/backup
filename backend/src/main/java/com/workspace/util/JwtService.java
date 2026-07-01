package com.workspace.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发 + 校验。
 *
 * 密钥从环境变量 JWT_SECRET 读(开发期 application.yml 里给一个默认值)。
 * 对齐 Monsora 鉴权:用 JWT sub claim 存 userId(UUID 字符串)。
 *
 * ⚠️ 开发期 token 默认 24h 过期,生产应该改成更短或用 refresh token。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(
            @Value("${jwt.secret:dev-only-secret-please-change-me-must-be-at-least-32-chars}") String secret,
            @Value("${jwt.ttl-millis:86400000}") long ttlMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMillis;
    }

    /**
     * 签发 token。userId 作为 sub claim。
     */
    public String issue(UUID userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 校验 token,返回 sub claim 里的 userId。无 / 失效 / 伪造都抛异常。
     */
    public UUID verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }
}