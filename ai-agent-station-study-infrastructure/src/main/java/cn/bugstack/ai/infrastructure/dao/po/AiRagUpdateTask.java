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
 * 知识库更新任务表
 * @author bugstack.cn
 * @description 知识库更新任务 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_rag_update_task")
public class AiRagUpdateTask {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 知识库ID列表(JSON)
     */
    private String ragIds;

    /**
     * 更新原因
     */
    private String updateReason;

    /**
     * 状态(PENDING/PROCESSING/COMPLETED/FAILED)
     */
    private String status;

    /**
     * 进度(0-100)
     */
    private Integer progress;

    /**
     * 总条目数
     */
    private Integer totalItems;

    /**
     * 已处理条目数
     */
    private Integer processedItems;

    /**
     * 失败条目数
     */
    private Integer failedItems;

    /**
     * 错误信息
     */
    private String errorMessage;

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
