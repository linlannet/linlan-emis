package net.linlan.emis.federated.provider;

import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;

/**
 * 微信 errcode → 业务码映射。只记录 provider:code 标签，不记录 errmsg 原文。
 */
public final class WechatErrcodeMapper {

    private WechatErrcodeMapper() {
    }

    public static FederatedException map(String errcode) {
        if (errcode == null || errcode.isBlank()) {
            return new FederatedException(FederatedErrorCode.PROVIDER_ERROR);
        }
        return switch (errcode) {
            case "40029", "40163" -> new FederatedException(FederatedErrorCode.CODE_INVALID);
            case "40013", "40125", "40001" -> new FederatedException(FederatedErrorCode.APP_CREDENTIAL_INVALID);
            case "-1", "45011" -> new FederatedException(FederatedErrorCode.PROVIDER_ERROR);
            default -> new FederatedException(FederatedErrorCode.PROVIDER_ERROR);
        };
    }

    public static String label(String errcode) {
        return "wechat:" + (errcode == null ? "unknown" : errcode);
    }
}