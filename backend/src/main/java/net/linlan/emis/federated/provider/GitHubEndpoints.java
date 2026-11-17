package net.linlan.emis.federated.provider;

/**
 * GitHub 端点常量。GitHub App 的 user-to-server 授权与 OAuth App 共用 /login/oauth/* 端点，
 * 由 client_id 归属与 ghu_ 前缀 token 区分；本实现不调用 /app/installations/**，不生成 App JWT。
 */
public final class GitHubEndpoints {

    public static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    public static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    public static final String USER_URL = "https://api.github.com/user";
    public static final String EMAILS_URL = "https://api.github.com/user/emails";

    public static final String ACCEPT_JSON = "application/json";
    public static final String ACCEPT_GITHUB_JSON = "application/vnd.github+json";

    private GitHubEndpoints() {
    }
}