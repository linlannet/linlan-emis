package net.linlan.emis.federated.dto;

/**
 * provider 只读状态：仅暴露三个布尔，不含 client-id / secret / 回调地址 / 私钥路径。
 */
public record ProviderStatusView(String provider, boolean enabled,
                                 boolean configured, Boolean privateKeyLoaded) {
}