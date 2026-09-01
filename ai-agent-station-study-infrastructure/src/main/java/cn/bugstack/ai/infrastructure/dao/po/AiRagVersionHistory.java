package cn.bugstack.ai.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("ai_rag_version_history")
public class AiRagVersionHistory {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
