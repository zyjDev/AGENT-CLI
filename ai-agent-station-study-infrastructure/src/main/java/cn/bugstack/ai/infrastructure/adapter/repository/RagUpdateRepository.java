package cn.bugstack.ai.infrastructure.adapter.repository;


import cn.bugstack.ai.api.dto.AiClientRagOrderResponseDTO;
import cn.bugstack.ai.api.dto.VersionHistoryDTO;
import cn.bugstack.ai.api.dto.TaskStatusResponseDTO;
import cn.bugstack.ai.domain.agent.adapter.repository.IRagUpdateRepository;
import cn.bugstack.ai.infrastructure.dao.IAiClientRagOrderDao;
import cn.bugstack.ai.infrastructure.dao.IAiRagUpdateTaskDao;
import cn.bugstack.ai.infrastructure.dao.IAiRagVersionHistoryDao;
import cn.bugstack.ai.infrastructure.dao.po.AiClientRagOrder;
import cn.bugstack.ai.infrastructure.dao.po.AiRagUpdateTask;
import cn.bugstack.ai.infrastructure.dao.po.AiRagVersionHistory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库更新仓储实现
 * @author bugstack.cn
 * @description 知识库更新仓储实现
 */
@Slf4j
@Repository
public class RagUpdateRepository implements IRagUpdateRepository {

    @Resource
    private IAiClientRagOrderDao aiClientRagOrderDao;

    @Resource
    private IAiRagUpdateTaskDao aiRagUpdateTaskDao;

    @Resource
    private IAiRagVersionHistoryDao aiRagVersionHistoryDao;

    @Override
    public List<AiClientRagOrderResponseDTO> queryUpdatedRagOrders(LocalDateTime updateTime) {
        LambdaQueryWrapper<AiClientRagOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.gt(AiClientRagOrder::getUpdateTime, updateTime)
               .orderByDesc(AiClientRagOrder::getUpdateTime);
        
        List<AiClientRagOrder> orders = aiClientRagOrderDao.selectList(wrapper);
        
        return orders.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<AiClientRagOrderResponseDTO> queryAllRagOrders() {
        LambdaQueryWrapper<AiClientRagOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiClientRagOrder::getUpdateTime);
        
        List<AiClientRagOrder> orders = aiClientRagOrderDao.selectList(wrapper);
        
        return orders.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public AiClientRagOrderResponseDTO queryRagOrderById(String ragId) {
        LambdaQueryWrapper<AiClientRagOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiClientRagOrder::getRagId, ragId);
        
        AiClientRagOrder order = aiClientRagOrderDao.selectOne(wrapper);
        return order != null ? convertToResponseDTO(order) : null;
    }

    @Override
    public boolean updateRagOrder(String ragId, String fileHash, String updateReason, Integer version) {
        LambdaQueryWrapper<AiClientRagOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiClientRagOrder::getRagId, ragId);
        
        AiClientRagOrder order = aiClientRagOrderDao.selectOne(wrapper);
        if (order == null) {
            log.error("知识库配置不存在: {}", ragId);
            return false;
        }

        order.setVersion(version);
        order.setFileHash(fileHash);
        order.setUpdateReason(updateReason);
        order.setUpdateTime(LocalDateTime.now());
        
