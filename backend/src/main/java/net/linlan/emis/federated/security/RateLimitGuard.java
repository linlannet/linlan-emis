package net.linlan.emis.federated.security;

import jakarta.servlet.http.HttpServletRequest;
import net.linlan.emis.federated.config.FederatedProperties;
import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 最小限流：1 分钟固定窗口，按 (scope, ip) 计数。
 * 覆盖联合登录发起 / 回调 / 本地登录三个 scope，不引入第三方限流依赖。
 */
@Component
public class RateLimitGuard {

    public static final String SCOPE_AUTHORIZE = "federated.authorize";
    public static final String SCOPE_CALLBACK = "federated.callback";
    public static final String SCOPE_LOCAL_LOGIN = "local.login";

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final FederatedProperties props;

    public RateLimitGuard(FederatedProperties props) {
        this.props = props;
    }

    public void checkAuthorize(HttpServletRequest request) {
        check(SCOPE_AUTHORIZE, clientIp(request), props.getRateLimit().getAuthorizePerMinute());
    }

    public void checkCallback(HttpServletRequest request) {
        check(SCOPE_CALLBACK, clientIp(request), props.getRateLimit().getCallbackPerMinute());
    }

    public void checkLocalLogin(HttpServletRequest request) {
        check(SCOPE_LOCAL_LOGIN, clientIp(request), props.getRateLimit().getLocalLoginPerMinute());
    }

    public void check(String scope, String key, int limit) {
        if (limit <= 0) {
            return;
        }
        if (windows.size() > MAX_ENTRIES) {
            long now = System.currentTimeMillis();
            windows.entrySet().removeIf(entry -> now - entry.getValue().startMillis >= WINDOW_MILLIS);
        }
        String cacheKey = scope + '|' + (key == null ? "unknown" : key);
        Window window = windows.computeIfAbsent(cacheKey, ignored -> new Window(System.currentTimeMillis()));
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.startMillis >= WINDOW_MILLIS) {
                window.startMillis = now;
                window.count = 0;
            }
            window.count++;
            if (window.count > limit) {
                throw new FederatedException(FederatedErrorCode.RATE_LIMITED);
            }
        }
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private long startMillis;
        private int count;

        private Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}