package cn.bugstack.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;


/**
 * 模型网关 HTTP 客户端配置
 * <p>
 * 这一层是「真正断开连接的一刀」，容易被漏掉，所以单独说明：
 * 常识上以为 Future.get(timeout) 超时就是「调用被取消了」，实际不是 —— Java 无法中断阻塞在 socket 读的线程，
 * 超时只是调用方不再等待，那次请求的线程要一直挂到 socket read timeout 才释放。
 * 因此节点超时 + HTTP 超时必须同时配置：
 * <ul>
 *   <li>read-timeout 要 &gt;= 最长的那个节点超时（否则正常长调用会被底层先掐断，节点超时永远轮不到生效）</li>
 *   <li>read-timeout 又不能太大（否则接口假死后线程要挂满 read-timeout 才回收）</li>
 * </ul>
 *
 * @author bugstack.cn
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NodeGuardProperties.class)
public class AiHttpClientConfig {

    public static final String MODEL_REST_CLIENT_BUILDER = "aiModelRestClientBuilder";

    @Bean(name = MODEL_REST_CLIENT_BUILDER)
    @ConditionalOnMissingBean(name = MODEL_REST_CLIENT_BUILDER)
    public RestClient.Builder aiModelRestClientBuilder(NodeGuardProperties properties) {
        long connectTimeoutMs = properties.getModelHttp() == null || properties.getModelHttp().getConnectTimeoutMs() == null
                ? 10_000L : properties.getModelHttp().getConnectTimeoutMs();
        long readTimeoutMs = properties.getModelHttp() == null || properties.getModelHttp().getReadTimeoutMs() == null
                ? 120_000L : properties.getModelHttp().getReadTimeoutMs();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Math.min(connectTimeoutMs, Integer.MAX_VALUE));
        requestFactory.setReadTimeout((int) Math.min(readTimeoutMs, Integer.MAX_VALUE));

        log.info("模型网关 HTTP 超时配置：connect={}ms, read={}ms", connectTimeoutMs, readTimeoutMs);
        // 不设 baseUrl：OpenAiApi 装配时会用自己的 baseUrl，这里只负责请求工厂（超时）
        return RestClient.builder().requestFactory(requestFactory);
    }
}
