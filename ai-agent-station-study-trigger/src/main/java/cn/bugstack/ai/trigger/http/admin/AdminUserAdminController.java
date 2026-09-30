package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAdminUserAdminService;
import cn.bugstack.ai.api.dto.AdminUserLoginRequestDTO;
import cn.bugstack.ai.api.dto.AdminUserChangePasswordRequestDTO;
import cn.bugstack.ai.api.dto.AdminUserQueryRequestDTO;
import cn.bugstack.ai.api.dto.AdminUserRegisterRequestDTO;
import cn.bugstack.ai.api.dto.AdminUserRequestDTO;
import cn.bugstack.ai.api.dto.AdminUserResponseDTO;
import cn.bugstack.ai.api.response.PageResult;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.infrastructure.dao.IAdminUserDao;
import cn.bugstack.ai.infrastructure.dao.po.AdminUser;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.trigger.config.AdminJwtTokenService;
import cn.bugstack.ai.trigger.support.AdminPageSupport;
import cn.bugstack.ai.trigger.support.LoginAttemptGuard;
import cn.bugstack.ai.trigger.support.OwnerGuard;
import cn.bugstack.ai.types.context.UserContext;
import cn.bugstack.ai.types.common.PasswordUtil;
import cn.bugstack.ai.types.common.SnowflakeId;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 管理员用户管理控制器
 * @description 管理员用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/admin-user")
// 跨域统一收敛到 WebCorsConfig 的白名单（原先此处是 origins = "*"，等于对任意站点放开）
public class AdminUserAdminController implements IAdminUserAdminService {

    @Resource
    private IAdminUserDao adminUserDao;

    @Resource
    private AdminJwtTokenService adminJwtTokenService;

    /** 密码校验入口的限流与临时锁定（防爆破 / 防批量注册） */
    @Resource
    private LoginAttemptGuard loginAttemptGuard;

