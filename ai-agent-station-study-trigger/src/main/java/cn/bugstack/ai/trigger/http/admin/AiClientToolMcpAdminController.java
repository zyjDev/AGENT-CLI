package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.IAiClientToolMcpAdminService;
import cn.bugstack.ai.api.dto.AiClientToolMcpQueryRequestDTO;
import cn.bugstack.ai.api.dto.AiClientToolMcpRequestDTO;
import cn.bugstack.ai.api.dto.AiClientToolMcpResponseDTO;
import cn.bugstack.ai.api.response.PageResult;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.infrastructure.dao.IAiClientToolMcpDao;
import cn.bugstack.ai.infrastructure.dao.po.AiClientToolMcp;
import cn.bugstack.ai.trigger.support.AdminPageSupport;
import cn.bugstack.ai.trigger.support.OwnerGuard;
import cn.bugstack.ai.types.common.UrlSafetyGuard;
import cn.bugstack.ai.types.context.UserContext;
import cn.bugstack.ai.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
 * MCP客户端配置管理控制器
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-tool-mcp")
// 跨域统一收敛到 WebCorsConfig 的白名单（原先此处是 origins = "*"，等于对任意站点放开）
public class AiClientToolMcpAdminController implements IAiClientToolMcpAdminService {

    @Resource
    private IAiClientToolMcpDao aiClientToolMcpDao;

