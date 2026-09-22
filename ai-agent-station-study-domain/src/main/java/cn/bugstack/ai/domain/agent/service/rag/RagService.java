package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.domain.agent.adapter.repository.IAgentRepository;
import cn.bugstack.ai.domain.agent.model.valobj.AiRagOrderVO;
import cn.bugstack.ai.domain.agent.service.IRagService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void storeRagFile(String name, String tag, List<MultipartFile> files) {
        for (MultipartFile file : files) {
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
                String ragId = UUID.randomUUID().toString();

                // 添加知识库标签和元数据
                int chunkTotal = documentList.size();
                for (int i = 0; i < chunkTotal; i++) {
                    Document doc = documentList.get(i);
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("knowledge", tag);
                    metadata.put("ragId", ragId);
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

                // 存储知识库文件
                vectorStore.accept(documentList);

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
                log.error("知识库文件上传失败: name={}, tag={}, fileName={}",
                        name, tag, file.getOriginalFilename(), e);
                throw new RuntimeException("知识库文件上传失败", e);
            }
        }
    }

    /**
     * 计算文件哈希
     * @param file 文件
     * @return 文件哈希
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
            log.error("计算文件哈希失败", e);
            return String.valueOf(System.currentTimeMillis());
        }
    }

}
