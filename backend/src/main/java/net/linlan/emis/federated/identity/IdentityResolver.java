package net.linlan.emis.federated.identity;

import net.linlan.emis.federated.audit.FederatedAuditEventType;
import net.linlan.emis.federated.audit.FederatedAuditService;
import net.linlan.emis.federated.dto.ExternalIdentity;
import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;
import net.linlan.emis.federated.repository.FederatedIdentityMapper;
import net.linlan.emis.federated.repository.entity.FederatedIdentity;
import net.linlan.emis.user.entity.EmisUser;
import net.linlan.emis.user.mapper.EmisUserMapper;
import org.springframework.stereotype.Component;

/**
 * 身份解析：固定顺序 ①active 绑定 → ②历史解绑 → ③邮箱/手机号冲突 → ④开户开关 → ⑤开户。
 * 禁止按外部 ID 直查用户，禁止静默合并。
 */
@Component
public class IdentityResolver {

    private final FederatedIdentityMapper identityMapper;
    private final EmisUserMapper userMapper;
    private final FederatedAuditService audit;

    public IdentityResolver(FederatedIdentityMapper identityMapper, EmisUserMapper userMapper,
                            FederatedAuditService audit) {
        this.identityMapper = identityMapper;
        this.userMapper = userMapper;
        this.audit = audit;
    }

    public Resolution resolve(ExternalIdentity identity, boolean provisioningEnabled) {
        if ("wechat".equals(identity.provider()) && isBlank(identity.subject())) {
            audit.reject(FederatedErrorCode.UNIONID_MISSING, FederatedAuditEventType.UNIONID_MISSING, "wechat");
            throw new FederatedException(FederatedErrorCode.UNIONID_MISSING);
        }
        if (isBlank(identity.subject())) {
            audit.reject(FederatedErrorCode.SUBJECT_MISSING, FederatedAuditEventType.SUBJECT_MISSING, identity.provider());
            throw new FederatedException(FederatedErrorCode.SUBJECT_MISSING);
        }

        FederatedIdentity active = identityMapper.findActiveBySubject(identity.provider(), identity.subject());
        if (active != null) {
            EmisUser user = userMapper.findByIdIncludeDeleted(active.getUserId());
            if (user == null || !"0".equals(user.getDelFlag()) || !"0".equals(user.getStatus())) {
                audit.reject(FederatedErrorCode.ACCOUNT_DISABLED, FederatedAuditEventType.ACCOUNT_DISABLED, identity.provider());
                throw new FederatedException(FederatedErrorCode.ACCOUNT_DISABLED);
            }
            return Resolution.login(user, active);
        }

        if (identityMapper.countHistoric(identity.provider(), identity.subject()) > 0) {
            audit.reject(FederatedErrorCode.REBIND_REQUIRED, FederatedAuditEventType.REBIND_REQUIRED, identity.provider());
            throw new FederatedException(FederatedErrorCode.REBIND_REQUIRED);
        }

        String email = normalize(identity.verifiedEmail());
        if (email != null && userMapper.countActiveByEmail(email) > 0) {
            audit.reject(FederatedErrorCode.EMAIL_CONFLICT, FederatedAuditEventType.EMAIL_CONFLICT, identity.provider());
            throw new FederatedException(FederatedErrorCode.EMAIL_CONFLICT);
        }
        String phone = normalize(identity.phone());
        if (phone != null && userMapper.countActiveByPhonenumber(phone) > 0) {
            audit.reject(FederatedErrorCode.EMAIL_CONFLICT, FederatedAuditEventType.EMAIL_CONFLICT, identity.provider());
            throw new FederatedException(FederatedErrorCode.EMAIL_CONFLICT);
        }

        if (!provisioningEnabled) {
            audit.reject(FederatedErrorCode.REBIND_REQUIRED, FederatedAuditEventType.REBIND_REQUIRED, identity.provider());
            throw new FederatedException(FederatedErrorCode.REBIND_REQUIRED);
        }
        return Resolution.provision();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record Resolution(Kind kind, EmisUser user, FederatedIdentity binding) {

        public enum Kind {
            LOGIN, PROVISION
        }

        public static Resolution login(EmisUser user, FederatedIdentity binding) {
            return new Resolution(Kind.LOGIN, user, binding);
        }

        public static Resolution provision() {
            return new Resolution(Kind.PROVISION, null, null);
        }
    }
}