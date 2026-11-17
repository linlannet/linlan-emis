package net.linlan.emis.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

/**
 * 授权服务器安全配置（spring-authorization-server 1.x，单一安全链）。
 * - OAuth2 / OIDC 端点（/oauth2/authorize、/oauth2/token、/oauth2/jwks、/userinfo、discovery）由 OAuth2AuthorizationServerConfigurer 提供
 * - formLogin 提供 /login 登录页；未认证访问 /oauth2/authorize 会由 AS 过滤器复用链级 LoginUrl 入口跳转到 /login
 * - /api/** 仍由 P0 的 JwtAuthenticationFilter + RoleInterceptor 自管（此处 permitAll）
 */
@Configuration
public class SecurityConfig {

    /**
     * 授权服务器安全链：只匹配 SAS 端点（/oauth2/**、/userinfo、discovery 等）。
     * 必须与 /api 的 HS256 JWT 链分离：否则 SAS 的 RS256 Bearer 解析会拦截并拒绝 /api 的 HS256 令牌。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        http
                .securityMatcher(authorizationServer.getEndpointsMatcher())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .with(authorizationServer, configurer -> configurer.oidc(Customizer.withDefaults()))
                // 授权端点（浏览器跳转）：覆盖 AS 默认 Bearer 入口，改为重定向到 /login
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new AntPathRequestMatcher("/oauth2/authorize")))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/oauth2/**"));
        return http.build();
    }

    /** 默认安全链：/api 自管 JWT（permitAll），表单登录页供 SAS 浏览器流程复用 */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/**", "/h2-console/**", "/error", "/favicon.ico", "/login").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                // /api/** 为无状态 JWT 接口（自管 JwtAuthenticationFilter + RoleInterceptor），豁免 CSRF
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/**"))
                // /api/** 跨域放行：dsh-java-app 等前端跨源调用登录/注销接口（本地开发放开，生产请收紧 allow-list）
                .cors(cors -> cors.configurationSource(apiCorsConfigurationSource()))
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));
        return http.build();
    }

    /** /api/** 的 CORS 配置：允许任意来源携带 JSON 调用（本地/内网联调用） */
    private CorsConfigurationSource apiCorsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** 客户端注册仓库（落库到 oauth2_registered_client）。
     *  注意：SAS 1.5 的客户端密钥默认由 RegisteredClientAuthenticationProvider 以 NoOp 直接比对，
     *  故 DataInitializer 中客户端密钥以明文存储（演示用）；生产请改用 BCrypt 并自定义认证 Provider。 */
    @Bean
    public RegisteredClientRepository registeredClientRepository(DataSource dataSource) {
        return new JdbcRegisteredClientRepository(new JdbcTemplate(dataSource));
    }

    /** RSA 密钥（演示用，每次启动随机生成；生产必须持久化到密钥库） */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * 令牌定制器：把租户/角色/用户名编入 access_token 与 id_token 的 claims。
     * 统一契约：sub = 用户ID；tenant_id / role_level / user_name 使用 snake_case。
     * 注意：JwtEncodingContext.getPrincipal() 返回 Authentication，真正的用户身份在
     * authentication.getPrincipal()（即 EmisUserDetails）中；不限定 Authentication 具体类型，
     * 避免 client_credentials 等场景被静默跳过。
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            Authentication authentication = context.getPrincipal();
            if (authentication != null && authentication.getPrincipal() instanceof EmisUserDetails user) {
                context.getClaims()
                        .subject(String.valueOf(user.getUserId()))
                        .claim("tenant_id", user.getTenantId())
                        .claim("role_level", user.getRoleLevel())
                        .claim("user_name", user.getUsername())
                        .claim("preferred_username", user.getUsername());
            }
        };
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成 RSA 密钥", ex);
        }
    }
}
