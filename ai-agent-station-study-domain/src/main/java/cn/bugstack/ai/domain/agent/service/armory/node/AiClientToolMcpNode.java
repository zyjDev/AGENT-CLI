package cn.bugstack.ai.domain.agent.service.armory.node;

import cn.bugstack.ai.domain.agent.model.entity.ArmoryCommandEntity;
import cn.bugstack.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import cn.bugstack.ai.domain.agent.service.armory.node.factory.DefaultArmoryStrategyFactory;
import cn.bugstack.ai.domain.agent.service.support.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP客户端配置节点
 *
 * 2025/7/5 12:48
 */
@Slf4j
@Service
public class AiClientToolMcpNode extends AbstractArmorySupport {

    @Resource
    private AiClientModelNode aiClientModelNode;

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 构建节点，Tool MCP 工具配置{}", JSON.toJSONString(requestParameter));

        List<AiClientToolMcpVO> aiClientToolMcpList = dynamicContext.getValue(dataName());

        if (aiClientToolMcpList == null || aiClientToolMcpList.isEmpty()) {
            log.warn("没有需要被初始化的 ai client tool mcp");
            return router(requestParameter, dynamicContext);
        }

        for (AiClientToolMcpVO mcpVO : aiClientToolMcpList) {
            try {
                // 创建 MCP 服务
                McpSyncClient mcpSyncClient = createMcpSyncClient(mcpVO);
                // 注册 MCP 对象
                registerBean(beanName(mcpVO.getToolMcpId()), McpSyncClient.class, mcpSyncClient);
            } catch (Exception e) {
                log.warn("MCP初始化失败，跳过: mcpId={}, mcpName={}, error={}", mcpVO.getToolMcpId(), mcpVO.getToolMcpName(), e.getMessage());
            }
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return aiClientModelNode;
    }

    @Override
    protected String beanName(String beanId) {
        return AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getBeanName(beanId);
    }

    @Override
    protected String dataName() {
        return AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getDataName();
    }

    /**
     * MCP 单次请求超时上限（分钟）。
     * <p>
     * 背景：ai_client_tool_mcp.request_timeout 的单位是分钟，缺省也按分钟兜底，
     * 意味着一个工具可以合法地卡住整条链路好几分钟 —— 而耗 Capacity 的正是 Step4 这类会调工具的节点。
     * 这里保留原有字段语义（避免改配置语义导致存量数据失效），但加上硬上限：
     * 超过上限的配置会被钳到上限值，并且明确打日志。
     */
    private static final int MAX_MCP_REQUEST_TIMEOUT_MINUTES = 2;

    private McpSyncClient createMcpSyncClient(AiClientToolMcpVO aiClientToolMcpVO) {
        String transportType = aiClientToolMcpVO.getTransportType();
        int configured = aiClientToolMcpVO.getRequestTimeout() != null ? aiClientToolMcpVO.getRequestTimeout() : 5;
        int requestTimeout = Math.min(configured, MAX_MCP_REQUEST_TIMEOUT_MINUTES);
        if (requestTimeout != configured) {
            log.warn("⚠️ MCP 请求超时配置 {}分钟 超过上限，已钳制为 {} 分钟：toolMcpId={}",
                    configured, requestTimeout, aiClientToolMcpVO.getToolMcpId());
        }

        switch (transportType) {
            case "sse" -> {
                AiClientToolMcpVO.TransportConfigSse transportConfigSse = aiClientToolMcpVO.getTransportConfigSse();
                // http://127.0.0.1:9999/sse?apikey=DElk89iu8Ehhnbu
                String originalBaseUri = transportConfigSse.getBaseUri();
                String baseUri;
                String sseEndpoint;

                int queryParamStartIndex = originalBaseUri.indexOf("sse");
                if (queryParamStartIndex != -1) {
                    baseUri = originalBaseUri.substring(0, queryParamStartIndex - 1);
                    sseEndpoint = originalBaseUri.substring(queryParamStartIndex - 1);
                } else {
                    baseUri = originalBaseUri;
                    sseEndpoint = transportConfigSse.getSseEndpoint();
                }

                sseEndpoint = StringUtils.isBlank(sseEndpoint) ? "/sse" : sseEndpoint;

                HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                        .builder(baseUri) // 使用截取后的 baseUri
                        .sseEndpoint(sseEndpoint) // 使用截取或默认的 sseEndpoint
                        .build();

                McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(requestTimeout)).build();
                var init_sse = mcpSyncClient.initialize();

                log.info("Tool SSE MCP Initialized {}", init_sse);
                return mcpSyncClient;
            }
            case "stdio" -> {
                AiClientToolMcpVO.TransportConfigStdio transportConfigStdio = aiClientToolMcpVO.getTransportConfigStdio();
                Map<String, AiClientToolMcpVO.TransportConfigStdio.Stdio> stdioMap = transportConfigStdio.getStdio();
                AiClientToolMcpVO.TransportConfigStdio.Stdio stdio = stdioMap.get(aiClientToolMcpVO.getToolMcpName());

                // https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem
                var stdioParams = ServerParameters.builder(stdio.getCommand())
                        .args(stdio.getArgs())
                        .env(stdio.getEnv())
                        .build();

                // MCP SDK 0.18 起 StdioClientTransport 需要显式传入 McpJsonMapper：
                // 由 McpJsonDefaults.getMapper() 通过 ServiceLoader 解析到 mcp-json-jackson2 提供的
                // JacksonMcpJsonMapper（其 get() 即 new JacksonMcpJsonMapper(new ObjectMapper())）。
                var mcpClient = McpClient.sync(new StdioClientTransport(stdioParams, McpJsonDefaults.getMapper()))
                        .requestTimeout(Duration.ofMinutes(requestTimeout)).build();
                var init_stdio = mcpClient.initialize();

                log.info("Tool Stdio MCP Initialized {}", init_stdio);
                return mcpClient;
            }
        }

        throw new RuntimeException("err! transportType " + transportType + " not exist!");
    }

}
