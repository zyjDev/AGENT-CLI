package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AiClientAdvisor;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 顾问配置表 DAO
 * @author bugstack虫洞栈
 * @description 顾问配置表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAiClientAdvisorDao extends BaseMapper<AiClientAdvisor> {

    default int updateById(AiClientAdvisor aiClientAdvisor) {
        UpdateWrapper<AiClientAdvisor> uw = new UpdateWrapper<>();
        uw.eq("id", aiClientAdvisor.getId());
        uw.set("advisor_id", aiClientAdvisor.getAdvisorId());
        uw.set("advisor_name", aiClientAdvisor.getAdvisorName());
        uw.set("advisor_type", aiClientAdvisor.getAdvisorType());
        uw.set("order_num", aiClientAdvisor.getOrderNum());
        uw.set("ext_param", aiClientAdvisor.getExtParam());
        uw.set("status", aiClientAdvisor.getStatus());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
    }

    default int updateByAdvisorId(AiClientAdvisor aiClientAdvisor) {
        UpdateWrapper<AiClientAdvisor> uw = new UpdateWrapper<>();
        uw.eq("advisor_id", aiClientAdvisor.getAdvisorId());
        uw.set("advisor_name", aiClientAdvisor.getAdvisorName());
        uw.set("advisor_type", aiClientAdvisor.getAdvisorType());
        uw.set("order_num", aiClientAdvisor.getOrderNum());
        uw.set("ext_param", aiClientAdvisor.getExtParam());
        uw.set("status", aiClientAdvisor.getStatus());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
    }

    default int deleteByAdvisorId(String advisorId) {
        return delete(new QueryWrapper<AiClientAdvisor>().eq("advisor_id", advisorId));
    }

    default AiClientAdvisor queryById(Long id) {
        return selectById(id);
    }

    default AiClientAdvisor queryByAdvisorId(String advisorId) {
        return selectOne(new QueryWrapper<AiClientAdvisor>().eq("advisor_id", advisorId));
    }

    default List<AiClientAdvisor> queryAll() {
        return selectList(new QueryWrapper<AiClientAdvisor>().orderByAsc("order_num").orderByDesc("create_time"));
    }

    default List<AiClientAdvisor> queryByStatus(Integer status) {
        return selectList(new QueryWrapper<AiClientAdvisor>().eq("status", status).orderByAsc("order_num").orderByDesc("create_time"));
    }

    default List<AiClientAdvisor> queryByAdvisorType(String advisorType) {
        return selectList(new QueryWrapper<AiClientAdvisor>().eq("advisor_type", advisorType).orderByAsc("order_num").orderByDesc("create_time"));
    }

}
