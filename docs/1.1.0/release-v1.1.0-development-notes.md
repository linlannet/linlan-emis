# v1.1.0 交付记录（GitHub App + 微信扫码联合登录）

> 日期：2026-11-17 ｜ 需求：`docs/1.1.0/spec.md`（R4）｜ 设计：`docs/1.1.0/design.md` ｜ 任务：`docs/1.1.0/tasks.md`
> 代码：`backend/` `emis-backend:1.1.0`

## 1. 交付范围

| 能力 | 状态 | 落位 |
| --- | --- | --- |
| GitHub App user-to-server 登录（PKCE S256、`ghu_` token、无 installation、不限组织） | 已实现 | `federated/provider/GitHubFederatedProvider` |
| 微信开放平台网站扫码（`snsapi_login`、unionid 主键） | 已实现 | `federated/provider/WechatFederatedProvider` |
| 首次登录自助开户（体验租户 `tenant_id=2` + 体验角色 `role_level=2`、免审批） | 已实现 | `federated/identity/UserProvisioningService` |
| 绑定 / 解绑 / 绑定列表 / provider 状态 / 微信内置浏览器引导（7 端点） | 已实现 | `federated/web/FederatedAuthController` |
| 平台令牌复用 HS256 JWT（claims 与本地登录一致） | 已实现 | `federated/identity/FederatedTokenIssuer` |
| 密钥零入库（环境变量）、state 一次性 + 摘要 Cookie、授权码一次性、最小限流 | 已实现 | `federated/config`、`federated/state`、`federated/security` |
| 审计（27 类事件，落表 + 结构化日志，失败降级） | 已实现 | `federated/audit` |
| 安全修复：Header 透传默认关闭、注销重复调用、登录限流、SAS/API 安全链拆分 | 已实现 | `WebMvcConfig`、`UserController`、`SecurityConfig` |
| 1.2+ 候选（其他 IdP、provider 注册表/SPI、MFA、吊销黑名单、跨租户绑定、审计看板等） | 未实现（按规格排除） | — |

## 2. 配置（仅环境变量，仓库 YAML 不含密钥）

```bash
export EMIS_FEDERATED_GITHUB_ENABLED=true
export EMIS_FEDERATED_GITHUB_CLIENT_ID=<GitHub App client_id>
export EMIS_FEDERATED_GITHUB_CLIENT_SECRET=<GitHub App client_secret>
export EMIS_FEDERATED_GITHUB_REDIRECT_URI=http://localhost:8080/api/auth/federated/github/callback

export EMIS_FEDERATED_WECHAT_ENABLED=true
export EMIS_FEDERATED_WECHAT_APP_ID=<网站应用 AppID>
export EMIS_FEDERATED_WECHAT_APP_SECRET=<AppSecret>
export EMIS_FEDERATED_WECHAT_REDIRECT_URI=http://localhost:8080/api/auth/federated/wechat/callback
```

- GitHub App 需勾选 `Email addresses: Read` 才能做邮箱冲突检测；未勾选时降级跳过并告警（不阻断登录）。
- 微信需在开放平台把网站应用绑定到同一主体，换码响应才会返回 `unionid`。
- 未配置凭据时服务可正常启动；调用联合登录返回 `50300`，本地登录不受影响。

## 3. 构建与测试

```bash
cd backend && mvn -q -DskipTests clean package
cd backend && mvn -q test
cd backend && mvn -q -Dtest=FederatedLoginIT test
```

| 项 | 结果 |
| --- | --- |
| 编译 | 通过（Java 17 / Boot 3.5.16） |
| 单元测试 | 通过（PKCE、state 一次性/并发、摘要 Cookie、errcode 映射、白名单、身份解析、用户名规则、令牌 claims、掩码、限流、注销、注册/哨兵密码、审计降级、RequestId） |
| 集成测试 `FederatedLoginIT` | 5/5 通过：GitHub 首登开户、授权码重放、邮箱冲突、微信 unionid 缺失、伪造 Header 不提权 |
| 外部依赖 | 测试全部使用 `@MockitoBean ProviderHttpClient`，未连接真实 GitHub / 微信 |

## 4. 冒烟验证（H2 standalone）

- GitHub `authorize` → `302` 到 `https://github.com/login/oauth/authorize`，含 `state`、`code_challenge`、`code_challenge_method=S256`，`Set-Cookie: EMIS_FED_STATE` 为摘要且 `HttpOnly; SameSite=Lax`。
- 微信 UA（`MicroMessenger`）→ `200 text/html` 引导页，含“请在系统浏览器中打开”与账号密码登录入口。
- `providers`（roleLevel=0）→ 仅 `provider/enabled/configured/privateKeyLoaded` 三个布尔。
- 体验角色（roleLevel=2）：`/api/auth/user/count` → 403；`/api/auth/me` → 200；带 `X-Role-Level: 0` 仍 403。
- 本地登录第 11 次/分钟 → `42900`；JWT header `alg=HS256`。
- `/oauth2/jwks` → 200；未认证 `/oauth2/authorize` → 302 到 `/login`。

## 5. 手工验证项（未执行）

| 项 | 阻塞原因 | 后续动作 |
| --- | --- | --- |
| AC-24：真实 GitHub App 登录后直接续接 `/oauth2/authorize` | 需要真实 GitHub App（client_id/secret、回调域名） | 运维配置后按设计 §2.8.6 步骤执行 |
| AC-35：真实 GitHub App 全链路无 installation 查询 | 同上 | 同上 |
| AC-40：微信内置浏览器真实打开 | 需要真实开放平台网站应用与可访问域名 | 配置后手工验证引导页与本地登录兜底 |

## 6. 已知风险

1. state/授权码去重为进程内存储，多实例部署需会话粘滞或外置存储（1.2+ 候选）。
2. GitHub App 邮箱权限未开启时，同邮箱冲突检测降级跳过（有 WARN 审计）。
3. 微信 unionid 依赖开放平台主体绑定；缺失时按规格拒绝（`40115`）。
4. 既有行为变更：`emis.tenant.header-trust-enabled` 默认 `false`；`/api/auth/register` 忽略请求体 `tenantId`（统一落体验租户）。
5. 安全链已拆分为“SAS 端点（RS256）”与“默认链（`/api` HS256 + 表单登录）”，部署侧如需自定义安全规则应以默认链为准。

## 7. 回滚

- 代码：回退本版本提交即可；新增 `federated` 包可整体删除。
- DDL：`emis_federated_identity` / `emis_federated_audit` 为 `CREATE TABLE IF NOT EXISTS`，回退代码后可保留（不影响既有表）；删除前须确认无数据。
- 配置：将 `EMIS_FEDERATED_*_ENABLED` 置 `false` 即可灰度停用，不影响本地登录与 OIDC。
- 行为回退：如需恢复 Header 透传，显式设置 `EMIS_TENANT_HEADER_TRUST_ENABLED=true`（不推荐）。
