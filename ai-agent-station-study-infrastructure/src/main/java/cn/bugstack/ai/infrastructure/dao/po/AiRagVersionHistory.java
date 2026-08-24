package cn.bugstack.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库版本历史表
 * @author bugstack.cn
 * @description 知识库版本历史 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiRagVersionHistory {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 知识库ID
     */
    private String ragId;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 文件哈希
     */
    private String fileHash;

    /**
     * 更新原因
     */
    private String updateReason;

    /**
     * 元数据快照(JSON)
     */
    private String metadataSnapshot;

    /**
     * 文档数量
     */
    private Integer documentCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
