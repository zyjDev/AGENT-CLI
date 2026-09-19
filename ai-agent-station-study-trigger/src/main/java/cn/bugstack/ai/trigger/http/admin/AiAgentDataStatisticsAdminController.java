package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiAgentDataStatisticsAdminService;
import cn.bugstack.ai.api.dto.DataStatisticsResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.service.metrics.AgentRequestMetrics;
import cn.bugstack.ai.infrastructure.dao.*;
import cn.bugstack.ai.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 数据统计
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/10/4 10:33
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/data/statistics")
// 跨域统一收敛到 WebCorsConfig 的白名单（原先此处是 origins = "*"，等于对任意站点放开）
public class AiAgentDataStatisticsAdminController implements IAiAgentDataStatisticsAdminService {

    @Resource
    private IAiAgentDao aiAgentDao;
    @Resource
    private IAiAgentTaskScheduleDao aiAgentTaskScheduleDao;
    @Resource
    private IAiClientAdvisorDao aiClientAdvisorDao;
    @Resource
    private IAiClientDao aiClientDao;
    @Resource
    private IAiClientModelDao aiClientModelDao;
    @Resource
    private IAiClientRagOrderDao aiClientRagOrderDao;
    @Resource
    private IAiClientSystemPromptDao aiClientSystemPromptDao;
    @Resource
    private IAiClientToolMcpDao aiClientToolMcpDao;

    /**
     * 当日 Agent 执行指标（内存态），口径见 {@link AgentRequestMetrics} 类注释。
     */
    @Resource
    private AgentRequestMetrics agentRequestMetrics;

    @Override
    @GetMapping("/get-data-statistics")
    public Response<DataStatisticsResponseDTO> getDataStatistics() {
        log.info("开始获取系统数据统计");

        // 各类配置数量：一律走 selectCount，不再用 queryAll().size()
        // —— 后者会把整表数据（含大字段）拉进 JVM 只为取一个长度，数据量上来必然拖慢首页。
        long agentCount = aiAgentDao.selectCount(null);
        long clientCount = aiClientDao.selectCount(null);
        long mcpToolCount = aiClientToolMcpDao.selectCount(null);
        long systemPromptCount = aiClientSystemPromptDao.selectCount(null);
        long ragOrderCount = aiClientRagOrderDao.selectCount(null);
        long advisorCount = aiClientAdvisorDao.selectCount(null);
        long modelCount = aiClientModelDao.selectCount(null);

        // 运行中任务 = 已启用的调度任务数（ai_agent_task_schedule.status = 1）。
        // 该表是 cron 配置表，项目里没有任务运行态表，故这是能做到的最接近口径。
        long runningTaskCount = aiAgentTaskScheduleDao.countEnabledTasks();

        // 今日请求数 / 成功率：来自内存指标组件。
        // 原先是写死的 0 和 95.5（假数据），而项目自有表里没有任何请求日志表，无法从库里统计。
        long todayRequestCount = agentRequestMetrics.getTodayRequestCount();
        double successRate = agentRequestMetrics.getSuccessRate();

        // 构建响应数据
        DataStatisticsResponseDTO responseDTO = DataStatisticsResponseDTO.builder()
                .activeAgentCount(agentCount)
                .clientCount(clientCount)
                .mcpToolCount(mcpToolCount)
                .systemPromptCount(systemPromptCount)
                .ragOrderCount(ragOrderCount)
                .advisorCount(advisorCount)
                .modelCount(modelCount)
                .todayRequestCount(todayRequestCount)
                .successRate(successRate)
                .runningTaskCount(runningTaskCount)
                .build();

        log.info("系统数据统计获取成功：智能体数量={}, 客户端数量={}, MCP工具数量={}, 系统提示数量={}, 知识库数量={}, 顾问数量={}, 模型数量={}, 今日请求={}, 成功率={}%, 运行中任务={}",
                agentCount, clientCount, mcpToolCount, systemPromptCount, ragOrderCount, advisorCount, modelCount,
                todayRequestCount, successRate, runningTaskCount);

        return Response.<DataStatisticsResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(responseDTO)
                .build();
    }

}
