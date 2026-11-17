package net.linlan.emis.config;

import net.linlan.emis.user.entity.EmisUser;
import net.linlan.emis.user.mapper.EmisUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 数据初始化（开发/演示用）：首次启动时写入默认租户 + 管理员/访客账号 + 测试 OIDC 客户端。
 */
@Component
@Order(10)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final EmisUserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder encoder;
    private final RegisteredClientRepository registeredClientRepository;

    public DataInitializer(EmisUserMapper userMapper, JdbcTemplate jdbcTemplate, BCryptPasswordEncoder encoder,
                           RegisteredClientRepository registeredClientRepository) {
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.encoder = encoder;
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public void run(String... args) {
        Long tenantCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM emis_tenant", Long.class);
        if (tenantCount != null && tenantCount == 0) {
            jdbcTemplate.update(
                    "INSERT INTO emis_tenant (tenant_id, tenant_name, status, created_at) VALUES (1, '默认租户', '0', NOW())");
        }
        seedExperienceTenant();

        if (userMapper.count() == 0) {
            EmisUser admin = new EmisUser();
            admin.setTenantId(null);
            admin.setRoleLevel(0);
            admin.setUserName("admin");
            admin.setNickName("管理员");
            admin.setPassword(encoder.encode("admin123"));
            admin.setStatus("0");
            admin.setCreatedBy("seed");
            userMapper.insert(admin);

            EmisUser guest = new EmisUser();
            guest.setTenantId(1L);
            guest.setRoleLevel(2);
            guest.setUserName("guest");
            guest.setNickName("访客");
            guest.setPassword(encoder.encode("guest123"));
            guest.setStatus("0");
            guest.setCreatedBy("seed");
            userMapper.insert(guest);
        }

        seedTestClient();
    }

    /** 体验租户幂等播种：联合登录自助开户的默认落点，不复用默认租户 1 */
    private void seedExperienceTenant() {
        List<Long> existing = jdbcTemplate.queryForList(
                "SELECT tenant_id FROM emis_tenant WHERE tenant_name = ?", Long.class, "体验租户");
        if (!existing.isEmpty()) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO emis_tenant (tenant_id, tenant_name, status, created_at) VALUES (2, '体验租户', '0', NOW())");
        } catch (DuplicateKeyException ex) {
            log.warn("体验租户 ID 2 已被占用，请运维核对 provisioning.default-tenant-id");
        }
    }

    /** 落库一个测试 OIDC 客户端（机密客户端，供 test-client 模块走标准授权码登录） */
    private void seedTestClient() {
        if (registeredClientRepository.findByClientId("test-client") != null) {
            return;
        }
        RegisteredClient client = RegisteredClient.withId("test-client")
                .clientId("test-client")
                // 客户端密钥以 BCrypt 存储：SAS 的 RegisteredClientAuthenticationProvider 使用容器的 PasswordEncoder(BCrypt) 校验
                .clientSecret(encoder.encode("test-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8081/login/oauth2/code/buddyone")
                .postLogoutRedirectUri("http://localhost:8081/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("tenant")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(1)).build())
                .build();
        registeredClientRepository.save(client);
    }
}
