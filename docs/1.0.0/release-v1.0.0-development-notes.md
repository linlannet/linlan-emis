# linlan-emis (BuddyOne) v1.0.0 开发说明

> 版本：`v1.0.0` | 开发周期：规划 + P0 落地（2026-11） | 基线：P0 最小后端骨架

## 1. 版本概览

v1.0.0 以**企业多租户 IAM/SSO 中台**为主线，包含两块内容：

1. **P0 最小后端骨架（已完成）**：本地账号登录（BCrypt）+ JJWT 自签 + `tenant_id`/`role_level` + `TenantContext` + `@RequiresRole`(fail-closed)，配套 `test-client` 演示 OIDC 授权码登录。
2. **标准 OAuth2/OIDC 授权服务器 + 下游 SSO（规划/进行中）**：引入 `spring-security-oauth2-authorization-server` 1.x，签发可 JWKS 验签的 RSA JWT，把 `tenant_id`/`role_level` 编入 claims，使 DSH-Desktop/xiaolan 走标准 OIDC 登录、dsh-java 走资源服务器校验。

另包含：AI 可发现性文档（`AGENTS.md`/`llms.txt`/`README.en.md`）、设计四件套（`docs/1.0.0/`）、backend/test-client pom 统一 `1.0.0`。

## 2. 版本亮点

### 2.1 本地账号认证（P0 已落地）

- `POST /api/auth/login` `{ userName, password }` → `Result<LoginResponse>`；BCrypt 哈希。
- 默认账号：`admin/admin123`(role_level=0)、`guest/guest123`(role_level=2)。
- `TenantContext` 全链路租户/角色上下文；`@RequiresRole` fail-closed 默认 403。

### 2.2 多租户内核（软隔离）

- `sys_user` 带 `tenant_id`；种子租户 `tenant_id=1`「默认租户」。
- 主任（`role_level=0`，`tenant_id IS NULL`）可跨租户；其余强制本租户。

### 2.3 标准 OAuth2/OIDC（规划）

- SAS 1.x：`/oauth2/authorize` `/oauth2/token` `/oauth2/jwks` `/oauth2/userinfo` `/.well-known/openid-configuration`。
- RSA 密钥（固定密钥库）+ `OAuth2TokenCustomizer` 把 `tenant_id`/`role_level` 编入 JWT。
- RegisteredClient 落库（Jdbc）。

### 2.4 下游 SSO 接入

- `test-client` 已验证标准 OIDC 授权码登录并解析 id_token claims。
- DSH-Desktop / xiaolan：OIDC 客户端；dsh-java：资源服务器（保留 `ApiKeyAuthInterceptor` 走机器调用）。

### 2.5 AI 可发现性

- `llms.txt`：面向 LLM 的项目摘要（能力、技术栈、命令、模块地图、关联仓库）。
- `AGENTS.md`：AI 编码助手的构建/测试命令、模块布局、代码约定与禁止事项。
- `README.en.md`：英文 README。

## 3. 任务映射（spec §5 → 实现）

| spec 能力 | 实现落位 | 状态 |
| --- | --- | --- |
| §5.1 本地账号认证 | `AuthController` + BCrypt + `DataInitializer` | 已完成 |
| §5.2 多租户模型与隔离 | `SysUser`/`emis_tenant` + `TenantContext` | 软隔离已落地 |
| §5.3 OAuth2/OIDC AS | `AuthorizationServerConfig` + Jdbc Client + JWKSource + TokenCustomizer | 待办 |
| §5.4 权限模型 | `@RequiresRole`/`@PublicApi` + `RoleInterceptor` | 已完成 |
| §5.5 下游 SSO | `test-client`（参考）+ 下游改造 | 参考实现已落地 |
| §5.6 版本治理 | pom 1.0.0、CHANGELOG、四件套 | 已完成 |
| §5.7 AI 可发现性 | `AGENTS.md`/`llms.txt`/`README.en.md` | 已完成 |

## 4. 验证结论

- P0：`mvn clean package` 构建通过；`RoleInterceptor` 三态实测（@PublicApi→200、无 token 受保护→401、guest 访问 admin→403）通过。
- `test-client` 标准 OIDC 授权码登录 BuddyOne 并解析 id_token 的 `tenant_id`/`role_level` 通过。
- SAS 1.x 全流程（授权码 + JWKS 验签 + claim 注入）待集成测试补齐（依赖 AS 落地）。

## 5. 已知限制

1. **OAuth2/OIDC AS 尚未落地**：v1.0.0 当前以 P0 自签 JJWT 提供本地登录；SAS 1.x 为规划项（见 tasks §4）。
2. **租户软隔离**：v1.0.0 为约定式软隔离，未强制 SQL 注入（`WHERE tenant_id`）；硬隔离留 P3。
3. **第三方登录 / MFA 缺失**：GitHub/Google/微信/飞书与 OTP/SMS 为 P1 规划。
4. **下游改造未全部完成**：dsh-java 资源服务器、DSH-Desktop/xiaolan OIDC 客户端接入进行中。
5. **frontend / py-api 占位**：Web 控制台与 Python API 尚未实现。

## 6. 依赖与体积增量

- **新增第三方依赖**：`spring-security-oauth2-authorization-server` 1.x（由 SB 3.5 BOM 管理，无额外版本声明）。
- **新增数据库表**：SAS 官方 3 张（`oauth2_registered_client`/`oauth2_authorization`/`oauth2_authorization_consent`）+ `emis_tenant`。
- **新增 Maven 模块：无**（全部落在 backend 内）。
- **版本统一**：backend / test-client pom 均为 `1.0.0`。
