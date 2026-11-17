package net.linlan.emis.federated.identity;

import net.linlan.emis.federated.audit.FederatedAuditEventType;
import net.linlan.emis.federated.audit.FederatedAuditService;
import net.linlan.emis.federated.dto.BindResult;
import net.linlan.emis.federated.dto.BindingView;
import net.linlan.emis.federated.dto.ExternalIdentity;
import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;
import net.linlan.emis.federated.error.FederatedNotFoundException;
import net.linlan.emis.federated.repository.FederatedIdentityMapper;
import net.linlan.emis.federated.repository.entity.FederatedIdentity;
import net.linlan.emis.federated.security.SensitiveMasker;
import net.linlan.emis.user.entity.EmisUser;
import net.linlan.emis.user.mapper.EmisUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * 绑定、解绑、登录副作用与软删联动。
 * 只写绑定表；不改 emis_user 的 user_name / tenant_id / role_level。
 */
@Service
public class FederatedIdentityService {

    private final FederatedIdentityMapper identityMapper;
    private final EmisUserMapper userMapper;
    private final UnbindGuard unbindGuard;
    private final FederatedAuditService audit;

    public FederatedIdentityService(FederatedIdentityMapper identityMapper, EmisUserMapper userMapper,
                                    UnbindGuard unbindGuard, FederatedAuditService audit) {
        this.identityMapper = identityMapper;
        this.userMapper = userMapper;
        this.unbindGuard = unbindGuard;
        this.audit = audit;
    }

    /** 已绑定登录：只更新登录痕迹；资料仅在为空时首次填充。 */
    public EmisUser touchLogin(FederatedIdentity binding, ExternalIdentity identity) {
        identityMapper.touchLogin(binding.getBindingId(), identity.openId());
        userMapper.fillProfileIfBlank(binding.getUserId(),
                truncate(identity.displayName(), 64), truncate(identity.avatarUrl(), 255));
        return userMapper.findById(binding.getUserId());
    }

    @Transactional(rollbackFor = Exception.class)
    public BindResult bind(Long sessionUserId, ExternalIdentity identity) {
        EmisUser user = userMapper.findByIdIncludeDeleted(sessionUserId);
        if (user == null || !"0".equals(user.getDelFlag()) || !"0".equals(user.getStatus())) {
            audit.reject(FederatedErrorCode.ACCOUNT_DISABLED, FederatedAuditEventType.ACCOUNT_DISABLED, identity.provider());
            throw new FederatedException(FederatedErrorCode.ACCOUNT_DISABLED);
        }
        if (identityMapper.findActiveBySubject(identity.provider(), identity.subject()) != null) {
            audit.reject(FederatedErrorCode.BIND_CONFLICT, FederatedAuditEventType.BIND_CONFLICT, identity.provider());
            throw new FederatedException(FederatedErrorCode.BIND_CONFLICT);
        }
        if (identityMapper.findActiveByUserAndProvider(sessionUserId, identity.provider()) != null) {
            audit.reject(FederatedErrorCode.BIND_CONFLICT, FederatedAuditEventType.BIND_CONFLICT, identity.provider());
            throw new FederatedException(FederatedErrorCode.BIND_CONFLICT);
        }

        FederatedIdentity binding = new FederatedIdentity();
        binding.setUserId(sessionUserId);
        binding.setTenantId(user.getTenantId());
        binding.setProvider(identity.provider());
        binding.setProviderSubject(identity.subject());
        binding.setOpenId(identity.openId());
        binding.setDisplayName(truncate(identity.displayName(), 128));
        binding.setAvatarUrl(truncate(identity.avatarUrl(), 512));
        binding.setStatus("active");
        binding.setActiveFlag("Y");
        try {
            identityMapper.insert(binding);
        } catch (DuplicateKeyException ex) {
            audit.reject(FederatedErrorCode.BIND_CONFLICT, FederatedAuditEventType.BIND_CONFLICT, identity.provider());
            throw new FederatedException(FederatedErrorCode.BIND_CONFLICT);
        }
        audit.success(FederatedAuditEventType.BIND_SUCCESS, identity.provider(),
                sessionUserId, user.getTenantId(), identity.subject());
        return new BindResult(identity.provider(), SensitiveMasker.maskSubject(identity.subject()), Instant.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public BindResult unbind(Long sessionUserId, Long bindingId) {
        FederatedIdentity binding = identityMapper.findById(bindingId);
        if (binding == null || !binding.getUserId().equals(sessionUserId)) {
            throw new FederatedNotFoundException();
        }
        if (!"active".equals(binding.getStatus())) {
            throw new FederatedNotFoundException();
        }

        EmisUser user = userMapper.findByIdIncludeDeleted(sessionUserId);
        if (user == null || !"0".equals(user.getDelFlag()) || !"0".equals(user.getStatus())) {
            audit.reject(FederatedErrorCode.ACCOUNT_DISABLED, FederatedAuditEventType.ACCOUNT_DISABLED, binding.getProvider());
            throw new FederatedException(FederatedErrorCode.ACCOUNT_DISABLED);
        }
        if (unbindGuard.isLastLoginMethod(user)) {
            audit.reject(FederatedErrorCode.UNBIND_LAST_METHOD, FederatedAuditEventType.UNBIND_LAST_METHOD, binding.getProvider());
            throw new FederatedException(FederatedErrorCode.UNBIND_LAST_METHOD);
        }

        if (identityMapper.markUnbound(bindingId, sessionUserId) == 0) {
            throw new FederatedNotFoundException();
        }
        audit.success(FederatedAuditEventType.UNBIND_SUCCESS, binding.getProvider(),
                sessionUserId, user.getTenantId(), binding.getProviderSubject());
        return new BindResult(binding.getProvider(),
                SensitiveMasker.maskSubject(binding.getProviderSubject()), Instant.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public int invalidateByUser(Long userId) {
        int rows = identityMapper.invalidateByUser(userId);
        if (rows > 0) {
            audit.record(FederatedAuditEventType.BIND_INVALIDATED, null, "success",
                    null, userId, null, null, "rows=" + rows);
        }
        return rows;
    }

    public List<BindingView> listBindings(Long userId) {
        return identityMapper.listActiveByUser(userId).stream()
                .map(binding -> new BindingView(
                        binding.getBindingId(),
                        binding.getProvider(),
                        SensitiveMasker.maskSubject(binding.getProviderSubject()),
                        binding.getDisplayName(),
                        binding.getAvatarUrl(),
                        binding.getStatus(),
                        toInstant(binding.getBoundAt()),
                        toInstant(binding.getLastLoginAt())))
                .toList();
    }

    private static Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}