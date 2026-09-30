package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改密码请求 DTO。
 *
 * <p>刻意**不包含 userId**：改自己的密码身份只能来自 JWT（服务端 UserContext），
 * 一旦允许请求体里指定 userId，普通用户就能改别人的密码。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserChangePasswordRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 原密码 */
    private String oldPassword;

    /** 新密码 */
    private String newPassword;

    /** 确认新密码：前端已校验一次，服务端必须再校验一次 */
    private String confirmPassword;

}
