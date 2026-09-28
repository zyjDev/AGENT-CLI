package cn.bugstack.ai.trigger.support;

import cn.bugstack.ai.api.response.PageResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端列表接口的分页支持工具。
 *
 * <p>把「入参收敛 + 结果转换」这两件每个列表接口都要做的事收敛到一处，
 * 避免 9 个控制器各写各的（改造前的 4 种分页写法里，有 2 种在 {@code pageNum=0}
 * 时 {@code start} 算出负数、{@code subList} 直接抛 {@code IndexOutOfBoundsException}）。
 *
 * <p>用法：
 * <pre>
 *   IPage&lt;AiClient&gt; page = aiClientDao.queryPage(
 *           AdminPageSupport.page(request.getPageNum(), request.getPageSize()),
 *           request.getClientId(), request.getClientName(), request.getStatus());
 *
 *   return Response.&lt;PageResult&lt;AiClientResponseDTO&gt;&gt;builder()
 *           .code(ResponseCode.SUCCESS.getCode())
 *           .info(ResponseCode.SUCCESS.getInfo())
 *           .data(AdminPageSupport.of(page, this::convertToAiClientResponseDTO))
 *           .build();
 * </pre>
 *
 * <p><b>依赖分页插件</b>：{@code queryPage} 内部走 {@code selectPage}，必须在
 * {@code DataSourceConfig} 自定义的 {@code MybatisSqlSessionFactoryBean} 上显式挂载
 * {@code PaginationInnerInterceptor}，否则不会报错、只会静默退化成「全量查询 + total=0」。
 *
 * @author bugstack虫洞栈
 */
public final class AdminPageSupport {

    /** 未传 pageSize 时的默认每页行数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** pageSize 上限；与 DataSourceConfig 里分页插件的 maxLimit 保持一致 */
    public static final int MAX_PAGE_SIZE = 200;

    private AdminPageSupport() {
    }

    /**
     * 构造分页参数对象（页码与每页行数均已收敛到合法范围）。
     */
    public static <T> Page<T> page(Integer pageNum, Integer pageSize) {
        return new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
    }

    /** 页码收敛：null / 小于 1 一律当第 1 页，避免 start 算出负数 */
    public static int normalizePageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? 1 : pageNum;
    }

    /** 每页行数收敛：null / 小于 1 取默认值，超过上限则截断（防止一次把整表捞回来） */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 把 MyBatis-Plus 的分页结果转成对外契约 {@link PageResult}，并顺带完成 PO → DTO 转换。
     *
     * <p>放在这里一起做，是为了避免「转换后的列表长度」和「total」两处口径走散。
     *
     * @param page      {@code selectPage} 的返回值
     * @param converter 单行 PO → DTO 的转换函数，通常是控制器里已有的 {@code convertToXxxResponseDTO}
     */
    public static <E, D> PageResult<D> of(IPage<E> page, Function<E, D> converter) {
        if (page == null) {
            return PageResult.<D>builder()
                    .list(List.of())
                    .total(0L)
                    .pageNum(1)
                    .pageSize(DEFAULT_PAGE_SIZE)
                    .build();
        }
        List<D> list = page.getRecords() == null
                ? List.of()
                : page.getRecords().stream().map(converter).collect(Collectors.toList());
        return PageResult.<D>builder()
                .list(list)
                .total(page.getTotal())
                .pageNum((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .build();
    }

}
