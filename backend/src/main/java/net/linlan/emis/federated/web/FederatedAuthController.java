package net.linlan.emis.federated.web;

import net.linlan.emis.common.Result;
import net.linlan.emis.common.TenantContext;
import net.linlan.emis.federated.config.FederatedProperties;
import net.linlan.emis.federated.dto.BindResult;
import net.linlan.emis.federated.dto.BindingView;
import net.linlan.emis.federated.dto.ProviderStatusView;
import net.linlan.emis.federated.error.FederatedErrorCode;
import net.linlan.emis.federated.error.FederatedException;
import net.linlan.emis.federated.error.FederatedNotFoundException;
import net.linlan.emis.federated.identity.FederatedIdentityService;
import net.linlan.emis.federated.identity.FederatedLoginService;
import net.linlan.emis.federated.provider.FederatedProvider;
import net.linlan.emis.federated.provider.ProviderResolver;
import net.linlan.emis.federated.security.PkceUtil;
import net.linlan.emis.federated.security.RateLimitGuard;
import net.linlan.emis.federated.security.RedirectWhitelist;
import net.linlan.emis.federated.state.FederatedState;
import net.linlan.emis.federated.state.FederatedStateService;
import net.linlan.emis.federated.state.StateCookieSupport;
import net.linlan.emis.user.security.PublicApi;
import net.linlan.emis.user.security.RequiresRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;

/**
 * 联合登录 7 个端点：发起登录/发起绑定/统一回调/绑定列表/解绑/provider 状态/微信内置浏览器引导。
 * 绑定目标只取会话身份，不读取任何请求参数或 Header 中的 user_id / tenant_id / role_level。
 */
@RestController
@RequestMapping("/api/auth/federated")
public class FederatedAuthController {

    private final ProviderResolver providerResolver;
    private final FederatedStateService stateService;
    private final StateCookieSupport cookieSupport;
    private final FederatedLoginService loginService;
    private final FederatedIdentityService identityService;
    private final RateLimitGuard rateLimit;
    private final RedirectWhitelist redirectWhitelist;
    private final FederatedProperties props;
    private final net.linlan.emis.federated.config.GitHubAppKeyProbe keyProbe;

    public FederatedAuthController(ProviderResolver providerResolver, FederatedStateService stateService,
                                   StateCookieSupport cookieSupport, FederatedLoginService loginService,
                                   FederatedIdentityService identityService, RateLimitGuard rateLimit,
                                   RedirectWhitelist redirectWhitelist, FederatedProperties props,
                                   net.linlan.emis.federated.config.GitHubAppKeyProbe keyProbe) {
        this.providerResolver = providerResolver;
        this.stateService = stateService;
        this.cookieSupport = cookieSupport;
        this.loginService = loginService;
        this.identityService = identityService;
        this.rateLimit = rateLimit;
        this.redirectWhitelist = redirectWhitelist;
        this.props = props;
        this.keyProbe = keyProbe;
    }

    @PublicApi
    @GetMapping("/{provider}/authorize")
    public ResponseEntity<?> authorize(@PathVariable String provider,
                                       @RequestParam(name = "response_mode", defaultValue = "redirect") String responseMode,
                                       @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                       HttpServletRequest request, HttpServletResponse response) {
        rateLimit.checkAuthorize(request);
        FederatedProvider federatedProvider = requireProvider(provider);
        requireAvailable(federatedProvider);

        String userAgent = request.getHeader("User-Agent");
        if ("wechat".equals(provider) && userAgent != null && userAgent.contains("MicroMessenger")) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(WechatInAppBrowserPage.render(redirectWhitelist.resolve(redirectUri)));
        }

