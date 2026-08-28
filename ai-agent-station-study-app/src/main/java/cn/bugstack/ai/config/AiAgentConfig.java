package cn.bugstack.ai.config;

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

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

}