    @Override
    @PostMapping("/create")
    public Response<Boolean> createAiClientToolMcp(@RequestBody AiClientToolMcpRequestDTO request) {
        try {
            log.info("创建MCP客户端配置请求：{}", request);

            // 传输通道安全校验（2026-09-30 加固）：
            //   stdio 会在服务器上 fork/exec 请求里的 command —— 等同把 shell 交出去，仅管理员可配；
            //   sse 的 baseUri 是「服务端主动去连的地址」，必须过 SSRF 校验。
            String transportProblem = validateTransport(request.getTransportType(), request.getTransportConfig());
            if (transportProblem != null) {
                log.warn("拒绝创建MCP配置：transportType={}, userId={}, reason={}",
                        request.getTransportType(), UserContext.userId(), transportProblem);
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(transportProblem)
                        .data(false)
                        .build();
            }

            // DTO转PO
            AiClientToolMcp aiClientToolMcp = convertToAiClientToolMcp(request);
            aiClientToolMcp.setCreateTime(LocalDateTime.now());
            aiClientToolMcp.setUpdateTime(LocalDateTime.now());
            
            // 归属：普通用户建的就是他自己的（管理员建的留空 = 平台默认，人人可用）
            OwnerGuard.stampOwnerOnCreate(aiClientToolMcp);

            int result = aiClientToolMcpDao.insert(aiClientToolMcp);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("创建MCP客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientToolMcpById(@RequestBody AiClientToolMcpRequestDTO request) {
        try {
            log.info("根据ID更新MCP客户端配置请求：{}", request);
            
            if (request.getId() == null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("ID不能为空")
                        .data(false)
                        .build();
            }
            
            // DTO转PO
            AiClientToolMcp aiClientToolMcp = convertToAiClientToolMcp(request);
            aiClientToolMcp.setUpdateTime(LocalDateTime.now());
            
            // 写权限：本人资源本人可改；公共资源仅管理员；他人私有资源不可改
            AiClientToolMcp existing = aiClientToolMcpDao.queryById(request.getId());
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("MCP 工具");
            }

            // 同样要过传输通道校验：更新是「部分更新」（未传字段沿用库中原值），
            // 只改 transportConfig 不改 transportType 就能换掉 sse 的 baseUri，所以按生效值校验
            String transportProblem = validateTransport(
                    effectiveValue(request.getTransportType(), existing.getTransportType()),
                    effectiveValue(request.getTransportConfig(), existing.getTransportConfig()));
            if (transportProblem != null) {
                log.warn("拒绝更新MCP配置：mcpId={}, userId={}, reason={}",
                        existing.getMcpId(), UserContext.userId(), transportProblem);
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(transportProblem)
                        .data(false)
                        .build();
            }

            int result = aiClientToolMcpDao.updateById(aiClientToolMcp);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID更新MCP客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-mcp-id")
    public Response<Boolean> updateAiClientToolMcpByMcpId(@RequestBody AiClientToolMcpRequestDTO request) {
        try {
            log.info("根据MCP ID更新MCP客户端配置请求：{}", request);
            
            if (!StringUtils.hasText(request.getMcpId())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("MCP ID不能为空")
                        .data(false)
                        .build();
            }
            
            // 写权限：updateByMcpId 的 Wrapper 只按 mcp_id 定位、不带归属条件，
            // 不补这道守卫，普通用户就能凭一个 mcpId 改掉他人私有 / 平台默认的 MCP 配置
            AiClientToolMcp existing = aiClientToolMcpDao.queryByMcpId(request.getMcpId());
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("MCP 工具");
            }

            // 与 update-by-id 同一套：按「更新后生效的」传输配置校验
            String transportProblem = validateTransport(
                    effectiveValue(request.getTransportType(), existing.getTransportType()),
                    effectiveValue(request.getTransportConfig(), existing.getTransportConfig()));
            if (transportProblem != null) {
                log.warn("拒绝更新MCP配置：mcpId={}, userId={}, reason={}",
                        request.getMcpId(), UserContext.userId(), transportProblem);
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(transportProblem)
                        .data(false)
                        .build();
            }

            // DTO转PO
            AiClientToolMcp aiClientToolMcp = convertToAiClientToolMcp(request);
            aiClientToolMcp.setUpdateTime(LocalDateTime.now());
            
            int result = aiClientToolMcpDao.updateByMcpId(aiClientToolMcp);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据MCP ID更新MCP客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientToolMcpById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID删除MCP客户端配置：{}", id);
            
            AiClientToolMcp existing = aiClientToolMcpDao.queryById(id);
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("MCP 工具");
            }
            int result = aiClientToolMcpDao.deleteById(id);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID删除MCP客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-mcp-id/{mcpId}")
    public Response<Boolean> deleteAiClientToolMcpByMcpId(@PathVariable("mcpId") String mcpId) {
        try {
            log.info("根据MCP ID删除MCP客户端配置：{}", mcpId);

            // 写权限：同 update-by-mcp-id，deleteByMcpId 也不带归属条件，必须自己补校验
            AiClientToolMcp existing = aiClientToolMcpDao.queryByMcpId(mcpId);
            if (existing == null || !OwnerGuard.writable(existing.getOwnerId())) {
                return OwnerGuard.deny("MCP 工具");
            }
            int result = aiClientToolMcpDao.deleteByMcpId(mcpId);
            
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据MCP ID删除MCP客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-id/{id}")
    public Response<AiClientToolMcpResponseDTO> queryAiClientToolMcpById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID查询MCP客户端配置：{}", id);
            
            AiClientToolMcp aiClientToolMcp = aiClientToolMcpDao.queryById(id);
            
            if (aiClientToolMcp == null) {
                return Response.<AiClientToolMcpResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(null)
                        .build();
            }
            
            AiClientToolMcpResponseDTO responseDTO = convertToAiClientToolMcpResponseDTO(aiClientToolMcp);
            
            return Response.<AiClientToolMcpResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据ID查询MCP客户端配置失败", e);
            return Response.<AiClientToolMcpResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-mcp-id/{mcpId}")
    public Response<AiClientToolMcpResponseDTO> queryAiClientToolMcpByMcpId(@PathVariable("mcpId") String mcpId) {
        try {
            log.info("根据MCP ID查询MCP客户端配置：{}", mcpId);
            
            AiClientToolMcp aiClientToolMcp = aiClientToolMcpDao.queryByMcpId(mcpId);

            // 读侧归属校验：queryByMcpId 刻意不带归属过滤（跨线程装配链路需要），
            // 请求线程取数返回给用户前必须补校验；他人私有 → 视同不存在，走下面的「未找到」分支
            if (aiClientToolMcp != null && !OwnerGuard.readable(aiClientToolMcp.getOwnerId())) {
                aiClientToolMcp = null;
            }
            
            if (aiClientToolMcp == null) {
                return Response.<AiClientToolMcpResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(null)
                        .build();
            }
            
            AiClientToolMcpResponseDTO responseDTO = convertToAiClientToolMcpResponseDTO(aiClientToolMcp);
            
            return Response.<AiClientToolMcpResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据MCP ID查询MCP客户端配置失败", e);
            return Response.<AiClientToolMcpResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-all")
    public Response<List<AiClientToolMcpResponseDTO>> queryAllAiClientToolMcps() {
        try {
            log.info("查询所有MCP客户端配置");
            
            List<AiClientToolMcp> aiClientToolMcps = aiClientToolMcpDao.queryAll();
            
            List<AiClientToolMcpResponseDTO> responseDTOs = aiClientToolMcps.stream()
                    .map(this::convertToAiClientToolMcpResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有MCP客户端配置失败", e);
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-status/{status}")
    public Response<List<AiClientToolMcpResponseDTO>> queryAiClientToolMcpsByStatus(@PathVariable("status") Integer status) {
        try {
            log.info("根据状态查询MCP客户端配置：{}", status);
            
            List<AiClientToolMcp> aiClientToolMcps = aiClientToolMcpDao.queryByStatus(status);
            
            List<AiClientToolMcpResponseDTO> responseDTOs = aiClientToolMcps.stream()
                    .map(this::convertToAiClientToolMcpResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据状态查询MCP客户端配置失败", e);
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-transport-type/{transportType}")
    public Response<List<AiClientToolMcpResponseDTO>> queryAiClientToolMcpsByTransportType(@PathVariable("transportType") String transportType) {
        try {
            log.info("根据传输类型查询MCP客户端配置：{}", transportType);
            
            List<AiClientToolMcp> aiClientToolMcps = aiClientToolMcpDao.queryByTransportType(transportType);
            
            List<AiClientToolMcpResponseDTO> responseDTOs = aiClientToolMcps.stream()
                    .map(this::convertToAiClientToolMcpResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据传输类型查询MCP客户端配置失败", e);
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-enabled")
    public Response<List<AiClientToolMcpResponseDTO>> queryEnabledAiClientToolMcps() {
        try {
            log.info("查询启用的MCP客户端配置");
            
            List<AiClientToolMcp> aiClientToolMcps = aiClientToolMcpDao.queryEnabledMcps();
            
            List<AiClientToolMcpResponseDTO> responseDTOs = aiClientToolMcps.stream()
                    .map(this::convertToAiClientToolMcpResponseDTO)
                    .collect(Collectors.toList());
            
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询启用的MCP客户端配置失败", e);
            return Response.<List<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/query-list")
    public Response<PageResult<AiClientToolMcpResponseDTO>> queryAiClientToolMcpList(@RequestBody AiClientToolMcpQueryRequestDTO request) {
        try {
            log.info("根据查询条件分页查询MCP客户端配置列表：{}", request);

            // 条件与归属过滤一起下推 SQL，total 由 count 语句得出（真正的物理分页）
            IPage<AiClientToolMcp> page = aiClientToolMcpDao.queryPage(
                    AdminPageSupport.page(request.getPageNum(), request.getPageSize()),
                    request.getMcpId(), request.getMcpName(), request.getTransportType(), request.getStatus());

            return Response.<PageResult<AiClientToolMcpResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(AdminPageSupport.of(page, this::convertToAiClientToolMcpResponseDTO))
                    .build();
        } catch (Exception e) {
            log.error("根据查询条件分页查询MCP客户端配置列表失败", e);
            return Response.<PageResult<AiClientToolMcpResponseDTO>>builder()
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
    private AiClientToolMcp convertToAiClientToolMcp(AiClientToolMcpRequestDTO requestDTO) {
        AiClientToolMcp aiClientToolMcp = new AiClientToolMcp();
        BeanUtils.copyProperties(requestDTO, aiClientToolMcp);
        return aiClientToolMcp;
    }

    /**
     * PO转响应DTO对象
     * @param aiClientToolMcp PO对象
     * @return 响应DTO
     */
    private AiClientToolMcpResponseDTO convertToAiClientToolMcpResponseDTO(AiClientToolMcp aiClientToolMcp) {
        AiClientToolMcpResponseDTO responseDTO = new AiClientToolMcpResponseDTO();
        BeanUtils.copyProperties(aiClientToolMcp, responseDTO);
        return responseDTO;
    }

    /**
     * MCP 传输通道安全校验。
     *
     * ============================ 规则（2026-09-30 上线加固）============================
     * 1. <b>stdio 仅管理员可配</b>：stdio 传输会把 transportConfig 里的 command / args 交给
     *    MCP SDK 在<b>服务器进程</b>上 fork/exec。而本接口此前只校验 token、不校验角色 ——
     *    自助注册的普通用户建一个 stdio MCP 再装配进自己的智能体，就等于在服务器上执行任意命令。
     * 2. <b>sse 的 baseUri 必须过 SSRF 校验</b>：服务端会作为客户端连过去，
     *    内网 / 链路本地（如 169.254.169.254 云元数据）一律拒绝。
     *    管理员维护平台默认资源时放行内网（自建模型网关常常就在内网，属正当场景）。
     * 3. <b>传输类型白名单</b>：装配节点只实现了 sse / stdio，其余值会在装配期抛
     *    {@code transportType not exist}，此处提前拦下。
     * ===================================================================================
     *
     * @param transportType   传输类型
     * @param transportConfig 传输配置（JSON 字符串）
     * @return {@code null} = 通过；否则返回可直接展示给用户的原因
     */
    private String validateTransport(String transportType, String transportConfig) {
        if (!StringUtils.hasText(transportType)) {
            return "传输类型不能为空";
        }

        boolean admin = UserContext.isAdmin();

        if ("stdio".equalsIgnoreCase(transportType)) {
            // stdio = 在服务器上执行本地命令，只允许管理员维护（平台默认 MCP 也归管理员）
            return admin ? null
                    : "stdio 传输会在服务器上执行本地命令，仅管理员可配置；普通用户请使用 sse 传输";
        }

        if (!"sse".equalsIgnoreCase(transportType)) {
            return "不支持的传输类型：" + transportType + "（仅支持 sse / stdio）";
        }

        String baseUri = parseSseBaseUri(transportConfig);
        if (baseUri == null) {
            return "sse 传输配置缺少 baseUri";
        }
        String problem = UrlSafetyGuard.checkHttpUrl(baseUri, admin);
        return problem == null ? null : "sse baseUri 不合法：" + problem;
    }

    /**
     * 从 transportConfig（JSON 字符串）里取 sse 的 baseUri。
     * <p>
     * 这里只做「树解析 + 读一个字符串字段」的只读取值，不绑定具体类型、不写回任何实体
     * （装配链路的字段映射不归本类负责）。
     */
    private String parseSseBaseUri(String transportConfig) {
        if (!StringUtils.hasText(transportConfig)) {
            return null;
        }
        try {
            JSONObject json = JSON.parseObject(transportConfig);
            if (json == null) {
                return null;
            }
            String baseUri = json.getString("baseUri");
            return StringUtils.hasText(baseUri) ? baseUri : null;
        } catch (Exception e) {
            log.warn("MCP transportConfig 不是合法 JSON，无法校验 sse baseUri");
            return null;
        }
    }

    /**
     * 部分更新语义取值：请求带了新值就用新值，没带（null / 空白）就沿用库中原值 ——
     * 与 {@link #convertToAiClientToolMcp} 的「只更新非 null 字段」保持一致。
     */
    private String effectiveValue(String fromRequest, String existing) {
        return StringUtils.hasText(fromRequest) ? fromRequest : existing;
    }

}