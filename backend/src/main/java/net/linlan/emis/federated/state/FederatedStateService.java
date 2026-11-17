package net.linlan.emis.federated.state;

import net.linlan.emis.federated.config.FederatedProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性 state 服务：SecureRandom 高熵、TTL、原子消费、容量上限。
 * 不依赖 HttpSession 或 Redis（单实例假设）。
 */
@Component
public class FederatedStateService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, FederatedState> states = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxEntries;

    public FederatedStateService(FederatedProperties props) {
        this.ttl = props.getState().getTtl();
        this.maxEntries = props.getState().getMaxEntries();
    }

    public FederatedState create(String provider, String codeVerifier,
                                 FederatedState.ResponseMode responseMode,
                                 FederatedState.StateMode mode,
                                 Long bindUserId, String redirectTarget) {
        cleanup();
        ensureCapacity();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        FederatedState state = new FederatedState(value, provider, codeVerifier, responseMode, mode,
                bindUserId, redirectTarget, now, now.plus(ttl));
        states.put(value, state);
        return state;
    }

    /** 原子消费：无论成功失败都不回写；过期视为无效。 */
    public FederatedState consume(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        FederatedState removed = states.remove(state);
        if (removed == null || removed.isExpired(Instant.now())) {
            return null;
        }
        return removed;
    }

    public int size() {
        return states.size();
    }

    private void cleanup() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private void ensureCapacity() {
        if (states.size() < maxEntries) {
            return;
        }
        states.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                .map(Map.Entry::getKey)
                .ifPresent(states::remove);
    }
}