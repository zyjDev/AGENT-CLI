package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiAgentDataStatisticsAdminService;
import cn.bugstack.ai.api.dto.DataStatisticsResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.service.metrics.AgentRequestMetrics;
import cn.bugstack.ai.infrastructure.dao.*;
import cn.bugstack.ai.infrastructure.dao.po.AiAgent;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
import cn.bugstack.ai.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 数据统计
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
        //
        // 口径 = 「我能用的」：系统默认（owner_id 为空，如 6 个基础智能体与基础客户端）+ 本人私有。
        // 为什么不用 visibleWrapper（列表口径）：系统默认资源在管理端列表里对普通用户「不可见」，
        // 但对话页能选到、装配时也会用；统计若按列表口径，普通用户看到的永远是 0（实测确认），
        // 看起来像功能坏了。别人的私有资源依然不计入（usableWrapper 不含别人）。
        // 「活跃智能体」额外加了 status = 1：卡片写的是"活跃"，就得只数启用的。
        long agentCount = aiAgentDao.selectCount(OwnerQuerySupport.<AiAgent>usableWrapper().eq("status", 1));
        long clientCount = aiClientDao.selectCount(OwnerQuerySupport.usableWrapper());
        long mcpToolCount = aiClientToolMcpDao.selectCount(OwnerQuerySupport.usableWrapper());
        long systemPromptCount = aiClientSystemPromptDao.selectCount(OwnerQuerySupport.usableWrapper());
        long ragOrderCount = aiClientRagOrderDao.selectCount(OwnerQuerySupport.usableWrapper());
        long advisorCount = aiClientAdvisorDao.selectCount(OwnerQuerySupport.usableWrapper());
        long modelCount = aiClientModelDao.selectCount(OwnerQuerySupport.usableWrapper());

        // 运行中任务 = 已启用的调度任务数（ai_agent_task_schedule.status = 1）
        long runningTaskCount = countRunningTasks();

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

    /**
     * 运行中任务数 = 「我能用的智能体」下已启用（status = 1）的调度任务数。
     *
     * <p>ai_agent_task_schedule <b>没有 owner_id 列</b>（这张 cron 配置表不由本项目写入 ——
     * 代码里只有按 agentId 删除、没有 insert），所以任务归属只能顺着 agent_id 找它的智能体：
     * 我能用的智能体（系统默认 + 本人私有）名下的任务才算我的，否则普通用户会看到别人的任务数。
     *
     * <p>刻意不用 join：项目 DAO 全程只用 Wrapper（无 XML），这里分两步查 ——
     * 先取「我能用」的 agentId，再按 agentId 计数。
     */
    private long countRunningTasks() {
        // agentId 在 PO 里是 String（底层列是 bigint，MyBatis-Plus 直接映射成字符串），
        // 所以这里必须是 List<String>，别再写成 List<Long> —— 类型不匹配只会得到一个
        // `Unresolved compilation problem` 的运行时错误，排查起来很费劲。
        List<String> usableAgentIds = aiAgentDao
                .selectList(OwnerQuerySupport.<AiAgent>usableWrapper().select("agent_id"))
                .stream()
                .map(AiAgent::getAgentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        // ⚠️ 空集合不能直接丢给 in()：MyBatis-Plus 会忽略空条件，那就退化成「全表计数」了
        if (usableAgentIds.isEmpty()) {
            return 0;
        }
        return aiAgentTaskScheduleDao.countEnabledTasks(usableAgentIds);
    }

}
