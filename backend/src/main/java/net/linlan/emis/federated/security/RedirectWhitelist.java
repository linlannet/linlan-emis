package net.linlan.emis.federated.security;

import net.linlan.emis.federated.config.FederatedProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/**
 * 登录完成后的前端回跳白名单：精确匹配 scheme + host + port + path。
 * 不使用 startsWith，防 good.com.evil.com / good.com@evil.com。
 */
@Component
public class RedirectWhitelist {

    private static final String DEFAULT_PATH = "/";

    private final List<String> allowed;

    public RedirectWhitelist(FederatedProperties props) {
        this.allowed = props.getRedirectWhitelist().stream()
                .filter(entry -> entry != null && !entry.isBlank())
                .map(RedirectWhitelist::normalize)
                .toList();
    }

    public boolean matches(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String normalized = normalize(uri);
        if (normalized == null) {
            return false;
        }
        return allowed.contains(normalized);
    }

    /** 未命中白名单时回到默认页，绝不跳转到请求方指定地址。 */
    public String resolve(String target) {
        return matches(target) ? target : DEFAULT_PATH;
    }

    private static String normalize(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase();
            host = host.toLowerCase();
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = DEFAULT_PATH;
            }
            return scheme + "://" + host + ":" + port + path;
        } catch (Exception ex) {
            return null;
        }
    }
}