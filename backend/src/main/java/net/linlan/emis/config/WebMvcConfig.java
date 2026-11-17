package net.linlan.emis.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.linlan.emis.common.TenantContext;
import net.linlan.emis.user.security.RoleInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册 角色拦截器（fail-closed）与 租户上下文 Header 注入拦截器（服务间传播）。
 * Header 透传默认关闭：不可信请求头不得覆盖 JWT 身份，避免绕过 @RequiresRole。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;
    private final boolean headerTrustEnabled;

    public WebMvcConfig(RoleInterceptor roleInterceptor,
                        @Value("${emis.tenant.header-trust-enabled:false}") boolean headerTrustEnabled) {
        this.roleInterceptor = roleInterceptor;
        this.headerTrustEnabled = headerTrustEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor);
        registry.addInterceptor(new TenantContextInterceptor(headerTrustEnabled));
    }

    /** 服务间调用时通过 Header 透传租户/角色/用户；仅在显式开启可信链路时生效 */
    static class TenantContextInterceptor implements HandlerInterceptor {

        private final boolean trustEnabled;

        TenantContextInterceptor(boolean trustEnabled) {
            this.trustEnabled = trustEnabled;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (!trustEnabled) {
                return true;
            }
            String tenantIdStr = request.getHeader("X-Tenant-Id");
            String roleLevelStr = request.getHeader("X-Role-Level");
            String userIdStr = request.getHeader("X-User-Id");

            if (tenantIdStr != null && !tenantIdStr.isEmpty()) {
                try {
                    TenantContext.setTenantId(Long.parseLong(tenantIdStr));
                } catch (NumberFormatException ignored) {
                }
            }
            if (roleLevelStr != null && !roleLevelStr.isEmpty()) {
                try {
                    TenantContext.setRoleLevel(Integer.parseInt(roleLevelStr));
                } catch (NumberFormatException ignored) {
                }
            }
            if (userIdStr != null && !userIdStr.isEmpty()) {
                try {
                    TenantContext.setUserId(Long.parseLong(userIdStr));
                } catch (NumberFormatException ignored) {
                }
            }
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            TenantContext.clear();
        }
    }
}