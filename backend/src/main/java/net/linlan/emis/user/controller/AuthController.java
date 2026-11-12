package net.linlan.emis.user.controller;

import net.linlan.emis.common.Result;
import net.linlan.emis.user.dto.LoginRequest;
import net.linlan.emis.user.dto.LoginResponse;
import net.linlan.emis.user.dto.RegisterRequest;
import net.linlan.emis.user.dto.UserInfoResponse;
import net.linlan.emis.user.security.PublicApi;
import net.linlan.emis.user.security.RequiresRole;
import net.linlan.emis.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PublicApi
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @PublicApi
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    /** 获取当前登录用户（任意已登录用户可访问） */
    @RequiresRole(2)
    @GetMapping("/me")
    public Result<UserInfoResponse> me() {
        return Result.success(userService.getCurrentUserInfo());
    }
}
