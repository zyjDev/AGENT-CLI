package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiAgentTaskSchedule;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能体任务调度配置表 DAO
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

    /**
     * 统计启用的调度任务数（status = 1），**全表口径**。
     * <p>
     * 用 selectCount 而不是 queryEnabledTasks().size() —— 后者会把整表数据拉进 JVM 只为取个长度。
     * <p>
     * 注意：这是不分用户的全局口径，只适合全局视角与校验程序
     * （docs/verify/DaoCountSqlVerify 用它和 queryEnabledTasks().size() 对账）；
     * 面向某个用户的统计请用 {@link #countEnabledTasks(List)}，否则会把别人的任务数算进来。
     */
    default long countEnabledTasks() {
        return selectCount(new QueryWrapper<AiAgentTaskSchedule>().eq("status", 1));
    }

    /**
     * 统计指定智能体下启用的调度任务数（status = 1）。
     * <p>
     * 为什么要传 agentIds：本表**没有 owner_id 列**（任务不是在本项目里创建的，只有 cron 配置），
     * 归属只能顺着 agent_id 找智能体。调用方按「我能用的智能体」过滤，避免跨用户统计。
     * <p>
     * 参数用 {@code List<String>}：{@code AiAgentTaskSchedule.agentId} 在 PO 里是 String
     * （底层列是 bigint，MyBatis-Plus 直接映射为字符串），其它 DAO 也有按 Long 传的写法，
     * 这里跟随 PO 的类型，避免调用方再转换一次。
     */
    default long countEnabledTasks(List<String> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return 0;
        }
        return selectCount(new QueryWrapper<AiAgentTaskSchedule>().eq("status", 1).in("agent_id", agentIds));
    }

    default List<Long> queryAllInvalidTaskScheduleIds() {
        return selectList(new QueryWrapper<AiAgentTaskSchedule>()
                .select("id")
                .eq("status", 0)
                .orderByDesc("create_time"))
                .stream().map(AiAgentTaskSchedule::getId).collect(Collectors.toList());
    }

}
