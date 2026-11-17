package net.linlan.emis.federated.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.linlan.emis.common.TenantContext;
import net.linlan.emis.federated.audit.FederatedAuditEventType;
import net.linlan.emis.federated.audit.FederatedAuditService;
import net.linlan.emis.federated.config.FederatedProperties;
import net.linlan.emis.federated.dto.BindResult;
import net.linlan.emis.federated.dto.ExternalIdentity;
import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;
import net.linlan.emis.federated.error.FederatedNotFoundException;
import net.linlan.emis.federated.provider.FederatedProvider;
import net.linlan.emis.federated.provider.GitHubErrorMapper;
import net.linlan.emis.federated.provider.ProviderResolver;
import net.linlan.emis.federated.security.RateLimitGuard;
import net.linlan.emis.federated.security.RedirectWhitelist;
import net.linlan.emis.federated.state.FederatedState;
import net.linlan.emis.federated.state.FederatedStateService;
import net.linlan.emis.federated.state.StateCookieSupport;
import net.linlan.emis.federated.state.UsedCodeCache;
import net.linlan.emis.user.dto.LoginResponse;
import net.linlan.emis.user.entity.EmisUser;
import org.springframework.stereotype.Service;

/**
 * 回调主流程编排：限流 → state → Cookie → error → code → exchange → 授权码去重
 * → 身份解析/绑定 → 签发 → 会话 → 审计。顺序不可交换。
 */
@Service
public class FederatedLoginService {

    private final ProviderResolver providerResolver;
    private final FederatedStateService stateService;
    private final StateCookieSupport cookieSupport;
    private final UsedCodeCache usedCodeCache;
    private final IdentityResolver identityResolver;
    private final UserProvisioningService provisioningService;
    private final FederatedIdentityService identityService;
    private final FederatedTokenIssuer tokenIssuer;
    private final AsSessionBridge sessionBridge;
    private final RedirectWhitelist redirectWhitelist;
    private final RateLimitGuard rateLimit;
    private final FederatedAuditService audit;
    private final FederatedProperties props;

    public FederatedLoginService(ProviderResolver providerResolver, FederatedStateService stateService,
                                 StateCookieSupport cookieSupport, UsedCodeCache usedCodeCache,
                                 IdentityResolver identityResolver, UserProvisioningService provisioningService,
                                 FederatedIdentityService identityService, FederatedTokenIssuer tokenIssuer,
                                 AsSessionBridge sessionBridge, RedirectWhitelist redirectWhitelist,
                                 RateLimitGuard rateLimit, FederatedAuditService audit, FederatedProperties props) {
        this.providerResolver = providerResolver;
        this.stateService = stateService;
        this.cookieSupport = cookieSupport;
        this.usedCodeCache = usedCodeCache;
        this.identityResolver = identityResolver;
        this.provisioningService = provisioningService;
        this.identityService = identityService;
        this.tokenIssuer = tokenIssuer;
        this.sessionBridge = sessionBridge;
        this.redirectWhitelist = redirectWhitelist;
        this.rateLimit = rateLimit;
        this.audit = audit;
        this.props = props;
    }

