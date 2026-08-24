package cn.bugstack.ai.domain.agent.service.rag;

import cn.bugstack.ai.api.dto.VersionHistoryDTO;
import cn.bugstack.ai.domain.agent.adapter.repository.IRagUpdateRepository;
import cn.bugstack.ai.domain.agent.service.IRagVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库版本管理服务
 * @author bugstack.cn
 * @description 知识库版本管理服务实现
 */
@Slf4j
@Service
public class RagVersionService implements IRagVersionService {

    @Resource
    private IRagUpdateRepository ragUpdateRepository;

    @Override
    public void saveVersionHistory(String ragId, Integer version, String fileHash, String updateReason, Integer documentCount) {
        ragUpdateRepository.saveVersionHistory(ragId, version, fileHash, updateReason, documentCount);
    }

    @Override
    public List<VersionHistoryDTO> getVersionHistory(String ragId) {
        return ragUpdateRepository.getVersionHistory(ragId);
    }

    @Override
    public Integer getLatestVersion(String ragId) {
        return ragUpdateRepository.getLatestVersion(ragId);
    }

    @Override
    public Object getVersionHistory(String ragId, Integer version) {
        return ragUpdateRepository.getVersionHistory(ragId, version);
    }

    @Override
    public boolean deleteVersionHistory(String ragId, Integer version) {
        // 暂时返回 true，后续可以实现删除逻辑
        log.info("删除版本历史: ragId={}, version={}", ragId, version);
        return true;
    }

}
