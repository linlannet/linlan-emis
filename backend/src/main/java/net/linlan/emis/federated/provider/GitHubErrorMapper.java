package net.linlan.emis.federated.provider;

import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;

/**
 * GitHub 回调 error → 业务码映射。
 */
public final class GitHubErrorMapper {

    private GitHubErrorMapper() {
    }

    public static FederatedException mapCallbackError(String error) {
        if (error == null || error.isBlank()) {
            return new FederatedException(FederatedErrorCode.PROVIDER_ERROR);
        }
        return switch (error) {
            case "access_denied" -> new FederatedException(FederatedErrorCode.CANCELLED);
            case "bad_verification_code", "invalid_grant" -> new FederatedException(FederatedErrorCode.CODE_INVALID);
            case "incorrect_client_credentials", "redirect_uri_mismatch",
                 "unverified_client", "unsupported_grant_type" ->
                    new FederatedException(FederatedErrorCode.APP_CREDENTIAL_INVALID);
            default -> new FederatedException(FederatedErrorCode.PROVIDER_ERROR);
        };
    }

    public static FederatedException mapTokenError(String error) {
        if (error == null || error.isBlank()) {
            return new FederatedException(FederatedErrorCode.PROVIDER_ERROR);
        }
        return switch (error) {
            case "bad_verification_code", "invalid_grant", "expired_token" ->
                    new FederatedException(FederatedErrorCode.CODE_INVALID);
            case "incorrect_client_credentials", "redirect_uri_mismatch" ->
                    new FederatedException(FederatedErrorCode.APP_CREDENTIAL_INVALID);
            default -> new FederatedException(FederatedErrorCode.PROVIDER_ERROR);
        };
    }
}