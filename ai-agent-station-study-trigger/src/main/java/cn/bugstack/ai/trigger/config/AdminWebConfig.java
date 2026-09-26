package cn.bugstack.ai.trigger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端接口鉴权配置
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    /**
     * 是否把 {@code /api/v1/agent/**} 也纳入鉴权。
     * <p>
     * <b>2026-09-26 起默认改为 true</b>：数据要按用户隔离（公共资源 = owner_id 为空，
     * 私有资源 = 本人），而对话链路必须先知道"当前是谁"才谈得上隔离；
     * 前端 zhishu-ui 的对话页与 SSE 请求本来就带 token，因此不会再出现演示被中断的问题。
     * <p>
     * 仍保留该开关：万一需要临时开放（例如外部巡检、压测），可显式置为 false。
     */
    @Value("${xfg.ai.auth.protect-agent-api:true}")
    private boolean protectAgentApi;

    public AdminWebConfig(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> patterns = new ArrayList<>(List.of("/api/v1/admin/**", "/api/v1/rag/**"));
        if (protectAgentApi) {
            patterns.add("/api/v1/agent/**");
        }

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns(patterns)
                .excludePathPatterns(
                        "/api/v1/admin/admin-user/login",
                        "/api/v1/admin/admin-user/validate-login",
                        // 自助注册必须放行：注册时还没有 token
                        "/api/v1/admin/admin-user/register");
    }
}
