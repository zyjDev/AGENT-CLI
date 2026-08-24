package cn.bugstack.ai.domain.agent.service.rag;
import cn.bugstack.ai.api.dto.VersionHistoryDTO;


import cn.bugstack.ai.domain.agent.adapter.repository.IRagUpdateRepository;
import cn.bugstack.ai.domain.agent.service.IRollbackService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 回滚服务实现
 * @author bugstack.cn
 * @description 回滚服务实现
 */
@Slf4j
@Service
public class RollbackService implements IRollbackService {

    @Resource
    private IRagUpdateRepository ragUpdateRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackToVersion(String ragId, Integer targetVersion) {
        try {
            // 1. 验证回滚可行性
            RollbackValidationResult validation = validateRollback(ragId, targetVersion);
            if (!validation.isValid()) {
                log.error("回滚验证失败: ragId={}, message={}", ragId, validation.getMessage());
                return false;
            }

            // 2. 查询目标版本历史
            var targetHistory = ragUpdateRepository.getVersionHistory(ragId, targetVersion);
            if (targetHistory == null) {
                log.error("目标版本不存在: ragId={}, version={}", ragId, targetVersion);
                return false;
            }

            // 3. 查询当前配置
            var currentOrder = ragUpdateRepository.queryRagOrderById(ragId);
            if (currentOrder == null) {
                log.error("知识库配置不存在: {}", ragId);
                return false;
            }

            // 4. 保存当前版本到历史
            ragUpdateRepository.saveVersionHistory(
                ragId,
                currentOrder.getVersion(),
                currentOrder.getFileHash(),
                "回滚到版本 " + targetVersion,
                0
            );

            // 5. 更新配置
            boolean updateResult = ragUpdateRepository.updateRagOrder(
                ragId,
                currentOrder.getFileHash(),
                "回滚到版本 " + targetVersion,
                targetVersion
            );

            if (updateResult) {
                log.info("回滚成功: ragId={}, targetVersion={}", ragId, targetVersion);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("回滚失败: ragId={}, targetVersion={}", ragId, targetVersion, e);
            throw new RuntimeException("回滚失败", e);
        }
    }

    @Override
    public RollbackValidationResult validateRollback(String ragId, Integer targetVersion) {
        // 1. 查询当前配置
        var currentOrder = ragUpdateRepository.queryRagOrderById(ragId);
        if (currentOrder == null) {
            return new RollbackValidationResult(false, "知识库配置不存在", null, null);
        }

        Integer currentVersion = currentOrder.getVersion();

        // 2. 检查目标版本是否有效
        if (targetVersion == null || targetVersion < 1) {
            return new RollbackValidationResult(false, "目标版本号无效", currentVersion, targetVersion);
        }

        // 3. 检查目标版本是否等于当前版本
        if (targetVersion.equals(currentVersion)) {
            return new RollbackValidationResult(false, "目标版本与当前版本相同", currentVersion, targetVersion);
        }

        // 4. 检查目标版本是否存在
        var targetHistory = ragUpdateRepository.getVersionHistory(ragId, targetVersion);
        if (targetHistory == null) {
            return new RollbackValidationResult(false, "目标版本不存在", currentVersion, targetVersion);
        }

        return new RollbackValidationResult(true, "验证通过", currentVersion, targetVersion);
    }

    @Override
    public List<VersionHistoryDTO> getVersionHistory(String ragId) {
        return ragUpdateRepository.getVersionHistory(ragId);
    }

    @Override
    public Integer getLatestVersion(String ragId) {
        return ragUpdateRepository.getLatestVersion(ragId);
    }

}
