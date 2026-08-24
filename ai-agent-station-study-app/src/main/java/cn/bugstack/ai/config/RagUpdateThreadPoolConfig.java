package cn.bugstack.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 知识库更新线程池配置 异步
 * @description 知识库更新线程池配置 异步
 */
@Configuration
@EnableAsync
public class RagUpdateThreadPoolConfig {

    /**
     * 知识库更新线程池
     * @return 线程池执行器
     */
    @Bean("ragUpdateExecutor")
    public Executor ragUpdateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(5);
        
        // 最大线程数
        executor.setMaxPoolSize(10);
        
        // 队列容量
        executor.setQueueCapacity(100);
        
        // 线程名前缀
        executor.setThreadNamePrefix("rag-update-");
        
        // 线程存活时间（秒）
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：CallerRunsPolicy - 由调用线程处理该任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务完成后关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(30);
        
        // 初始化
        executor.initialize();
        
        return executor;
    }

}
