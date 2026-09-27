package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * AI智能体拖拉拽配置响应DTO
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/1/20 10:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentDrawConfigResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置ID（唯一标识）
     */
    private String configId;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 关联的智能体ID
     */
    private String agentId;

    /**
     * 完整的拖拉拽配置JSON数据（包含nodes和edges）
     */
    private String configData;

    /**
     * 配置版本号
     */
    private Integer version;

    /**
     * 状态(0:禁用,1:启用)
     */
    private Integer status;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否平台默认资源（owner_id 为空）。
     * <p>
     * 前端据此展示「平台默认」标识并隐藏编辑 / 删除：普通用户能看到默认智能体的信息，
     * 但不能改（后端写入也会被 OwnerGuard 拦下，这里只是别给出会失败的按钮）。
     * 刻意不直接暴露 owner_id —— 前端只需要"是不是平台默认"这一个判断。
     */
    private Boolean platformDefault;

}