package net.linlan.emis.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 自定义 UserDetails：在 Spring Security 标准字段之外携带 tenantId / roleLevel / userId，
 * 供 OAuth2 令牌定制器（OAuth2TokenCustomizer）把这些字段编入 JWT。
 */
public class EmisUserDetails implements UserDetails {

    private final Long userId;
    private final Long tenantId;
    private final Integer roleLevel;
    private final String username;
    private final String password;
    private final boolean enabled;

    public EmisUserDetails(Long userId, Long tenantId, Integer roleLevel,
                           String username, String password, boolean enabled) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.roleLevel = roleLevel;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Integer getRoleLevel() {
        return roleLevel;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AuthorityUtils.createAuthorityList("ROLE_USER");
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
