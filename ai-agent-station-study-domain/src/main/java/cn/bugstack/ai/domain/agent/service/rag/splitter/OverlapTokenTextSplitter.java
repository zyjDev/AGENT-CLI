package cn.bugstack.ai.domain.agent.service.rag.splitter;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 在 {@link TokenTextSplitter} 之上叠加「相邻块重叠」，
 * 用于缓解「一个语义单元正好被切在两个 chunk 之间，导致两块各自都不完整」。
 * <p>
 * <b>为什么要自己写</b>：Spring AI 1.1.8 的 TokenTextSplitter 构造器只有
 * chunkSize / minChunkSizeChars / minChunkLengthToEmbed / maxNumChunks / keepSeparator，
 * <b>没有 overlap 参数</b> —— 框架层面不提供重叠能力，配置不出来。
 * 但它的 {@code splitText(String)} 是 protected 且非 final，
 * 因此可以继承后复用父类「按 token 填满 + 回退到句子边界」的逻辑，再对结果做一次重叠增强。
 * <p>
 * <b>为什么用继承而不是组合</b>：{@code splitText} 是 protected，跨类不可见，
 * 组合写法（delegate.splitText(...)）编译不过。
 * <p>
 * <b>重叠策略</b>：第 i 块的头部带上「第 i-1 块尾部的 N 个 token」，
 * 并向前对齐到最近的句子/段落边界 —— 保证块头是<b>完整句子</b>而不是半句话
 * （半句话会给向量引入噪声，比不重叠更糟）。
 * <p>
 * <b>块总数不变</b>（只是每块变长），因此不影响向量表结构，也不需要改表。
 * <p>
 * <b>注意</b>：本类只影响「新入库」的文档。切换切分器后<b>存量 chunk 不会自动重切</b>，
 * 必须重新上传/更新知识库文档，否则新旧两种切法会混在同一张向量表里。
 *
 * @author 改造
 */
public class OverlapTokenTextSplitter extends TokenTextSplitter {

    /** 与父类同一套编码（cl100k_base），用于精确按 token 取尾部 */
    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    /** 每块头部携带上一块的 token 数；0 表示不重叠（等价于原 TokenTextSplitter） */
    private final int overlapTokens;

    public OverlapTokenTextSplitter(int overlapTokens) {
        super();
        this.overlapTokens = Math.max(overlapTokens, 0);
    }

    @Override
    protected List<String> splitText(String text) {
        List<String> base = super.splitText(text);
        // 不重叠 / 只有一块时，行为与原 TokenTextSplitter 完全一致
        if (overlapTokens <= 0 || base.size() <= 1) {
            return base;
        }

        List<String> out = new ArrayList<>(base.size());
        // 第一块没有「上一块」，保持原样
        out.add(base.get(0));
        for (int i = 1; i < base.size(); i++) {
            String carry = tailOf(base.get(i - 1));
            out.add(carry.isEmpty()
                    ? base.get(i)
                    : carry + System.lineSeparator() + base.get(i));
        }
        return out;
    }

    /** 取上一块尾部 overlapTokens 个 token，并对齐到句子/段落边界 */
    private String tailOf(String prev) {
        IntArrayList tokens = ENCODING.encode(prev);
        if (tokens.size() <= overlapTokens) {
            return prev;
        }

        IntArrayList tail = new IntArrayList();
        for (int i = tokens.size() - overlapTokens; i < tokens.size(); i++) {
            tail.add(tokens.get(i));
        }
        return alignToBoundary(ENCODING.decode(tail));
    }

    /**
     * 从片段开头往后找第一个句子/段落边界，返回其后的内容 ——
     * 目的是丢掉「被切断的半句话」，让块头从一个完整句子或新段落开始。
     * <p>
     * 边界判定按可靠性排序：
     * <ol>
     *     <li>段落分隔（\n\n）—— 技术文档里最可靠的语义边界；</li>
     *     <li>中文句末标点（。！？）—— 无歧义；</li>
     *     <li>英文句末标点 —— <b>必须</b>满足「标点 + 空白 + 大写字母/标题符」才算。
     *         只看 {@code '.'} 是不行的：代码里的 {@code foo.bar()}、{@code import a.b.C}、
     *         {@code 1.5} 全会被误判成句子结束，把块头切在代码中间（实测踩过：
     *         块头出现过 {@code openAiApi(groqApi)} 这种半截代码）。</li>
     * </ol>
     * 找不到任何边界时原样返回：宁可重叠内容不完美，也不能丢内容。
     */
    private String alignToBoundary(String raw) {
        int n = raw.length();
        for (int i = 0; i < n; i++) {
            char c = raw.charAt(i);

            // ① 段落分隔（\n\n），语义边界最干净
            if (c == '\n' && i + 1 < n && raw.charAt(i + 1) == '\n') {
                int j = skipWhitespace(raw, i + 2);
                if (j < n) {
                    return raw.substring(j);
                }
            }

            // ② 中文句末标点
            if (c == '。' || c == '！' || c == '？') {
                int j = skipWhitespace(raw, i + 1);
                if (j < n) {
                    return raw.substring(j);
                }
            }

            // ③ 英文句末标点：必须「标点 + 空白 + 大写字母/标题符」，避开代码里的点
            if (c == '.' || c == '!' || c == '?') {
                int j = i + 1;
                if (j < n && Character.isWhitespace(raw.charAt(j))) {
                    int k = skipWhitespace(raw, j);
                    if (k < n) {
                        char next = raw.charAt(k);
                        if (Character.isUpperCase(next) || next == '#' || next == '*') {
                            return raw.substring(k);
                        }
                    }
                }
            }
        }
        // 找不到边界时原样返回（只去掉前导空白，不丢内容）
        return raw.stripLeading();
    }

    /** 跳过 from 起的全部空白（含换行）—— 必须跳换行，否则块头会以 \n 开头产生空行 */
    private int skipWhitespace(String s, int from) {
        int i = from;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

}