    public CallbackResult handleCallback(String providerCode, String code, String state, String error,
                                         HttpServletRequest request, HttpServletResponse response) {
        TenantContext.clear();
        try {
            rateLimit.checkCallback(request);

            FederatedProvider provider = providerResolver.find(providerCode);
            if (provider == null) {
                throw new FederatedNotFoundException();
            }
            if (!provider.enabled()) {
                throw new FederatedException(FederatedErrorCode.NOT_CONFIGURED, "该登录方式已停用");
            }

            FederatedState federatedState = stateService.consume(state);
            if (federatedState == null || !providerCode.equals(federatedState.provider())) {
                throw new FederatedException(FederatedErrorCode.STATE_INVALID);
            }
            if (!cookieSupport.verify(request, federatedState.state())) {
                throw new FederatedException(FederatedErrorCode.STATE_INVALID);
            }

            if (error != null && !error.isBlank()) {
                throw GitHubErrorMapper.mapCallbackError(error);
            }
            if (code == null || code.isBlank()) {
                throw new FederatedException(FederatedErrorCode.CODE_INVALID);
            }
            if (!provider.configured()) {
                throw new FederatedException(FederatedErrorCode.NOT_CONFIGURED);
            }

            ExternalIdentity identity = provider.exchange(code, federatedState.codeVerifier());

            if (!usedCodeCache.checkAndMark(code)) {
                audit.reject(FederatedErrorCode.CODE_INVALID, FederatedAuditEventType.CODE_REPLAY, providerCode);
                throw new FederatedException(FederatedErrorCode.CODE_INVALID);
            }

            if (federatedState.mode() == FederatedState.StateMode.BIND) {
                BindResult result = identityService.bind(federatedState.bindUserId(), identity);
                return CallbackResult.binding(result);
            }

            IdentityResolver.Resolution resolution =
                    identityResolver.resolve(identity, props.getProvisioning().isEnabled());
            EmisUser user = switch (resolution.kind()) {
                case LOGIN -> identityService.touchLogin(resolution.binding(), identity);
                case PROVISION -> provisioningService.provision(identity, providerCode);
            };

            LoginResponse loginResponse = tokenIssuer.issue(user);
            audit.success(FederatedAuditEventType.LOGIN_SUCCESS, providerCode,
                    user.getUserId(), user.getTenantId(), identity.subject());

            if (federatedState.responseMode() == FederatedState.ResponseMode.REDIRECT) {
                sessionBridge.establish(request, response, user);
                return CallbackResult.redirect(redirectWhitelist.resolve(federatedState.redirectTarget()));
            }
            return CallbackResult.json(loginResponse);
        } catch (FederatedException ex) {
            auditFailure(ex, providerCode);
            throw ex;
        } finally {
            TenantContext.clear();
        }
    }

    private void auditFailure(FederatedException ex, String providerCode) {
        FederatedAuditEventType type = switch (ex.getCode()) {
            case 40110 -> FederatedAuditEventType.LOGIN_CANCELLED;
            case 40111 -> FederatedAuditEventType.STATE_INVALID;
            case 40112 -> FederatedAuditEventType.CODE_INVALID;
            case 40113 -> FederatedAuditEventType.SUBJECT_MISSING;
            case 40114 -> FederatedAuditEventType.APP_CREDENTIAL_INVALID;
            case 40115 -> FederatedAuditEventType.UNIONID_MISSING;
            case 40310 -> FederatedAuditEventType.REBIND_REQUIRED;
            case 40311 -> FederatedAuditEventType.ACCOUNT_DISABLED;
            case 40312 -> FederatedAuditEventType.BIND_CONFLICT;
            case 40313 -> FederatedAuditEventType.UNBIND_LAST_METHOD;
            case 40314 -> FederatedAuditEventType.EXPERIENCE_TENANT_UNAVAILABLE;
            case 40910 -> FederatedAuditEventType.EMAIL_CONFLICT;
            case 42900 -> FederatedAuditEventType.RATE_LIMITED;
            case 50300 -> FederatedAuditEventType.CONFIG_MISSING;
            default -> FederatedAuditEventType.LOGIN_FAILED;
        };
        audit.record(type, providerCode, "rejected", ex.getCode(), null, null, null, null);
    }

    public record CallbackResult(boolean redirect, LoginResponse loginResponse,
                                 BindResult bindResult, String redirectTarget) {

        public static CallbackResult json(LoginResponse loginResponse) {
            return new CallbackResult(false, loginResponse, null, null);
        }

        public static CallbackResult redirect(String target) {
            return new CallbackResult(true, null, null, target);
        }

        public static CallbackResult binding(BindResult bindResult) {
            return new CallbackResult(false, null, bindResult, null);
        }

        public Object payload() {
            return loginResponse != null ? loginResponse : bindResult;
        }
    }
}