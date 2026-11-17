package net.linlan.emis.user.service;

import net.linlan.emis.common.TenantContext;
import net.linlan.emis.federated.config.FederatedProperties;
import net.linlan.emis.federated.config.RoleLevels;
import net.linlan.emis.federated.identity.FederatedIdentityService;
import net.linlan.emis.federated.identity.UserProvisioningService;
import net.linlan.emis.user.security.JwtUtil;
import net.linlan.emis.user.dto.LoginRequest;
import net.linlan.emis.user.dto.LoginResponse;
import net.linlan.emis.user.dto.RegisterRequest;
import net.linlan.emis.user.dto.UserInfoResponse;
import net.linlan.emis.user.entity.EmisUser;
import net.linlan.emis.user.mapper.EmisUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户认证与服务（改编自 dsh-java-ontology 的 SysUserService，repository 替换为 MyBatis Mapper）。
 */
@Service
public class UserService {

    private final EmisUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final FederatedProperties federatedProperties;
    private final FederatedIdentityService federatedIdentityService;

    public UserService(EmisUserMapper userMapper, JwtUtil jwtUtil, BCryptPasswordEncoder passwordEncoder,
                       FederatedProperties federatedProperties, FederatedIdentityService federatedIdentityService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.federatedProperties = federatedProperties;
        this.federatedIdentityService = federatedIdentityService;
    }

    public LoginResponse login(LoginRequest request) {
        EmisUser user = userMapper.findByUserName(request.getUserName());
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if ("1".equals(user.getStatus())) {
            throw new IllegalStateException("账号已停用");
        }
        if (UserProvisioningService.FEDERATED_PASSWORD_SENTINEL.equals(user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return toLogin(user);
    }

    public LoginResponse register(RegisterRequest request) {
        if (userMapper.findByUserName(request.getUserName()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        EmisUser user = new EmisUser();
        user.setUserName(request.getUserName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickName(request.getNickName());
        user.setEmail(request.getEmail());
        user.setPhonenumber(request.getPhonenumber());
        user.setTenantId(federatedProperties.getProvisioning().getDefaultTenantId());
        user.setRoleLevel(RoleLevels.EXPERIENCE);
        user.setStatus("0");
        user.setCreatedBy("register");
        userMapper.insert(user);
        return toLogin(user);
    }

    public UserInfoResponse getCurrentUserInfo() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            throw new IllegalStateException("未登录");
        }
        EmisUser user = userMapper.findById(userId);
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }
        return toInfo(user);
    }

    public List<UserInfoResponse> findAll() {
        return userMapper.findAll().stream().map(this::toInfo).toList();
    }

    public long count() {
        return userMapper.count();
    }

    @Transactional(rollbackFor = Exception.class)
    public int softDelete(Long userId) {
        int rows = userMapper.softDelete(userId);
        if (rows > 0) {
            federatedIdentityService.invalidateByUser(userId);
        }
        return rows;
    }

    private LoginResponse toLogin(EmisUser user) {
        LoginResponse response = new LoginResponse();
        response.setToken(jwtUtil.generateToken(user.getUserId(), user.getUserName(), user.getRoleLevel(), user.getTenantId()));
        response.setUserId(user.getUserId());
        response.setUserName(user.getUserName());
        response.setNickName(user.getNickName());
        response.setRoleLevel(user.getRoleLevel());
        response.setTenantId(user.getTenantId());
        return response;
    }

    private UserInfoResponse toInfo(EmisUser user) {
        UserInfoResponse info = new UserInfoResponse();
        info.setUserId(user.getUserId());
        info.setTenantId(user.getTenantId());
        info.setRoleLevel(user.getRoleLevel());
        info.setUserName(user.getUserName());
        info.setNickName(user.getNickName());
        info.setEmail(user.getEmail());
        info.setPhonenumber(user.getPhonenumber());
        info.setStatus(user.getStatus());
        info.setLoginIp(user.getLoginIp());
        info.setLoginDate(user.getLoginDate());
        info.setCreatedAt(user.getCreatedAt());
        info.setRemark(user.getRemark());
        return info;
    }
}
