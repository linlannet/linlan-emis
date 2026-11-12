package net.linlan.emis.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.jsonwebtoken.Claims;
import net.linlan.emis.user.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 令牌 claims 契约测试：验证两条签发路径使用同一套 claims。
 * 统一契约：sub = userId；tenant_id / role_level / user_name（+ preferred_username）使用 snake_case。
 */
class JwtClaimsContractTest {

    @Test
    void sasTokenCustomizerWritesUnifiedClaims() throws Exception {
        EmisUserDetails user = new EmisUserDetails(7L, 1024L, 1, "alice", "secret", true);
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getPrincipal()).thenReturn(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(context.getClaims()).thenReturn(claims);

        OAuth2TokenCustomizer<JwtEncodingContext> customizer = new SecurityConfig().jwtTokenCustomizer();
        customizer.customize(context);

        KeyPair keyPair = generateRsaKey();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(toRsaKey(keyPair)));
        String token = new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims.build()))
                .getTokenValue();

        Jwt jwt = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build().decode(token);
        assertEquals("7", jwt.getSubject());
        assertEquals(1024L, number(jwt.getClaim("tenant_id")).longValue());
        assertEquals(1, number(jwt.getClaim("role_level")).intValue());
        assertEquals("alice", jwt.getClaimAsString("user_name"));
        assertEquals("alice", jwt.getClaimAsString("preferred_username"));
        assertNull(jwt.getClaim("tenantId"));
        assertNull(jwt.getClaim("roleLevel"));
        assertNull(jwt.getClaim("userName"));
    }

    @Test
    void legacyJwtUtilAlsoWritesUnifiedClaims() {
        JwtUtil jwtUtil = new JwtUtil("linlan-emis-test-secret-0123456789abcdef", 60_000L);

        Claims claims = jwtUtil.parseToken(jwtUtil.generateToken(7L, "alice", 1, 1024L));

        assertEquals("7", claims.getSubject());
        assertEquals("alice", jwtUtil.getUserName(claims));
        assertEquals(1, jwtUtil.getRoleLevel(claims));
        assertEquals(1024L, jwtUtil.getTenantId(claims));
        assertNull(claims.get("userName"));
        assertNull(claims.get("roleLevel"));
        assertNull(claims.get("tenantId"));
    }

    private static Number number(Object value) {
        return (Number) value;
    }

    private static RSAKey toRsaKey(KeyPair keyPair) {
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    private static KeyPair generateRsaKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
