package cn.bugstack.ai.trigger.job;

import cn.bugstack.ai.domain.agent.model.entity.ExecuteCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.AiAgentTaskScheduleVO;
import cn.bugstack.ai.domain.agent.service.IAgentDispatchService;
import cn.bugstack.ai.domain.agent.service.ITaskService;
import cn.bugstack.ai.trigger.support.DiscardingResponseBodyEmitter;
import cn.bugstack.ai.types.job.model.TaskScheduleVO;
import cn.bugstack.ai.types.job.provider.ITaskDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能体任务
 *
 */
@Slf4j
@Service
public class AgentTaskJob implements ITaskDataProvider {

    @Resource
    private ITaskService taskService;

    @Resource
    private IAgentDispatchService dispatchService;

    @Override
    public List<TaskScheduleVO> queryAllValidTaskSchedule() {
        List<AiAgentTaskScheduleVO> aiAgentTaskScheduleVOS = taskService.queryAllValidTaskSchedule();
        List<TaskScheduleVO> result = new ArrayList<>();
        for (AiAgentTaskScheduleVO aiAgentTaskScheduleVO : aiAgentTaskScheduleVOS) {
            TaskScheduleVO taskScheduleVO = new TaskScheduleVO();
            taskScheduleVO.setId(aiAgentTaskScheduleVO.getId());
            taskScheduleVO.setDescription(aiAgentTaskScheduleVO.getDescription());
            taskScheduleVO.setCronExpression(aiAgentTaskScheduleVO.getCronExpression());
            taskScheduleVO.setTaskParam(aiAgentTaskScheduleVO.getTaskParam());
            taskScheduleVO.setTaskLogic(() -> executeTask(aiAgentTaskScheduleVO));

            result.add(taskScheduleVO);
        }
        return result;
    }

    /**
     * 执行一次定时任务。
     *
     * <p><b>关于可观测性</b>：{@code dispatch} 是「提交即返回」的异步语义（内部再交给线程池执行），
     * 所以成功日志只能记到「已受理」为止；真正的执行结果 / 失败原因由传入的
     * {@link DiscardingResponseBodyEmitter} 兜底记录 —— 定时任务本就没有 HTTP 观察者，
     * 这是它唯一的可观测出口。
     *
     * @param scheduleVO 任务调度配置
     */
    private void executeTask(AiAgentTaskScheduleVO scheduleVO) {
        String sessionId = String.valueOf(System.nanoTime());
        try {
            dispatchService.dispatch(
                    ExecuteCommandEntity.builder()
                            .aiAgentId(scheduleVO.getAgentId())
                            .sessionId(sessionId)
                            .maxStep(1)
                            .message(scheduleVO.getTaskParam())
                            .build(),
                    // 不要用 new ResponseBodyEmitter()：它未被 Spring MVC 初始化，send(...) 会把整个
                    // 任务的 SSE 事件无上限缓冲在堆内存里、任务结束后整体丢弃（详见 DiscardingResponseBodyEmitter 类注释）
                    new DiscardingResponseBodyEmitter("task-" + scheduleVO.getId()));
            log.info("定时任务已受理：scheduleId={}, agentId={}, sessionId={}",
                    scheduleVO.getId(), scheduleVO.getAgentId(), sessionId);
        } catch (Exception e) {
            log.error("定时任务提交失败：scheduleId={}, agentId={}, sessionId={}",
                    scheduleVO.getId(), scheduleVO.getAgentId(), sessionId, e);
        }
    }

    @Override
    public List<Long> queryAllInvalidTaskScheduleIds() {
        return taskService.queryAllInvalidTaskScheduleIds();
    }

}
