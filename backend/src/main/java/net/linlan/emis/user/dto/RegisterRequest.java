package net.linlan.emis.user.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String userName;
    private String password;
    private String nickName;
    private String email;
    private String phonenumber;
    /** 可选，缺省落入默认租户 1 */
    private Long tenantId;
}
