package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiClientApiAdminService;
import cn.bugstack.ai.api.dto.AiClientApiQueryRequestDTO;
import cn.bugstack.ai.api.dto.AiClientApiRequestDTO;
import cn.bugstack.ai.api.dto.AiClientApiResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.infrastructure.dao.IAiClientApiDao;
import cn.bugstack.ai.infrastructure.dao.po.AiClientApi;
import cn.bugstack.ai.trigger.support.OwnerGuard;
import cn.bugstack.ai.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI客户端API配置管理控制器
 *
 * @author bugstack虫洞栈
 * @description AI客户端API配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-api")
// 跨域统一收敛到 WebCorsConfig 的白名单（原先此处是 origins = "*"，等于对任意站点放开）
public class AiClientApiAdminController implements IAiClientApiAdminService {

    @Resource
    private IAiClientApiDao aiClientApiDao;

    @Override
    @PostMapping("/create")
    public Response<Boolean> createAiClientApi(@RequestBody AiClientApiRequestDTO request) {
        try {
            log.info("创建AI客户端API配置请求：{}", request);
            
            // DTO转PO
            AiClientApi aiClientApi = convertToAiClientApi(request);
            aiClientApi.setCreateTime(LocalDateTime.now());
            aiClientApi.setUpdateTime(LocalDateTime.now());

            // 嵌入路径选填：只配对话模型的用户不该被这个字段卡住（embeddings 只在知识库/向量化时用）。
            // 该列是 NOT NULL，所以留空就补默认值；填了的原样保留。
            if (!StringUtils.hasText(aiClientApi.getEmbeddingsPath())) {
                aiClientApi.setEmbeddingsPath("v1/embeddings");
            }

            // 归属：普通用户建的就是他自己的（管理员建的留空 = 平台默认，人人可用）
            OwnerGuard.stampOwnerOnCreate(aiClientApi);

            int result = aiClientApiDao.insert(aiClientApi);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("创建AI客户端API配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientApiById(@RequestBody AiClientApiRequestDTO request) {
        try {
            log.info("根据ID更新AI客户端API配置请求：{}", request);
            
            if (request.getId() == null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AiClientApi aiClientApi = convertToAiClientApi(request);
            aiClientApi.setUpdateTime(LocalDateTime.now());
            // 密钥留空 / 传回掩码 = 不修改（表单不再回填原密钥）
            maskToNull(aiClientApi);
            
            // 写权限：本人资源本人可改；公共资源仅管理员；他人私有资源不可改
            AiClientApi existing = aiClientApiDao.queryById(request.getId());
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("API 通道");
            }
            int result = aiClientApiDao.updateById(aiClientApi);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID更新AI客户端API配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-api-id")
    public Response<Boolean> updateAiClientApiByApiId(@RequestBody AiClientApiRequestDTO request) {
        try {
            log.info("根据API ID更新AI客户端API配置请求：{}", request);
            
            if (!StringUtils.hasText(request.getApiId())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("API ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AiClientApi aiClientApi = convertToAiClientApi(request);
            aiClientApi.setUpdateTime(LocalDateTime.now());
            // 同上：密钥留空 / 传回掩码 = 不修改
            maskToNull(aiClientApi);
            
            // 同上：按业务ID改也走同一套归属校验（apiKey 属敏感凭据）
            AiClientApi existing = aiClientApiDao.queryByApiId(aiClientApi.getApiId());
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("API 通道");
            }
            int result = aiClientApiDao.updateByApiId(aiClientApi);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据API ID更新AI客户端API配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientApiById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID删除AI客户端API配置请求：{}", id);
            
            AiClientApi existing = aiClientApiDao.queryById(id);
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("API 通道");
            }
            int result = aiClientApiDao.deleteById(id);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID删除AI客户端API配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-api-id/{apiId}")
    public Response<Boolean> deleteAiClientApiByApiId(@PathVariable("apiId") String apiId) {
        try {
            log.info("根据API ID删除AI客户端API配置请求：{}", apiId);
            
            AiClientApi existing = aiClientApiDao.queryByApiId(apiId);
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("API 通道");
            }
            int result = aiClientApiDao.deleteByApiId(apiId);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据API ID删除AI客户端API配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-id/{id}")
    public Response<AiClientApiResponseDTO> queryAiClientApiById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID查询AI客户端API配置请求：{}", id);
            
            AiClientApi aiClientApi = aiClientApiDao.queryById(id);
            
            if (aiClientApi == null) {
                return Response.<AiClientApiResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端API配置")
                        .data(null)
                        .build();
            }
            
            // PO转DTO
            AiClientApiResponseDTO responseDTO = convertToAiClientApiResponseDTO(aiClientApi);
            
            return Response.<AiClientApiResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据ID查询AI客户端API配置失败", e);
            return Response.<AiClientApiResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-api-id/{apiId}")
    public Response<AiClientApiResponseDTO> queryAiClientApiByApiId(@PathVariable("apiId") String apiId) {
        try {
            log.info("根据API ID查询AI客户端API配置请求：{}", apiId);
            
            AiClientApi aiClientApi = aiClientApiDao.queryByApiId(apiId);
            
            if (aiClientApi == null) {
                return Response.<AiClientApiResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端API配置")
                        .data(null)
                        .build();
            }
            
            // PO转DTO
            AiClientApiResponseDTO responseDTO = convertToAiClientApiResponseDTO(aiClientApi);
            
            return Response.<AiClientApiResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据API ID查询AI客户端API配置失败", e);
            return Response.<AiClientApiResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-enabled")
    public Response<List<AiClientApiResponseDTO>> queryEnabledAiClientApis() {
        try {
            log.info("查询所有启用的AI客户端API配置");
            
            List<AiClientApi> aiClientApiList = aiClientApiDao.queryEnabledApis();
            
            // PO转DTO
            List<AiClientApiResponseDTO> responseDTOList = aiClientApiList.stream()
                    .map(this::convertToAiClientApiResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientApiResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOList)
                    .build();
        } catch (Exception e) {
            log.error("查询所有启用的AI客户端API配置失败", e);
            return Response.<List<AiClientApiResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/query-list")
    public Response<List<AiClientApiResponseDTO>> queryAiClientApiList(@RequestBody AiClientApiQueryRequestDTO request) {
        try {
            log.info("分页查询AI客户端API配置列表请求：{}", request);
            
            // 这里需要根据实际的DAO实现来调整，如果DAO没有分页查询方法，需要先添加
            // 暂时使用查询所有然后过滤的方式
            List<AiClientApi> allApiList = aiClientApiDao.queryAll();
            
            // 根据查询条件过滤
            List<AiClientApi> filteredList = allApiList.stream()
                    .filter(api -> {
                        boolean match = true;
                        if (StringUtils.hasText(request.getApiId())) {
                            match = match && api.getApiId().contains(request.getApiId());
                        }
                        if (StringUtils.hasText(request.getBaseUrl())) {
                            match = match && api.getBaseUrl().contains(request.getBaseUrl());
                        }
                        if (request.getStatus() != null) {
                            match = match && request.getStatus().equals(api.getStatus());
                        }
                        return match;
                    })
                    .collect(Collectors.toList());
            
            // 简单分页处理
            int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
            int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
            int startIndex = (pageNum - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, filteredList.size());
            
            List<AiClientApi> pagedList = startIndex < filteredList.size() ? 
                    filteredList.subList(startIndex, endIndex) : List.of();
            
            // PO转DTO
            List<AiClientApiResponseDTO> responseDTOList = pagedList.stream()
                    .map(this::convertToAiClientApiResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientApiResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOList)
                    .build();
        } catch (Exception e) {
            log.error("分页查询AI客户端API配置列表失败", e);
            return Response.<List<AiClientApiResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-all")
    public Response<List<AiClientApiResponseDTO>> queryAllAiClientApis() {
        try {
            log.info("查询所有AI客户端API配置");
            
            List<AiClientApi> aiClientApiList = aiClientApiDao.queryAll();
            
            // PO转DTO
            List<AiClientApiResponseDTO> responseDTOList = aiClientApiList.stream()
                    .map(this::convertToAiClientApiResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientApiResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOList)
                    .build();
        } catch (Exception e) {
            log.error("查询所有AI客户端API配置失败", e);
            return Response.<List<AiClientApiResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    /**
     * DTO转PO对象
     */
    private AiClientApi convertToAiClientApi(AiClientApiRequestDTO requestDTO) {
        AiClientApi aiClientApi = new AiClientApi();
        BeanUtils.copyProperties(requestDTO, aiClientApi);
        return aiClientApi;
    }

    /**
     * PO转DTO对象。
     * <p>
     * ⚠️ apiKey 出网关前**一律掩码**：管理后台不显示"查看明文"按钮还不够 ——
     * 只要明文还在响应体里，任何人按 F12 就能拿到（等于把密钥写在白板上）。
     * 前端表单编辑时也不需要原值：留空即表示"不修改"（见 {@link #maskToNull}）。
     */
    private AiClientApiResponseDTO convertToAiClientApiResponseDTO(AiClientApi aiClientApi) {
        AiClientApiResponseDTO responseDTO = new AiClientApiResponseDTO();
        BeanUtils.copyProperties(aiClientApi, responseDTO);
        responseDTO.setApiKey(maskApiKey(aiClientApi.getApiKey()));
        return responseDTO;
    }

    /**
     * 密钥掩码：保留前 4 位与后 4 位，中间固定 4 个星号（不暴露真实长度）。
     * 太短的一律只给 "****"，避免把短密钥整个还原出来。
     */
    private String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 更新时把 apiKey 归一化成「保留原值」。
     * <p>
     * 前端编辑表单不再回填原密钥，所以：
     * <ul>
     *   <li>留空 —— 用户没打算改密钥，返回 null，MyBatis-Plus 会跳过该字段；</li>
     *   <li>填的正好是掩码（`sk-c****xYz`）—— 说明某处把详情里的掩码又提交了回来，
     *       同样视为"不修改"，否则会把真密钥写成星号，把用户通道弄坏。</li>
     * </ul>
     */
    private void maskToNull(AiClientApi aiClientApi) {
        String apiKey = aiClientApi.getApiKey();
        if (!StringUtils.hasText(apiKey) || apiKey.contains("****")) {
            aiClientApi.setApiKey(null);
        }
    }

}
