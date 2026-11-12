# 04 · 权限模型设计（@RequiresRole fail-closed + role_level）

> 文档定位：定义 linlan-emis 的访问控制模型——`role_level` 层级语义、`@RequiresRole`(fail-closed) 拦截、
> 与上游 JWT、下游细粒度权限的关系。核心来自 dsh-java-ontology Phase 8 蓝图（已落地于 P0）。
> 关联：02（提供 `role_level`/`tenant_id`）、03（把 claim 编入 JWT）、01（下游接入）。

---

## 1. 设计原则

- **fail-closed（默认拒绝）**：任何未显式标注 `@PublicApi` 且未标注 `@RequiresRole` 的端点，**默认 403**。
- **角色层级数值化**：`role_level` 越小权限越大（0 主任 → 2 访客）。
- **租户 + 角色二维**：租户隔离（02）在前，角色校验在后；两者共同决定可访问性。
- **BuddyOne 只管"身份认证 + 租户 + 基础角色"**，下游业务系统的细粒度权限各自保留。

---

## 2. 角色层级

| `role_level` | 名称 | 语义 | 典型能力 |
|---|---|---|---|
| 0 | 主任 Director | 跨租户/租户管理/用户管理 | 管理台、用户增删 |
| 1 | 研究员 Researcher | 本租户业务操作 | 业务接口 |
| 2 | 访客 Guest | 只读/基础 | 公开 + 受限接口 |

校验规则：`@RequiresRole(N)` ⇒ 当前 `role_level <= N` 放行（见 §4）。

---

## 3. 认证链路（请求如何带上角色）

```
请求 ─▶ JwtAuthenticationFilter(Servlet Filter)
       解析 Bearer JWT → 取出 tenant_id / role_level / userId
       → TenantContext.setXxx(...)         （ThreadLocal）
     ─▶ DispatchServlet ─▶ RoleInterceptor(HandlerInterceptor)
       读取 handler 方法注解：
         @PublicApi         → 直接放行
         @RequiresRole(N)   → role_level<=N ? 放行 : 403 / 未登录 401
         无注解            → 403（fail-closed）
     ─▶ Controller
       业务可从 TenantContext.getTenantId()/getRoleLevel() 取当前上下文
```

> 代码已落地：
> - `backend/.../sys/security/JwtAuthenticationFilter.java`（改编 ontology，前缀 `emis.jwt.*`）
> - `backend/.../sys/security/RoleInterceptor.java`（fail-closed）
> - `backend/.../sys/security/RequiresRole.java`、`PublicApi.java`
> - `backend/.../plat/config/WebMvcConfig.java`（注册拦截器 + 租户 Header 透传）

---

## 4. 注解语义

```java
@Target(METHOD) @Retention(RUNTIME)
public @interface RequiresRole { int value(); }   // value = 允许的最大 role_level

@Target(METHOD) @Retention(RUNTIME)
public @interface PublicApi {}                    // 跳过角色校验
```

`RoleInterceptor.preHandle` 逻辑（已验证）：
- 有 `@PublicApi` → 放行。
- 无 `@PublicApi` 且无 `@RequiresRole` → **403**（fail-closed）。
- 有 `@RequiresRole(N)`：
  - `TenantContext.getRoleLevel() == null` → **401 未登录**；
  - `role_level > N` → **403 权限不足**；
  - 否则放行。

验证结果（P0 实测）：
- `/api/demo/public`（@PublicApi）→ 200
- `/api/demo/admin`（@RequiresRole(0)）无 token → 401
- `/api/demo/me`（@RequiresRole(2)）admin token → 200（roleLevel=0）
- `/api/auth/user`（@RequiresRole(0)）guest token（roleLevel=2）→ 403

---

## 5. 与 emis RBAC 的关系

- emis 有完整 RBAC（`role` 表、菜单/权限点、`SysRoleController`），粒度更细。
- 本设计**有意只取 `role_level` 三层基础角色**作为跨产品统一语义，避免把 emis 的复杂 RBAC 强塞进中台。
- 演进路径：若需更细权限，可将 emis 的 role/permission 表接入，把 `@RequiresRole` 升级为支持具体权限点的 `@PreAuthorize("hasAuthority('user:delete')")`，但 `role_level` 仍作为"纵向层级"保留。

---

## 6. 接入下游后的权限闭环

当 03 的 OAuth2 AS 上线后：
1. 用户经 OIDC 登录，BuddyOne 签发的 JWT 含 `tenant_id`/`role_level`。
2. 下游（dsh-java/xiaolan）资源服务器解码 JWT，把 claim 注入 `TenantContext`（复用同名上下文）。
3. 下游 Controller 可继续用 `@RequiresRole` 或 Spring Security 的 `@PreAuthorize`，语义一致。
4. 服务间调用用 `X-Tenant-Id`/`X-Role-Level` Header 透传（已在 `WebMvcConfig.TenantContextInterceptor` 支持），实现租户上下文跨服务传播。

---

## 7. 后续增强（P3）

- `tenant_id` 行级强制隔离（MyBatis 拦截器，见 02 §3）。
- `@RequiresRole` 支持"纵向层级 + 横向权限点"组合。
- 主任跨租户审计、角色变更的事件通知。
