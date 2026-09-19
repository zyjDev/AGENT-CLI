package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 数据统计响应 DTO
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * @description 数据统计响应数据传输对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataStatisticsResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 活跃代理数量
     */
    private Long activeAgentCount;

    /**
     * 客户端数量
     */
    private Long clientCount;

    /**
     * MCP工具数量
     */
    private Long mcpToolCount;

    /**
     * 系统提示词数量
     */
    private Long systemPromptCount;

    /**
     * 知识库数量
     */
    private Long ragOrderCount;

    /**
     * 顾问配置数量
     */
    private Long advisorCount;

    /**
     * 模型配置数量
     */
    private Long modelCount;

    /**
     * 今日请求数量。
     * <p>
     * 口径：当日进入 Agent 执行链路的次数（IAgentDispatchService#dispatch 调用数），
     * 含 HTTP SSE 入口与定时任务两条来源。来源为内存计数器，服务重启后当日归零。
     * 详见 {@code cn.bugstack.ai.domain.agent.service.metrics.AgentRequestMetrics}。
     */
    private Long todayRequestCount;

    /**
     * 成功率（百分比，保留 1 位小数）。
     * <p>
     * 口径：整条执行链路未抛异常的占比。注意这是「调用是否跑完」而非「业务是否成功」。
     */
    private Double successRate;

    /**
     * 运行中任务数量。
     * <p>
     * 口径：ai_agent_task_schedule 中 status = 1 的已启用调度数（该表是 cron 配置表，
     * 项目无任务运行态表）。
     */
    private Long runningTaskCount;
}