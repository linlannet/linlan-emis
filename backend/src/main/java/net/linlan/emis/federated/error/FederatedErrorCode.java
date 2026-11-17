package net.linlan.emis.federated.error;

/**
 * 联合登录业务错误码（一码一义，共 15 个）。
 * HTTP 状态与固定中文文案集中此处，禁止在调用点改写语义。
 */
public enum FederatedErrorCode {

    NOT_CONFIGURED(50300, 503, "该登录方式未配置，请联系管理员"),
    CANCELLED(40110, 401, "已取消授权"),
    STATE_INVALID(40111, 401, "登录校验已失效，请重新发起登录"),
    CODE_INVALID(40112, 401, "授权已失效，请重新登录"),
    SUBJECT_MISSING(40113, 401, "未能获取第三方身份，请重试"),
    APP_CREDENTIAL_INVALID(40114, 502, "第三方登录配置错误，请联系管理员"),
    UNIONID_MISSING(40115, 401, "微信身份信息不完整，请联系管理员完成开放平台绑定后重试"),
    REBIND_REQUIRED(40310, 403, "该第三方账号尚未绑定平台账号，请先登录后绑定"),
    ACCOUNT_DISABLED(40311, 403, "账号已停用，请联系管理员"),
    BIND_CONFLICT(40312, 409, "该第三方账号已绑定其他平台账号"),
    UNBIND_LAST_METHOD(40313, 403, "请先设置本地密码或绑定其他登录方式"),
    EXPERIENCE_TENANT_UNAVAILABLE(40314, 403, "体验通道已关闭，请联系管理员"),
    EMAIL_CONFLICT(40910, 409, "已存在同邮箱或手机号账号，请先用本地账号登录后绑定"),
    RATE_LIMITED(42900, 429, "操作过于频繁，请稍后重试"),
    PROVIDER_ERROR(50200, 502, "第三方服务暂时不可用，请稍后重试或使用本地登录");

    private final int code;
    private final int httpStatus;
    private final String message;

    FederatedErrorCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public String getMessage() { return message; }
}