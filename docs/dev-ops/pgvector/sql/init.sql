-- =============================================================================
-- PostgreSQL / pgvector 初始化脚本
--
-- ⚠️ 本文件只在数据目录为空时执行（docker-entrypoint-initdb.d 机制）。
--    已有数据卷的情况下不会重复执行，改动本文件不影响现存数据。
--
-- 本文件内容按现存库（ai-rag-knowledge）的真实结构还原，可用以下命令核对：
--   docker exec vector_db psql -U postgres -d ai-rag-knowledge -c "\d vector_store_openai"
--
-- 维度说明：三张表的维度不一致是历史沿革，不是笔误 ——
--   vector_store / store_openai 建表时沿用 Spring AI 默认的 1536，
--   vector_store_openai 后改为 512，与配置项 dimensions=512 对应
--   （模型为阿里云百炼 text-embedding-v3，支持 1024/768/512/256/128/64）。
--   维度与配置不一致时插入会报 "expected 512 dimensions, not N"。
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- Spring AI PgVectorStore 的默认表名（本项目未使用，保留以兼容框架默认行为）
CREATE TABLE IF NOT EXISTS vector_store (
    id        uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    content   text NOT NULL,
    metadata  jsonb,
    embedding vector(1536)
);

-- 历史遗留表（本项目未使用）
CREATE TABLE IF NOT EXISTS store_openai (
    id        uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    content   text NOT NULL,
    metadata  jsonb,
    embedding vector(1536)
);

-- 本项目实际使用的向量表（AiAgentConfig 中 vectorTableName("vector_store_openai")）
CREATE TABLE IF NOT EXISTS vector_store_openai (
    id        uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    content   text NOT NULL,
    metadata  jsonb,
    embedding vector(512)
);

-- 向量近邻索引：必须显式创建。
-- 原因：AiAgentConfig 手写的 PgVectorStore Bean 未设置 initializeSchema
--   （Spring AI 默认 false），框架不会自动建索引 → 退化为全表扫描 KNN。
-- opclass 必须与查询算子匹配：cosine 距离（<=>）配 vector_cosine_ops，
--   若误用 L2（<->）或内积（<#>）的 opclass，规划器只能回退顺序扫描。
-- 索引名刻意沿用 Spring AI 的命名规则（表名 + "_index"），
--   这样将来若打开 initializeSchema，其 CREATE INDEX IF NOT EXISTS 会命中同名索引，不会重复创建。
-- 调优：hnsw.ef_search 默认 40，召回不足时可 SET hnsw.ef_search = 100;（会话级）。
CREATE INDEX IF NOT EXISTS vector_store_openai_index
    ON public.vector_store_openai
    USING hnsw (embedding vector_cosine_ops);
