# linlan-emis (BuddyOne) v1.0.0 编码任务清单

> 版本：`v1.0.0` | 基线：P0 最小后端骨架（已落地） | 架构：Spring Boot 3.5.16 / Java 17 / MyBatis 3.0.5
>
> 本清单将 `docs/1.0.0/spec.md` 的 §5.1–§5.7 能力领域与 `design.md` 的分层落位、接口清单转化为可执行、可验收的编码任务。
>
> 主线为**企业多租户 IAM/SSO 中台**：本地账号认证（已有）+ OAuth2/OIDC 授权服务器 + 多租户隔离 + 下游 SSO 接入。
>
> **范围边界（强制）**：
> 1. **不新增 Maven 模块**，依赖方向保持 `controller → service/security → mapper → entity`。
> 2. **仅引入 spring-authorization-server 1.x**，不新增其他第三方依赖。
> 3. **v1.0.0 为租户软隔离**，硬隔离（MyBatis 插件）留待 P3。
>
> **任务统计**：共 **8 个任务组**、约 **30 条可勾选条目**。
>
> **完成状态图例**：`[x]` 已完成 ｜ `[ ]` 待办/后续优化

---

## 1. 版本治理

> 依赖：无（基座） ｜ 覆盖：spec §5.6 ｜ 完成状态：已完成

### 1.1 统一版本标识为 1.0.0
- [x] backend / test-client pom 版本统一升级为 `1.0.0`；验收：两处 pom 一致 —— `backend/pom.xml`、`test-client/pom.xml`
- [x] `CHANGELOG.md` 新增 1.0.0 分节，区分"新增"与"更新"；验收：分节存在且分类清晰 —— `CHANGELOG.md`
- [x] 四件套（`spec.md`/`design.md`/`tasks.md`/`release-v1.0.0-development-notes.md`）归档于 `docs/1.0.0/`；验收：四件套齐备

---

## 2. 本地账号认证（P0 已有，验收对齐）

> 依赖：无 ｜ 覆盖：spec §5.1 ｜ 完成状态：已完成

### 2.1 登录 / 注册 / 注销 / me
- [x] `AuthController`：`POST /api/auth/login`、`POST /api/auth/register`、`GET /api/auth/me`、`DELETE /api/auth/user/{id}`（需 role_level=0）；验收：端点与 spec §5.1 一致 —— `backend/.../sys/controller/AuthController.java`
- [x] BCrypt 密码校验（`spring-security-crypto`）；验收：登录成功签发令牌 —— `backend/.../sys/security/`
- [x] `DataInitializer` 播种 admin/admin123(0) / guest/guest123(2) 与默认租户 —— `backend/.../config/DataInitializer.java`

---

## 3. 多租户模型与隔离

> 依赖：无（可与 4 并行） ｜ 覆盖：spec §5.2、design §2.3 ｜ 完成状态：部分完成（软隔离已落地）

### 3.1 实体与上下文
- [x] `sys_user`（`tenant_id`/`role_level`）+ `emis_tenant`；验收：DDL 与 `02` 一致 —— `backend/.../sys/entity/SysUser.java`、`schema.sql`
- [x] `TenantContext`（ThreadLocal：tenantId/roleLevel/userId）+ 注入/清理；验收：请求链路可取到上下文 —— `backend/.../common/TenantContext.java`
- [ ] 软隔离校验：查询自动拼 `tenant_id`，主任（`NULL`）跨租户；验收：单测跨租户拒绝 —— `backend/.../sys/mapper/`（P3 前为约定式，待拦截器强制）

---

## 4. OAuth2 / OIDC 授权服务器

> 依赖：2（认证）、3（租户/角色） ｜ 覆盖：spec §5.3、design §2.4/§2.5 ｜ 完成状态：待办

