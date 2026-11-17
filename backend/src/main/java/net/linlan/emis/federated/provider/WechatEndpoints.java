package net.linlan.emis.federated.provider;

/**
 * 微信开放平台网站应用扫码端点与字段常量（不使用小程序/公众号端点）。
 */
public final class WechatEndpoints {

    public static final String QRCONNECT_URL = "https://open.weixin.qq.com/connect/qrconnect";
    public static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    public static final String USER_INFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    public static final String SCOPE_SNSAPI_LOGIN = "snsapi_login";
    public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";

    public static final String FIELD_OPEN_ID = "openid";
    public static final String FIELD_UNION_ID = "unionid";
    public static final String FIELD_NICK_NAME = "nickname";
    public static final String FIELD_HEAD_IMG_URL = "headimgurl";
    public static final String FIELD_ERRCODE = "errcode";
    public static final String FIELD_ERRMSG = "errmsg";
    public static final String FIELD_ACCESS_TOKEN = "access_token";

    private WechatEndpoints() {
    }
}