package net.linlan.emis.user.controller;

import net.linlan.emis.common.Result;
import net.linlan.emis.common.TenantContext;
import net.linlan.emis.user.security.PublicApi;
import net.linlan.emis.user.security.RequiresRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 演示端点：展示 fail-closed 权限模型 与 租户上下文。
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @PublicApi
    @GetMapping("/public")
    public Result<String> publicApi() {
        return Result.success("公开接口，无需登录");
    }

    /** 任意已登录用户可访问，回显当前租户/角色上下文 */
    @RequiresRole(2)
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userId", TenantContext.getUserId());
        ctx.put("tenantId", TenantContext.getTenantId());
        ctx.put("roleLevel", TenantContext.getRoleLevel());
        return Result.success(ctx);
    }

    /** 仅主任（roleLevel=0）可访问 */
    @RequiresRole(0)
    @GetMapping("/admin")
    public Result<String> admin() {
        return Result.success("租户管理台：仅主任可访问");
    }
}
