package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务状态响应 DTO
 *
 * @author bugstack.cn
 * @description 任务状态响应数据传输对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskStatusResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private String taskId;

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
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 错误信息
     */
    private String errorMessage;

}
