package net.linlan.emis.federated.repository.entity;

import java.time.LocalDateTime;

/**
 * 外部身份绑定（只保留历史，不物理删除）。
 */
public class FederatedIdentity {

    private Long bindingId;
    private Long userId;
    private Long tenantId;
    private String provider;
    private String providerSubject;
    private String openId;
    private String displayName;
    private String avatarUrl;
    private String status;
    private String activeFlag;
    private LocalDateTime boundAt;
    private LocalDateTime unboundAt;
    private LocalDateTime lastLoginAt;
    private Long loginCount;
    private String extraJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getBindingId() { return bindingId; }
    public void setBindingId(Long bindingId) { this.bindingId = bindingId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderSubject() { return providerSubject; }
    public void setProviderSubject(String providerSubject) { this.providerSubject = providerSubject; }
    public String getOpenId() { return openId; }
    public void setOpenId(String openId) { this.openId = openId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActiveFlag() { return activeFlag; }
    public void setActiveFlag(String activeFlag) { this.activeFlag = activeFlag; }
    public LocalDateTime getBoundAt() { return boundAt; }
    public void setBoundAt(LocalDateTime boundAt) { this.boundAt = boundAt; }
    public LocalDateTime getUnboundAt() { return unboundAt; }
    public void setUnboundAt(LocalDateTime unboundAt) { this.unboundAt = unboundAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Long getLoginCount() { return loginCount; }
    public void setLoginCount(Long loginCount) { this.loginCount = loginCount; }
    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}