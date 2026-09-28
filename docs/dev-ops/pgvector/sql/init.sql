-- ==============================================================================
-- init.sql —— pgvector 基线初始化脚本（数据库：ai-rag-knowledge）
-- ==============================================================================
-- 来源：2026-09-28 从运行中的 pgvector:v0.5.0（PostgreSQL 15.4）实例导出：
--       docker exec vector_db pg_dump -s -U postgres ai-rag-knowledge
--       仅表结构、不含任何数据。本文件在其基础上做了三处**等价**改写：
--       ① 显式建库 + 切库（原因见下）；② 主键内联进 CREATE TABLE；③ 全部补 IF NOT EXISTS。
--
-- 为什么必须显式建库（2026-09-28 修复 P0-2）：
--   两套 compose 的 POSTGRES_DB 取值不一致（springai / ai-rag-knowledge），而应用侧
--   application-*.yml 固定连接 ai-rag-knowledge。只依赖 compose 变量时，全新环境会
--   「库建了、但不是应用要的那个」，表现为连接失败或找不到表。此处建库并 \connect，
--   使本脚本与 compose 变量的取值彻底解耦。
--
-- 执行时机：挂载到 /docker-entrypoint-initdb.d/init.sql，**仅在 PG 数据卷为空时执行一次**。
-- 幂等：全部 IF NOT EXISTS，重复执行安全；**不含任何 DROP / DELETE / TRUNCATE**。
--
-- 现网表说明（共 3 张，本基线只保留第 1 张）：
--   vector_store_openai —— 当前使用。512 维（智谱 embedding-3），hnsw 余弦索引。
--   vector_store        —— 历史遗留：1536 维（OpenAI 维度），无索引，当前代码未引用。
--   store_openai        —— 历史遗留：同 vector_store。
--   两张遗留表刻意**不纳入基线**，避免全新环境凭空多出无用表。如确需保留，取消注释：
--     CREATE TABLE IF NOT EXISTS public.vector_store (
--         id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
--         content text NOT NULL, metadata jsonb, embedding public.vector(1536));
--     CREATE TABLE IF NOT EXISTS public.store_openai (
--         id uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
--         content text NOT NULL, metadata jsonb, embedding public.vector(1536));
--
-- 结构变更后重新导出：
--   docker exec vector_db pg_dump -s -U postgres ai-rag-knowledge > docs/dev-ops/pgvector/sql/init.sql
-- ==============================================================================

-- 1) 建库（幂等）并切入目标库 ---------------------------------------------------
SELECT 'CREATE DATABASE "ai-rag-knowledge"'
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ai-rag-knowledge')\gexec

\connect "ai-rag-knowledge"

SET search_path = public;

-- 2) 向量扩展 -------------------------------------------------------------------
-- pgvector 提供 vector 类型与 hnsw / ivfflat 索引；缺失时下面的建表会直接失败。
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

-- 3) 向量表（当前使用）----------------------------------------------------------
-- 表名与 Spring AI PgVectorStore 的默认表名一致。应用侧 initialize-schema 默认为 true，
-- 即使本脚本缺失也会在启动时自建 —— 此处提供基线，是为了让全新环境的结构与现网一致，
-- 并保证 extension 与索引一定存在。
CREATE TABLE IF NOT EXISTS public.vector_store_openai (
    id        uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    content   text NOT NULL,
    metadata  jsonb,
    embedding public.vector(512)
);

-- 读写路径依赖此索引；缺失时向量检索退化为全表扫描（慢但不报错，容易被长期忽略）。
CREATE INDEX IF NOT EXISTS vector_store_openai_index
    ON public.vector_store_openai
    USING hnsw (embedding public.vector_cosine_ops);
