# 03 · OAuth2 授权服务器设计（spring-authorization-server 0.4 → 1.x，标准 OIDC）

> 文档定位：把 P0 的"自签 JJWT"升级为**标准 OAuth2 授权服务器（OIDC）**，使 DSH-Desktop / linlan-xiaolan / dsh-java
> 能用标准协议登录与校验。重点：spring-authorization-server **0.4.0 → 1.5.x 破坏性大改**迁移清单 + OIDC 端点 + 下游接入。
> 参考实现：`emis-java-backend/emis-module-protocol`（当前跑在 SAS 0.4.0 / SB 2.7 / Java 8）。

---

## 1. 为什么要做这一步

- P0 用 `JwtUtil`（jjwt 0.12）自签 JWT，**不是标准 OAuth2**，下游无法用 `spring-security-oauth2-resource-server` 直接校验，也无法做授权码登录。
- 目标：让 DSH-Desktop / xiaolan 走**标准 OIDC 授权码登录**，dsh-java 用 **JWKS** 校验 JWT——这就是"对标 WorkBuddy 的 SSO"。

---

## 2. 现状 vs 目标（大改点）

emis 当前（`emis-module-protocol`）：`spring-security-oauth2-authorization-server` **0.4.0** + Spring Boot **2.7.18** + Spring Security **5.7.11** + `JwtAccessTokenConverter`/`TokenEnhancer` 风格。

升级到 1.5.x（配合 SB 3.5.16 / Spring Security 6.5 / Java 17）是**破坏性大改**，核心变化：

| 维度 | 0.4.0（emis 现状） | 1.5.x（目标） |
|---|---|---|
| 启动注解 | `@EnableAuthorizationServer` + `AuthorizationServerConfigurer` | **已移除**；改为 `SecurityFilterChain` + `OAuth2AuthorizationServerConfigurer` |
| 安全配置 | `WebSecurityConfigurerAdapter` 继承式 | **组件式 lambda DSL**（`HttpSecurity` 链式） |
| Token 编码 | `JwtAccessTokenConverter` / `TokenEnhancer` | `JwtEncoder`(Nimbus) + `OAuth2TokenCustomizer<JwtEncodingContext>` 注入自定义 claim |
| 客户端存储 | `RegisteredClientRepository`（已存在） | 同，但 `ClientSettings`/`TokenSettings` 字段调整；建议 `JdbcRegisteredClientRepository` |
| 授权记录 | `OAuth2AuthorizationService` | `JdbcOAuth2AuthorizationService` + `JdbcOAuth2AuthorizationConsentService`（需新表） |
| JWKS | 手动暴露 | 内置 `NimbusJwkSetEndpointFilter`，`/oauth2/jwks` 自动注册 |
| 端点 | `/oauth2/authorize` `/oauth2/token` | 同 + `/oauth2/userinfo`(OIDC) + `/.well-known/openid-configuration` |
| 颁发者 | — | `spring.security.oauth2.authorizationserver.issuer` 属性（或 `AuthorizationServerSettings`） |

> 最大的隐性成本：**0.4→1.x  riding on Spring Security 5.7→6.5 的整套迁移**（`javax`→`jakarta`、`SecurityFilterChain`、移除 `WebSecurityConfigurerAdapter`、`AuthorizationFilter` 顺序、CSRF 默认策略等）。emis 的 `module-authn`/`module-provider` 中大量 filter/configurer 需随之改写。

---

## 3. 1.x 配置骨架

### 3.1 授权服务器安全链

```java
@Bean
@Order(1)
public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
    http.securityMatcher(authorizationServer.getEndpointsMatcher())
        .with(authorizationServer, c -> c
            .oidc(Customizer.withDefaults())            // 启用 OIDC（userinfo、discovery）
        )
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        // 用户登录页 / 表单登录（或自定义认证过滤器）
        .formLogin(Customizer.withDefaults());
    return http.build();
}
```

### 3.2 用户认证（标准 Spring Security）

- 实现 `UserDetailsService`：从 `sys_user` 加载用户（映射 `tenant_id`/`role_level` 到 `UserDetails` 的 `username`/authorities）。
- `DaoAuthenticationProvider` + BCrypt（`spring-security-crypto` 已对齐）。
- 替换 emis 旧 `IdpAuthentication`/`LocalSession` 等自定义认证。

### 3.3 客户端注册（Jdbc）

```java
@Bean
public RegisteredClientRepository registeredClientRepository(DataSource ds) {
    return new JdbcRegisteredClientRepository(ds);
    // 注册示例（首次 seeding）：
    // RegisteredClient.withId("dsh-desktop")
    //   .clientId("dsh-desktop").clientSecret(encoder.encode("..."))
    //   .clientAuthenticationMethod(CLIENT_SECRET_BASIC)
    //   .authorizationGrantType(AUTHORIZATION_CODE).authorizationGrantType(REFRESH_TOKEN)
    //   .redirectUri("http://localhost:8081/login/oauth2/code/buddyone")
    //   .scope("openid").scope("profile").scope("tenant")
    //   .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
    //   .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(1)).build())
    //   .build();
}
```

