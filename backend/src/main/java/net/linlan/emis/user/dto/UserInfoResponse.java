package net.linlan.emis.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserInfoResponse {
    private Long userId;
    private Long tenantId;
    private Integer roleLevel;
    private String userName;
    private String nickName;
    private String email;
    private String phonenumber;
    private String status;
    private String loginIp;
    private LocalDateTime loginDate;
    private LocalDateTime createdAt;
    private String remark;
}
