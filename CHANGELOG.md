# 版本

> 各版本详情见 `docs/1.0.0/` 架构文档；以下按模块记录功能更新。
## 版本号：1.1.0
- 更新日期：2026-11-17
- 状态：联合登录（GitHub App + 微信扫码）交付
- 设计文档：`docs/1.1.0/`（`spec.md` / `design.md` / `tasks.md` / `release-v1.1.0-development-notes.md`）

- **后端 backend（emis-backend，v1.1.0）**：
  - **联合登录**：GitHub **App** user-to-server 授权（PKCE S256，`ghu_` token，不依赖 installation、不限制组织成员）；微信开放平台**网站扫码**（`snsapi_login`）
  - **身份主键**：GitHub = 数字用户 ID；微信 = **unionid**（缺失直接 `40115`，禁止回退 openid；openid 仅记录最近一次）
  - **自助开户**：免管理员审批，无本地账号时直接落库，默认 **体验租户 `tenant_id=2`** + **体验角色 `role_level=2`**
  - **账号映射**：同邮箱/手机号命中既有账号 → `40910`，必须先本地登录后绑定，禁止静默合并；历史解绑/失效 → `40310`
  - **绑定/解绑**：7 个端点（发起登录/发起绑定/统一回调/绑定列表/解绑/provider 状态/微信内置浏览器引导）；解绑最后一个登录方式 → `40313`；绑定/解绑不改变 `user_name`/`tenant_id`/`role_level`
  - **令牌**：平台令牌复用既有 **HS256** JWT（claims 与本地登录完全一致）；OIDC 侧保持 **RS256 + `/oauth2/jwks`**
  - **安全**：state 一次性（≤10 分钟）+ 摘要 Cookie、授权码一次性去重、GitHub 启用 PKCE、密钥零入库（环境变量）、日志与审计 subject 掩码、最小限流（发起 20/回调 30/本地登录 10 每分钟）
  - **错误码**：15 个业务码（`50300`、`40110~40115`、`40310~40314`、`40910`、`42900`、`50200`）
  - **安全修复**：`emis.tenant.header-trust-enabled` 默认 `false`，伪造 `X-Role-Level`/`X-User-Id`/`X-Tenant-Id` 不再覆盖 JWT 身份（修复 `@RequiresRole` 绕过风险）；`UserController.delete` 重复调用 `softDelete` 修正；登录新增限流
  - **安全链拆分**：授权服务器（SAS，RS256）与 `/api`（HS256 JWT）分链，修复带平台 JWT 调用 `/api/**` 被 RS256 Bearer 解析拦截的问题
  - **行为变更**：`/api/auth/register` 忽略请求体 `tenantId`，统一落体验租户、`role_level=2`
  - **数据模型**：新增 `emis_federated_identity`（active/unbound/invalid 三态，`active_flag` 复合唯一）与 `emis_federated_audit`（27 类事件，双库幂等 DDL）
  - **测试**：新增 16 个测试类（含 `FederatedLoginIT` 端到端：首登开户、授权码重放、邮箱冲突、unionid 缺失、伪造 Header）
- **1.2+ 候选（本期不实现）**：其他 IdP（飞书/企微/任意 OIDC，dsh-java 侧自行接入）、provider 注册表/SPI/优先级路由/管理后台、SAML/CAS、MFA/OTP/短信、令牌吊销黑名单、跨租户绑定、微信多端 openid 管理、审计看板、体验租户升级接口、GitHub App JWT/私钥托管
- **AI agent 友好性优化**：README 中英双语同步联合登录能力、环境变量与错误码；`AGENTS.md` 补齐 `federated` 包边界、7 端点与零密钥约定；`llms.txt` 更新为 v1.1.0 能力摘要；新增 `docs/1.1.0/` 四件套

## 版本号：1.0.0
- 更新日期：2026-11-13
- 状态：开发预览（P0 最小后端骨架已落地）

- **后端 backend（emis-backend）**：
  - 本地账号登录（用户名/密码，BCrypt）+ JWT（jjwt 0.12.6）签发
  - 多租户内核：`tenant_id` + `TenantContext` + 行级隔离
  - 基础角色：`role_level`（0 主任 / 1 研究员 / 2 访客）+ `@RequiresRole`（fail-closed）
  - 接口：`POST /api/auth/login`、`POST /api/auth/register`、`GET /api/auth/me`、`DELETE /api/auth/user/{id}`（需 roleLevel=0）
  - 默认账号：`admin/admin123`(roleLevel=0)、`guest/guest123`(roleLevel=2)
  - 数据源：H2（standalone 零依赖）/ MySQL（生产）
- **测试客户端 test-client（linlan-emis-test-client）**：
  - 标准 OIDC 授权码登录 BuddyOne，展示 id_token 中的 `tenant_id` / `role_level` claim
  - 对标 DSH-Desktop / xiaolan 接入 SSO 的参考实现
- **AI 可发现性（默认文件补齐，参考 dsh-java）**：
  - 新增 `AGENTS.md`（构建/测试命令与约定）、`llms.txt`（面向 LLM 的项目摘要）、`README.en.md`（英文 README），以及本 `CHANGELOG.md`
  - `README.md` 补充 "For AI" 链接（指向 `llms.txt` / `AGENTS.md`）
  - **设计四件套初始化（参考 dsh-java 0.2.3 模板）**：新增 `docs/1.0.0/spec.md`（需求规格）/ `design.md`（技术方案）/ `tasks.md`（任务清单）/ `release-v1.0.0-development-notes.md`（开发说明），复用既有 `01-architecture.md`~`04-permission-model.md`；架构文档由 `docs/` 重组至 `docs/1.0.0/`
  - **版本对齐**：backend / test-client 版本统一为 `1.0.0`（与文档版本一致）

## 演进分期
- **P0（已完成）**：最小后端骨架——本地登录 + JJWT 签发 + `tenant_id`/`role_level` + `TenantContext` + `@RequiresRole`(fail-closed)
- **P1**：第三方联合登录（GitHub/Google/微信/飞书）+ 短信/OTP 登录
- **P2**：引入 **spring-authorization-server 1.x** 标准化 OAuth2/OIDC 端点 + JWKS；dsh-java/DSH-Desktop/xiaolan 改造为客户端/资源服务器
- **P3**：`tenant_id` 硬隔离（MyBatis 行级隔离插件）+ 权限语义统一（`role_level`/`@RequiresRole` 与下游对齐）
