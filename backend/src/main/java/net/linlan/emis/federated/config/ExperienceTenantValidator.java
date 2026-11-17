package net.linlan.emis.federated.config;

import net.linlan.emis.federated.repository.EmisTenantMapper;
import net.linlan.emis.federated.repository.entity.EmisTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 体验租户可用性校验：只告警，不阻断启动；运行期开户前仍会再校验。
 */
@Component
@Order(20)
public class ExperienceTenantValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExperienceTenantValidator.class);

    private final FederatedProperties props;
    private final EmisTenantMapper tenantMapper;

    public ExperienceTenantValidator(FederatedProperties props, EmisTenantMapper tenantMapper) {
        this.props = props;
        this.tenantMapper = tenantMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long tenantId = props.getProvisioning().getDefaultTenantId();
        EmisTenant tenant = tenantMapper.findUsable(tenantId);
        if (tenant == null) {
            log.warn("体验租户不可用（tenant_id={}），自动开户将返回 40314", tenantId);
        }
    }
}