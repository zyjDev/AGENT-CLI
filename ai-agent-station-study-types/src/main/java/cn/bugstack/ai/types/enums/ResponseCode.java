package cn.bugstack.ai.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    LOGIN_FAILED("0003", "登录失败"),
    /**
     * 普通用户自建智能体却没有自己的模型 Key（借道了平台默认/别人的模型或客户端）。
     * 单独给一个码，前端才能据此弹「去配置」按钮，而不是只丢一句报错。
     */
    NEED_OWN_MODEL_KEY("0004", "需要先配置你自己的模型 Key"),
    ;

    private String code;
    private String info;

}
