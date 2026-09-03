package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientSystemPrompt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
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
     * 根据ID更新系统提示词配置（等价原 XML updateById 的动态 set）
     */
    default int updateById(AiClientSystemPrompt aiClientSystemPrompt) {
        LambdaUpdateWrapper<AiClientSystemPrompt> uw = new LambdaUpdateWrapper<>();
        uw.eq(AiClientSystemPrompt::getId, aiClientSystemPrompt.getId());
        if (aiClientSystemPrompt.getPromptId() != null && !aiClientSystemPrompt.getPromptId().isEmpty()) {
            uw.set(AiClientSystemPrompt::getPromptId, aiClientSystemPrompt.getPromptId());
        }
        if (aiClientSystemPrompt.getPromptName() != null && !aiClientSystemPrompt.getPromptName().isEmpty()) {
            uw.set(AiClientSystemPrompt::getPromptName, aiClientSystemPrompt.getPromptName());
        }
        if (aiClientSystemPrompt.getPromptContent() != null && !aiClientSystemPrompt.getPromptContent().isEmpty()) {
            uw.set(AiClientSystemPrompt::getPromptContent, aiClientSystemPrompt.getPromptContent());
        }
        if (aiClientSystemPrompt.getDescription() != null) {
            uw.set(AiClientSystemPrompt::getDescription, aiClientSystemPrompt.getDescription());
        }
        if (aiClientSystemPrompt.getStatus() != null) {
            uw.set(AiClientSystemPrompt::getStatus, aiClientSystemPrompt.getStatus());
        }
        uw.set(AiClientSystemPrompt::getUpdateTime, LocalDateTime.now());
        return update(null, uw);
    }

    /**
     * 根据提示词ID更新系统提示词配置（等价原 XML updateByPromptId 的动态 set）
     */
    default int updateByPromptId(AiClientSystemPrompt aiClientSystemPrompt) {
        LambdaUpdateWrapper<AiClientSystemPrompt> uw = new LambdaUpdateWrapper<>();
        uw.eq(AiClientSystemPrompt::getPromptId, aiClientSystemPrompt.getPromptId());
        if (aiClientSystemPrompt.getPromptName() != null && !aiClientSystemPrompt.getPromptName().isEmpty()) {
            uw.set(AiClientSystemPrompt::getPromptName, aiClientSystemPrompt.getPromptName());
        }
        if (aiClientSystemPrompt.getPromptContent() != null && !aiClientSystemPrompt.getPromptContent().isEmpty()) {
            uw.set(AiClientSystemPrompt::getPromptContent, aiClientSystemPrompt.getPromptContent());
        }
        if (aiClientSystemPrompt.getDescription() != null) {
            uw.set(AiClientSystemPrompt::getDescription, aiClientSystemPrompt.getDescription());
        }
        if (aiClientSystemPrompt.getStatus() != null) {
            uw.set(AiClientSystemPrompt::getStatus, aiClientSystemPrompt.getStatus());
        }
        uw.set(AiClientSystemPrompt::getUpdateTime, LocalDateTime.now());
        return update(null, uw);
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
