package cn.bugstack.ai.domain.agent.service;

import cn.bugstack.ai.api.dto.VersionHistoryDTO;



import java.util.List;

/**
 * 回滚服务接口
 * @author bugstack.cn
 * @description 回滚服务接口
 */
public interface IRollbackService {

    /**
     * 回滚到指定版本
     * @param ragId 知识库ID
     * @param targetVersion 目标版本号
     * @return 是否成功
     */
    boolean rollbackToVersion(String ragId, Integer targetVersion);

    /**
     * 验证回滚可行性
     * @param ragId 知识库ID
     * @param targetVersion 目标版本号
     * @return 验证结果
     */
    RollbackValidationResult validateRollback(String ragId, Integer targetVersion);

    /**
     * 获取版本历史列表
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
     * 回滚验证结果
     */
    class RollbackValidationResult {
        private boolean valid;
        private String message;
        private Integer currentVersion;
        private Integer targetVersion;

        public RollbackValidationResult(boolean valid, String message, Integer currentVersion, Integer targetVersion) {
            this.valid = valid;
            this.message = message;
            this.currentVersion = currentVersion;
            this.targetVersion = targetVersion;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Integer getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(Integer currentVersion) {
            this.currentVersion = currentVersion;
        }

        public Integer getTargetVersion() {
            return targetVersion;
        }

        public void setTargetVersion(Integer targetVersion) {
            this.targetVersion = targetVersion;
        }
    }

}
