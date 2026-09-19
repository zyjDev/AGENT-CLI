package cn.bugstack.ai.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> implements Serializable {

    private static final long serialVersionUID = 7000723935764546321L;

    /**
     * 成功码，与 {@code cn.bugstack.ai.types.enums.ResponseCode#SUCCESS} 一致。
     * <p>
     * api 模块不依赖 types 模块，故此处只能写字面量；修改 ResponseCode 时务必同步这里。
     * 前端（ai-agent-station-front）所有 service 都按 {@code result.code === '0000'} 判成功，
     * 这里曾经写成 "200"，导致所有走 {@link #success(Object)} 的接口被前端当成失败。
     */
    public static final String SUCCESS_CODE = "0000";

    /**
     * 未知失败码，与 {@code cn.bugstack.ai.types.enums.ResponseCode#UN_ERROR} 一致。
     */
    public static final String UN_ERROR_CODE = "0001";

    private String code;
    private String info;
    private String message;
    private T data;

    public static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(SUCCESS_CODE)
                .info("success")
                .message("success")
                .data(data)
                .build();
    }

    /**
     * 构造错误响应（code 固定为 {@link #UN_ERROR_CODE}）。
     */
    public static <T> Response<T> error(String message) {
        return error(UN_ERROR_CODE, message);
    }

    /**
     * 构造带业务错误码的错误响应。
     * <p>
     * 用于把 {@code BizException} 的 code 透传给调用方，而不是被统一压成 "0001"。
     *
     * @param code    业务错误码，见 {@code cn.bugstack.ai.types.enums.ResponseCode}
     * @param message 错误描述
     */
    public static <T> Response<T> error(String code, String message) {
        return Response.<T>builder()
                .code(code)
                .info(message)
                .message(message)
                .build();
    }

}