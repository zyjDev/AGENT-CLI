package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * Agent 策略调度器接口
 */
public interface IAgentDispatchService {
    /**
     * 调度执行命令
     * @param requestParameter 执行命令实体
     * @param emitter 响应体发射器
     * @throws Exception 异常
     */
    void dispatch(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception;

}
