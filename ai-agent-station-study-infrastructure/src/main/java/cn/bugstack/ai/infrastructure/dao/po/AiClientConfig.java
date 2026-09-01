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
 * AI客户端统一关联配置表
 * @author bugstack虫洞栈
 * @description AI客户端统一关联配置表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_client_config")
public class AiClientConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 源类型（model、client）
     */
    private String sourceType;

    /**
     * 源ID（如 chatModelId、chatClientId 等）
     */
    private String sourceId;

    /**
     * 目标类型（model、client）
     */
    private String targetType;

    /**
     * 目标ID（如 openAiApiId、chatModelId、systemPromptId、advisorId 等）
     */
    private String targetId;

    /**
     * 扩展参数（JSON格式）
     */
    private String extParam;

    /**
     * 状态(0:禁用,1:启用)
     */
    private Integer status;

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