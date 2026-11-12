package net.linlan.emis.plat.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.linlan.emis.common.TenantContext;
import net.linlan.emis.user.security.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册 角色拦截器（fail-closed）与 租户上下文 Header 注入拦截器（服务间传播）。
 * 改编自 dsh-java-ontology 的 WebMvcConfig。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;

    public WebMvcConfig(RoleInterceptor roleInterceptor) {
        this.roleInterceptor = roleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor);
        registry.addInterceptor(new TenantContextInterceptor());
    }

    /** 服务间调用时通过 Header 透传租户/角色/用户（内部可信链路使用） */
    static class TenantContextInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
