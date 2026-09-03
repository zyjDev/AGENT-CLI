package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentTaskSchedule;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能体任务调度配置表 DAO
 * @author bugstack虫洞栈
 * @description 智能体任务调度配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiAgentTaskScheduleDao extends BaseMapper<AiAgentTaskSchedule> {

    default int updateById(AiAgentTaskSchedule aiAgentTaskSchedule) {
        UpdateWrapper<AiAgentTaskSchedule> uw = new UpdateWrapper<>();
        uw.eq("id", aiAgentTaskSchedule.getId());
        uw.set("agent_id", aiAgentTaskSchedule.getAgentId());
        uw.set("task_name", aiAgentTaskSchedule.getTaskName());
        uw.set("description", aiAgentTaskSchedule.getDescription());
        uw.set("cron_expression", aiAgentTaskSchedule.getCronExpression());
        uw.set("task_param", aiAgentTaskSchedule.getTaskParam());
        uw.set("status", aiAgentTaskSchedule.getStatus());
        uw.set("update_time", aiAgentTaskSchedule.getUpdateTime());
        return update(null, uw);
    }

    default int deleteByAgentId(Long agentId) {
        return delete(new QueryWrapper<AiAgentTaskSchedule>().eq("agent_id", agentId));
    }

    default AiAgentTaskSchedule queryById(Long id) {
        return selectById(id);
    }

    default List<AiAgentTaskSchedule> queryByAgentId(Long agentId) {
        return selectList(new QueryWrapper<AiAgentTaskSchedule>().eq("agent_id", agentId).orderByDesc("create_time"));
    }

    default List<AiAgentTaskSchedule> queryEnabledTasks() {
        return selectList(new QueryWrapper<AiAgentTaskSchedule>().eq("status", 1).orderByDesc("create_time"));
    }

    default AiAgentTaskSchedule queryByTaskName(String taskName) {
        return selectOne(new QueryWrapper<AiAgentTaskSchedule>().eq("task_name", taskName));
    }

    default List<AiAgentTaskSchedule> queryAll() {
        return selectList(new QueryWrapper<AiAgentTaskSchedule>().orderByDesc("create_time"));
    }

    default List<AiAgentTaskSchedule> queryAllValidTaskSchedule() {
        return selectList(new QueryWrapper<AiAgentTaskSchedule>().eq("status", 1).orderByDesc("create_time"));
    }

    default List<Long> queryAllInvalidTaskScheduleIds() {
        return selectList(new QueryWrapper<AiAgentTaskSchedule>()
                .select("id")
                .eq("status", 0)
                .orderByDesc("create_time"))
                .stream().map(AiAgentTaskSchedule::getId).collect(Collectors.toList());
    }

}
