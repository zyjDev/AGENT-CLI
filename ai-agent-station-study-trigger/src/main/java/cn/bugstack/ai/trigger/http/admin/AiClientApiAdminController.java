package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiClientApiAdminService;
import cn.bugstack.ai.api.dto.AiClientApiQueryRequestDTO;
import cn.bugstack.ai.api.dto.AiClientApiRequestDTO;
import cn.bugstack.ai.api.dto.AiClientApiResponseDTO;
import cn.bugstack.ai.api.response.PageResult;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.infrastructure.dao.IAiClientApiDao;
import cn.bugstack.ai.infrastructure.dao.po.AiClientApi;
import cn.bugstack.ai.trigger.support.AdminPageSupport;
import cn.bugstack.ai.trigger.support.OwnerGuard;
import cn.bugstack.ai.types.common.UrlSafetyGuard;
import cn.bugstack.ai.types.context.UserContext;
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
 * AI客户端API配置管理控制器
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

            // base_url 是「装配后由服务端主动去请求的地址」，必须过 SSRF 校验（2026-09-30 加固）：
            // 普通用户填内网 / 链路本地地址（如 http://169.254.169.254/... 云元数据），
            // 就等于借服务端的网络位置去访问内网。管理员维护平台默认资源时放行内网。
            String baseUrlProblem = UrlSafetyGuard.checkHttpUrl(request.getBaseUrl(), UserContext.isAdmin());
            if (baseUrlProblem != null) {
                log.warn("拒绝创建API通道：baseUrl={}, userId={}, reason={}",
                        request.getBaseUrl(), UserContext.userId(), baseUrlProblem);
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("base_url 不合法：" + baseUrlProblem)
                        .data(false)
                        .build();
            }

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

            // 部分更新：只传 apiKey 时 base_url 沿用库中原值，所以按「生效后的 base_url」校验
            String baseUrlProblem = UrlSafetyGuard.checkHttpUrl(
                    effectiveValue(request.getBaseUrl(), existing.getBaseUrl()), UserContext.isAdmin());
            if (baseUrlProblem != null) {
                log.warn("拒绝更新API通道：apiId={}, userId={}, reason={}",
                        existing.getApiId(), UserContext.userId(), baseUrlProblem);
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("base_url 不合法：" + baseUrlProblem)
                        .data(false)
                        .build();
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

            // 同上：按生效后的 base_url 做 SSRF 校验
            String baseUrlProblem = UrlSafetyGuard.checkHttpUrl(
                    effectiveValue(request.getBaseUrl(), existing.getBaseUrl()), UserContext.isAdmin());
            if (baseUrlProblem != null) {
                log.warn("拒绝更新API通道：apiId={}, userId={}, reason={}",
                        aiClientApi.getApiId(), UserContext.userId(), baseUrlProblem);
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("base_url 不合法：" + baseUrlProblem)
                        .data(false)
                        .build();
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

            // 读侧归属校验：queryByApiId 刻意不带归属过滤（跨线程装配链路需要），
            // 请求线程取数返回给用户前必须补校验；他人私有 → 视同不存在，走下面的「未找到」分支
            if (aiClientApi != null && !OwnerGuard.readable(aiClientApi.getOwnerId())) {
                aiClientApi = null;
            }
            
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
    public Response<PageResult<AiClientApiResponseDTO>> queryAiClientApiList(@RequestBody AiClientApiQueryRequestDTO request) {
        try {
            log.info("分页查询AI客户端API配置列表请求：{}", request);

            // 条件与归属过滤一起下推 SQL，total 由 count 语句得出（真正的物理分页）
            IPage<AiClientApi> page = aiClientApiDao.queryPage(
                    AdminPageSupport.page(request.getPageNum(), request.getPageSize()),
                    request.getApiId(), request.getBaseUrl(), request.getStatus());

            return Response.<PageResult<AiClientApiResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(AdminPageSupport.of(page, this::convertToAiClientApiResponseDTO))
                    .build();
        } catch (Exception e) {
            log.error("分页查询AI客户端API配置列表失败", e);
            return Response.<PageResult<AiClientApiResponseDTO>>builder()
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
     * 部分更新语义取值：请求带了新值就用新值，没带（null / 空白）就沿用库中原值 ——
     * 与 {@link #convertToAiClientApi} 的「只更新非 null 字段」保持一致。
     * <p>
     * SSRF 校验必须基于「生效后的 base_url」：否则只要不传 base_url 就能绕过校验，
     * 却仍然把库里的地址交给装配链路去请求。
     */
    private String effectiveValue(String fromRequest, String existing) {
        return StringUtils.hasText(fromRequest) ? fromRequest : existing;
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
