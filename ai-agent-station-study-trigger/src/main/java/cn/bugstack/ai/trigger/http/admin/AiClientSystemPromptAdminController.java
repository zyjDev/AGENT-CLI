package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiClientSystemPromptAdminService;
import cn.bugstack.ai.api.dto.AiClientSystemPromptQueryRequestDTO;
import cn.bugstack.ai.api.dto.AiClientSystemPromptRequestDTO;
import cn.bugstack.ai.api.dto.AiClientSystemPromptResponseDTO;
import cn.bugstack.ai.api.response.PageResult;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.infrastructure.dao.IAiClientSystemPromptDao;
import cn.bugstack.ai.infrastructure.dao.po.AiClientSystemPrompt;
import cn.bugstack.ai.trigger.support.AdminPageSupport;
import cn.bugstack.ai.trigger.support.OwnerGuard;
import cn.bugstack.ai.types.enums.ResponseCode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统提示词配置管理控制器
 * @description 系统提示词配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-system-prompt")
// 跨域统一收敛到 WebCorsConfig 的白名单（原先此处是 origins = "*"，等于对任意站点放开）
public class AiClientSystemPromptAdminController implements IAiClientSystemPromptAdminService {

    @Resource
    private IAiClientSystemPromptDao aiClientSystemPromptDao;

