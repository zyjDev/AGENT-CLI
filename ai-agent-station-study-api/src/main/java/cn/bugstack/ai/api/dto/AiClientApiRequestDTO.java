package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI客户端API配置请求 DTO
 * @description AI客户端API配置请求数据传输对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientApiRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（更新时使用）
     */
    private Long id;

    /**
     * API ID
     */
    private String apiId;

    /**
     * 基础URL
     */
    private String baseUrl;

    /**
     * API密钥
     * <p>
     * ⚠️ 不参与 {@code toString()}：创建 / 更新接口会把整个请求对象打进日志
     * （{@code log.info("...请求：{}", request)}），带上就会把明文密钥写进日志文件与日志平台。
     */
    @ToString.Exclude
    private String apiKey;

    /**
     * 对话补全路径
     */
    private String completionsPath;

    /**
     * 嵌入向量路径
     */
    private String embeddingsPath;

    /**
     * 状态(0:禁用,1:启用)
     */
    private Integer status;

}
