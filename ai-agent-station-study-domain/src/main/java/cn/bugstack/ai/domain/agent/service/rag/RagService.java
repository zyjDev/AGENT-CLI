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

                // 添加知识库标签和元数据
                documentList.forEach(doc -> {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("knowledge", tag);
                    metadata.put("version", "1");
                    metadata.put("lastUpdateTime", LocalDateTime.now().toString());
                    metadata.put("fileHash", fileHash);
                    metadata.put("updateReason", "初始上传");
                    doc.getMetadata().putAll(metadata);
                });

                // 存储知识库文件
                vectorStore.accept(documentList);

                // 存储到数据库
                AiRagOrderVO aiRagOrderVO = new AiRagOrderVO();
                aiRagOrderVO.setRagName(name);
                aiRagOrderVO.setKnowledgeTag(tag);
                aiRagOrderVO.setVersion(1);
                aiRagOrderVO.setFileHash(fileHash);
                aiRagOrderVO.setUpdateReason("初始上传");
                repository.createTagOrder(aiRagOrderVO);

                log.info("知识库文件上传成功: name={}, tag={}, fileHash={}, documentCount={}", 
                        name, tag, fileHash, documentList.size());

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
