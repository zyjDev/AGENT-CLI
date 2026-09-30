package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientAdvisor;
import cn.bugstack.ai.infrastructure.dao.support.OwnerQuerySupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 顾问配置表 DAO
 * @description 顾问配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiClientAdvisorDao extends BaseMapper<AiClientAdvisor> {

    /**
     * 根据顾问ID更新顾问配置
     * <p>
     * 实体驱动更新：只有非 null 字段进入 SET 子句，未传字段保持库中原值；
     * update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发。
     */
    default int updateByAdvisorId(AiClientAdvisor aiClientAdvisor) {
        return update(aiClientAdvisor, new UpdateWrapper<AiClientAdvisor>().eq("advisor_id", aiClientAdvisor.getAdvisorId()));
    }

    default int deleteByAdvisorId(String advisorId) {
        return delete(new QueryWrapper<AiClientAdvisor>().eq("advisor_id", advisorId));
    }

    default AiClientAdvisor queryById(Long id) {
        AiClientAdvisor po = selectById(id);
        if (po != null && !OwnerQuerySupport.visibleToCurrentUser(po.getOwnerId())) {
            return null;
        }
        return po;
    }

    default AiClientAdvisor queryByAdvisorId(String advisorId) {
        return selectOne(new QueryWrapper<AiClientAdvisor>().eq("advisor_id", advisorId));
    }

    default List<AiClientAdvisor> queryAll() {
        return selectList(OwnerQuerySupport.<AiClientAdvisor>visibleWrapper().orderByAsc("order_num").orderByDesc("create_time"));
    }

    /**
     * 管理端列表分页查询：条件与归属过滤一起下推 SQL，total 由 count 得出。
     * 归属口径与排序均同 {@link #queryAll()}（visible；order_num 升序 + create_time 降序）。
     */
    default IPage<AiClientAdvisor> queryPage(IPage<AiClientAdvisor> page, String advisorId, String advisorName, String advisorType, Integer status) {
        LambdaQueryWrapper<AiClientAdvisor> wrapper = OwnerQuerySupport.visibleLambdaWrapper(AiClientAdvisor::getOwnerId);
        if (advisorId != null && !advisorId.isBlank()) {
            wrapper.eq(AiClientAdvisor::getAdvisorId, advisorId);
        }
        if (advisorName != null && !advisorName.isBlank()) {
            wrapper.like(AiClientAdvisor::getAdvisorName, advisorName);
        }
        if (advisorType != null && !advisorType.isBlank()) {
            wrapper.eq(AiClientAdvisor::getAdvisorType, advisorType);
        }
        if (status != null) {
            wrapper.eq(AiClientAdvisor::getStatus, status);
        }
        return selectPage(page, wrapper.orderByAsc(AiClientAdvisor::getOrderNum).orderByDesc(AiClientAdvisor::getCreateTime));
    }

    default List<AiClientAdvisor> queryByStatus(Integer status) {
        return selectList(OwnerQuerySupport.<AiClientAdvisor>visibleWrapper().eq("status", status).orderByAsc("order_num").orderByDesc("create_time"));
    }

    default List<AiClientAdvisor> queryByAdvisorType(String advisorType) {
        return selectList(OwnerQuerySupport.<AiClientAdvisor>visibleWrapper().eq("advisor_type", advisorType).orderByAsc("order_num").orderByDesc("create_time"));
    }

}
