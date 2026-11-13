# 02 · 用户与多租户数据模型设计

> 文档定位：定义 linlan-emis 的核心身份数据模型——用户、租户、角色层级，以及多租户隔离策略。
> 整合来源：emis-java-backend 的 `UserInfo` 字段词汇 + dsh-java-ontology 的 `tenant_id`/`role_level` 蓝图。
> 关联：03（OAuth2 把本模型的 claim 编入 JWT）、04（权限模型消费 `role_level`）。

---

## 1. 实体模型

### 1.1 用户 `sys_user`（合并 emis `UserInfo` + ontology 租户/角色）

| 字段 | 类型 | 说明 | 来源 |
|---|---|---|---|
| `user_id` | BIGINT PK | 用户ID | emis |
| `tenant_id` | BIGINT | 租户ID（主任/平台级可 NULL） | **ontology（新增）** |
| `role_level` | INT | 0=主任 1=研究员 2=访客 | **ontology（新增）** |
| `user_name` | VARCHAR(64) UNIQUE | 登录名 | emis |
| `nick_name` | VARCHAR(64) | 昵称 | emis |
| `email` / `phonenumber` | VARCHAR | 邮箱/手机 | emis |
| `avatar_url` | VARCHAR(255) | 头像 | emis |
| `password` | VARCHAR(128) | **BCrypt 密文** | emis（沿用 BCrypt） |
| `status` | CHAR(1) | 0=正常 1=停用 | emis |
| `del_flag` | CHAR(1) | 0=正常 1=删除（软删） | emis |
| `login_ip` / `login_date` | — | 最后登录 | emis |
| `created_by/at` `updated_by/at` `remark` | — | 审计字段 | emis |

> 完整 DDL 见 `backend/src/main/resources/schema.sql`。Java 实体见 `backend/.../sys/entity/SysUser.java`。

### 1.2 租户 `emis_tenant`

```sql
CREATE TABLE emis_tenant (
    tenant_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_name VARCHAR(128) NOT NULL,
    status      CHAR(1) DEFAULT '0',
    created_at  DATETIME DEFAULT NOW()
);
```

- 种子：`tenant_id=1`「默认租户」。
- 与 emis 的差异：emis 的"租户"是 `ORGAN(ORGAN_TYPE=RENT)` 的**机构维度（软）**；本设计升级为**独立的 `tenant` 实体 + `tenant_id` 硬隔离**（见 §3）。

### 1.3 角色层级 `role_level`（跨产品统一语义）

| 值 | 名称 | 可访问 |
|---|---|---|
| 0 | 主任（Director） | 全部（租户管理、用户管理） |
| 1 | 研究员（Researcher） | 业务接口 |
| 2 | 访客（Guest） | 公开 + 基础接口 |

语义：`@RequiresRole(N)` ⇒ 当前 `role_level <= N` 放行（见 04）。

---

## 2. 租户上下文 `TenantContext`

`ThreadLocal` 存放当前请求的 `tenantId / roleLevel / userId`，由 JWT 过滤器或**服务间 Header**（X-Tenant-Id / X-Role-Level / X-User-Id）注入，请求结束清理。

```java
public final class TenantContext {
    private static final ThreadLocal<Long>    CURRENT_TENANT    = new ThreadLocal<>();
    private static final ThreadLocal<Integer> CURRENT_ROLE_LEVEL= new ThreadLocal<>();
    private static final ThreadLocal<Long>    CURRENT_USER_ID   = new ThreadLocal<>();
    // getter/setter/isDirector/isResearcher/isGuest/clear
}
```

> 代码见 `backend/.../common/TenantContext.java`（改编自 ontology）。

---

## 3. 多租户隔离策略

| 方案 | 说明 | 推荐度 |
|---|---|---|
| **A. 软隔离（当前）** | 所有表带 `tenant_id`，查询手动/拦截器自动拼 `WHERE tenant_id = ?` | 起步快，已落地 |
| **B. 硬隔离（P3 目标）** | MyBatis 拦截器/插件在 SQL 执行前**强制注入** `tenant_id` 过滤；写操作校验归属 | 安全，对齐 ontology 蓝图 |

**P3 落地方式**（最小侵入）：
- 自定义 MyBatis `@Intercepts` 拦截 `Executor.query`/`update`，对带 `@TenantEntity` 注解的表自动追加 `tenant_id` 条件（取 `TenantContext.getTenantId()`）。
- 主任（`role_level=0`，`tenant_id IS NULL`）可跨租户；其余强制本租户。
- 与 ontology Phase 8「`tenant_id` 行级强制隔离」完全对齐。

> 注：emis 旧模型是 `ORGAN_TYPE=RENT` 机构维度，本设计不直接套用，而是采用 ontology 的 `tenant_id` 主键隔离，避免"机构树"复杂度。

---

## 4. 与 emis / ontology 的字段映射

| 概念 | emis（`UserInfo`/`ORGAN`） | ontology（Phase 8） | 本设计 `sys_user` |
|---|---|---|---|
| 用户 | `UserInfo`（username/password/salt/...） | `onto_sys_user` | `sys_user`（合并两者字段） |
| 租户 | `ORGAN(ORGAN_TYPE=RENT)` 软 | `tenant_id` 列 | `tenant_id` + `emis_tenant` |
| 角色 | emis RBAC（role 表） | `role_level` 数值层级 | `role_level` 数值层级 |
| 上下文 | `IdentityProviderUserDetails` | `TenantContext` | `TenantContext`（沿用） |

结论：**用户字段以 emis 为主，租户/角色以 ontology 为主**，形成最小但自洽的模型。
