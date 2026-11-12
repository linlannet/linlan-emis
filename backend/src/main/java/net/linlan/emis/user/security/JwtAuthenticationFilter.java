package net.linlan.emis.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.linlan.emis.common.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import java.io.IOException;

/**
 * JWT 认证过滤器（改编自 dsh-java-ontology），解析 Bearer 令牌并设置 TenantContext。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final String header;
    private final String prefix;
    private final boolean defaultDirectorEnabled;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            @Value("${emis.jwt.header:Authorization}") String header,
            @Value("${emis.jwt.prefix:Bearer }") String prefix,
            @Value("${emis.jwt.default-director-enabled:false}") boolean defaultDirectorEnabled) {
        this.jwtUtil = jwtUtil;
        this.header = header;
        this.prefix = prefix;
        this.defaultDirectorEnabled = defaultDirectorEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            boolean authenticated = false;
            if (token != null && jwtUtil.validateToken(token)) {
                Claims claims = jwtUtil.parseToken(token);
                TenantContext.setUserId(jwtUtil.getUserId(claims));
                TenantContext.setRoleLevel(jwtUtil.getRoleLevel(claims));
                TenantContext.setTenantId(jwtUtil.getTenantId(claims));
                authenticated = true;
            }
            if (!authenticated && defaultDirectorEnabled) {
                TenantContext.setUserId(1L);
                TenantContext.setRoleLevel(0);
                TenantContext.setTenantId(null);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(header);
        if (authHeader != null && authHeader.startsWith(prefix)) {
            return authHeader.substring(prefix.length());
        }
        return null;
    }
}
