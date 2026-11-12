package net.linlan.emis.user.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String userName;
    private String nickName;
    private Integer roleLevel;
    private Long tenantId;
}
