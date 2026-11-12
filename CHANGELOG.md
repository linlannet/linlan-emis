# 版本

> 各版本详情见 `docs/` 架构文档；以下按模块记录功能更新。
## 版本号：0.1.0
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

## 演进分期
- **P0（已完成）**：最小后端骨架——本地登录 + JJWT 签发 + `tenant_id`/`role_level` + `TenantContext` + `@RequiresRole`(fail-closed)
- **P1**：第三方联合登录（GitHub/Google/微信/飞书）+ 短信/OTP 登录
- **P2**：引入 **spring-authorization-server 1.x** 标准化 OAuth2/OIDC 端点 + JWKS；dsh-java/DSH-Desktop/xiaolan 改造为客户端/资源服务器
- **P3**：`tenant_id` 硬隔离（MyBatis 行级隔离插件）+ 权限语义统一（`role_level`/`@RequiresRole` 与下游对齐）