### 4.1 SAS 1.x 安全链
- [ ] 引入 `spring-security-oauth2-authorization-server` 1.x（SB BOM 管理）；验收：依赖树含 1.5.x —— `backend/pom.xml`
- [ ] `AuthorizationServerConfig`：`SecurityFilterChain` + `OAuth2AuthorizationServerConfigurer` + OIDC（`oidc(Customizer.withDefaults())`）+ 表单登录；验收：端点注册 —— `backend/.../sys/oauth2/`
- [ ] `UserDetailsService`：从 `sys_user` 加载，映射 `tenant_id`/`role_level` 到 `UserDetails`；验收：标准 `DaoAuthenticationProvider` + BCrypt —— 同上
- [ ] `RegisteredClientRepository`（Jdbc）+ 首次 seeding（DSH-Desktop 等客户端）；验收：`oauth2_registered_client` 落库 —— 同上
- [ ] `JWKSource`（RSA 固定密钥库）+ `JwtEncoder`；验收：`/oauth2/jwks` 返回公钥 —— 同上
- [ ] `OAuth2TokenCustomizer<JwtEncodingContext>`：把 `tenant_id`/`role_level` 编入 JWT claims；验收：id_token 含两 claim —— 同上
- [ ] 引入 SAS 官方 schema（`oauth2_registered_client`/`oauth2_authorization`/`oauth2_authorization_consent`）；验收：表存在 —— `schema.sql`

---

## 5. 权限模型 @RequiresRole

> 依赖：无 ｜ 覆盖：spec §5.4、design §2.6 ｜ 完成状态：已完成（P0）

### 5.1 注解与拦截器
- [x] `@RequiresRole(N)` / `@PublicApi` 注解 + `RoleInterceptor`（fail-closed：无注解默认 403、未登录 401、role_level>N 403）；验收：三态实测通过 —— `backend/.../sys/security/`
- [x] `WebMvcConfig` 注册拦截器 + 租户 Header 透传（`X-Tenant-Id`/`X-Role-Level`/`X-User-Id`）；验收：跨服务上下文传播 —— `backend/.../plat/config/WebMvcConfig.java`

---

## 6. 下游接入 / SSO

> 依赖：4（AS） ｜ 覆盖：spec §5.5、design §1.3 ｜ 完成状态：参考实现已落地（test-client）

### 6.1 OIDC 客户端参考
- [x] `test-client`：标准 OIDC 授权码登录 BuddyOne，展示 id_token 的 `tenant_id`/`role_level`；验收：登录成功并解析 claims —— `test-client/`
- [ ] DSH-Desktop：加 `spring-security-oauth2-client` + `oauth2Login()`（authorization_code）；验收：桌面端经 BuddyOne 登录 —— `dsh-java-desktop`
- [ ] dsh-java：加 `oauth2ResourceServer().jwt()`（JWKS）替换/并存 `ApiKeyAuthInterceptor`；验收：资源服务器校验通过 —— `dsh-java`
- [ ] linlan-xiaolan：同 DSH-Desktop 接入 —— `linlan-xiaolan`

---

## 7. AI 可发现性文档

> 依赖：无（横切） ｜ 覆盖：spec §5.7 ｜ 完成状态：已完成

### 7.1 仓库级机器可读入口
- [x] `AGENTS.md`：项目定位、模块布局、构建/测试命令、代码约定、禁止事项 —— `AGENTS.md`
- [x] `llms.txt`：面向 LLM 的项目摘要 —— `llms.txt`
- [x] `README.en.md`：英文 README —— `README.en.md`

---

## 8. 测试与文档归档

> 依赖：全部实现 ｜ 覆盖：design §2.6 ｜ 完成状态：进行中

### 8.1 单元测试
- [x] `RoleInterceptor` 三态、`UserDetailsService` 映射（P0 实测）
- [ ] `OAuth2TokenCustomizer` claim 注入单测
- [ ] OIDC 授权码全流程集成测试（test-client 登录 + JWKS 验签）
- [ ] 跨租户隔离断言单测

### 8.2 四件套归档
- [x] `spec.md`/`design.md`/`tasks.md`/`release-v1.0.0-development-notes.md` 归档于 `docs/1.0.0/`
- [x] `CHANGELOG.md` 1.0.0 分节补充（设计四件套初始化 + 文档重组至 `docs/1.0.0/`）

---

## 9. 已知边界与后续版本任务（本版本不做）

- [ ] `tenant_id` 硬隔离（MyBatis 拦截器/插件，P3）。
- [ ] 第三方联合登录（GitHub/Google/微信/飞书）与 OTP/SMS/MFA（P1）。
- [ ] `@RequiresRole` 支持"纵向层级 + 横向权限点"组合（P3）。
- [ ] 主任跨租户审计、角色变更事件通知（P3）。
