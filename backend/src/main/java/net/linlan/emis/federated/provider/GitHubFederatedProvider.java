package net.linlan.emis.federated.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.linlan.emis.federated.config.FederatedProperties;
import net.linlan.emis.federated.dto.ExternalIdentity;
import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GitHub App 联合登录适配。
 * - 使用 user-to-server 授权码换码（App 的 client_id/client_secret）
 * - 不调用 /app/installations/**，不生成 App JWT，不限制组织成员
 * - 身份主键固定为数字用户 ID，不使用 login 或邮箱
 */
@Component
public class GitHubFederatedProvider implements FederatedProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubFederatedProvider.class);

    private final FederatedProperties props;
    private final ProviderHttpClient http;
    private final ObjectMapper objectMapper;

    public GitHubFederatedProvider(FederatedProperties props, ProviderHttpClient http, ObjectMapper objectMapper) {
        this.props = props;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return "github";
    }

    @Override
    public boolean enabled() {
        return props.getGithub().isEnabled();
    }

    @Override
    public boolean configured() {
        return props.getGithub().isConfigured();
    }

    @Override
    public String buildAuthorizeUrl(String state, String codeChallenge) {
        FederatedProperties.Github gh = props.getGithub();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", gh.getClientId());
        query.put("redirect_uri", gh.getRedirectUri());
        query.put("state", state);
        query.put("code_challenge", codeChallenge);
        query.put("code_challenge_method", "S256");
        return GitHubEndpoints.AUTHORIZE_URL + "?" + toQuery(query);
    }

    @Override
    public ExternalIdentity exchange(String code, String codeVerifier) {
        FederatedProperties.Github gh = props.getGithub();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", gh.getClientId());
        form.put("client_secret", gh.getClientSecret());
        form.put("code", code);
        if (codeVerifier != null) {
            form.put("code_verifier", codeVerifier);
        }

        String tokenBody = http.postForm(GitHubEndpoints.TOKEN_URL, form,
                Map.of("Accept", GitHubEndpoints.ACCEPT_JSON, "User-Agent", "linlan-emis"));
        JsonNode tokenNode = read(tokenBody);
        if (tokenNode.hasNonNull("error")) {
            throw GitHubErrorMapper.mapTokenError(tokenNode.get("error").asText());
        }
        String accessToken = tokenNode.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new FederatedException(FederatedErrorCode.CODE_INVALID);
        }

        JsonNode user = read(http.getJson(GitHubEndpoints.USER_URL, Map.of(
                "Accept", GitHubEndpoints.ACCEPT_GITHUB_JSON,
                "Authorization", "Bearer " + accessToken,
                "User-Agent", "linlan-emis")));
        long id = user.path("id").asLong(0L);
        if (id <= 0L) {
            throw new FederatedException(FederatedErrorCode.SUBJECT_MISSING);
        }
        String login = user.path("login").asText("");
        String displayName = user.path("name").asText("");
        if (displayName.isBlank()) {
            displayName = login;
        }
        String avatarUrl = user.path("avatar_url").asText(null);
        return new ExternalIdentity("github", String.valueOf(id), null,
                displayName, avatarUrl, fetchVerifiedEmail(accessToken), null);
    }

    private String fetchVerifiedEmail(String accessToken) {
        try {
            JsonNode emails = read(http.getJson(GitHubEndpoints.EMAILS_URL, Map.of(
                    "Accept", GitHubEndpoints.ACCEPT_GITHUB_JSON,
                    "Authorization", "Bearer " + accessToken,
                    "User-Agent", "linlan-emis")));
            if (emails.isArray()) {
                for (JsonNode email : emails) {
                    if (email.path("primary").asBoolean(false) && email.path("verified").asBoolean(false)) {
                        return email.path("email").asText(null);
                    }
                }
            }
            return null;
        } catch (FederatedException ex) {
            log.warn("GitHub email check skipped (missing Email addresses permission?)");
            return null;
        }
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new FederatedException(FederatedErrorCode.PROVIDER_ERROR, ex);
        }
    }

    private static String toQuery(Map<String, String> query) {
        StringBuilder sb = new StringBuilder();
        query.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}