        int rows = aiClientRagOrderDao.updateById(order);
        return rows > 0;
    }

    @Override
    public void saveVersionHistory(String ragId, Integer version, String fileHash, String updateReason, Integer documentCount) {
        AiRagVersionHistory history = new AiRagVersionHistory();
        history.setRagId(ragId);
        history.setVersion(version);
        history.setFileHash(fileHash);
        history.setUpdateReason(updateReason);
        history.setDocumentCount(documentCount);
        history.setCreateTime(LocalDateTime.now());
        
        aiRagVersionHistoryDao.insert(history);
        log.info("保存版本历史: ragId={}, version={}, documentCount={}", ragId, version, documentCount);
    }

    @Override
    public List<VersionHistoryDTO> getVersionHistory(String ragId) {
        LambdaQueryWrapper<AiRagVersionHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiRagVersionHistory::getRagId, ragId)
               .orderByDesc(AiRagVersionHistory::getVersion);
        
        List<AiRagVersionHistory> historyList = aiRagVersionHistoryDao.selectList(wrapper);
        return historyList.stream().map(this::convertToVersionHistoryDTO).collect(Collectors.toList());
    }

    @Override
    public Integer getLatestVersion(String ragId) {
        LambdaQueryWrapper<AiRagVersionHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiRagVersionHistory::getRagId, ragId)
               .orderByDesc(AiRagVersionHistory::getVersion)
               .last("LIMIT 1");
        
        AiRagVersionHistory latest = aiRagVersionHistoryDao.selectOne(wrapper);
        return latest != null ? latest.getVersion() : 0;
    }

    @Override
    public Object getVersionHistory(String ragId, Integer version) {
        LambdaQueryWrapper<AiRagVersionHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiRagVersionHistory::getRagId, ragId)
               .eq(AiRagVersionHistory::getVersion, version);
        
        return aiRagVersionHistoryDao.selectOne(wrapper);
    }

    @Override
    public void createUpdateTask(String taskId, List<String> ragIds, String updateReason) {
        AiRagUpdateTask task = new AiRagUpdateTask();
        task.setTaskId(taskId);
        task.setRagIds(String.join(",", ragIds));
        task.setUpdateReason(updateReason);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setTotalItems(ragIds.size());
        task.setProcessedItems(0);
        task.setFailedItems(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        
        aiRagUpdateTaskDao.insert(task);
    }

    @Override
    public void updateTaskStatus(String taskId, String status, Integer progress, Integer processedItems, Integer failedItems, String errorMessage) {
        LambdaQueryWrapper<AiRagUpdateTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiRagUpdateTask::getTaskId, taskId);
        
        AiRagUpdateTask task = aiRagUpdateTaskDao.selectOne(wrapper);
        if (task != null) {
            task.setStatus(status);
            task.setProgress(progress);
            task.setProcessedItems(processedItems);
            task.setFailedItems(failedItems);
            task.setErrorMessage(errorMessage);
            task.setUpdateTime(LocalDateTime.now());
            
            aiRagUpdateTaskDao.updateById(task);
        }
    }

    @Override
    public TaskStatusResponseDTO queryTaskStatus(String taskId) {
        LambdaQueryWrapper<AiRagUpdateTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiRagUpdateTask::getTaskId, taskId);
        
        AiRagUpdateTask task = aiRagUpdateTaskDao.selectOne(wrapper);
        if (task == null) {
            return null;
        }
        
        return TaskStatusResponseDTO.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .progress(task.getProgress())
                .totalItems(task.getTotalItems())
                .processedItems(task.getProcessedItems())
                .failedItems(task.getFailedItems())
                .startTime(task.getCreateTime())
                .endTime(task.getUpdateTime())
                .errorMessage(task.getErrorMessage())
                .build();
    }

    /**
     * 转换为响应DTO
     */

    private VersionHistoryDTO convertToVersionHistoryDTO(AiRagVersionHistory history) {
        return VersionHistoryDTO.builder()
                .id(history.getId())
                .ragId(history.getRagId())
                .version(history.getVersion())
                .fileHash(history.getFileHash())
                .updateReason(history.getUpdateReason())
                .metadataSnapshot(history.getMetadataSnapshot())
                .documentCount(history.getDocumentCount())
                .createTime(history.getCreateTime())
                .build();
    }
    private AiClientRagOrderResponseDTO convertToResponseDTO(AiClientRagOrder order) {
        return AiClientRagOrderResponseDTO.builder()
                .id(order.getId())
                .ragId(order.getRagId())
                .ragName(order.getRagName())
                .knowledgeTag(order.getKnowledgeTag())
                .status(order.getStatus())
                .version(order.getVersion())
                .fileHash(order.getFileHash())
                .updateReason(order.getUpdateReason())
                .createTime(order.getCreateTime())
                .updateTime(order.getUpdateTime())
                .build();
    }

}
