package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.valobj.AiRagOrderVO;
import cn.bugstack.ai.domain.agent.service.IRagService;
import cn.bugstack.ai.types.common.OwnerScope;
import cn.bugstack.ai.types.context.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import cn.bugstack.ai.types.common.SnowflakeId;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库服务
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/10/4 09:12
 */
@Slf4j
@Service
public class RagService implements IRagService {

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private PgVectorStore vectorStore;

    @Resource
    private IAgentRepository repository;

    /**
     * 上传知识库文件（新增知识库）。
     *
     * <p>⚠️ **故意不加 `@Transactional`**（P1-5）：本方法同时写 MySQL 与 pgvector 两个库，
     * 而 pgvector 走独立的 `pgVectorJdbcTemplate`，与 MySQL 不在同一个事务管理器里 ——
     * 跨库本来就不可能原子。原实现在方法上挂 `@Transactional`，只带来两个副作用：
     * <ul>
     *   <li>「写向量」（逐批网络 IO、耗时最长）被包进 MySQL 事务，长时间占着连接与行锁；</li>
     *   <li>回滚只回滚 MySQL，已写入的向量照样留着 —— 变成「元数据被回滚、向量还在」的错配，
     *       比不回滚更糟。</li>
     * </ul>
     * 去掉之后，每个文件的元数据插入各自独立提交，与实际向量写入进度对齐；
     * 若元数据插入失败，则主动删掉本次刚写入的向量（见 catch 中的补偿）。
     */
    @Override
    public void storeRagFile(String name, String tag, List<MultipartFile> files) {
        for (MultipartFile file : files) {
            // 这两个变量声明在 try 之外：失败补偿要靠它们定位「本次刚写入的向量」
            String ragId = null;
            boolean vectorsWritten = false;
            try {
                TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
                List<Document> documentList = tokenTextSplitter.apply(documentReader.get());

                // 计算文件哈希
                String fileHash = calculateFileHash(file);

                // ⚠️ 必须先确定 ragId，再写入向量。
                //    更新链路的删除条件是 `knowledge == tag && ragId == ragId`
                //    （见 RagUpdateServiceImpl.deleteOldDocuments），而原实现写 chunk 时只带 knowledge、
                //    不带 ragId —— 导致「初次上传」这批 chunk 永远匹配不到删除条件，后续更新只能不断叠加
                //    新知识，旧知识永久残留在向量库中（向量检索会同时召回新旧两版内容）。
                //    ragId 由这里生成并显式传给 createTagOrder（其内部已支持使用调用方传入的值）。
                // ragId 统一改用雪花（纯数字字符串，同一格式便于排查与排序）
                ragId = SnowflakeId.nextIdStr();

                // 添加知识库标签和元数据
                int chunkTotal = documentList.size();
                for (int i = 0; i < chunkTotal; i++) {
                    Document doc = documentList.get(i);
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("knowledge", tag);
                    metadata.put("ragId", ragId);
        // 归属：知识库由当前登录用户创建（上传是同步请求线程，上下文可用）；
        //      公共资源（无登录上下文）统一写 __public__ —— 向量 metadata 没有 NULL 语义
        metadata.put(OwnerScope.VECTOR_OWNER_FIELD, OwnerScope.vectorOwnerOf(UserContext.userId()));
                    metadata.put("version", "1");
                    metadata.put("lastUpdateTime", LocalDateTime.now().toString());
                    metadata.put("fileHash", fileHash);
                    metadata.put("updateReason", "初始上传");
                    // chunk 在文档内的序号（从 0 开始）。
                    // 重叠解决的是「答案被切在边界上」；序号解决的是「答案分散在相邻块」——
                    // 有了序号，检索侧才能做「邻居扩展」（命中第 N 块时把 N±1 一起带回）。
                    metadata.put("chunkIndex", i);
                    metadata.put("chunkTotal", chunkTotal);
                    doc.getMetadata().putAll(metadata);
                }

                // 存储知识库文件（先写向量、后写元数据：后者失败时才有明确的补偿对象）
                vectorStore.accept(documentList);
                vectorsWritten = true;

                // 存储到数据库
                AiRagOrderVO aiRagOrderVO = new AiRagOrderVO();
                aiRagOrderVO.setRagId(ragId);
                aiRagOrderVO.setRagName(name);
                aiRagOrderVO.setKnowledgeTag(tag);
                aiRagOrderVO.setVersion(1);
                aiRagOrderVO.setFileHash(fileHash);
                aiRagOrderVO.setUpdateReason("初始上传");
                repository.createTagOrder(aiRagOrderVO);

                log.info("知识库文件上传成功: name={}, tag={}, ragId={}, fileHash={}, documentCount={}",
                        name, tag, ragId, fileHash, documentList.size());

            } catch (Exception e) {
                if (vectorsWritten) {
                    // 补偿：向量已落库、元数据没写上 → 删掉这批向量。
                    // 否则留下的是「检索能命中、管理端看不到、也没有任何入口能删」的孤儿向量。
                    deleteVectorsQuietly(tag, ragId);
                }
                log.error("知识库文件上传失败: name={}, tag={}, fileName={}",
                        name, tag, file.getOriginalFilename(), e);
                throw new RuntimeException("知识库文件上传失败", e);
            }
        }
    }

    /**
     * 计算文件哈希
     *
     * <p>失败即抛（P1-6），与 {@code RagUpdateServiceImpl#calculateFileHash} 保持同一语义：
     * 这个哈希是「文件内容是否变化」的**唯一判据**，算不出来说明输入不可用，
     * 必须失败而不是编造一个值。原实现返回 {@code System.currentTimeMillis()}，后果是：
     * <ul>
     *   <li>该值每次调用都不同 → 「文件没变」被永久判定为「变了」，每次上传都全量重新切分 +
     *       重新向量化（产生重复点、失去幂等性）；</li>
     *   <li>降级是静默的 —— 调用方拿到的仍是一个「合法」哈希字符串，只有一行日志，
     *       运维永远不会注意到。</li>
     * </ul>
     *
     * @param file 文件
     * @return 文件哈希（MD5 十六进制）
     * @throws IllegalStateException 读取文件内容或摘要算法不可用时
     */
    private String calculateFileHash(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(file.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算文件哈希失败: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * 静默删除指定 (knowledge, ragId) 的向量 —— 仅用于写入失败后的补偿。
     *
     * <p>删不掉也只记日志：此时要紧的是把原始异常抛给调用方，补偿动作不能把它盖住。
     * 真删不掉时靠这行 error 日志人工清理。
     */
    private void deleteVectorsQuietly(String tag, String ragId) {
        if (ragId == null) {
            return;
        }
        try {
            org.springframework.ai.vectorstore.filter.FilterExpressionTextParser parser = new org.springframework.ai.vectorstore.filter.FilterExpressionTextParser();
            String filterExpr = String.format("knowledge == '%s' && ragId == '%s'", tag, ragId);
            vectorStore.delete(parser.parse(filterExpr));
            log.warn("知识库上传失败，已回滚本次写入的向量: tag={}, ragId={}", tag, ragId);
        } catch (Exception cleanupError) {
            log.error("回滚本次写入的向量失败，需人工清理: tag={}, ragId={}", tag, ragId, cleanupError);
        }
    }

}
