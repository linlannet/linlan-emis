# 01 · linlan-emis 总体架构设计（BuddyOne / 多租户 IAM·SSO 中台）

> 文档定位：说明 linlan-emis 的产品对标、系统定位、部署拓扑、技术栈对齐与演进分期。
> 关联：emis-java-backend（参考实现）、dsh-java-ontology（租户/权限蓝图）、linlan-eframe（框架底座）、dsh-java（下游资源服务）。

---

## 1. 产品对标

| 角色 | 产品 | 说明 |
|---|---|---|
| 对标 WorkBuddy 的 **IdP/SSO 中台** | **linlan-emis（代号 BuddyOne）** | 统一账号、多租户、OAuth2/OIDC 登录、基础角色 |
| 对标 WorkBuddy 的 **客户端** | **DSH-Desktop** | 桌面端，作为 OIDC 公共客户端登录 BuddyOne |
| 既有产品（改造为资源服务） | **dsh-java / linlan-xiaolan** | 作为 OAuth2 客户端/资源服务器消费 BuddyOne 签发的 JWT |

**核心判断**：身份/租户是跨产品中台，集中建一个 IdP 比每个产品各做一套更合理。DSH-Desktop 对标 WorkBuddy，BuddyOne 对标 WorkBuddy 背后的身份中台。

---

## 2. 系统定位

linlan-emis 是一个**独立部署的多租户身份与访问管理中台（IAM/SSO）**，职责边界：

- **认证（Authentication）**：本地账号（用户名/密码 BCrypt）、第三方联合登录（GitHub/Google/微信/飞书…）、OTP/短信、MFA。
- **授权（Authorization）**：标准 **OAuth2 + OIDC** 授权服务器，签发 access/refresh/id_token（JWT，JWKS 可验）。
- **租户（Tenant）**：`tenant_id` 多租户内核 + `TenantContext` + 行级隔离。
- **基础角色（Role）**：`role_level`（0 主任 / 1 研究员 / 2 访客）作为跨产品统一语义。
- **不做**：各下游产品自身的细粒度业务权限（由下游保留）。

---

## 3. 部署拓扑

```
                         ┌──────────────────────────────┐
                         │   linlan-emis (BuddyOne)     │
                         │   独立 Spring Boot 服务       │
                         │   SB 3.5.16 / Java 17        │
                         │ ┌──────────────────────────┐ │
                         │ │ 认证: 本地账号 + 第三方   │ │
                         │ │        + OTP/MFA          │ │
                         │ │ 授权: SAS 1.x (OIDC/JWKS) │ │
                         │ │ 租户: tenant + TenantCtx  │ │
                         │ └──────────────────────────┘ │
                         └───────────┬──────────────────┘
                                     │  OIDC (authorize/token/jwks/userinfo)
                ┌────────────────────┼─────────────────────┐
                │                    │                      │
        ┌───────▼──────┐     ┌───────▼───────┐      ┌───────▼──────┐
        │ DSH-Desktop  │     │   dsh-java    │      │ linlan-xiaolan│
        │ OIDC 公共    │     │ 资源服务器    │      │ (继承 eframe) │
        │ 客户端       │     │ (JWT 校验)    │      │ 资源服务器    │
        │              │     │ + 机器API-Key │      │              │
        └──────────────┘     └───────────────┘      └──────────────┘
```

- **DSH-Desktop / xiaolan**：OAuth2 公共/机密客户端（`oauth2Login()`），登录走标准 OIDC。
- **dsh-java**：改造为 **OAuth2 资源服务器**（`oauth2ResourceServer().jwt()`，通过 JWKS 校验 BuddyOne 签发的用户 JWT）；保留 `ApiKeyAuthInterceptor` 用于**服务间机器凭证**。
- **ontology 平台**：同理，复用 BuddyOne 的 `tenant_id`/`role_level` 语义。

---

## 4. 技术栈（与 dsh-java 对齐）

| 项 | 值 | 来源/说明 |
|---|---|---|
| Spring Boot | **3.5.16** | 与 dsh-java 一致 |
| Java | **17** | 与 dsh-java 一致 |
| MyBatis | **3.0.5** | `mybatis-spring-boot-starter`，与 dsh-java 一致 |
| JWT | jjwt 0.12.x | 与 dsh-java-ontology 一致 |
| 密码 | `spring-security-crypto` 6.5.x（BCrypt） | 由 SB BOM 管理，与 dsh-java `spring.security` 对齐 |
| OAuth2 AS | `spring-security-oauth2-authorization-server` **1.5.x** | 由 SB 3.5 BOM 管理（emis 旧版为 0.4.0，需大改，见 03） |
| 数据库 | MySQL（生产）/ H2（本地演示） | 与 dsh-java 一致 |

**复用来源**：
- `emis-java-backend`：`module-protocol`（AS/OIDC/SAML）、`module-authn`（OTP/MFA/第三方）、`module-provider`（IdP 联邦）、`module-user`、`module-app`（客户端注册）——提供**已验证的能力模板**。
- `dsh-java-ontology`：Phase 8 租户/权限蓝图（`tenant_id`/`role_level`/`TenantContext`/`@RequiresRole`/`RoleInterceptor` fail-closed）。
- `linlan-eframe`：框架 parent / starter（可选，若走"独立工程基于 eframe"路线）。

---

## 5. 演进分期

- **P0（已完成）**：最小后端骨架——本地账号登录 + JJWT 签发 + `tenant_id`/`role_level` + `TenantContext` + `@RequiresRole`(fail-closed)。见当前 `backend/` 代码。
- **P1**：第三方联合登录（搬 `emis-module-social`/`module-authn`）+ 短信/OTP 登录。
- **P2**：引入 **spring-authorization-server 1.x** 标准化 OAuth2/OIDC 端点 + JWKS；dsh-java/DSH-Desktop/xiaolan 改造为客户端/资源服务器（见 03）。
- **P3**：`tenant_id` 硬隔离（MyBatis 行级隔离插件）+ 权限语义统一（`role_level`/`@RequiresRole` 与下游对齐）。

---

## 6. 与当前最小后端的衔接

当前 `backend/` 已落地 P0：`SysUser(tenant_id,role_level)`、`TenantContext`、`JwtUtil`、`JwtAuthenticationFilter`、`@RequiresRole`、`RoleInterceptor`、`WebMvcConfig`。
后续 P2 会将"自签 JJWT"升级为"标准 OAuth2 AS 签发的 OIDC JWT"，但 `TenantContext`/`@RequiresRole`/`role_level` 语义保持不变，`JwtAuthenticationFilter` 改为信任 AS 签发的 JWT（或由 Spring Security `oauth2ResourceServer().jwt()` 接管）。
