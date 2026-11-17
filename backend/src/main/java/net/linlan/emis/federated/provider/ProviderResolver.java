package net.linlan.emis.federated.provider;

import org.springframework.stereotype.Component;

/**
 * provider 解析器：显式 switch，不使用注册表、SPI 或优先级路由。
 */
@Component
public class ProviderResolver {

    private final GitHubFederatedProvider github;
    private final WechatFederatedProvider wechat;

    public ProviderResolver(GitHubFederatedProvider github, WechatFederatedProvider wechat) {
        this.github = github;
        this.wechat = wechat;
    }

    /** 未知 provider 返回 null，由控制器映射为 404（不泄露 provider 清单）。 */
    public FederatedProvider find(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "github" -> github;
            case "wechat" -> wechat;
            default -> null;
        };
    }

    public java.util.List<FederatedProvider> all() {
        return java.util.List.of(github, wechat);
    }
}