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
 * <p>
 * 异常处理约定：本类不做 try/catch。领域层抛出的 BizException 由
 * {@code GlobalExceptionHandler#handleBizException} 统一转成带业务错误码的 Response，
 * 这样「非法参数(0002)」「未实现」等语义不会被压成统一的 0001。
 * 只有「方法正常返回但业务上失败」（result 为 null / false、校验不通过）才在方法内构造错误响应。
 * <p>
 * 另：原先 11 个方法全部用 {@code Response.success(...)}，而该方法历史返回 code="200"，
 * 与前端约定的 "0000" 不符 —— 已在本轮一并修正为 ResponseCode.SUCCESS 的 "0000"。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
// 跨域统一收敛到 WebCorsConfig 的白名单（原先此处是 origins = "*"，等于对任意站点放开）
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
        log.info("查询待更新文档: updateTime={}", updateTime);
        return Response.success(ragUpdateService.queryUpdatedRagOrders(updateTime));
    }

    /**
     * 查询所有知识库配置
     * @return 知识库配置列表
     */
    @GetMapping("/list")
    public Response<List<AiClientRagOrderResponseDTO>> queryAllRagOrders() {
        log.info("查询所有知识库配置");
        return Response.success(ragUpdateService.queryAllRagOrders());
    }

    /**
     * 根据ID查询知识库配置
     * @param ragId 知识库ID
     * @return 知识库配置
     */
    @GetMapping("/{ragId}")
    public Response<AiClientRagOrderResponseDTO> queryRagOrderById(@PathVariable("ragId") String ragId) {
        log.info("查询知识库配置: ragId={}", ragId);
        AiClientRagOrderResponseDTO result = ragUpdateService.queryRagOrderById(ragId);
        if (result == null) {
            return Response.error("知识库配置不存在");
        }
        return Response.success(result);
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
        log.info("更新知识库文档: ragId={}, fileCount={}, updateReason={}",
                ragId, files == null ? 0 : files.size(), updateReason);
        boolean result = ragUpdateService.updateRagDocuments(ragId, files, updateReason);
        return result ? Response.success(true) : Response.error("更新失败");
    }

    /**
     * 异步批量更新知识库
     * <p>
     * 注意：当前实现只会立刻把任务标记为 FAILED 并返回 taskId —— 批量更新需要原始文件，
     * 而 ai_client_rag_order 只保存 fileHash，服务端没有可重建向量的内容。
     * 任务不会抛异常（这是「受理失败」而非「调用失败」），失败原因写在任务状态里。
     * @param request 批量更新请求
     * @return 任务ID
     */
    @PostMapping("/async-batch-update")
    public Response<String> asyncBatchUpdateRag(@RequestBody BatchUpdateRequestDTO request) {
        log.info("提交异步批量更新任务: ragIds={}, updateReason={}", request.getRagIds(), request.getUpdateReason());
        String taskId = asyncRagUpdateService.submitBatchUpdateTask(request.getRagIds(), request.getUpdateReason());
        return Response.success(taskId);
    }

    /**
     * 查询更新任务状态
     * @param taskId 任务ID
     * @return 任务状态
     */
    @GetMapping("/task-status")
    public Response<TaskStatusResponseDTO> queryUpdateTaskStatus(@RequestParam("taskId") String taskId) {
        log.info("查询任务状态: taskId={}", taskId);
        TaskStatusResponseDTO result = asyncRagUpdateService.queryTaskStatus(taskId);
        if (result == null) {
            return Response.error("任务不存在");
        }
        return Response.success(result);
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/cancel-task")
    public Response<Boolean> cancelTask(@RequestParam("taskId") String taskId) {
        log.info("取消任务: taskId={}", taskId);
        boolean result = asyncRagUpdateService.cancelTask(taskId);
        return result ? Response.success(true) : Response.error("取消失败");
    }

    /**
     * 重试失败任务
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/retry-task")
    public Response<Boolean> retryFailedTask(@RequestParam("taskId") String taskId) {
        log.info("重试失败任务: taskId={}", taskId);
        boolean result = asyncRagUpdateService.retryFailedTask(taskId);
        return result ? Response.success(true) : Response.error("重试失败");
    }

    /**
     * 回滚版本
     * @param request 回滚请求
     * @return 操作结果
     */
    @PostMapping("/rollback")
    public Response<Boolean> rollbackRagVersion(@RequestBody RollbackRequestDTO request) {
        log.info("回滚版本: ragId={}, targetVersion={}", request.getRagId(), request.getTargetVersion());

        // 验证回滚可行性：不可行属于「业务上不允许」，返回错误响应而非抛异常
        IRollbackService.RollbackValidationResult validation = rollbackService.validateRollback(
                request.getRagId(), request.getTargetVersion());
        if (!validation.isValid()) {
            return Response.error(validation.getMessage());
        }

        boolean result = rollbackService.rollbackToVersion(request.getRagId(), request.getTargetVersion());
        return result ? Response.success(true) : Response.error("回滚失败");
    }

    /**
     * 获取版本历史
     * @param ragId 知识库ID
     * @return 版本历史列表
     */
    @GetMapping("/version-history/{ragId}")
    public Response<List<VersionHistoryDTO>> getVersionHistory(@PathVariable("ragId") String ragId) {
        log.info("获取版本历史: ragId={}", ragId);
        return Response.success(rollbackService.getVersionHistory(ragId));
    }

    /**
     * 获取最新版本号
     * @param ragId 知识库ID
     * @return 最新版本号
     */
    @GetMapping("/latest-version/{ragId}")
    public Response<Integer> getLatestVersion(@PathVariable("ragId") String ragId) {
        log.info("获取最新版本号: ragId={}", ragId);
        return Response.success(rollbackService.getLatestVersion(ragId));
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
