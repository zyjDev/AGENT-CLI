package cn.bugstack.ai.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源归属值对象 —— 「这个客户端 / 模型 / API 通道是谁的」。
 *
 * <p>只用于归属类校验（如「自建智能体必须使用自己的模型 Key」），
 * 因此只带判定所需的最小字段：id、名称（提示语要用）、ownerId。
 * 不携带 base_url / api_key 等敏感或无关字段。
 *
 * @author bugstack虫洞栈
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiResourceOwnerVO {

    /**
     * 资源 id（clientId / modelId / apiId）
     */
    private String resourceId;

    /**
     * 资源名称（用于给用户看的提示语；可能为空，为空时提示语回落用 resourceId）
     */
    private String resourceName;

    /**
     * 归属用户；空/null = 平台默认资源（人人可用）
     */
    private String ownerId;

}