    @Override
    @PostMapping("/register")
    public Response<AdminUserResponseDTO> registerAdminUser(@RequestBody AdminUserRegisterRequestDTO request) {
        try {
            if (request == null || !StringUtils.hasText(request.getUsername())
                    || !StringUtils.hasText(request.getPassword())) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名和密码不能为空")
                        .data(null)
                        .build();
            }

            String username = request.getUsername().trim();
            if (username.length() < 3) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名至少 3 个字符")
                        .data(null)
                        .build();
            }
            if (request.getPassword().length() < 6) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("密码至少 6 位")
                        .data(null)
                        .build();
            }
            // 两次密码一致：前端已校验一次，服务端必须再校验一次（注册是匿名接口，不能只信前端）
            if (StringUtils.hasText(request.getConfirmPassword())
                    && !request.getPassword().equals(request.getConfirmPassword())) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("两次输入的密码不一致")
                        .data(null)
                        .build();
            }
            log.info("用户自助注册请求，username={}, ip={}", username, loginAttemptGuard.clientIp());

            // 注册是匿名接口：没有验证码、也没有邮箱验证，脚本可以无限开号（每号一行数据 + 一个 token）。
            // 这里按来源 IP 做滑动窗口限流，只计成功创建 —— 重试「用户名已存在」不吃配额，
            // 否则前端的用户名冲突提示会先把正常用户挡在门外。
            long registerWaitSeconds = loginAttemptGuard.registerBlockedSeconds();
            if (registerWaitSeconds > 0) {
                log.warn("注册过于频繁，已限流：ip={}, username={}", loginAttemptGuard.clientIp(), username);
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("注册过于频繁，请约 " + Math.max(1, (registerWaitSeconds + 59) / 60) + " 分钟后再试")
                        .data(null)
                        .build();
            }

            if (adminUserDao.queryByUsername(username) != null) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名已存在")
                        .data(null)
                        .build();
            }

            AdminUser adminUser = AdminUser.builder()
                    // userId 用雪花（纯数字字符串）：它是业务表 owner_id 的取值，格式统一便于排查
                    .userId(SnowflakeId.nextIdStr())
                    .username(username)
                    .password(PasswordUtil.encode(request.getPassword()))
                    .status(1)
                    // 自助注册一律是普通用户：管理员账号只能由管理员在管理端创建 / 或由 DBA 改库
                    .userRole(UserContext.ROLE_USER)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            if (adminUserDao.insert(adminUser) <= 0) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("注册失败，请稍后重试")
                        .data(null)
                        .build();
            }

            // 真正建号成功才吃配额
            loginAttemptGuard.recordRegister();

            // 注册即登录：直接签发 token，前端不必再走一次登录
            AdminUserResponseDTO responseDTO = convertToAdminUserResponseDTO(adminUser);
            responseDTO.setToken(adminJwtTokenService.createToken(
                    adminUser.getUserId(), adminUser.getUsername(), adminUser.getUserRole()));

            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("用户自助注册失败", e);
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/change-password")
    public Response<Boolean> changePassword(@RequestBody AdminUserChangePasswordRequestDTO request) {
        try {
            // 身份只认 JWT：请求体里没有 userId，从根上杜绝「改别人密码」
            String currentUserId = UserContext.userId();
            if (!StringUtils.hasText(currentUserId)) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("未登录或登录已过期")
                        .data(false)
                        .build();
            }
            if (request == null || !StringUtils.hasText(request.getOldPassword())
                    || !StringUtils.hasText(request.getNewPassword())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("原密码和新密码不能为空")
                        .data(false)
                        .build();
            }
            if (request.getNewPassword().length() < 6) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("新密码至少 6 位")
                        .data(false)
                        .build();
            }
            // 两次新密码一致：前端已校验，服务端必须再校验一次（不能只信前端）
            if (StringUtils.hasText(request.getConfirmPassword())
                    && !request.getNewPassword().equals(request.getConfirmPassword())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("两次输入的新密码不一致")
                        .data(false)
                        .build();
            }
            if (request.getNewPassword().equals(request.getOldPassword())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("新密码不能与原密码相同")
                        .data(false)
                        .build();
            }

            AdminUser adminUser = adminUserDao.queryByUserId(currentUserId);
            if (adminUser == null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("账号不存在")
                        .data(false)
                        .build();
            }

            // 原密码同样是「密码校验入口」：不接限流的话，
            // 「拿到 token（或偷到 token）后用本接口慢慢试原密码」就是一条不受限的旁路。
            // 按 userId 计数：与登录的账号维度计数是两套 key（见 LoginAttemptGuard 的 key 说明）。
            String attemptKey = LoginAttemptGuard.userKey(currentUserId);
            long lockedSeconds = loginAttemptGuard.lockedSecondsRemaining(attemptKey);
            if (lockedSeconds > 0) {
                log.warn("账号处于临时锁定中，拒绝修改密码：userId={}，剩余 {} 秒", currentUserId, lockedSeconds);
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(loginLockedMessage(lockedSeconds))
                        .data(false)
                        .build();
            }

            // 必须先验原密码：否则 token 一旦泄露就能直接改密码接管账号
            if (!passwordMatches(request.getOldPassword(), adminUser.getPassword())) {
                loginAttemptGuard.recordFailure(attemptKey);
                log.warn("修改密码失败：原密码不正确，userId={}", currentUserId);
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("原密码不正确")
                        .data(false)
                        .build();
            }
            loginAttemptGuard.reset(attemptKey);

            // 只更新密码与更新时间（MyBatis-Plus 默认忽略 null 字段），避免整行覆盖
            AdminUser update = new AdminUser();
            update.setId(adminUser.getId());
            update.setPassword(PasswordUtil.encode(request.getNewPassword()));
            update.setUpdateTime(LocalDateTime.now());
            if (adminUserDao.updateById(update) <= 0) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("修改失败，请稍后重试")
                        .data(false)
                        .build();
            }

            log.info("用户修改密码成功，userId={}", currentUserId);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("修改密码失败，userId={}", UserContext.userId(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PostMapping("/create")
    public Response<Boolean> createAdminUser(@RequestBody AdminUserRequestDTO request) {
        try {
            // ⚠️ 鉴权必须放在**第一行**（P1-9）：不能晚于任何参数校验与查库。
            //    原实现把它放在方法末尾，导致「用户名已存在」会先于「无权限」返回 ——
            //    任何登录用户都能据此枚举系统内用户名（两种响应可区分）；
            //    而且谁在它之前加一句写库，就立刻变成越权写入。
            //    注：AdminAuthInterceptor 已对 /api/v1/admin/admin-user/** 整段拦截（登录/注册/校验/改密除外），
            //        这里是第二道闸 —— 将来若调整拦截器路径白名单，各接口仍可自保。
            if (!UserContext.isAdmin()) {
                return OwnerGuard.deny("用户");
            }
            if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名和密码不能为空")
                        .data(false)
                        .build();
            }
            log.info("创建管理员用户请求，username={}", request.getUsername());

            if (adminUserDao.queryByUsername(request.getUsername()) != null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名已存在")
                        .data(false)
                        .build();
            }

            AdminUser adminUser = convertToAdminUser(request);
            adminUser.setPassword(PasswordUtil.encode(request.getPassword()));
            if (!StringUtils.hasText(adminUser.getUserId())) {
                adminUser.setUserId(SnowflakeId.nextIdStr());
            }
            if (adminUser.getStatus() == null) {
                adminUser.setStatus(1);
            }
            adminUser.setCreateTime(LocalDateTime.now());
            adminUser.setUpdateTime(LocalDateTime.now());

            // 账号管理是管理员专属能力（普通用户走 /register 自助开户）—— 鉴权已前置到方法第一行
            int result = adminUserDao.insert(adminUser);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("创建管理员用户失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-id")
    public Response<Boolean> updateAdminUserById(@RequestBody AdminUserRequestDTO request) {
        try {
            // 鉴权前置（P1-9）：见 createAdminUser 的说明
            if (!UserContext.isAdmin()) {
                return OwnerGuard.deny("用户");
            }
            log.info("根据ID更新管理员用户请求，id={}", request.getId());
            
            if (request.getId() == null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AdminUser adminUser = convertToAdminUser(request);
            applyPasswordIfProvided(adminUser, request.getPassword());
            adminUser.setUpdateTime(LocalDateTime.now());

            // 账号管理是管理员专属能力：普通用户不得改动任何账号（包括自己）的角色与状态
            // —— 鉴权已前置到方法第一行（P1-9）
            int result = adminUserDao.updateById(adminUser);
            if (result <= 0) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("更新失败，用户不存在")
                        .data(false)
                        .build();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("根据ID更新管理员用户失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-user-id")
    public Response<Boolean> updateAdminUserByUserId(@RequestBody AdminUserRequestDTO request) {
        try {
            // ⚠️ 补上本方法**原本完全缺失**的鉴权（P1-9 修复时一并发现）：
            //    同文件的 create / update-by-id / delete-by-id 三处都有校验（虽在末尾），
            //    唯独 update-by-user-id 与 delete-by-user-id 一处都没有 —— 当时仅靠
            //    AdminAuthInterceptor 的路径前缀拦着。拦截器一旦调整白名单，这两个入口可直接改/删任意账号。
            if (!UserContext.isAdmin()) {
                return OwnerGuard.deny("用户");
            }
            log.info("根据用户ID更新管理员用户请求，userId={}", request.getUserId());
            
            if (!StringUtils.hasText(request.getUserId())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AdminUser adminUser = convertToAdminUser(request);
            applyPasswordIfProvided(adminUser, request.getPassword());
            adminUser.setUpdateTime(LocalDateTime.now());

            int result = adminUserDao.updateByUserId(adminUser);
            if (result <= 0) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("更新失败，用户不存在")
                        .data(false)
                        .build();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("根据用户ID更新管理员用户失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAdminUserById(@PathVariable("id") Long id) {
        try {
            // 鉴权前置（P1-9）：见 createAdminUser 的说明
            if (!UserContext.isAdmin()) {
                return OwnerGuard.deny("用户");
            }
            log.info("根据ID删除管理员用户请求：{}", id);
            
            int result = adminUserDao.deleteById(id);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID删除管理员用户失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-user-id/{userId}")
    public Response<Boolean> deleteAdminUserByUserId(@PathVariable("userId") String userId) {
        try {
            // ⚠️ 补上本方法**原本完全缺失**的鉴权（P1-9 修复时一并发现），原因同 update-by-user-id
            if (!UserContext.isAdmin()) {
                return OwnerGuard.deny("用户");
            }
            log.info("根据用户ID删除管理员用户请求：{}", userId);
            
            int result = adminUserDao.deleteByUserId(userId);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据用户ID删除管理员用户失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-id/{id}")
    public Response<AdminUserResponseDTO> queryAdminUserById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID查询管理员用户请求：{}", id);
            
            AdminUser adminUser = adminUserDao.queryById(id);
            if (adminUser == null) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(null)
                        .build();
            }
            
            AdminUserResponseDTO responseDTO = convertToAdminUserResponseDTO(adminUser);
            
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据ID查询管理员用户失败", e);
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-user-id/{userId}")
    public Response<AdminUserResponseDTO> queryAdminUserByUserId(@PathVariable("userId") String userId) {
        try {
            log.info("根据用户ID查询管理员用户请求：{}", userId);
            
            AdminUser adminUser = adminUserDao.queryByUserId(userId);
            if (adminUser == null) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(null)
                        .build();
            }
            
            AdminUserResponseDTO responseDTO = convertToAdminUserResponseDTO(adminUser);
            
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据用户ID查询管理员用户失败", e);
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-username/{username}")
    public Response<AdminUserResponseDTO> queryAdminUserByUsername(@PathVariable("username") String username) {
        try {
            log.info("根据用户名查询管理员用户请求：{}", username);
            
            AdminUser adminUser = adminUserDao.queryByUsername(username);
            if (adminUser == null) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(null)
                        .build();
            }
            
            AdminUserResponseDTO responseDTO = convertToAdminUserResponseDTO(adminUser);
            
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据用户名查询管理员用户失败", e);
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-enabled")
    public Response<List<AdminUserResponseDTO>> queryEnabledAdminUsers() {
        try {
            log.info("查询启用状态的管理员用户列表");
            
            List<AdminUser> adminUsers = adminUserDao.queryEnabledUsers();
            List<AdminUserResponseDTO> responseDTOs = adminUsers.stream()
                    .map(this::convertToAdminUserResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询启用状态的管理员用户列表失败", e);
            return Response.<List<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-status/{status}")
    public Response<List<AdminUserResponseDTO>> queryAdminUsersByStatus(@PathVariable("status") Integer status) {
        try {
            log.info("根据状态查询管理员用户列表请求：{}", status);
            
            List<AdminUser> adminUsers = adminUserDao.queryByStatus(status);
            List<AdminUserResponseDTO> responseDTOs = adminUsers.stream()
                    .map(this::convertToAdminUserResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据状态查询管理员用户列表失败", e);
            return Response.<List<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/query-list")
    public Response<PageResult<AdminUserResponseDTO>> queryAdminUserList(@RequestBody AdminUserQueryRequestDTO request) {
        try {
            log.info("根据条件分页查询管理员用户列表请求，username={}", request.getUsername());

            // 条件一起下推 SQL，total 由 count 语句得出（真正的物理分页）。
            // admin_user 表没有 owner_id 列，因此这里刻意不做归属过滤。
            IPage<AdminUser> page = adminUserDao.queryPage(
                    AdminPageSupport.page(request.getPageNum(), request.getPageSize()),
                    request.getUserId(), request.getUsername(), request.getStatus());

            return Response.<PageResult<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(AdminPageSupport.of(page, this::convertToAdminUserResponseDTO))
                    .build();
        } catch (Exception e) {
            log.error("根据条件分页查询管理员用户列表失败", e);
            return Response.<PageResult<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-all")
    public Response<List<AdminUserResponseDTO>> queryAllAdminUsers() {
        try {
            log.info("查询所有管理员用户");
            
            List<AdminUser> adminUsers = adminUserDao.queryAll();
            List<AdminUserResponseDTO> responseDTOs = adminUsers.stream()
                    .map(this::convertToAdminUserResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有管理员用户失败", e);
            return Response.<List<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/login")
    public Response<AdminUserResponseDTO> loginAdminUser(@RequestBody AdminUserLoginRequestDTO request) {
        try {
            if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名或密码不能为空")
                        .data(null)
                        .build();
            }
            String username = request.getUsername().trim();
            log.info("管理员用户登录请求：{}", username);

            // 锁定检查放在密码校验之前（2026-09-30 加固）：被锁期间不再做一次昂贵的 PBKDF2 计算，
            // 也不让「响应耗时」成为口令试错的旁路信号。key 走 trim + 小写，
            // 避免靠大小写变化换一个计数器绕过限流。
            String attemptKey = LoginAttemptGuard.accountKey(username);
            long lockedSeconds = loginAttemptGuard.lockedSecondsRemaining(attemptKey);
            if (lockedSeconds > 0) {
                log.warn("账号处于临时锁定中，拒绝登录：username={}，剩余 {} 秒", username, lockedSeconds);
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(loginLockedMessage(lockedSeconds))
                        .data(null)
                        .build();
            }

            AdminUser adminUser = adminUserDao.queryByUsername(username);
            if (adminUser == null || !passwordMatches(request.getPassword(), adminUser.getPassword())) {
                // 不存在的账号同样计数：否则「你已被锁定」就成了账号存在性的探测信号
                loginAttemptGuard.recordFailure(attemptKey);
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名或密码错误")
                        .data(null)
                        .build();
            }
            // 密码正确即清零失败计数（下面还有禁用 / 锁定状态检查，与「猜对密码」是两件事）
            loginAttemptGuard.reset(attemptKey);

            Integer status = adminUser.getStatus();
            if (status != null && status == 0) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户已被禁用")
                        .data(null)
                        .build();
            }
            if (status != null && status == 2) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户已被锁定")
                        .data(null)
                        .build();
            }

            upgradePlaintextPasswordIfNeeded(adminUser, request.getPassword());

            AdminUserResponseDTO responseDTO = convertToAdminUserResponseDTO(adminUser);
            // 角色写进 token：决定能否修改「公共资源」。角色调整后需重新登录才生效
            responseDTO.setToken(adminJwtTokenService.createToken(
                    adminUser.getUserId(), adminUser.getUsername(), adminUser.getUserRole()));
            
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("管理员用户登录失败", e);
            return Response.<AdminUserResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/validate-login")
    public Response<Boolean> validateAdminUserLogin(@RequestBody AdminUserLoginRequestDTO request) {
        try {
            if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名或密码不能为空")
                        .data(false)
                        .build();
            }
            String username = request.getUsername().trim();
            log.info("管理员用户登录校验请求：{}", username);

            // 本接口同样在验密码，而且被 AdminWebConfig 显式放行为匿名接口 ——
            // 不接同一套锁定，就等于给爆破留了一条绕过 /login 的旁路。
            String attemptKey = LoginAttemptGuard.accountKey(username);
            long lockedSeconds = loginAttemptGuard.lockedSecondsRemaining(attemptKey);
            if (lockedSeconds > 0) {
                log.warn("账号处于临时锁定中，拒绝登录校验：username={}，剩余 {} 秒", username, lockedSeconds);
                return Response.<Boolean>builder()
                        .code(ResponseCode.LOGIN_FAILED.getCode())
                        .info(loginLockedMessage(lockedSeconds))
                        .data(false)
                        .build();
            }

            AdminUser adminUser = adminUserDao.queryByUsername(username);
            if (adminUser == null || !passwordMatches(request.getPassword(), adminUser.getPassword())) {
                loginAttemptGuard.recordFailure(attemptKey);
                return Response.<Boolean>builder()
                        .code(ResponseCode.LOGIN_FAILED.getCode())
                        .info(ResponseCode.LOGIN_FAILED.getInfo())
                        .data(false)
                        .build();
            }
            loginAttemptGuard.reset(attemptKey);

            Integer status = adminUser.getStatus();
            if (status != null && status == 0) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.LOGIN_FAILED.getCode())
                        .info("用户已被禁用")
                        .data(false)
                        .build();
            }
            if (status != null && status == 2) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.LOGIN_FAILED.getCode())
                        .info("用户已被锁定")
                        .data(false)
                        .build();
            }

            upgradePlaintextPasswordIfNeeded(adminUser, request.getPassword());

            // 登录校验成功
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("管理员用户登录校验失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    /**
     * 锁定时长的对外提示：只给「大约多少分钟」，不暴露精确剩余秒数（少给一点探测信息）。
     */
    private String loginLockedMessage(long lockedSeconds) {
        long minutes = Math.max(1, (lockedSeconds + 59) / 60);
        return "尝试次数过多，账号已临时锁定，请约 " + minutes + " 分钟后再试";
    }

    /**
     * DTO转PO
     */
    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(storedPassword)) {
            return false;
        }
        if (PasswordUtil.isHashed(storedPassword)) {
            return PasswordUtil.matches(rawPassword, storedPassword);
        }
        return storedPassword.equals(rawPassword);
    }

    private void applyPasswordIfProvided(AdminUser adminUser, String rawPassword) {
        if (StringUtils.hasText(rawPassword) && !PasswordUtil.isHashed(rawPassword)) {
            adminUser.setPassword(PasswordUtil.encode(rawPassword));
        }
    }

    private void upgradePlaintextPasswordIfNeeded(AdminUser adminUser, String rawPassword) {
        if (adminUser.getId() == null || PasswordUtil.isHashed(adminUser.getPassword())) {
            return;
        }
        AdminUser update = new AdminUser();
        update.setId(adminUser.getId());
        update.setPassword(PasswordUtil.encode(rawPassword));
        adminUserDao.updateById(update);
        adminUser.setPassword(update.getPassword());
    }

    private AdminUser convertToAdminUser(AdminUserRequestDTO requestDTO) {
        AdminUser adminUser = new AdminUser();
        BeanUtils.copyProperties(requestDTO, adminUser);
        return adminUser;
    }

    /**
     * PO转DTO
     */
    private AdminUserResponseDTO convertToAdminUserResponseDTO(AdminUser adminUser) {
        AdminUserResponseDTO responseDTO = new AdminUserResponseDTO();
        BeanUtils.copyProperties(adminUser, responseDTO);
        return responseDTO;
    }

}
