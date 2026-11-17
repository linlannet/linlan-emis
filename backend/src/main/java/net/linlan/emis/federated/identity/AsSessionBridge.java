package net.linlan.emis.federated.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.linlan.emis.security.EmisUserDetails;
import net.linlan.emis.user.entity.EmisUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 浏览器路径会话桥接：复用既有 HttpSessionSecurityContextRepository（与 formLogin 同一会话），
 * 不新增第二套会话，不在会话中存放 token。
 */
@Component
public class AsSessionBridge {

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public void establish(HttpServletRequest request, HttpServletResponse response, EmisUser user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        EmisUserDetails details = new EmisUserDetails(user.getUserId(), user.getTenantId(), user.getRoleLevel(),
                user.getUserName(), user.getPassword(), true);
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                details, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}