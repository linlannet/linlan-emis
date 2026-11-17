package net.linlan.emis.federated.audit;

import net.linlan.emis.federated.config.FederatedProperties;
import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.repository.FederatedAuditMapper;
import net.linlan.emis.federated.security.SensitiveMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 审计写入：落表 + 结构化日志双写；任何失败只告警，不阻断主流程。
 */
@Service
public class FederatedAuditService {

    private static final Logger log = LoggerFactory.getLogger(FederatedAuditService.class);

    private final FederatedAuditMapper auditMapper;
    private final FederatedProperties props;

    public FederatedAuditService(FederatedAuditMapper auditMapper, FederatedProperties props) {
        this.auditMapper = auditMapper;
        this.props = props;
    }

    public void record(FederatedAuditEventType type, String provider, String result,
                       Integer errorCode, Long userId, Long tenantId, String subject, String extra) {
        String requestId = MDC.get("request_id");
        HttpServletRequest request = currentRequest();
        String clientIp = request == null ? null : clientIp(request);
        String userAgent = truncate(request == null ? null : request.getHeader("User-Agent"), 255);
        String subjectMask = SensitiveMasker.maskSubject(subject);
        FederatedAuditEvent event = new FederatedAuditEvent(provider, type, result,
                errorCode == null ? null : String.valueOf(errorCode), requestId,
                userId, tenantId, clientIp, userAgent, subjectMask, truncate(extra, 1000));
        try {
            if (props.getAudit().isEnabled()) {
                auditMapper.insert(event);
            }
        } catch (Exception ex) {
            log.warn("federated audit insert failed type={} provider={}", type, provider);
        }
        log.info("fed provider={} event={} result={} code={} req={} uid={} tid={}",
                provider, type, result, errorCode, requestId, userId, tenantId);
    }

    public void success(FederatedAuditEventType type, String provider, Long userId, Long tenantId, String subject) {
        record(type, provider, "success", null, userId, tenantId, subject, null);
    }

    public void reject(FederatedErrorCode code, FederatedAuditEventType type, String provider) {
        record(type, provider, "rejected", code == null ? null : code.getCode(), null, null, null, null);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}