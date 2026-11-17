package net.linlan.emis.federated.repository.entity;

import java.time.LocalDateTime;

/**
 * 租户（只读投影）。
 */
public class EmisTenant {

    private Long tenantId;
    private String tenantName;
    private String status;
    private LocalDateTime createdAt;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}