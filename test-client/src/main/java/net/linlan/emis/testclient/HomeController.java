package net.linlan.emis.testclient;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示首页 / 接口：登录后展示从 BuddyOne id_token 解析出的身份与租户/角色 claim。
 */
@RestController
public class HomeController {

    /** 访问根路径会触发 OIDC 登录（未登录则跳转 BuddyOne 登录页） */
    @GetMapping("/")
    public Map<String, Object> home(@AuthenticationPrincipal OAuth2User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (user == null) {
            result.put("authenticated", false);
            result.put("message", "请访问任意受保护路径以触发 OIDC 登录");
            return result;
        }
        result.put("authenticated", true);
        result.put("name", user.getName());
        result.put("attributes", user.getAttributes());
        return result;
    }

    /** 受保护接口：返回 id_token 中的 claim（含 tenant_id / role_level / user_name） */
    @GetMapping("/api/me")
    public Object me(@AuthenticationPrincipal OAuth2User user) {
        return user != null ? user.getAttributes() : Map.of("authenticated", false);
    }
}
