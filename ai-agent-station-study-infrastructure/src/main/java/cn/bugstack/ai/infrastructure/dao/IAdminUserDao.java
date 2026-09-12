package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AdminUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 管理员用户表 DAO
 * @description 管理员用户表数据访问对象（MyBatis-Plus 迁移版，SQL 由 Wrapper 拼接，无 XML）
 */
@Mapper
public interface IAdminUserDao extends BaseMapper<AdminUser> {

    /**
     * 根据用户ID更新管理员用户
     * <p>
     * 使用实体驱动更新：只有实体中非 null 的字段才会进入 SET 子句（MyBatis-Plus 默认 NOT_NULL 策略），
     * 避免调用方未传的字段被写成 NULL；update_time 由 TimeMetaObjectHandler 自动填充。
     * <p>
     * 注意：不要在此接口中声明 default int updateById(...)，否则会覆盖 BaseMapper.updateById 的 SQL 派发。
     */
    default int updateByUserId(AdminUser adminUser) {
        return update(adminUser, new UpdateWrapper<AdminUser>().eq("user_id", adminUser.getUserId()));
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
