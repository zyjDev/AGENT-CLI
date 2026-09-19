package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.types.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 * @author bugstack.cn
 * @description 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     * <p>
     * 必须排在 {@link #handleRuntimeException} 之前被匹配（Spring 按异常类型精确度择优），
     * 否则 BizException 会被当成普通 RuntimeException 走 {@code Response.error(message)}，
     * 其 code 被固定值覆盖 —— 调用方拿不到「非法参数 / 未实现」这类可区分的错误码。
     * <p>
     * 业务异常属于「可预期的调用方错误」，只记 WARN 且不打堆栈，避免污染错误日志。
     */
    @ExceptionHandler(BizException.class)
    public Response<?> handleBizException(BizException e) {
        String info = StringUtils.hasText(e.getInfo()) ? e.getInfo() : e.getMessage();
        log.warn("业务异常: code={}, info={}", e.getCode(), info);
        return Response.error(e.getCode(), info);
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Response<?> handleRuntimeException(RuntimeException e) {
        log.error("业务异常: {}", e.getMessage(), e);
        return Response.error(e.getMessage());
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Response<?> handleValidationException(MethodArgumentNotValidException e) {
        log.error("参数验证异常: {}", e.getMessage(), e);
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("参数验证失败");
        return Response.error(message);
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Response<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("文件上传大小超限: {}", e.getMessage(), e);
        return Response.error("上传文件大小超过限制");
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public Response<?> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return Response.error("系统异常，请稍后重试");
    }

}
