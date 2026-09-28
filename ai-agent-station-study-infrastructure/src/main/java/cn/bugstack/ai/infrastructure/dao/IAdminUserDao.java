package cn.bugstack.ai.infrastructure.dao;

import cn.bugstack.ai.infrastructure.dao.po.AdminUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

    /**
     * 管理端列表分页查询：条件下推 SQL，total 由 count 得出。
     *
     * <p><b>本表没有归属列</b>（admin_user 是账号表，不属于任何用户），因此刻意不加
     * OwnerQuerySupport 的 visible / usable 过滤 —— 与其它 8 个资源表不同，不是漏写。
     * 接口自身的访问控制由 AdminUserAdminController 的「仅管理员」白名单保证。
     */
    default IPage<AdminUser> queryPage(IPage<AdminUser> page, String userId, String username, Integer status) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        if (userId != null && !userId.isBlank()) {
            wrapper.eq(AdminUser::getUserId, userId);
        }
        if (username != null && !username.isBlank()) {
            wrapper.like(AdminUser::getUsername, username);
        }
        if (status != null) {
            wrapper.eq(AdminUser::getStatus, status);
        }
        return selectPage(page, wrapper.orderByDesc(AdminUser::getCreateTime));
    }


}
