package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AdminUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员用户表 DAO
 * @description 管理员用户表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAdminUserDao extends BaseMapper<AdminUser> {

    default int updateById(AdminUser adminUser) {
        UpdateWrapper<AdminUser> uw = new UpdateWrapper<>();
        uw.eq("id", adminUser.getId());
        uw.set("user_id", adminUser.getUserId());
        uw.set("username", adminUser.getUsername());
        uw.set("password", adminUser.getPassword());
        uw.set("status", adminUser.getStatus());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
    }

    default int updateByUserId(AdminUser adminUser) {
        UpdateWrapper<AdminUser> uw = new UpdateWrapper<>();
        uw.eq("user_id", adminUser.getUserId());
        uw.set("username", adminUser.getUsername());
        uw.set("password", adminUser.getPassword());
        uw.set("status", adminUser.getStatus());
        uw.set("update_time", LocalDateTime.now());
        return update(null, uw);
    }

    default int deleteByUserId(String userId) {
        return delete(new QueryWrapper<AdminUser>().eq("user_id", userId));
    }

    default AdminUser queryById(Long id) {
        return selectById(id);
    }

    default AdminUser queryByUserId(String userId) {
        return selectOne(new QueryWrapper<AdminUser>().eq("user_id", userId));
    }

    default AdminUser queryByUsername(String username) {
        return selectOne(new QueryWrapper<AdminUser>().eq("username", username));
    }

    default List<AdminUser> queryEnabledUsers() {
        return selectList(new QueryWrapper<AdminUser>().eq("status", 1).orderByDesc("create_time"));
    }

    default List<AdminUser> queryByStatus(Integer status) {
        return selectList(new QueryWrapper<AdminUser>().eq("status", status).orderByDesc("create_time"));
    }

    default List<AdminUser> queryAll() {
        return selectList(new QueryWrapper<AdminUser>().orderByDesc("create_time"));
    }

    default AdminUser queryByUsernameAndPassword(String username, String password) {
        return selectOne(new QueryWrapper<AdminUser>().eq("username", username).eq("password", password).eq("status", 1));
    }

}
