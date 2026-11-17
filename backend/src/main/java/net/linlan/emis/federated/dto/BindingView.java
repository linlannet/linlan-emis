package net.linlan.emis.federated.dto;

import java.time.Instant;

/**
 * 当前用户有效绑定列表项。subject 以掩码形式返回。
 */
public record BindingView(Long bindingId, String provider, String subjectMasked,
                          String displayName, String avatarUrl, String status,
                          Instant boundAt, Instant lastLoginAt) {
}