        FederatedState.ResponseMode mode = toResponseMode(responseMode);
        String verifier = "github".equals(provider) ? PkceUtil.verifier() : null;
        String challenge = verifier == null ? null : PkceUtil.challengeS256(verifier);
        FederatedState state = stateService.create(provider, verifier, mode,
                FederatedState.StateMode.LOGIN, null, redirectUri);
        cookieSupport.write(request, response, state.state());
        return redirectTo(federatedProvider.buildAuthorizeUrl(state.state(), challenge));
    }

    @RequiresRole(2)
    @GetMapping("/{provider}/bind/authorize")
    public ResponseEntity<?> bindAuthorize(@PathVariable String provider,
                                           @RequestParam(name = "response_mode", defaultValue = "json") String responseMode,
                                           @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                           HttpServletRequest request, HttpServletResponse response) {
        rateLimit.checkAuthorize(request);
        FederatedProvider federatedProvider = requireProvider(provider);
        requireAvailable(federatedProvider);

        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.unauthorized("未登录"));
        }
        FederatedState.ResponseMode mode = toResponseMode(responseMode);
        String verifier = "github".equals(provider) ? PkceUtil.verifier() : null;
        String challenge = verifier == null ? null : PkceUtil.challengeS256(verifier);
        FederatedState state = stateService.create(provider, verifier, mode,
                FederatedState.StateMode.BIND, userId, redirectUri);
        cookieSupport.write(request, response, state.state());
        return redirectTo(federatedProvider.buildAuthorizeUrl(state.state(), challenge));
    }

    @PublicApi
    @GetMapping("/{provider}/callback")
    public ResponseEntity<?> callback(@PathVariable String provider,
                                      @RequestParam(name = "code", required = false) String code,
                                      @RequestParam(name = "state", required = false) String state,
                                      @RequestParam(name = "error", required = false) String error,
                                      HttpServletRequest request, HttpServletResponse response) {
        FederatedLoginService.CallbackResult result =
                loginService.handleCallback(provider, code, state, error, request, response);
        if (result.redirect()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(result.redirectTarget()))
                    .build();
        }
        return ResponseEntity.ok(Result.success(result.payload()));
    }

    @RequiresRole(2)
    @GetMapping("/bindings")
    public Result<List<BindingView>> bindings() {
        Long userId = TenantContext.getUserId();
        return Result.success(identityService.listBindings(userId));
    }

    @RequiresRole(2)
    @DeleteMapping("/bindings/{bindingId}")
    public Result<BindResult> unbind(@PathVariable Long bindingId) {
        Long userId = TenantContext.getUserId();
        return Result.success(identityService.unbind(userId, bindingId));
    }

    @RequiresRole(0)
    @GetMapping("/providers")
    public Result<List<ProviderStatusView>> providers() {
        List<ProviderStatusView> views = providerResolver.all().stream()
                .map(provider -> new ProviderStatusView(provider.code(), provider.enabled(), provider.configured(),
                        "github".equals(provider.code()) ? keyProbe.isLoaded() : null))
                .toList();
        return Result.success(views);
    }

    @PublicApi
    @GetMapping("/wechat/in-app-browser-guide")
    public ResponseEntity<String> wechatGuide(@RequestParam(name = "return", required = false) String returnUrl) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(WechatInAppBrowserPage.render(redirectWhitelist.resolve(returnUrl)));
    }

    private FederatedProvider requireProvider(String provider) {
        FederatedProvider resolved = providerResolver.find(provider);
        if (resolved == null) {
            throw new FederatedNotFoundException();
        }
        return resolved;
    }

    private void requireAvailable(FederatedProvider provider) {
        if (!provider.enabled()) {
            throw new FederatedException(FederatedErrorCode.NOT_CONFIGURED, "该登录方式已停用");
        }
        if (!provider.configured()) {
            throw new FederatedException(FederatedErrorCode.NOT_CONFIGURED);
        }
    }

    private static FederatedState.ResponseMode toResponseMode(String value) {
        return "json".equalsIgnoreCase(value)
                ? FederatedState.ResponseMode.JSON
                : FederatedState.ResponseMode.REDIRECT;
    }

    private static ResponseEntity<Void> redirectTo(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}