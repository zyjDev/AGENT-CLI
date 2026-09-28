package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiClientAdvisorAdminService;
import cn.bugstack.ai.api.dto.AiClientAdvisorQueryRequestDTO;
import cn.bugstack.ai.api.dto.AiClientAdvisorRequestDTO;
import cn.bugstack.ai.api.dto.AiClientAdvisorResponseDTO;
import cn.bugstack.ai.api.response.PageResult;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.infrastructure.dao.IAiClientAdvisorDao;
import cn.bugstack.ai.infrastructure.dao.po.AiClientAdvisor;
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
 * 顾问配置管理控制器
 *
 * @author bugstack虫洞栈
 * @description 顾问配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-advisor")
// 跨域统一收敛到 WebCorsConfig 的白名单（原先此处是 origins = "*"，等于对任意站点放开）
public class AiClientAdvisorAdminController implements IAiClientAdvisorAdminService {

    @Resource
    private IAiClientAdvisorDao aiClientAdvisorDao;

    @Override
    @PostMapping("/create")
    public Response<Boolean> createAiClientAdvisor(@RequestBody AiClientAdvisorRequestDTO request) {
        try {
            log.info("创建顾问配置请求：{}", request);
            
            // DTO转PO
            AiClientAdvisor aiClientAdvisor = convertToAiClientAdvisor(request);
            aiClientAdvisor.setCreateTime(LocalDateTime.now());
            aiClientAdvisor.setUpdateTime(LocalDateTime.now());
            
            // 归属：普通用户建的就是他自己的（管理员建的留空 = 平台默认，人人可用）
            OwnerGuard.stampOwnerOnCreate(aiClientAdvisor);

            int result = aiClientAdvisorDao.insert(aiClientAdvisor);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("创建顾问配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientAdvisorById(@RequestBody AiClientAdvisorRequestDTO request) {
        try {
            log.info("根据ID更新顾问配置请求：{}", request);
            
            if (request.getId() == null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AiClientAdvisor aiClientAdvisor = convertToAiClientAdvisor(request);
            aiClientAdvisor.setUpdateTime(LocalDateTime.now());
            
            // 写权限：本人资源本人可改；公共资源仅管理员；他人私有资源不可改
            AiClientAdvisor existing = aiClientAdvisorDao.queryById(request.getId());
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("顾问");
            }
            int result = aiClientAdvisorDao.updateById(aiClientAdvisor);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID更新顾问配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-advisor-id")
    public Response<Boolean> updateAiClientAdvisorByAdvisorId(@RequestBody AiClientAdvisorRequestDTO request) {
        try {
            log.info("根据顾问ID更新顾问配置请求：{}", request);
            
            if (!StringUtils.hasText(request.getAdvisorId())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("顾问ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AiClientAdvisor aiClientAdvisor = convertToAiClientAdvisor(request);
            aiClientAdvisor.setUpdateTime(LocalDateTime.now());
            
            int result = aiClientAdvisorDao.updateByAdvisorId(aiClientAdvisor);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据顾问ID更新顾问配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientAdvisorById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID删除顾问配置请求：{}", id);
            
            AiClientAdvisor existing = aiClientAdvisorDao.queryById(id);
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("顾问");
            }
            int result = aiClientAdvisorDao.deleteById(id);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID删除顾问配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-advisor-id/{advisorId}")
    public Response<Boolean> deleteAiClientAdvisorByAdvisorId(@PathVariable("advisorId") String advisorId) {
        try {
            log.info("根据顾问ID删除顾问配置请求：{}", advisorId);
            
            int result = aiClientAdvisorDao.deleteByAdvisorId(advisorId);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据顾问ID删除顾问配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-id/{id}")
    public Response<AiClientAdvisorResponseDTO> queryAiClientAdvisorById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID查询顾问配置请求：{}", id);
            
            AiClientAdvisor aiClientAdvisor = aiClientAdvisorDao.queryById(id);
            
            if (aiClientAdvisor == null) {
                return Response.<AiClientAdvisorResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info("未找到对应的顾问配置")
                        .data(null)
                        .build();
            }
            
            AiClientAdvisorResponseDTO responseDTO = convertToAiClientAdvisorResponseDTO(aiClientAdvisor);
            
            return Response.<AiClientAdvisorResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据ID查询顾问配置失败", e);
            return Response.<AiClientAdvisorResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-advisor-id/{advisorId}")
    public Response<AiClientAdvisorResponseDTO> queryAiClientAdvisorByAdvisorId(@PathVariable("advisorId") String advisorId) {
        try {
            log.info("根据顾问ID查询顾问配置请求：{}", advisorId);
            
            AiClientAdvisor aiClientAdvisor = aiClientAdvisorDao.queryByAdvisorId(advisorId);

            // 读侧归属校验：queryByAdvisorId 刻意不带归属过滤（跨线程装配链路需要），
            // 请求线程取数返回给用户前必须补校验；他人私有 → 视同不存在，走下面的「未找到」分支
            if (aiClientAdvisor != null && !OwnerGuard.readable(aiClientAdvisor.getOwnerId())) {
                aiClientAdvisor = null;
            }
            
            if (aiClientAdvisor == null) {
                return Response.<AiClientAdvisorResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info("未找到对应的顾问配置")
                        .data(null)
                        .build();
            }
            
            AiClientAdvisorResponseDTO responseDTO = convertToAiClientAdvisorResponseDTO(aiClientAdvisor);
            
            return Response.<AiClientAdvisorResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据顾问ID查询顾问配置失败", e);
            return Response.<AiClientAdvisorResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-enabled")
    public Response<List<AiClientAdvisorResponseDTO>> queryEnabledAiClientAdvisors() {
        try {
            log.info("查询所有启用的顾问配置");
            
            List<AiClientAdvisor> aiClientAdvisors = aiClientAdvisorDao.queryByStatus(1);
            
            List<AiClientAdvisorResponseDTO> responseDTOs = aiClientAdvisors.stream()
                    .map(this::convertToAiClientAdvisorResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有启用的顾问配置失败", e);
            return Response.<List<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-status/{status}")
    public Response<List<AiClientAdvisorResponseDTO>> queryAiClientAdvisorsByStatus(@PathVariable("status") Integer status) {
        try {
            log.info("根据状态查询顾问配置请求：{}", status);
            
            List<AiClientAdvisor> aiClientAdvisors = aiClientAdvisorDao.queryByStatus(status);
            
            List<AiClientAdvisorResponseDTO> responseDTOs = aiClientAdvisors.stream()
                    .map(this::convertToAiClientAdvisorResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据状态查询顾问配置失败", e);
            return Response.<List<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-type/{advisorType}")
    public Response<List<AiClientAdvisorResponseDTO>> queryAiClientAdvisorsByType(@PathVariable("advisorType") String advisorType) {
        try {
            log.info("根据顾问类型查询顾问配置请求：{}", advisorType);
            
            List<AiClientAdvisor> aiClientAdvisors = aiClientAdvisorDao.queryByAdvisorType(advisorType);
            
            List<AiClientAdvisorResponseDTO> responseDTOs = aiClientAdvisors.stream()
                    .map(this::convertToAiClientAdvisorResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据顾问类型查询顾问配置失败", e);
            return Response.<List<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/query-list")
    public Response<PageResult<AiClientAdvisorResponseDTO>> queryAiClientAdvisorList(@RequestBody AiClientAdvisorQueryRequestDTO request) {
        try {
            log.info("根据条件分页查询顾问配置列表请求：{}", request);

            // 条件与归属过滤一起下推 SQL，total 由 count 语句得出（真正的物理分页）
            IPage<AiClientAdvisor> page = aiClientAdvisorDao.queryPage(
                    AdminPageSupport.page(request.getPageNum(), request.getPageSize()),
                    request.getAdvisorId(), request.getAdvisorName(), request.getAdvisorType(), request.getStatus());

            return Response.<PageResult<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(AdminPageSupport.of(page, this::convertToAiClientAdvisorResponseDTO))
                    .build();
        } catch (Exception e) {
            log.error("根据条件分页查询顾问配置列表失败", e);
            return Response.<PageResult<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-all")
    public Response<List<AiClientAdvisorResponseDTO>> queryAllAiClientAdvisors() {
        try {
            log.info("查询所有顾问配置");
            
            List<AiClientAdvisor> aiClientAdvisors = aiClientAdvisorDao.queryAll();
            
            List<AiClientAdvisorResponseDTO> responseDTOs = aiClientAdvisors.stream()
                    .map(this::convertToAiClientAdvisorResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有顾问配置失败", e);
            return Response.<List<AiClientAdvisorResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    /**
     * DTO转PO对象
     * @param requestDTO 请求DTO
     * @return PO对象
     */
    private AiClientAdvisor convertToAiClientAdvisor(AiClientAdvisorRequestDTO requestDTO) {
        AiClientAdvisor aiClientAdvisor = new AiClientAdvisor();
        BeanUtils.copyProperties(requestDTO, aiClientAdvisor);
        return aiClientAdvisor;
    }

    /**
     * PO转响应DTO对象
     * @param aiClientAdvisor PO对象
     * @return 响应DTO
     */
    private AiClientAdvisorResponseDTO convertToAiClientAdvisorResponseDTO(AiClientAdvisor aiClientAdvisor) {
        AiClientAdvisorResponseDTO responseDTO = new AiClientAdvisorResponseDTO();
        BeanUtils.copyProperties(aiClientAdvisor, responseDTO);
        return responseDTO;
    }

}