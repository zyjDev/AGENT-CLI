package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 「把自己的 Key 绑定到某个智能体」的请求。
 *
 * <p>绑定语义：用户 U 用 apiId 这条自己的密钥，给 agentId 这个智能体接一套属于他的链路
 * （model → client → client_config → 用户级 ai_agent_flow_config），运行时优先走他的链路。
 * 绑定关系就存在 ai_agent_flow_config（agent_id + client_id + owner_id = U），因此：
 * <ul>
 *   <li>同一智能体重复绑定 = 覆盖（先删他在这条智能体上的旧绑定再重建）；</li>
 *   <li>解绑 = 删掉他那几条绑定行（他的 model / client 行保留，可能被他别的智能体共用）。</li>
 * </ul>
 *
 * <p>刻意不带 userId：身份只认 JWT，否则普通用户能替别人绑定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientApiBindRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户自己的 API 通道 ID（base_url + api_key 所在行） */
    private String apiId;

    /** 要绑定的智能体 ID（查询已绑定时可不传） */
    private String agentId;

}
