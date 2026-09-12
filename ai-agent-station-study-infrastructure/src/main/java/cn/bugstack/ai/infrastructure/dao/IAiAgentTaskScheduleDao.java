package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentTaskSchedule;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能体任务调度配置表 DAO
 * @author bugstack虫洞栈
 * @description 智能体任务调度配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 * <p>
 * 注意：本接口不要声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发，
 * 导致内置「只更新非 null 字段」语义失效并把未传字段写成 NULL。按 id 更新请直接使用 BaseMapper.updateById。
 */
@Mapper
public interface IAiAgentTaskScheduleDao extends BaseMapper<AiAgentTaskSchedule> {

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
