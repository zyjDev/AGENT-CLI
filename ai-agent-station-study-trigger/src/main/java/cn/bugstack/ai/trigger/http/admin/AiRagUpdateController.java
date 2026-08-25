package cn.bugstack.ai.trigger.http.admin;

import cn.bugstack.ai.api.dto.*;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.domain.agent.service.IAsyncRagUpdateService;
import cn.bugstack.ai.domain.agent.service.IRagUpdateService;
import cn.bugstack.ai.domain.agent.service.IRollbackService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库更新管理接口
 * @author bugstack.cn
 * @description 知识库更新管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class AiRagUpdateController {

    @Resource
    private IRagUpdateService ragUpdateService;

    @Resource
    private IAsyncRagUpdateService asyncRagUpdateService;

    @Resource
    private IRollbackService rollbackService;

    /**
     * 查询待更新文档
     * @param updateTime 更新时间阈值
     * @return 待更新的知识库列表
     */
    @GetMapping("/updated")
    public Response<List<AiClientRagOrderResponseDTO>> queryUpdatedRagOrders(
            @RequestParam("updateTime") LocalDateTime updateTime) {
        try {
            log.info("查询待更新文档: updateTime={}", updateTime);
            List<AiClientRagOrderResponseDTO> result = ragUpdateService.queryUpdatedRagOrders(updateTime);
            return Response.success(result);
        } catch (Exception e) {
            log.error("查询待更新文档失败", e);
            return Response.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询所有知识库配置
     * @return 知识库配置列表
     */
    @GetMapping("/list")
    public Response<List<AiClientRagOrderResponseDTO>> queryAllRagOrders() {
        try {
            log.info("查询所有知识库配置");
            List<AiClientRagOrderResponseDTO> result = ragUpdateService.queryAllRagOrders();
            return Response.success(result);
        } catch (Exception e) {
            log.error("查询所有知识库配置失败", e);
            return Response.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询知识库配置
     * @param ragId 知识库ID
     * @return 知识库配置
     */
    @GetMapping("/{ragId}")
    public Response<AiClientRagOrderResponseDTO> queryRagOrderById(@PathVariable("ragId") String ragId) {
        try {
            log.info("查询知识库配置: ragId={}", ragId);
            AiClientRagOrderResponseDTO result = ragUpdateService.queryRagOrderById(ragId);
            if (result == null) {
                return Response.error("知识库配置不存在");
            }
            return Response.success(result);
        } catch (Exception e) {
            log.error("查询知识库配置失败: ragId={}", ragId, e);
            return Response.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 更新知识库文档
     * @param ragId 知识库ID
     * @param files 文件列表
     * @param updateReason 更新原因
     * @return 操作结果
     */
    @PostMapping("/update")
    public Response<Boolean> updateRagDocuments(
            @RequestParam("ragId") String ragId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "updateReason", required = false) String updateReason) {
        try {
            log.info("更新知识库文档: ragId={}, fileCount={}, updateReason={}", ragId, files.size(), updateReason);
            boolean result = ragUpdateService.updateRagDocuments(ragId, files, updateReason);
            if (result) {
                return Response.success(true);
            } else {
                return Response.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新知识库文档失败: ragId={}", ragId, e);
            return Response.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 异步批量更新知识库
     * @param request 批量更新请求
     * @return 任务ID
     */
    @PostMapping("/async-batch-update")
    public Response<String> asyncBatchUpdateRag(@RequestBody BatchUpdateRequestDTO request) {
        try {
            log.info("提交异步批量更新任务: ragIds={}, updateReason={}", request.getRagIds(), request.getUpdateReason());
            String taskId = asyncRagUpdateService.submitBatchUpdateTask(request.getRagIds(), request.getUpdateReason());
            return Response.success(taskId);
        } catch (Exception e) {
            log.error("提交异步批量更新任务失败", e);
            return Response.error("提交失败: " + e.getMessage());
        }
    }

    /**
     * 查询更新任务状态
     * @param taskId 任务ID
     * @return 任务状态
     */
    @GetMapping("/task-status")
    public Response<TaskStatusResponseDTO> queryUpdateTaskStatus(@RequestParam("taskId") String taskId) {
        try {
            log.info("查询任务状态: taskId={}", taskId);
            TaskStatusResponseDTO result = asyncRagUpdateService.queryTaskStatus(taskId);
            if (result == null) {
                return Response.error("任务不存在");
            }
            return Response.success(result);
        } catch (Exception e) {
            log.error("查询任务状态失败: taskId={}", taskId, e);
            return Response.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/cancel-task")
    public Response<Boolean> cancelTask(@RequestParam("taskId") String taskId) {
        try {
            log.info("取消任务: taskId={}", taskId);
            boolean result = asyncRagUpdateService.cancelTask(taskId);
            if (result) {
                return Response.success(true);
            } else {
                return Response.error("取消失败");
            }
        } catch (Exception e) {
            log.error("取消任务失败: taskId={}", taskId, e);
            return Response.error("取消失败: " + e.getMessage());
        }
    }

    /**
     * 重试失败任务
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/retry-task")
    public Response<Boolean> retryFailedTask(@RequestParam("taskId") String taskId) {
        try {
            log.info("重试失败任务: taskId={}", taskId);
            boolean result = asyncRagUpdateService.retryFailedTask(taskId);
            if (result) {
                return Response.success(true);
            } else {
                return Response.error("重试失败");
            }
        } catch (Exception e) {
            log.error("重试失败任务失败: taskId={}", taskId, e);
            return Response.error("重试失败: " + e.getMessage());
        }
    }

    /**
     * 回滚版本
     * @param request 回滚请求
     * @return 操作结果
     */
    @PostMapping("/rollback")
    public Response<Boolean> rollbackRagVersion(@RequestBody RollbackRequestDTO request) {
        try {
            log.info("回滚版本: ragId={}, targetVersion={}", request.getRagId(), request.getTargetVersion());
            
            // 验证回滚可行性
            IRollbackService.RollbackValidationResult validation = rollbackService.validateRollback(
                request.getRagId(), request.getTargetVersion());
            
            if (!validation.isValid()) {
                return Response.error(validation.getMessage());
            }
            
            boolean result = rollbackService.rollbackToVersion(request.getRagId(), request.getTargetVersion());
            if (result) {
                return Response.success(true);
            } else {
                return Response.error("回滚失败");
            }
        } catch (Exception e) {
            log.error("回滚版本失败: ragId={}, targetVersion={}", request.getRagId(), request.getTargetVersion(), e);
            return Response.error("回滚失败: " + e.getMessage());
        }
    }

    /**
     * 获取版本历史
     * @param ragId 知识库ID
     * @return 版本历史列表
     */
    @GetMapping("/version-history/{ragId}")
    public Response<List<VersionHistoryDTO>> getVersionHistory(@PathVariable("ragId") String ragId) {
        try {
            log.info("获取版本历史: ragId={}", ragId);
            List<VersionHistoryDTO> result = rollbackService.getVersionHistory(ragId);
            return Response.success(result);
        } catch (Exception e) {
            log.error("获取版本历史失败: ragId={}", ragId, e);
            return Response.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新版本号
     * @param ragId 知识库ID
     * @return 最新版本号
     */
    @GetMapping("/latest-version/{ragId}")
    public Response<Integer> getLatestVersion(@PathVariable("ragId") String ragId) {
        try {
            log.info("获取最新版本号: ragId={}", ragId);
            Integer result = rollbackService.getLatestVersion(ragId);
            return Response.success(result);
        } catch (Exception e) {
            log.error("获取最新版本号失败: ragId={}", ragId, e);
            return Response.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 批量更新请求DTO
     */
    @lombok.Data
    public static class BatchUpdateRequestDTO {
        private List<String> ragIds;
        private String updateReason;
    }

}