### 3.4 密钥与 JWKS（RSA）

```java
@Bean
public JWKSource<SecurityContext> jwkSource() {
    RSAKey rsaKey = generateRsa();          // 生产用固定密钥/密钥库，切勿每次启动随机
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
}
@Bean public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
}
```

### 3.5 把租户/角色编入 JWT（关键：对接 02 / 04）

```java
@Bean
public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
    return context -> {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            Authentication principal = context.getPrincipal();
            // 从 UserDetails 取出 tenant_id / role_level 放入 claims
            context.getClaims().claim("tenant_id", ...);
            context.getClaims().claim("role_level", ...);
            context.getClaims().claim("scope", ...);
        }
    };
}
```

> 这样下游资源服务器解码 JWT 后，即可拿到 `tenant_id`/`role_level`，直接喂给 `TenantContext` 与 `@RequiresRole`（见 04）。

### 3.6 数据库表

引入 SAS 官方 schema：`oauth2_registered_client`、`oauth2_authorization`、`oauth2_authorization_consent`
（取自 `spring-security-oauth2-authorization-server` 的 `src/main/resources/org/springframework/security/oauth2/server/authorization/jdbc/...-schema.sql`）。

---

## 4. 对外端点（标准 OIDC）

| 端点 | 用途 |
|---|---|
| `/oauth2/authorize` | 授权码流程起点 |
| `/oauth2/token` | 换取 access/refresh/id_token |
| `/oauth2/jwks` | **JWT 公钥集合**（下游验签） |
| `/oauth2/userinfo` | OIDC 用户信息 |
| `/.well-known/openid-configuration` | **OIDC Discovery**（下游自动发现 issuer/jwks/endpoint） |

---

## 5. 下游接入（DSH-Desktop / xiaolan / dsh-java）

### 5.1 作为 OIDC 客户端（DSH-Desktop、xiaolan 门户登录）

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          buddyone:
            client-id: dsh-desktop
            client-secret: <secret>
            scope: openid,profile,tenant
            redirect-uri: "{baseUrl}/login/oauth2/code/buddyone"
            client-name: BuddyOne
        provider:
          buddyone:
            issuer-uri: http://localhost:8080          # 自动发现 jwks/authorize/token
```

`SecurityFilterChain` 加 `.oauth2Login(Customizer.withDefaults())`，登录即跳 BuddyOne。

### 5.2 作为 OAuth2 资源服务器（校验 JWT）

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080            # 拉取 JWKS 并校验签名 + issuer
```

```java
@Bean
public SecurityFilterChain api(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(myConverter())));
    return http.build();
}
// myConverter: 把 JWT 的 tenant_id/role_level claims 映射进 Authentication，
// 并 set 到 TenantContext —— 让下游可直接复用 @RequiresRole(04)。
```

### 5.3 dsh-java 具体改造

- **新增** `spring-security-oauth2-resource-server` + `oauth2ResourceServer().jwt()` 替换/并存现有 `ApiKeyAuthInterceptor`。
- **机器/服务间调用**仍走 `ApiKeyAuthInterceptor`（API-Key）；**用户请求**改走 JWT 校验。
- 解码后把 `tenant_id`/`role_level` 注入 `TenantContext`（若沿用现有 TenantContext 概念）。
- dsh-java 当前无 Spring Security，引入后需把现有 `ApiKeyAuthInterceptor` 与新的资源服务器链协调（建议：资源服务器链放行 `/api/**` 内的机器接口给 ApiKey 拦截器）。

---

## 6. 工作量与风险

| 项 | 量级 | 说明 |
|---|---|---|
| SAS 0.4→1.x 配置重写 | 中 | `SecurityFilterChain` + `OAuth2TokenCustomizer` + JWKS |
| 用户认证改标准 `UserDetailsService` | 中 | 替换 emis 自定义 filter |
| 客户端/授权记录落库 | 小 | Jdbc 三张表 |
| emis `module-authn/provider` 适配 SB6 | **大** | 第三方/OTP/MFA filter 跟随 SS6 改写（推迟到 P1） |
| 下游改造 | 小-中 | DSH-Desktop/xiaolan 加 oauth2 client；dsh-java 加 resource-server |

**建议顺序**：先在 P2 把"标准 AS + OIDC + JWKS + 自签客户端落库 + 一个测试客户端登录"跑通，再让 DSH-Desktop/xiaolan 接入；`module-authn/provider` 的第三方/MFA 放到 P1 并行。
