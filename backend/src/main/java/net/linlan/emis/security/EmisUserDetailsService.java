package net.linlan.emis.security;

import net.linlan.emis.user.entity.EmisUser;
import net.linlan.emis.user.mapper.EmisUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 授权服务器登录用的 UserDetailsService：从 emis_user 加载身份。
 * 密码为 BCrypt（spring-security-crypto，由 spring-security 的 DaoAuthenticationProvider 校验）。
 */
@Service
public class EmisUserDetailsService implements UserDetailsService {

    private final EmisUserMapper userMapper;

    public EmisUserDetailsService(EmisUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        EmisUser user = userMapper.findByUserName(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return new EmisUserDetails(
                user.getUserId(),
                user.getTenantId(),
                user.getRoleLevel(),
                user.getUserName(),
                user.getPassword(),
                "0".equals(user.getStatus()));
    }
}
