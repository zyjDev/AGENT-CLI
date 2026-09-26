package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAdminUserAdminService;
import cn.bugstack.ai.api.dto.AdminUserLoginRequestDTO;
import cn.bugstack.ai.api.dto.AdminUserQueryRequestDTO;
import cn.bugstack.ai.api.dto.AdminUserRegisterRequestDTO;
import cn.bugstack.ai.api.dto.AdminUserRequestDTO;
import cn.bugstack.ai.api.dto.AdminUserResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.infrastructure.dao.IAdminUserDao;
import cn.bugstack.ai.infrastructure.dao.po.AdminUser;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.trigger.config.AdminJwtTokenService;
import cn.bugstack.ai.types.common.PasswordUtil;
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
 *
 * @author bugstack虫洞栈
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
            log.info("用户自助注册请求，username={}", username);

            if (adminUserDao.queryByUsername(username) != null) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名已存在")
                        .data(null)
                        .build();
            }

            AdminUser adminUser = AdminUser.builder()
                    .userId(UUID.randomUUID().toString())
                    .username(username)
                    .password(PasswordUtil.encode(request.getPassword()))
                    .status(1)
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

            // 注册即登录：直接签发 token，前端不必再走一次登录
            AdminUserResponseDTO responseDTO = convertToAdminUserResponseDTO(adminUser);
            responseDTO.setToken(adminJwtTokenService.createToken(adminUser.getUserId(), adminUser.getUsername()));

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
    @PostMapping("/create")
    public Response<Boolean> createAdminUser(@RequestBody AdminUserRequestDTO request) {
        try {
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
                adminUser.setUserId(UUID.randomUUID().toString());
            }
            if (adminUser.getStatus() == null) {
                adminUser.setStatus(1);
            }
            adminUser.setCreateTime(LocalDateTime.now());
            adminUser.setUpdateTime(LocalDateTime.now());

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
    public Response<List<AdminUserResponseDTO>> queryAdminUserList(@RequestBody AdminUserQueryRequestDTO request) {
        try {
            log.info("根据条件查询管理员用户列表请求，username={}", request.getUsername());
            
            // 这里可以根据查询条件进行过滤，暂时先查询所有
            List<AdminUser> adminUsers = adminUserDao.queryAll();
            
            // 根据查询条件进行过滤
            List<AdminUser> filteredUsers = adminUsers.stream()
                    .filter(user -> {
                        boolean match = true;
                        if (StringUtils.hasText(request.getUserId())) {
                            match = match && user.getUserId().equals(request.getUserId());
                        }
                        if (StringUtils.hasText(request.getUsername())) {
                            match = match && user.getUsername().contains(request.getUsername());
                        }
                        if (request.getStatus() != null) {
                            match = match && request.getStatus().equals(user.getStatus());
                        }
                        return match;
                    })
                    .collect(Collectors.toList());
            
            // 分页处理
            int pageNum = request.getPageNum() != null ? Math.max(1, request.getPageNum()) : 1;
            int pageSize = request.getPageSize() != null ? Math.max(1, request.getPageSize()) : 10;
            int startIndex = (pageNum - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, filteredUsers.size());

            List<AdminUser> pagedUsers = startIndex >= filteredUsers.size()
                    ? List.of()
                    : filteredUsers.subList(startIndex, endIndex);
            List<AdminUserResponseDTO> responseDTOs = pagedUsers.stream()
                    .map(this::convertToAdminUserResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AdminUserResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据条件查询管理员用户列表失败", e);
            return Response.<List<AdminUserResponseDTO>>builder()
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
            log.info("管理员用户登录请求：{}", request.getUsername());

            AdminUser adminUser = adminUserDao.queryByUsername(request.getUsername());
            if (adminUser == null || !passwordMatches(request.getPassword(), adminUser.getPassword())) {
                return Response.<AdminUserResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名或密码错误")
                        .data(null)
                        .build();
            }

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
            responseDTO.setToken(adminJwtTokenService.createToken(adminUser.getUserId(), adminUser.getUsername()));
            
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
            log.info("管理员用户登录校验请求：{}", request.getUsername());
            if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("用户名或密码不能为空")
                        .data(false)
                        .build();
            }
            log.info("管理员用户登录校验请求：{}", request.getUsername());

            AdminUser adminUser = adminUserDao.queryByUsername(request.getUsername());
            if (adminUser == null || !passwordMatches(request.getPassword(), adminUser.getPassword())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.LOGIN_FAILED.getCode())
                        .info(ResponseCode.LOGIN_FAILED.getInfo())
                        .data(false)
                        .build();
            }

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
