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

- **认证**：本地账号（用户名/密码，BCrypt）；**GitHub App + 微信扫码联合登录**（v1.1.0，按需启用）；OTP/短信/MFA（规划中）。
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
| GET | `/api/auth/federated/{provider}/authorize` | 发起联合登录（`github` / `wechat`；`response_mode=redirect\|json`） |
| GET | `/api/auth/federated/{provider}/bind/authorize` | 已登录用户发起绑定（目标只取会话身份） |
| GET | `/api/auth/federated/{provider}/callback` | 统一回调（登录/绑定由服务端 state 决定） |
| GET | `/api/auth/federated/bindings` | 当前用户绑定列表（subject 掩码） |
| DELETE | `/api/auth/federated/bindings/{bindingId}` | 解绑（仅本人） |
| GET | `/api/auth/federated/providers` | provider 状态（需 roleLevel=0，仅三个布尔） |
| GET | `/h2-console` | H2 控制台（本地） |

### 联合登录（v1.1.0）

- **GitHub**：GitHub **App** 的 user-to-server 授权码换码（`ghu_` token）；身份主键为数字用户 ID，不使用 login/邮箱；不依赖 installation，不限制组织成员。App 私钥可选，仅服务端读取，缺失不阻断登录。
- **微信**：微信开放平台**网站应用扫码**（`snsapi_login`）；身份主键为 **unionid**，openid 仅记录最近一次；unionid 缺失直接拒绝（`40115`），不回退 openid。
- **首次登录**：无需管理员审批，直接落库；无本地账号时自动开户，默认落入 **体验租户（`tenant_id=2`）** + **体验角色（`role_level=2`）**。
- **冲突处理**：同邮箱/手机号命中既有账号时返回 `40910`，必须先本地登录后绑定，禁止静默合并。
- **令牌**：复用既有 HS256 平台 JWT（claims 与本地登录完全一致）；OIDC 侧仍为 RS256 + `/oauth2/jwks`。
- **微信内置浏览器**：检测到 `MicroMessenger` 时返回引导页，提示在系统浏览器打开，并保留账号密码登录兜底。
- **错误码**：`50300` 未配置 / `40110` 取消 / `40111` state 失效 / `40112` code 失效 / `40113` 身份缺失 / `40114` 配置被拒 / `40115` unionid 缺失 / `40310` 未绑定 / `40311` 停用 / `40312` 绑定冲突 / `40313` 最后登录方式 / `40314` 体验租户不可用 / `40910` 邮箱冲突 / `42900` 限流 / `50200` 第三方异常。

配置全部通过环境变量注入，仓库内 yml 不含密钥：

```bash
export EMIS_FEDERATED_GITHUB_ENABLED=true
export EMIS_FEDERATED_GITHUB_CLIENT_ID=<GitHub App client_id>
export EMIS_FEDERATED_GITHUB_CLIENT_SECRET=<GitHub App client_secret>
export EMIS_FEDERATED_GITHUB_REDIRECT_URI=http://localhost:8080/api/auth/federated/github/callback

export EMIS_FEDERATED_WECHAT_ENABLED=true
export EMIS_FEDERATED_WECHAT_APP_ID=<开放平台网站应用 AppID>
export EMIS_FEDERATED_WECHAT_APP_SECRET=<AppSecret>
export EMIS_FEDERATED_WECHAT_REDIRECT_URI=http://localhost:8080/api/auth/federated/wechat/callback
```

> `emis.tenant.header-trust-enabled` 默认 `false`：不可信 `X-Role-Level` / `X-User-Id` / `X-Tenant-Id` 不再覆盖 JWT 身份；仅可信服务间链路可显式开启。

## 模块

- `backend/` —— `emis-backend`（Spring Boot 应用，v1.1.0）
- `test-client/` —— `linlan-emis-test-client`（演示 OIDC 授权码客户端，v1.0.0）
- `py-api/` —— Python API（占位）
- `frontend/` —— Web 控制台（占位）
- `scripts/` —— `package.sh` / `start-local-jar.sh` / `start-mysql-jar.sh` / `verify-oidc.sh`
- `docs/1.1.0/` —— v1.1.0 设计四件套：`spec.md`（联合登录需求规格）/ `design.md`（实现方案）/ `tasks.md`（任务清单）/ `release-v1.1.0-development-notes.md`（交付记录）
- `docs/1.0.0/` —— 设计四件套：`spec.md`（需求规格）/ `design.md`（技术方案）/ `tasks.md`（任务清单）/ `release-v1.0.0-development-notes.md`（开发说明）；既有设计：`01-architecture.md` / `02-user-tenant-model.md` / `03-oauth2-authorization-server.md` / `04-permission-model.md`

## 安全与隔离

- 密码使用 BCrypt（`spring-security-crypto`）哈希存储。
- JWT 通过 jjwt 0.12.x 签发/校验。
- `TenantContext` 传播 `tenant_id`；`@RequiresRole` 对 `role_level` 默认失败拒绝。
- 登录 state 一次性（TTL ≤10 分钟）+ 摘要 Cookie；GitHub 启用 PKCE(S256)；授权码一次性去重。
- 联合登录密钥（client_secret / AppSecret）只从环境变量读取，零入库、零日志、零响应。
- 禁止记录明文密钥 / 令牌 / 授权码；审计与日志中的 subject 一律掩码。

## 演进路线

- **P0**（已完成）：最小后端骨架——本地登录 + JJWT + 租户 + 角色。
- **P1**（已完成，v1.1.0）：GitHub App + 微信扫码联合登录（体验租户/体验角色、免审批自助开户）；短信/OTP 仍为后续候选。
- **P2**（已完成）：spring-authorization-server 1.x 标准化 OAuth2/OIDC + JWKS。
- **P3**：`tenant_id` 硬隔离 + 权限语义统一。

## 版本记录

各版本变更见 [CHANGELOG.md](./CHANGELOG.md)；英文文档见 [README.en.md](./README.en.md)。
