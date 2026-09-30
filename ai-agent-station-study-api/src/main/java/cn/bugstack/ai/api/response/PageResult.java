package cn.bugstack.ai.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 管理端列表接口的统一分页返回体，即 {@link Response#getData()} 的载荷类型。
 *
 * <p>改造前：列表接口返回 {@code Response<List<XxxResponseDTO>>}，前端只能靠
 * {@code data.length} 当 total —— 既拿不到真实总行数，也没法区分「最后一页」与
 * 「本页恰好满」。改造后统一返回 {@code Response<PageResult<XxxResponseDTO>>}。
 *
 * <p><b>契约变更点</b>：{@code data} 从「裸数组」变成「对象」。前端做的是双形态兼容
 * （见 {@code zhishu-ui/src/components/admin/types.ts} 的 {@code PagePayload}），
 * 因此后端可以逐接口灰度迁移，中途不会把页面打崩。
 *
 * <p><b>分页是真分页</b>：条件下推到 SQL 并带 LIMIT，{@code total} 由 count 语句得出，
 * 不再是把全表捞进内存再 {@code subList}。
 *
 * @param <T> 列表元素类型（通常是各资源的 ResponseDTO）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据；无数据时是空列表，不会是 null */
    private List<T> list;

    /** 满足查询条件的总行数（前端分页器据此渲染总页数） */
    private long total;

    /** 当前页码，从 1 开始 */
    private int pageNum;

    /** 每页行数（已按上限收敛，见 {@code AdminPageSupport}） */
    private int pageSize;

}
