package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 「这条 API 密钥当前绑定给了哪些智能体」的展示项。
 *
 * <p>用于客户端 API 管理页：让用户看得见自己配的 Key 用在了哪儿，并能解绑。
 *
 * @author bugstack虫洞栈
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClientApiBoundAgentResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 智能体ID */
    private String agentId;

    /** 智能体名称（列表上直接展示，避免前端再查一次） */
    private String agentName;

    /** 该智能体是否属于平台默认（owner 为空）—— 默认智能体只能绑定不能改 */
    private Boolean platformDefault;

}
