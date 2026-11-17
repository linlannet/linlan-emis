package net.linlan.emis.federated.identity;

import net.linlan.emis.federated.audit.FederatedAuditEventType;
import net.linlan.emis.federated.audit.FederatedAuditService;
import net.linlan.emis.federated.config.FederatedProperties;
import net.linlan.emis.federated.dto.ExternalIdentity;
import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;
import net.linlan.emis.federated.repository.EmisTenantMapper;
import net.linlan.emis.federated.repository.FederatedIdentityMapper;
import net.linlan.emis.federated.repository.entity.EmisTenant;
import net.linlan.emis.federated.repository.entity.FederatedIdentity;
import net.linlan.emis.user.entity.EmisUser;
import net.linlan.emis.user.mapper.EmisUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自助开户事务：用户与绑定在同一事务内写入；任一失败整体回滚。
 */
@Service
public class UserProvisioningService {

    public static final String FEDERATED_PASSWORD_SENTINEL = "!";

    private final EmisUserMapper userMapper;
    private final FederatedIdentityMapper identityMapper;
    private final EmisTenantMapper tenantMapper;
    private final PlatformUserNameGenerator userNameGenerator;
    private final FederatedProperties props;
    private final FederatedAuditService audit;

    public UserProvisioningService(EmisUserMapper userMapper, FederatedIdentityMapper identityMapper,
                                   EmisTenantMapper tenantMapper, PlatformUserNameGenerator userNameGenerator,
                                   FederatedProperties props, FederatedAuditService audit) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.tenantMapper = tenantMapper;
        this.userNameGenerator = userNameGenerator;
        this.props = props;
        this.audit = audit;
    }

    @Transactional(rollbackFor = Exception.class)
    public EmisUser provision(ExternalIdentity identity, String providerCode) {
        if (identityMapper.findActiveBySubject(providerCode, identity.subject()) != null) {
            audit.reject(FederatedErrorCode.BIND_CONFLICT, FederatedAuditEventType.BIND_CONFLICT, providerCode);
            throw new FederatedException(FederatedErrorCode.BIND_CONFLICT);
        }

        EmisTenant tenant = tenantMapper.findUsable(props.getProvisioning().getDefaultTenantId());
        if (tenant == null || !"0".equals(tenant.getStatus())) {
            audit.reject(FederatedErrorCode.EXPERIENCE_TENANT_UNAVAILABLE,
                    FederatedAuditEventType.EXPERIENCE_TENANT_UNAVAILABLE, providerCode);
            throw new FederatedException(FederatedErrorCode.EXPERIENCE_TENANT_UNAVAILABLE);
        }

        String userName = userNameGenerator.generate(providerCode, identity.subject());

        EmisUser user = new EmisUser();
        user.setTenantId(tenant.getTenantId());
        user.setRoleLevel(props.getProvisioning().getDefaultRoleLevel());
        user.setUserName(userName);
        user.setNickName(truncate(identity.displayName(), 64));
        user.setAvatarUrl(truncate(identity.avatarUrl(), 255));
        user.setPassword(FEDERATED_PASSWORD_SENTINEL);
        user.setStatus("0");
        user.setDelFlag("0");
        user.setCreatedBy("federated:" + providerCode);
        userMapper.insert(user);

        FederatedIdentity binding = new FederatedIdentity();
        binding.setUserId(user.getUserId());
        binding.setTenantId(user.getTenantId());
        binding.setProvider(providerCode);
        binding.setProviderSubject(identity.subject());
        binding.setOpenId(identity.openId());
        binding.setDisplayName(truncate(identity.displayName(), 128));
        binding.setAvatarUrl(truncate(identity.avatarUrl(), 512));
        binding.setStatus("active");
        binding.setActiveFlag("Y");
        try {
            identityMapper.insert(binding);
        } catch (DuplicateKeyException ex) {
            audit.reject(FederatedErrorCode.BIND_CONFLICT, FederatedAuditEventType.BIND_CONFLICT, providerCode);
            throw new FederatedException(FederatedErrorCode.BIND_CONFLICT);
        }

        audit.success(FederatedAuditEventType.PROVISION_SUCCESS, providerCode,
                user.getUserId(), user.getTenantId(), identity.subject());
        return user;
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}