package net.linlan.emis.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（改编自 dsh-java-ontology 的 JwtUtil，前缀改为 emis.jwt.*）。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${emis.jwt.secret}") String secret,
            @Value("${emis.jwt.expiration-ms:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String userName, Integer roleLevel, Long tenantId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("user_name", userName)
                .claim("preferred_username", userName)
                .claim("role_level", roleLevel)
                .claim("tenant_id", tenantId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getUserName(Claims claims) {
        String userName = claims.get("user_name", String.class);
        return userName != null ? userName : claims.get("userName", String.class);
    }

    public Integer getRoleLevel(Claims claims) {
        return toInteger(claims.get("role_level"), claims.get("roleLevel"));
    }

    public Long getTenantId(Claims claims) {
        return toLong(claims.get("tenant_id"), claims.get("tenantId"));
    }

    private static Integer toInteger(Object primary, Object fallback) {
        Object value = primary != null ? primary : fallback;
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
    }

    private static Long toLong(Object primary, Object fallback) {
        Object value = primary != null ? primary : fallback;
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
