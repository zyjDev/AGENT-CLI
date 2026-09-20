package cn.bugstack.ai.trigger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域配置
 * ⚠️ 必须同时删掉各 Controller 上的 {@code @CrossOrigin} —— 方法级/类级跨域配置的优先级高于
 * 全局配置，只要还留着 {@code origins = "*"}，本白名单就不会生效。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    /**
     * 允许跨域的来源白名单，逗号分隔
     */
    @Value("${xfg.ai.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:5173,http://127.0.0.1:5173,http://localhost:8080,http://127.0.0.1:8080,http://localhost:8099,http://127.0.0.1:8099}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