    @Override
    @PostMapping("/create")
    public Response<Boolean> createAiClientSystemPrompt(@RequestBody AiClientSystemPromptRequestDTO request) {
        try {
            log.info("创建系统提示词配置请求：{}", request);
            
            // DTO转PO
            AiClientSystemPrompt aiClientSystemPrompt = convertToAiClientSystemPrompt(request);
            aiClientSystemPrompt.setCreateTime(LocalDateTime.now());
            aiClientSystemPrompt.setUpdateTime(LocalDateTime.now());
            
            // 归属：普通用户建的就是他自己的（管理员建的留空 = 平台默认，人人可用）
            OwnerGuard.stampOwnerOnCreate(aiClientSystemPrompt);

            aiClientSystemPromptDao.insert(aiClientSystemPrompt);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("创建系统提示词配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientSystemPromptById(@RequestBody AiClientSystemPromptRequestDTO request) {
        try {
            log.info("根据ID更新系统提示词配置请求：{}", request);
            
            if (request.getId() == null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AiClientSystemPrompt aiClientSystemPrompt = convertToAiClientSystemPrompt(request);
            aiClientSystemPrompt.setUpdateTime(LocalDateTime.now());
            
            // 写权限：本人资源本人可改；公共资源仅管理员；他人私有资源不可改
            AiClientSystemPrompt existing = aiClientSystemPromptDao.queryById(request.getId());
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("系统提示词");
            }
            int result = aiClientSystemPromptDao.updateById(aiClientSystemPrompt);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID更新系统提示词配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-prompt-id")
    public Response<Boolean> updateAiClientSystemPromptByPromptId(@RequestBody AiClientSystemPromptRequestDTO request) {
        try {
            log.info("根据提示词ID更新系统提示词配置请求：{}", request);
            
            if (!StringUtils.hasText(request.getPromptId())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("提示词ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AiClientSystemPrompt aiClientSystemPrompt = convertToAiClientSystemPrompt(request);
            aiClientSystemPrompt.setUpdateTime(LocalDateTime.now());
            
            int result = aiClientSystemPromptDao.updateByPromptId(aiClientSystemPrompt);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据提示词ID更新系统提示词配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientSystemPromptById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID删除系统提示词配置：{}", id);
            
            AiClientSystemPrompt existing = aiClientSystemPromptDao.queryById(id);
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("系统提示词");
            }
            int result = aiClientSystemPromptDao.deleteById(id);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID删除系统提示词配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-prompt-id/{promptId}")
    public Response<Boolean> deleteAiClientSystemPromptByPromptId(@PathVariable("promptId") String promptId) {
        try {
            log.info("根据提示词ID删除系统提示词配置：{}", promptId);
            
            int result = aiClientSystemPromptDao.deleteByPromptId(promptId);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据提示词ID删除系统提示词配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-id/{id}")
    public Response<AiClientSystemPromptResponseDTO> queryAiClientSystemPromptById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID查询系统提示词配置：{}", id);
            
            AiClientSystemPrompt aiClientSystemPrompt = aiClientSystemPromptDao.queryById(id);
            
            if (aiClientSystemPrompt == null) {
                return Response.<AiClientSystemPromptResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("数据不存在")
                        .data(null)
                        .build();
            }
            
            AiClientSystemPromptResponseDTO responseDTO = convertToAiClientSystemPromptResponseDTO(aiClientSystemPrompt);
            
            return Response.<AiClientSystemPromptResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据ID查询系统提示词配置失败", e);
            return Response.<AiClientSystemPromptResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-prompt-id/{promptId}")
    public Response<AiClientSystemPromptResponseDTO> queryAiClientSystemPromptByPromptId(@PathVariable("promptId") String promptId) {
        try {
            log.info("根据提示词ID查询系统提示词配置：{}", promptId);
            
            AiClientSystemPrompt aiClientSystemPrompt = aiClientSystemPromptDao.queryByPromptId(promptId);

            // 读侧归属校验：queryByPromptId 刻意不带归属过滤（跨线程装配链路需要），
            // 请求线程取数返回给用户前必须补校验；他人私有 → 视同不存在，走下面的「未找到」分支
            if (aiClientSystemPrompt != null && !OwnerGuard.readable(aiClientSystemPrompt.getOwnerId())) {
                aiClientSystemPrompt = null;
            }
            
            if (aiClientSystemPrompt == null) {
                return Response.<AiClientSystemPromptResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("系统提示词配置不存在")
                        .data(null)
                        .build();
            }
            
            AiClientSystemPromptResponseDTO responseDTO = convertToAiClientSystemPromptResponseDTO(aiClientSystemPrompt);
            
            return Response.<AiClientSystemPromptResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据提示词ID查询系统提示词配置失败", e);
            return Response.<AiClientSystemPromptResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-all")
    public Response<List<AiClientSystemPromptResponseDTO>> queryAllAiClientSystemPrompts() {
        try {
            log.info("查询所有系统提示词配置");
            
            List<AiClientSystemPrompt> aiClientSystemPrompts = aiClientSystemPromptDao.queryAll();
            
            List<AiClientSystemPromptResponseDTO> responseDTOs = aiClientSystemPrompts.stream()
                    .map(this::convertToAiClientSystemPromptResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientSystemPromptResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有系统提示词配置失败", e);
            return Response.<List<AiClientSystemPromptResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-enabled")
    public Response<List<AiClientSystemPromptResponseDTO>> queryEnabledAiClientSystemPrompts() {
        try {
            log.info("查询启用的系统提示词配置");
            
            List<AiClientSystemPrompt> aiClientSystemPrompts = aiClientSystemPromptDao.queryEnabledPrompts();
            
            List<AiClientSystemPromptResponseDTO> responseDTOs = aiClientSystemPrompts.stream()
                    .map(this::convertToAiClientSystemPromptResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientSystemPromptResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询启用的系统提示词配置失败", e);
            return Response.<List<AiClientSystemPromptResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-prompt-name/{promptName}")
    public Response<List<AiClientSystemPromptResponseDTO>> queryAiClientSystemPromptsByPromptName(@PathVariable("promptName") String promptName) {
        try {
            log.info("根据提示词名称查询系统提示词配置：{}", promptName);
            
            List<AiClientSystemPrompt> aiClientSystemPrompts = aiClientSystemPromptDao.queryByPromptName(promptName);
            
            List<AiClientSystemPromptResponseDTO> responseDTOs = aiClientSystemPrompts.stream()
                    .map(this::convertToAiClientSystemPromptResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientSystemPromptResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据提示词名称查询系统提示词配置失败", e);
            return Response.<List<AiClientSystemPromptResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/query-list")
    public Response<PageResult<AiClientSystemPromptResponseDTO>> queryAiClientSystemPromptList(@RequestBody AiClientSystemPromptQueryRequestDTO request) {
        try {
            log.info("根据条件分页查询系统提示词配置列表：{}", request);

            // 条件与归属过滤一起下推 SQL，total 由 count 语句得出（真正的物理分页）
            IPage<AiClientSystemPrompt> page = aiClientSystemPromptDao.queryPage(
                    AdminPageSupport.page(request.getPageNum(), request.getPageSize()),
                    request.getPromptId(), request.getPromptName(), request.getStatus());

            return Response.<PageResult<AiClientSystemPromptResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(AdminPageSupport.of(page, this::convertToAiClientSystemPromptResponseDTO))
                    .build();
        } catch (Exception e) {
            log.error("根据条件分页查询系统提示词配置列表失败", e);
            return Response.<PageResult<AiClientSystemPromptResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    /**
     * DTO转PO对象
     */
    private AiClientSystemPrompt convertToAiClientSystemPrompt(AiClientSystemPromptRequestDTO requestDTO) {
        AiClientSystemPrompt aiClientSystemPrompt = new AiClientSystemPrompt();
        BeanUtils.copyProperties(requestDTO, aiClientSystemPrompt);
        return aiClientSystemPrompt;
    }

    /**
     * PO转DTO对象
     */
    private AiClientSystemPromptResponseDTO convertToAiClientSystemPromptResponseDTO(AiClientSystemPrompt aiClientSystemPrompt) {
        AiClientSystemPromptResponseDTO responseDTO = new AiClientSystemPromptResponseDTO();
        BeanUtils.copyProperties(aiClientSystemPrompt, responseDTO);
        return responseDTO;
    }

}
