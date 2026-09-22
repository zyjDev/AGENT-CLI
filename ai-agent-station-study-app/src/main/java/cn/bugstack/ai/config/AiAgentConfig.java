package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.agent.service.rag.splitter.OverlapTokenTextSplitter;
import org.springframework.ai.document.MetadataMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AiAgentConfig {

    @Bean("vectorStore")
    public PgVectorStore pgVectorStore(
                                       @Value("${spring.ai.openai.embedding.base-url:https://api.openai.com/v1}") String embeddingBaseUrl,
                                       @Value("${spring.ai.openai.embedding.api-key:${spring.ai.openai.api-key}}") String embeddingApiKey,
                                       @Value("${spring.ai.openai.embedding.options.model:text-embedding-ada-002}") String embeddingModel,
                                       @Value("${spring.ai.openai.embedding.options.dimensions:512}") int embeddingDimensions,
                                       @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        OpenAiApi embeddingApi = OpenAiApi.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(embeddingApiKey)
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingModel)
                .dimensions(embeddingDimensions)
                .build();
        OpenAiEmbeddingModel openAiEmbeddingModel = new OpenAiEmbeddingModel(embeddingApi, MetadataMode.EMBED, options);
        return PgVectorStore.builder(jdbcTemplate, openAiEmbeddingModel)
                .vectorTableName("vector_store_openai")
                .build();
    }

    /**
     * 文档切分器：800 token/块 + 相邻块重叠 100 token。
     * <p>
     * 为什么不用原生 {@link TokenTextSplitter}：它的构造器只有
     * chunkSize / minChunkSizeChars / minChunkLengthToEmbed / maxNumChunks / keepSeparator，
     * <b>没有 overlap 参数</b> —— 框架不提供重叠能力，配不出来。
     * 后果是「一个语义单元正好被切在两块之间」时，两块各自都不完整，谁都召不回完整答案。
     * <p>
     * 100 token 的由来（实测 126 篇文档 / 540 块，cl100k_base 编码）：
     * 原始块 p50 = 793 token（约 3600 字符），叠加后 p50 = 899 token、max ≈ 1000 token，
     * 全量 token 增约 13.6%，仍远低于 text-embedding-v3 的 8192 token 输入上限。
     * <p>
     * ⚠️ 切换切分器后<b>存量 chunk 不会自动重切</b>，必须重新上传知识库文档，
     * 否则新旧两种切法会混在同一张向量表里（同一文档的块边界不一致）。
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new OverlapTokenTextSplitter(100);
    }

}
