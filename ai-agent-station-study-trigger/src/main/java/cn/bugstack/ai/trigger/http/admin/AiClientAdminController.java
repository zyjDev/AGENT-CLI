package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiClientAdminService;
import cn.bugstack.ai.api.dto.AiClientQueryRequestDTO;
import cn.bugstack.ai.api.dto.AiClientRequestDTO;
import cn.bugstack.ai.api.dto.AiClientResponseDTO;
import cn.bugstack.ai.api.response.PageResult;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.infrastructure.dao.IAiClientDao;
import cn.bugstack.ai.infrastructure.dao.po.AiClient;
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
 * AI客户端管理控制器
 *
 * @author bugstack虫洞栈
 * @description AI客户端配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client")
// 跨域统一收敛到 WebCorsConfig 的白名单（原先此处是 origins = "*"，等于对任意站点放开）
public class AiClientAdminController implements IAiClientAdminService {

    @Resource
    private IAiClientDao aiClientDao;

    @Override
    @PostMapping("/create")
    public Response<Boolean> createAiClient(@RequestBody AiClientRequestDTO request) {
        try {
            log.info("创建AI客户端配置请求：{}", request);
            
            // DTO转PO
            AiClient aiClient = convertToAiClient(request);
            aiClient.setCreateTime(LocalDateTime.now());
            aiClient.setUpdateTime(LocalDateTime.now());
            
            // 归属：普通用户建的就是他自己的（管理员建的留空 = 平台默认，人人可用）
            OwnerGuard.stampOwnerOnCreate(aiClient);

            int result = aiClientDao.insert(aiClient);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("创建AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientById(@RequestBody AiClientRequestDTO request) {
        try {
            log.info("根据ID更新AI客户端配置请求：{}", request);
            
            if (request.getId() == null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AiClient aiClient = convertToAiClient(request);
            aiClient.setUpdateTime(LocalDateTime.now());
            
            // 写权限：本人资源本人可改；公共资源仅管理员；他人私有资源不可改
            AiClient existing = aiClientDao.queryById(request.getId());
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("客户端");
            }
            int result = aiClientDao.updateById(aiClient);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID更新AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-client-id")
    public Response<Boolean> updateAiClientByClientId(@RequestBody AiClientRequestDTO request) {
        try {
            log.info("根据客户端ID更新AI客户端配置请求：{}", request);
            
            if (!StringUtils.hasText(request.getClientId())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("客户端ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AiClient aiClient = convertToAiClient(request);
            aiClient.setUpdateTime(LocalDateTime.now());
            
            AiClient existing = aiClientDao.queryByClientId(aiClient.getClientId());
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("客户端");
            }
            int result = aiClientDao.updateByClientId(aiClient);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据客户端ID更新AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID删除AI客户端配置请求：{}", id);
            
            AiClient existing = aiClientDao.queryById(id);
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("客户端");
            }
            int result = aiClientDao.deleteById(id);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID删除AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-client-id/{clientId}")
    public Response<Boolean> deleteAiClientByClientId(@PathVariable("clientId") String clientId) {
        try {
            log.info("根据客户端ID删除AI客户端配置请求：{}", clientId);
            
            AiClient existing = aiClientDao.queryByClientId(clientId);
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("客户端");
            }
            int result = aiClientDao.deleteByClientId(clientId);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据客户端ID删除AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-id/{id}")
    public Response<AiClientResponseDTO> queryAiClientById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID查询AI客户端配置请求：{}", id);
            
            AiClient aiClient = aiClientDao.queryById(id);
            
            if (aiClient == null) {
                return Response.<AiClientResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端配置")
                        .data(null)
                        .build();
            }
            
            // PO转DTO
            AiClientResponseDTO responseDTO = convertToAiClientResponseDTO(aiClient);
            
            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据ID查询AI客户端配置失败", e);
            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-client-id/{clientId}")
    public Response<AiClientResponseDTO> queryAiClientByClientId(@PathVariable("clientId") String clientId) {
        try {
            log.info("根据客户端ID查询AI客户端配置请求：{}", clientId);
            
            AiClient aiClient = aiClientDao.queryByClientId(clientId);

            // 读侧归属校验：queryByClientId 刻意不带归属过滤（跨线程装配链路需要），
            // 请求线程取数返回给用户前必须补校验；他人私有 → 视同不存在，走下面的「未找到」分支
            if (aiClient != null && !OwnerGuard.readable(aiClient.getOwnerId())) {
                aiClient = null;
            }
            
            if (aiClient == null) {
                return Response.<AiClientResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端配置")
                        .data(null)
                        .build();
            }
            
            // PO转DTO
            AiClientResponseDTO responseDTO = convertToAiClientResponseDTO(aiClient);
            
            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据客户端ID查询AI客户端配置失败", e);
            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-enabled")
    public Response<List<AiClientResponseDTO>> queryEnabledAiClients() {
        try {
            log.info("查询所有启用的AI客户端配置");
            
            List<AiClient> aiClients = aiClientDao.queryEnabledClients();
            
            List<AiClientResponseDTO> responseDTOs = aiClients.stream()
                    .map(this::convertToAiClientResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有启用的AI客户端配置失败", e);
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/query-list")
    public Response<PageResult<AiClientResponseDTO>> queryAiClientList(@RequestBody AiClientQueryRequestDTO request) {
        try {
            log.info("根据条件分页查询AI客户端配置列表请求：{}", request);

            // 条件与归属过滤一起下推 SQL，total 由 count 语句得出（真正的物理分页）
            IPage<AiClient> page = aiClientDao.queryPage(
                    AdminPageSupport.page(request.getPageNum(), request.getPageSize()),
                    request.getClientId(), request.getClientName(), request.getStatus());

            return Response.<PageResult<AiClientResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(AdminPageSupport.of(page, this::convertToAiClientResponseDTO))
                    .build();
        } catch (Exception e) {
            log.error("根据条件分页查询AI客户端配置列表失败", e);
            return Response.<PageResult<AiClientResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-all")
    public Response<List<AiClientResponseDTO>> queryAllAiClients() {
        try {
            log.info("查询所有AI客户端配置");
            
            List<AiClient> aiClients = aiClientDao.queryAll();
            
            List<AiClientResponseDTO> responseDTOs = aiClients.stream()
                    .map(this::convertToAiClientResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有AI客户端配置失败", e);
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    /**
     * DTO转PO对象
     */
    private AiClient convertToAiClient(AiClientRequestDTO requestDTO) {
        AiClient aiClient = new AiClient();
        BeanUtils.copyProperties(requestDTO, aiClient);
        return aiClient;
    }

    /**
     * PO转DTO对象
     */
    private AiClientResponseDTO convertToAiClientResponseDTO(AiClient aiClient) {
        AiClientResponseDTO responseDTO = new AiClientResponseDTO();
        BeanUtils.copyProperties(aiClient, responseDTO);
        return responseDTO;
    }

}