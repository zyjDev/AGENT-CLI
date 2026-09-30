package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户自助注册请求 DTO
 * @description 登录页注册入口的请求体；两次密码由服务端再校验一次，不能只信前端
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserRegisterRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户名（登录账号，全局唯一）
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 确认密码
     */
    private String confirmPassword;

}
