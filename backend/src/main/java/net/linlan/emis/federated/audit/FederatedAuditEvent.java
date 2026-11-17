package net.linlan.emis.federated.audit;

/**
 * 审计事件（脱敏后写入）。
 */
public record FederatedAuditEvent(String provider, FederatedAuditEventType eventType,
                                  String eventResult, String errorCode, String requestId,
                                  Long userId, Long tenantId, String clientIp,
                                  String userAgent, String subjectMask, String extraInfo) {
}