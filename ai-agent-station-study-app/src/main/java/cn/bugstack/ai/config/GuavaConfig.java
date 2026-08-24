package cn.bugstack.ai.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Guava 缓存配置
 */
@Configuration
public class GuavaConfig {

    @Bean(name = "cache")
    /**
     * 配置 Guava 缓存，过期时间为 3 秒
     */
    public Cache<String, String> cache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .build();
    }

}
