package net.linlan.emis.federated.identity;

import net.linlan.emis.federated.repository.FederatedIdentityMapper;
import net.linlan.emis.user.entity.EmisUser;
import org.springframework.stereotype.Component;

/**
 * 解绑守卫：无本地可用密码且仅剩 1 个 active 绑定时，禁止解绑（40313）。
 */
@Component
public class UnbindGuard {

    private final FederatedIdentityMapper identityMapper;

    public UnbindGuard(FederatedIdentityMapper identityMapper) {
        this.identityMapper = identityMapper;
    }

    public boolean isLastLoginMethod(EmisUser user) {
        boolean hasLocalPassword = user.getPassword() != null
                && !user.getPassword().isBlank()
                && !UserProvisioningService.FEDERATED_PASSWORD_SENTINEL.equals(user.getPassword());
        long activeBindings = identityMapper.countActiveByUser(user.getUserId());
        return !hasLocalPassword && activeBindings == 1;
    }
}