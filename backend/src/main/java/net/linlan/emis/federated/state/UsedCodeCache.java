package net.linlan.emis.federated.state;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 授权码一次性缓存：只保存 SHA-256(code)，TTL 10 分钟。
 */
@Component
public class UsedCodeCache {

    private static final long TTL_MILLIS = 10 * 60 * 1000L;
    private static final int MAX_ENTRIES = 10_000;

    private final ConcurrentHashMap<String, Instant> usedCodes = new ConcurrentHashMap<>();

    /** 首次返回 true 并登记；重复返回 false。 */
    public boolean checkAndMark(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String hash = sha256(code);
        Instant now = Instant.now();
        if (usedCodes.size() > MAX_ENTRIES) {
            usedCodes.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
            if (usedCodes.size() >= MAX_ENTRIES) {
                usedCodes.entrySet().stream()
                        .min(Comparator.comparing(Map.Entry::getValue))
                        .map(Map.Entry::getKey)
                        .ifPresent(usedCodes::remove);
            }
        }
        return usedCodes.putIfAbsent(hash, now.plusMillis(TTL_MILLIS)) == null;
    }

    public int size() {
        return usedCodes.size();
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}