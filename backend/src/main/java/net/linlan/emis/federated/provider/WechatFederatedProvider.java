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
 * 微信开放平台网站应用扫码登录适配。
 * - 身份主键固定为 unionid（只认换码响应），unionid 缺失直接拒绝，禁止回退 openid
 * - openid 仅作附属标识，记录最近一次
 * - 不使用小程序/公众号端点，不支持 PKCE
 */
@Component
public class WechatFederatedProvider implements FederatedProvider {

    private static final Logger log = LoggerFactory.getLogger(WechatFederatedProvider.class);

    private final FederatedProperties props;
    private final ProviderHttpClient http;
    private final ObjectMapper objectMapper;

    public WechatFederatedProvider(FederatedProperties props, ProviderHttpClient http, ObjectMapper objectMapper) {
        this.props = props;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return "wechat";
    }

    @Override
    public boolean enabled() {
        return props.getWechat().isEnabled();
    }

    @Override
    public boolean configured() {
        return props.getWechat().isConfigured();
    }

    @Override
    public String buildAuthorizeUrl(String state, String codeChallenge) {
        FederatedProperties.Wechat wx = props.getWechat();
        Map<String, String> query = new LinkedHashMap<>();
        query.put("appid", wx.getAppId());
        query.put("redirect_uri", wx.getRedirectUri());
        query.put("response_type", "code");
        query.put("scope", WechatEndpoints.SCOPE_SNSAPI_LOGIN);
        query.put("state", state);
        return WechatEndpoints.QRCONNECT_URL + "?" + toQuery(query) + "#wechat_redirect";
    }

    @Override
    public ExternalIdentity exchange(String code, String codeVerifier) {
        FederatedProperties.Wechat wx = props.getWechat();
        String tokenUrl = WechatEndpoints.ACCESS_TOKEN_URL
                + "?appid=" + enc(wx.getAppId())
                + "&secret=" + enc(wx.getAppSecret())
                + "&code=" + enc(code)
                + "&grant_type=" + WechatEndpoints.GRANT_TYPE_AUTHORIZATION_CODE;
        JsonNode token = read(http.getJson(tokenUrl, Map.of()));
        String errcode = token.path(WechatEndpoints.FIELD_ERRCODE).asText("");
        if (!errcode.isBlank() && !"0".equals(errcode)) {
            log.warn("wechat token exchange rejected: {}", WechatErrcodeMapper.label(errcode));
            throw WechatErrcodeMapper.map(errcode);
        }

        String unionId = token.path(WechatEndpoints.FIELD_UNION_ID).asText("");
        if (unionId.isBlank()) {
            throw new FederatedException(FederatedErrorCode.UNIONID_MISSING);
        }
        String openId = token.path(WechatEndpoints.FIELD_OPEN_ID).asText("");
        if (openId.isBlank()) {
            throw new FederatedException(FederatedErrorCode.SUBJECT_MISSING);
        }
        String accessToken = token.path(WechatEndpoints.FIELD_ACCESS_TOKEN).asText("");

        String nickName = null;
        String avatarUrl = null;
        try {
            String userInfoUrl = WechatEndpoints.USER_INFO_URL
                    + "?access_token=" + enc(accessToken)
                    + "&openid=" + enc(openId)
                    + "&lang=zh_CN";
            JsonNode info = read(http.getJson(userInfoUrl, Map.of()));
            String infoErr = info.path(WechatEndpoints.FIELD_ERRCODE).asText("");
            if (infoErr.isBlank() || "0".equals(infoErr)) {
                nickName = info.path(WechatEndpoints.FIELD_NICK_NAME).asText(null);
                avatarUrl = info.path(WechatEndpoints.FIELD_HEAD_IMG_URL).asText(null);
            }
        } catch (FederatedException ex) {
            log.warn("wechat userinfo skipped; profile stays empty");
        }
        return new ExternalIdentity("wechat", unionId, openId, nickName, avatarUrl, null, null);
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new FederatedException(FederatedErrorCode.PROVIDER_ERROR, ex);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
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
            sb.append(enc(k)).append('=').append(enc(v));
        });
        return sb.toString();
    }
}