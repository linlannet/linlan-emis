package net.linlan.emis.federated.repository;

import net.linlan.emis.federated.audit.FederatedAuditEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FederatedAuditMapper {

    @Insert("INSERT INTO emis_federated_audit (event_time, provider, event_type, event_result, error_code, " +
            "request_id, user_id, tenant_id, client_ip, user_agent, subject_mask, extra_info) " +
            "VALUES (NOW(), #{provider}, #{eventType}, #{eventResult}, #{errorCode}, " +
            "#{requestId}, #{userId}, #{tenantId}, #{clientIp}, #{userAgent}, #{subjectMask}, #{extraInfo})")
    int insert(FederatedAuditEvent event);
}