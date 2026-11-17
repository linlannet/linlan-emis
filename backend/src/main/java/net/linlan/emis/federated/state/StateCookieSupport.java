package net.linlan.emis.federated.state;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 摘要 Cookie：值为 SHA-256(state)，与会话绑定；HttpOnly + SameSite=Lax。
 * Cookie 不含 state 明文，篡改即校验失败。
 */
@Component
public class StateCookieSupport {

    public static final String COOKIE_NAME = "EMIS_FED_STATE";
    private static final String COOKIE_PATH = "/api/auth/federated";
    private static final int MAX_AGE_SECONDS = 600;

    public void write(HttpServletRequest request, HttpServletResponse response, String state) {
        StringBuilder cookie = new StringBuilder()
                .append(COOKIE_NAME).append('=').append(digest(state))
                .append("; Path=").append(COOKIE_PATH)
                .append("; Max-Age=").append(MAX_AGE_SECONDS)
                .append("; HttpOnly; SameSite=Lax");
        if (isSecureRequired(request)) {
            cookie.append("; Secure");
        }
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public boolean verify(HttpServletRequest request, String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        String expected = digest(state);
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        cookie.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
        return false;
    }

    private static boolean isSecureRequired(HttpServletRequest request) {
        String serverName = request.getServerName();
        return !"localhost".equals(serverName) && !"127.0.0.1".equals(serverName);
    }

    public static String digest(String state) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(state.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}