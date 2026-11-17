package net.linlan.emis.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.linlan.emis.common.Result;
import net.linlan.emis.common.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 角色权限拦截器（改编自 dsh-java-ontology），fail-closed：
 * 未标注 @PublicApi 且未标注 @RequiresRole 的端点默认拒绝。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if ("/error".equals(request.getRequestURI())) {
            return true;
        }
        if (handlerMethod.getMethodAnnotation(PublicApi.class) != null) {
            return true;
        }
        RequiresRole annotation = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (annotation == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.forbidden("未授权的端点")));
            return false;
        }
        Integer currentRoleLevel = TenantContext.getRoleLevel();
        if (currentRoleLevel == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.unauthorized("未登录")));
            return false;
        }
        if (currentRoleLevel > annotation.value()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.forbidden("权限不足")));
            return false;
        }
        return true;
    }
}
