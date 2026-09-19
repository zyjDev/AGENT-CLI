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
     * 默认关闭：对话链路是课程演示页（{@code docs/dev-ops/nginx/html/index.html}）唯一的入口，
     * 而该页面调用时不带 token，一旦强制鉴权会直接打断演示。
     * <p>
     * 生产环境应置为 {@code true} —— 否则任何人都能匿名调用 Agent，白耗 LLM 额度。
     */
    @Value("${xfg.ai.auth.protect-agent-api:false}")
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
                        "/api/v1/admin/admin-user/validate-login");
    }
}
