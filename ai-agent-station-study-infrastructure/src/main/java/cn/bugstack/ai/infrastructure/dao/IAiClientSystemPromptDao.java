package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientSystemPrompt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统提示词配置表 DAO
 * @description 系统提示词配置表数据访问对象（MyBatis-Plus BaseMapper + 自定义方法用 Wrapper 实现，XML 已移除）
 */
@Mapper
public interface IAiClientSystemPromptDao extends BaseMapper<AiClientSystemPrompt> {

    /**
     * 根据ID查询系统提示词配置（复用 BaseMapper.selectById）
     */
    default AiClientSystemPrompt queryById(Long id) {
        return selectById(id);
    }

    /**
     * 根据提示词ID查询系统提示词配置
     */
    default AiClientSystemPrompt queryByPromptId(String promptId) {
        return selectOne(new LambdaQueryWrapper<AiClientSystemPrompt>()
                .eq(AiClientSystemPrompt::getPromptId, promptId));
    }

    /**
     * 根据提示词ID更新系统提示词配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发，
     * 导致内置「只更新非 null 字段」语义失效并把未传字段写成 NULL。按 id 更新请直接使用 BaseMapper.updateById。
     */
    default int updateByPromptId(AiClientSystemPrompt aiClientSystemPrompt) {
        return update(aiClientSystemPrompt, new LambdaUpdateWrapper<AiClientSystemPrompt>()
                .eq(AiClientSystemPrompt::getPromptId, aiClientSystemPrompt.getPromptId()));
    }

    /**
     * 根据提示词ID删除系统提示词配置
     */
    default int deleteByPromptId(String promptId) {
        return delete(new LambdaQueryWrapper<AiClientSystemPrompt>()
                .eq(AiClientSystemPrompt::getPromptId, promptId));
    }

    /**
     * 查询启用的系统提示词配置
     */
    default List<AiClientSystemPrompt> queryEnabledPrompts() {
        return selectList(new LambdaQueryWrapper<AiClientSystemPrompt>()
                .eq(AiClientSystemPrompt::getStatus, 1)
                .orderByDesc(AiClientSystemPrompt::getCreateTime));
    }

    /**
     * 根据提示词名称模糊查询系统提示词配置
     */
    default List<AiClientSystemPrompt> queryByPromptName(String promptName) {
        return selectList(new LambdaQueryWrapper<AiClientSystemPrompt>()
                .like(AiClientSystemPrompt::getPromptName, promptName)
                .orderByDesc(AiClientSystemPrompt::getCreateTime));
    }

    /**
     * 查询所有系统提示词配置
     */
    default List<AiClientSystemPrompt> queryAll() {
        return selectList(new LambdaQueryWrapper<AiClientSystemPrompt>()
                .orderByDesc(AiClientSystemPrompt::getCreateTime));
    }

}
