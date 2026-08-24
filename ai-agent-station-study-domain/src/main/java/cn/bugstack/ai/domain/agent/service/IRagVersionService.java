package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.api.dto.VersionHistoryDTO;
import java.util.List;

/**
 * 知识库版本管理服务接口
 * @author bugstack.cn
 * @description 知识库版本管理服务接口
 */
public interface IRagVersionService {

    /**
     * 保存版本历史
     * @param ragId 知识库ID
     * @param version 版本号
     * @param fileHash 文件哈希
     * @param updateReason 更新原因
     * @param documentCount 文档数量
     */
    void saveVersionHistory(String ragId, Integer version, String fileHash, String updateReason, Integer documentCount);

    /**
     * 获取知识库版本历史
     * @param ragId 知识库ID
     * @return 版本历史列表
     */
    List<VersionHistoryDTO> getVersionHistory(String ragId);

    /**
     * 获取最新版本号
     * @param ragId 知识库ID
     * @return 最新版本号
     */
    Integer getLatestVersion(String ragId);

    /**
     * 获取指定版本的历史记录
     * @param ragId 知识库ID
     * @param version 版本号
     * @return 版本历史记录
     */
    Object getVersionHistory(String ragId, Integer version);

    /**
     * 删除版本历史
     * @param ragId 知识库ID
     * @param version 版本号
     * @return 是否成功
     */
    boolean deleteVersionHistory(String ragId, Integer version);

}
