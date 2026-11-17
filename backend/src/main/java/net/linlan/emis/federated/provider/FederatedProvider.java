package net.linlan.emis.federated.provider;

import net.linlan.emis.federated.dto.ExternalIdentity;

/**
 * 联合登录 provider 唯一扩展点：授权发起、换码、外部身份提取。
 * 新增 provider = 新实现类 + 配置项（需要改代码），不引入注册表或插件发现。
 */
public interface FederatedProvider {

    String code();

    boolean enabled();

    boolean configured();

    String buildAuthorizeUrl(String state, String codeChallenge);

    ExternalIdentity exchange(String code, String codeVerifier);
}