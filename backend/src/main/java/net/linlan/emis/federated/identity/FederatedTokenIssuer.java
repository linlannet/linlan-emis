package net.linlan.emis.federated.identity;

import net.linlan.emis.user.dto.LoginResponse;
import net.linlan.emis.user.entity.EmisUser;
import net.linlan.emis.user.security.JwtUtil;
import org.springframework.stereotype.Component;

/**
 * 平台令牌唯一签发点：复用既有 HS256 JwtUtil，LoginResponse 与本地登录同源同构。
 */
@Component
public class FederatedTokenIssuer {

    private final JwtUtil jwtUtil;

    public FederatedTokenIssuer(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse issue(EmisUser user) {
        LoginResponse response = new LoginResponse();
        response.setToken(jwtUtil.generateToken(user.getUserId(), user.getUserName(),
                user.getRoleLevel(), user.getTenantId()));
        response.setUserId(user.getUserId());
        response.setUserName(user.getUserName());
        response.setNickName(user.getNickName());
        response.setRoleLevel(user.getRoleLevel());
        response.setTenantId(user.getTenantId());
        return response;
    }
}