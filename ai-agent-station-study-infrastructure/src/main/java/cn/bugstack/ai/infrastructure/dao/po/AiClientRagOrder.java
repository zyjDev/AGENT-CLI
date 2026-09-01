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
 * 知识库配置表
 * @author bugstack.cn
 * @description 知识库配置表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_client_rag_order")
public class AiClientRagOrder {

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
     * 知识库名称
     */
    private String ragName;

    /**
     * 知识标签
     */
    private String knowledgeTag;

    /**
     * 状态(0:禁用,1:启用)
     */
    private Integer status;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 文件内容哈希
     */
    private String fileHash;

    /**
     * 更新原因
     */
    private String updateReason;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
