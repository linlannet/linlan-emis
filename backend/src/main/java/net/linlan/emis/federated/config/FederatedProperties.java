package net.linlan.emis.federated.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 联合登录配置（emis.federated.*）。
 * 所有密钥只从环境变量注入，仓库 yml 中仅保留空占位。
 */
@Component
@ConfigurationProperties(prefix = "emis.federated")
public class FederatedProperties {

    private static final Logger log = LoggerFactory.getLogger(FederatedProperties.class);

    private Github github = new Github();
    private Wechat wechat = new Wechat();
    private Provisioning provisioning = new Provisioning();
    private State state = new State();
    private Http http = new Http();
    private RateLimit rateLimit = new RateLimit();
    private List<String> redirectWhitelist = new ArrayList<>();
    private Audit audit = new Audit();

    @PostConstruct
    void validate() {
        if (!wechat.isRequireUnionid()) {
            throw new IllegalStateException("emis.federated.wechat.require-unionid 必须为 true：不允许回退 openid 作为身份主键");
        }
        if (provisioning.getDefaultRoleLevel() != RoleLevels.EXPERIENCE) {
            throw new IllegalStateException("emis.federated.provisioning.default-role-level 必须为 2（体验角色），禁止借开户提权");
        }
        if (state.getTtl() != null && state.getTtl().compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalStateException("emis.federated.state.ttl 不得超过 10m");
        }
        if (github.isEnabled() && !github.isConfigured()) {
            log.warn("GitHub 登录已启用但配置不完整，调用将返回 50300");
        }
        if (wechat.isEnabled() && !wechat.isConfigured()) {
            log.warn("微信登录已启用但配置不完整，调用将返回 50300");
        }
        warnInsecureRedirect("github", github.getRedirectUri());
        warnInsecureRedirect("wechat", wechat.getRedirectUri());
        if (http.getConnectTimeout() != null && http.getConnectTimeout().compareTo(Duration.ofSeconds(3)) > 0) {
            log.warn("emis.federated.http.connect-timeout 超过建议值 3s");
        }
        if (http.getReadTimeout() != null && http.getReadTimeout().compareTo(Duration.ofSeconds(5)) > 0) {
            log.warn("emis.federated.http.read-timeout 超过建议值 5s");
        }
    }

    private void warnInsecureRedirect(String provider, String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank() || redirectUri.startsWith("https://")) {
            return;
        }
        String host = redirectUri.replaceFirst("^https?://", "").split("/")[0];
        if (!host.startsWith("localhost") && !host.startsWith("127.0.0.1")) {
            log.warn("{} 的 redirect-uri 非 HTTPS，生产环境必须使用 HTTPS", provider);
        }
    }

    public static class Github {
        private boolean enabled = false;
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";
        private String appId = "";
        private String privateKeyPath = "";

        public boolean isConfigured() {
            return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId == null ? "" : clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret == null ? "" : clientSecret; }
        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri == null ? "" : redirectUri; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId == null ? "" : appId; }
        public String getPrivateKeyPath() { return privateKeyPath; }
        public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath == null ? "" : privateKeyPath; }
    }

    public static class Wechat {
        private boolean enabled = false;
        private String appId = "";
        private String appSecret = "";
        private String redirectUri = "";
        private boolean requireUnionid = true;

        public boolean isConfigured() {
            return !appId.isBlank() && !appSecret.isBlank() && !redirectUri.isBlank();
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId == null ? "" : appId; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret == null ? "" : appSecret; }
        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri == null ? "" : redirectUri; }
        public boolean isRequireUnionid() { return requireUnionid; }
        public void setRequireUnionid(boolean requireUnionid) { this.requireUnionid = requireUnionid; }
    }

    public static class Provisioning {
        private boolean enabled = true;
        private Long defaultTenantId = 2L;
        private int defaultRoleLevel = RoleLevels.EXPERIENCE;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Long getDefaultTenantId() { return defaultTenantId; }
        public void setDefaultTenantId(Long defaultTenantId) { this.defaultTenantId = defaultTenantId; }
        public int getDefaultRoleLevel() { return defaultRoleLevel; }
        public void setDefaultRoleLevel(int defaultRoleLevel) { this.defaultRoleLevel = defaultRoleLevel; }
    }

    public static class State {
        private Duration ttl = Duration.ofMinutes(10);
        private int maxEntries = 10_000;

        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }
        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    }

    public static class Http {
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(5);

        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    public static class RateLimit {
        private int authorizePerMinute = 20;
        private int callbackPerMinute = 30;
        private int localLoginPerMinute = 10;

        public int getAuthorizePerMinute() { return authorizePerMinute; }
        public void setAuthorizePerMinute(int authorizePerMinute) { this.authorizePerMinute = authorizePerMinute; }
        public int getCallbackPerMinute() { return callbackPerMinute; }
        public void setCallbackPerMinute(int callbackPerMinute) { this.callbackPerMinute = callbackPerMinute; }
        public int getLocalLoginPerMinute() { return localLoginPerMinute; }
        public void setLocalLoginPerMinute(int localLoginPerMinute) { this.localLoginPerMinute = localLoginPerMinute; }
    }

    public static class Audit {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public Github getGithub() { return github; }
    public void setGithub(Github github) { this.github = github; }
    public Wechat getWechat() { return wechat; }
    public void setWechat(Wechat wechat) { this.wechat = wechat; }
    public Provisioning getProvisioning() { return provisioning; }
    public void setProvisioning(Provisioning provisioning) { this.provisioning = provisioning; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public Http getHttp() { return http; }
    public void setHttp(Http http) { this.http = http; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    public List<String> getRedirectWhitelist() { return redirectWhitelist; }
    public void setRedirectWhitelist(List<String> redirectWhitelist) {
        this.redirectWhitelist = redirectWhitelist == null ? new ArrayList<>() : redirectWhitelist;
    }
    public Audit getAudit() { return audit; }
    public void setAudit(Audit audit) { this.audit = audit; }
}