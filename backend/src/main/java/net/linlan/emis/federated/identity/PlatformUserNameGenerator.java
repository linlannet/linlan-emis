package net.linlan.emis.federated.identity;

import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;
import net.linlan.emis.federated.state.UsedCodeCache;
import net.linlan.emis.user.mapper.EmisUserMapper;
import org.springframework.stereotype.Component;

/**
 * 平台用户名生成：github → gh_&lt;数字ID&gt;；wechat → wx_&lt;sha256(unionid) 前 12 位&gt;。
 * 冲突后缀 _2.._5，5 次仍冲突 → 50200。用户名不泄露 unionid。
 */
@Component
public class PlatformUserNameGenerator {

    private static final int MAX_LENGTH = 64;

    private final EmisUserMapper userMapper;

    public PlatformUserNameGenerator(EmisUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public String generate(String provider, String subject) {
        String base = "github".equals(provider)
                ? "gh_" + subject
                : "wx_" + UsedCodeCache.sha256(subject).substring(0, 12);
        if (base.length() > MAX_LENGTH) {
            base = base.substring(0, MAX_LENGTH);
        }
        for (int suffix = 1; suffix <= 5; suffix++) {
            String candidate = suffix == 1 ? base : base + "_" + suffix;
            if (candidate.length() > MAX_LENGTH) {
                candidate = candidate.substring(0, MAX_LENGTH);
            }
            if (userMapper.countByUserNameIncludeDeleted(candidate) == 0) {
                return candidate;
            }
        }
        throw new FederatedException(FederatedErrorCode.PROVIDER_ERROR);
    }
}