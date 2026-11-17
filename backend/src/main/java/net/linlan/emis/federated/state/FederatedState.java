package net.linlan.emis.federated.state;

import java.time.Instant;

/**
 * 服务端一次性 state 记录。
 */
public record FederatedState(String state, String provider, String codeVerifier,
                             ResponseMode responseMode, StateMode mode,
                             Long bindUserId, String redirectTarget,
                             Instant issuedAt, Instant expiresAt) {

    public enum ResponseMode {
        JSON, REDIRECT
    }

    public enum StateMode {
        LOGIN, BIND
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || now.isAfter(expiresAt);
    }
}