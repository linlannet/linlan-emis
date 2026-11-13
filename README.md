# linlan-emis（BuddyOne）

> 企业多租户身份认证与访问管理中台（IAM/SSO）。统一账号、多租户、OAuth2/OIDC 登录与基础角色。
> 技术栈与 dsh-java 对齐：Spring Boot 3.5.16 / Java 17 / MyBatis 3.0.5 / jjwt 0.12.6。
> **For AI**：[llms.txt](./llms.txt) · [AGENTS.md](./AGENTS.md)

linlan-emis（代号 **BuddyOne**）是独立部署的多租户身份与访问管理中台，作为 Linlan 产品家族的统一身份提供商（IdP/SSO）：DSH-Desktop 以 OIDC 公共客户端登录；dsh-java / linlan-xiaolan 作为 OAuth2 资源服务器消费其签发的 JWT。

- **仓库**：<https://github.com/linlannet/linlan-emis>
- **端口**：`8080`（H2 本地模式开箱即用）
- **许可**：见仓库根目录 LICENSE

---

## 功能

- **认证**：本地账号（用户名/密码，BCrypt）；第三方联合登录（GitHub/Google/微信/飞书，规划中）；OTP/短信/MFA（规划中）。
- **授权**：标准 **OAuth2 + OIDC** 授权服务器（spring-authorization-server 1.x），签发 access/refresh/id_token（JWT，JWKS 可验）。
- **多租户**：`tenant_id` 内核 + `TenantContext` + 行级隔离。
- **基础角色**：`role_level`（0 主任 / 1 研究员 / 2 访客）作为跨产品统一语义。
- **默认失败拒绝**：`@RequiresRole` 与 `RoleInterceptor` 默认拒绝。

## 技术栈

（与 dsh-java 对齐）Spring Boot 3.5.16 · Java 17 · MyBatis 3.0.5 · jjwt 0.12.6 · spring-security-crypto（BCrypt）· spring-security-oauth2-authorization-server 1.x · MySQL（生产）/ H2（本地）。

## 快速开始

### 构建

```bash
cd backend && mvn -q -DskipTests clean package
```

### 本地运行（H2，零外部依赖）

```bash
./scripts/start-local-jar.sh --build
```

启动后访问 http://localhost:8080 （H2 控制台 http://localhost:8080/h2-console）。

### 默认账号（DataInitializer 播种）

| 用户 | 密码 | role_level |
|---|---|---|
| admin | admin123 | 0（主任，可注销他人） |
| guest | guest123 | 2（普通用户） |

### 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | `{ userName, password }` -> `Result<LoginResponse>` |
| POST | `/api/auth/register` | 注册 |
| GET | `/api/auth/me` | 当前用户 |
| DELETE | `/api/auth/user/{id}` | 注销用户（需 roleLevel=0） |
| GET | `/h2-console` | H2 控制台（本地） |

## 模块

- `backend/` —— `emis-backend`（Spring Boot 应用，v1.0.0）
- `test-client/` —— `linlan-emis-test-client`（演示 OIDC 授权码客户端，v1.0.0）
- `py-api/` —— Python API（占位）
- `frontend/` —— Web 控制台（占位）
- `scripts/` —— `package.sh` / `start-local-jar.sh` / `start-mysql-jar.sh` / `verify-oidc.sh`
- `docs/1.0.0/` —— 设计四件套：`spec.md`（需求规格）/ `design.md`（技术方案）/ `tasks.md`（任务清单）/ `release-v1.0.0-development-notes.md`（开发说明）；既有设计：`01-architecture.md` / `02-user-tenant-model.md` / `03-oauth2-authorization-server.md` / `04-permission-model.md`

## 安全与隔离

- 密码使用 BCrypt（`spring-security-crypto`）哈希存储。
- JWT 通过 jjwt 0.12.x 签发/校验。
- `TenantContext` 传播 `tenant_id`；`@RequiresRole` 对 `role_level` 默认失败拒绝。
- 禁止记录明文密钥 / 令牌。

## 演进路线

- **P0**（已完成）：最小后端骨架——本地登录 + JJWT + 租户 + 角色。
- **P1**：第三方联合登录 + 短信/OTP。
- **P2**：引入 spring-authorization-server 1.x 标准化 OAuth2/OIDC + JWKS。
- **P3**：`tenant_id` 硬隔离 + 权限语义统一。

## 版本记录

各版本变更见 [CHANGELOG.md](./CHANGELOG.md)；英文文档见 [README.en.md](./README.en.md)。
