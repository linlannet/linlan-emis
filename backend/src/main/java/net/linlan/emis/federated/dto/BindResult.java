package net.linlan.emis.federated.dto;

import java.time.Instant;

/**
 * 绑定/解绑结果。
 */
public record BindResult(String provider, String providerSubjectMasked, Instant boundAtOrUnboundAt) {
}