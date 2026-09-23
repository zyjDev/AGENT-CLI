package cn.bugstack.ai.domain.agent.service.rag.splitter;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 句边界优先 + 相邻块重叠的切分器。
 * <p>
 * <b>为什么自己写</b>：Spring AI 1.1.8 的 {@link TokenTextSplitter} 构造器只有
 * chunkSize / minChunkSizeChars / minChunkLengthToEmbed / maxNumChunks / keepSeparator，
 * <b>没有 overlap 参数</b>，也<b>没有「按句子切分」能力</b> —— 两者都配不出来。
 * 但 {@code splitText(String)} 是 protected 且非 final，可以覆写。
 *
 * <h2>为什么不用「父类切好再补重叠」</h2>
 * 第一版是这么做的（先 {@code super.splitText()} 再对结果补头部重叠），实测暴露两个硬伤：
 * <ol>
 *     <li><b>块尾永远半句</b>：重叠片段是在 <i>token 层</i> 精确切 100 个出来的，
 *         {@code decode} 回字符串时首尾必然落在半句话中间。只对「开头」做边界对齐，
 *         结尾的半句无解 —— 因为那个位置根本不是边界。</li>
 *     <li><b>有效重叠只有 ~80 token</b>：从「第一个边界之后」截断，会把边界之前的整段丢掉，
 *         缺口约 20%。实测 127 篇 / 540 块，目标 100 token 实际 mean 只有 79.5。</li>
 * </ol>
 * 根因是<b>顺序错了</b>：token 切分决定了「切点」，补重叠只能去迁就这个切点。
 * 正确顺序是<b>先按句子切、再按 token 预算装箱</b>，让切点天然落在句子边界上，
 * 重叠也就自然是完整句子。
 *
 * <h2>本实现的算法</h2>
 * <ol>
 *     <li><b>圈表格</b>：先扫出 AsciiDoc（{@code |====} 成对）与 Markdown（连续 {@code |} 行）
 *         的表格区间，<b>整段作为一个不可拆单元</b>。必须在分段之前做 ——
 *         表格内部常夹空行，按空行分段会把一张表劈成两半。</li>
 *     <li><b>分段</b>：表格区间<b>之外</b>的文本按空行切成段落（顺带保留 Markdown 标题、代码块）。</li>
 *     <li><b>切句</b>：段内按句末标点切成句子 —— 但<b>代码块整块不切</b>，
 *         否则 {@code foo.bar()} 会被切碎。</li>
 *     <li><b>装箱</b>：按顺序把句子塞进累计 ≤ chunkSize 的块里。单个单元超预算时分两种：
 *         表格<b>独占一块、允许超限</b>；其余（长代码块 / 无标点长文）先按行边界切，
 *         只有单行自身超预算才按 token 硬切。</li>
 *     <li><b>补重叠</b>：为第 i 块从第 i-1 块<b>末尾往前回吐完整句子</b>，
 *         直到凑够 overlapTokens 或回吐量达到该块自身长度的 1/2（防重复压过正文）。
 *         若前一块是「整张超预算的表」，则退而回吐它的<b>末尾若干整行</b>（剥掉结构标记）。</li>
 * </ol>
 * 因为重叠片段是「整句」，块头的起点与终点<b>都在句边界上</b>，两个硬伤同时消失。
 *
 * <h2>不变量</h2>
 * <ul>
 *     <li><b>不丢内容</b>：所有句子按原顺序、逐字出现在至少一个块里。</li>
 *     <li><b>块数由装箱决定</b>，与「补不补重叠」无关。</li>
 *     <li><b>表格不被切断</b>：每张表的起始与结束标记必在同一块内。</li>
 *     <li>overlapTokens = 0 时退化为「句边界切分、无重叠」。</li>
 * </ul>
 *
 * <h2>⚠️ 上线注意</h2>
 * 切换切分器后<b>存量 chunk 不会自动重切</b>，必须重新上传/更新知识库文档，
 * 否则新旧两种切法会混在同一张向量表里（同一文档的块边界不一致）。
 * 另外 {@code docs/rag-eval/src/RagCorpusIngest.java} 里复刻了入库逻辑，
 * 也必须同步改成同一个切分器，否则评测结论对不上生产。
 *
 * @author 改造
 */
public class OverlapTokenTextSplitter extends TokenTextSplitter {

    /** 与父类同一套编码（cl100k_base） */
    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    /**
     * 句末标点。故意的：
     * <ul>
     *     <li><b>只有</b> ASCII 的 {@code . ! ?} 与全角 {@code 。！？}；
     *         {@code ：；、，} 是 <i>句内</i> 停顿，切在这里反而制造碎句。</li>
     *     <li>英文标点<b>必须</b>满足「标点 + 空白 + 大写字母/数字/标题符」才算句末。
     *         只看 {@code '.'} 会把代码里的 {@code foo.bar()}、{@code import a.b.C}、
     *         {@code 1.5} 全当句末（实测踩过：块头出现 {@code openAiApi(groqApi)} 这种半截代码）。</li>
     *     <li>允许标点后是 <b>数字</b>（{@code ...in 2024. 5 things} 之类的列表），
     *         也允许是 {@code `} / {@code "} / {@code [} 等（{@code Use X. `Y` does Z.}）。</li>
     * </ul>
     * 多个标点连写（{@code !!!}、{@code ...}）走 {@code mark+} 整体消费。
     */
    private static final Pattern SENTENCE_END = Pattern.compile(
            "(?:[。！？]+|[.!?]+(?=\\s+(?=[A-Z0-9#*`\"'\\[(/:\\-])))(?!\\s*[a-z0-9])");

    /** 至少一个空行视为段落边界 */
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");

    /**
     * AsciiDoc 表格区间：从<b>独占一行</b>的 {@code |====} 到下一个同款标记。
     * <p>
     * <b>为什么必须在「分段之前」先扫区间</b>：AsciiDoc 表格内部<b>习惯性夹空行</b>
     * （表头与数据行之间常有一个空行），而 {@link #PARAGRAPH_BREAK} 正是按空行分段的 ——
     * 结果是<b>同一张表格被劈成两个段落</b>：
     * <pre>
     * [cols="3,5,1", stripes=even]        ← 段落 A
     * |====
     * | Property | Description | Default
     *                                     ← 这个空行把表格劈开
     * | spring.ai.retry.max-attempts | ... ← 段落 B（约 2000 token）
     * ...
     * |====
     * </pre>
     * 段落 B 命中 {@link #TABLE_ROW} 被判原子，但它单独就超预算 → 走
     * {@link #expandOversized} 按行切 → <b>{@code |====} 起始标记与表头留在段落 A 的块里，
     * 数据行散落到后面几块</b>。后果是那几块开头是<b>没有表头的裸数据行</b>
     * （{@code | spring.ai.xxx | 描述 | -}），LLM 无从判断每列含义 —— 语义直接降级。
     * 实测 133 张表里 <b>52 张被切断</b>（{@code groq-chat} / {@code openai-chat} /
     * {@code zhipuai-chat} 等整张表全部散架）。
     * <p>
     * 修法是「先圈出表格区间，再在区间<b>之外</b>做段落/句子切分」，
     * 表格区间整体作为一个不可拆单元交给 {@link #pack}。
     */
    private static final Pattern ASCIIDOC_TABLE_BLOCK = Pattern.compile(
            "(?m)^\\|====[ \\t]*$[\\s\\S]*?^\\|====[ \\t]*$");

    /** Markdown 表格区间：连续的 {@code |} 开头的行（至少两行才算表格，单行怕是正文里的竖线） */
    private static final Pattern MD_TABLE_BLOCK = Pattern.compile(
            "(?m)(?:^\\s*\\|[^\\n]*\\|[ \\t]*\\n){2,}");

    /** AsciiDoc 表格的起止标记行（独占一行） */
    private static final Pattern ASCIIDOC_TABLE_MARKER = Pattern.compile("^\\|====$");

    /**
     * 代码块 / 前端模板块 / 缩进代码。命中的段落<b>整段不切句</b> ——
     * 这是为了避开「代码里的点被当句末」，也是上一版遗漏的一类失败模式。
     */
    private static final Pattern CODE_BLOCK = Pattern.compile(
            "^\\s*(?:```|~~~|----|====|\\.\\.\\.\\.|\\[source|<[a-zA-Z/]|\\{\\{|\\{%|\\$ |#include|#\\w+$)");

    /**
     * 表格行：<b>只要行首是 {@code |} 就算</b>（Markdown 与 AsciiDoc 的数据行都以 {@code |} 开头）。
     * <p>
     * 上一版写的是 {@code ^\s*\|.*\|\s*$}（要求<b>行首行尾都有</b> {@code |}），
     * 这对 Markdown 表格成立，但 <b>AsciiDoc 的表格数据行行尾通常没有 {@code |}</b>：
     * <pre>
     * |====
     * | spring.ai.openai.embedding.options.encodingFormat   | The format to return the embeddings in. Can be either float or base64.  | -
     * |====
     * </pre>
     * 于是这些行被判成「普通正文」→ 按句子切开 → {@code ...the embeddings in.} 被当句末，
     * 下一行 {@code Can be either fl...} 变成新单元。后果是：
     * <ul>
     *     <li>同一张表格被切得七零八落（实测一个表格段落碎成 6 个单元）；</li>
     *     <li>切点落在句子内部，覆盖率与句边界两项指标同时下滑。</li>
     * </ul>
     * 行首 {@code |} 是<b>足够强的信号</b> —— 正常英文/中文正文不会以 {@code |} 开头，
     * 放宽到「只判行首」不会误伤正文。
     */
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|");

    /** 每块头部最多携带上一块自身长度的这个比例，防止重叠压过本块正文 */
    private static final double MAX_OVERLAP_RATIO = 0.5;

    /**
     * 表格「整张给」的 token 硬上限。
     * <p>
     * 「表格不被切断」是有价值的，但<b>不能无限让位</b>：实测语料里有连续多张表
     * 被空行连成一片，整体可达 <b>7600 token</b>，逼近 embedding 的 8192 上限 ——
     * 一旦超限，embedding 请求会直接失败（或静默截断），比「表格被切开」严重得多。
     * <p>
     * 取 3000 的理由：约为 chunkSize 的 3.75 倍，仍在 embedding 安全区内；
     * 而实测单张正常表格多在 1000~2300 token（最大的 {@code openai-chat} 表 2256），
     * 3000 足以覆盖真实表格，又能拦住「多表连片」这种异常长的单元。
     * 超过此值的单元退回 {@link #expandOversized} 按行切。
     */
    private static final int TABLE_MAX_TOKENS = 3000;

    /** 每块头部携带上一块的 token 数；0 表示不重叠（退化为「句边界切分」） */
    private final int overlapTokens;

    /**
     * 单块 token 上限。父类的 chunkSize 是 private 无 getter，子类取不到，
     * 这里与 {@code AiAgentConfig#tokenTextSplitter} 的默认构造保持一致（800）。
     * <p>
     * 如果将来换成 {@code new TokenTextSplitter(chunkSize, ...)} 非默认值，
     * <b>必须</b>把同一个值传进来，否则会「按 800 读、按别的值理解」。
     */
    private final int chunkSize;

    public OverlapTokenTextSplitter(int overlapTokens) {
        this(overlapTokens, 800);
    }

    public OverlapTokenTextSplitter(int overlapTokens, int chunkSize) {
        super();
        this.overlapTokens = Math.max(overlapTokens, 0);
        this.chunkSize = chunkSize > 0 ? chunkSize : 800;
    }

    // ------------------------------------------------------------------ 主流程

    @Override
    protected List<String> splitText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Unit> units = toUnits(text);
        if (units.isEmpty()) {
            return new ArrayList<>();
        }

        List<Piece> packed = pack(units);
        return assemble(packed);
    }

    /**
     * 语义单元。{@code unsplittable=true} 表示「即使超过 chunkSize 也不许切开」——
     * 目前只有<b>完整表格</b>会带上这个标记。
     * <p>
     * <b>为什么表格必须整张给</b>：表格的语义<b>只存在于「表头 + 数据行」的组合</b>里。
     * 一旦拆开，后面的块就是一堆 {@code | spring.ai.xxx | 描述 | -}，
     * 没有列名、没有 {@code |====}，LLM 读不出每列是什么 —— 信息在，但不可用。
     * 所以这里<b>宁可让单块超过 chunkSize</b>（embedding 上限是 8192，chunkSize=800 只是习惯值），
     * 也不把表拆开。这与「块数变多、答案跨块」的代价相比，是明显更划算的一侧。
     */
    private static final class Unit {
        final String text;
        final boolean unsplittable;

        Unit(String text, boolean unsplittable) {
            this.text = text;
            this.unsplittable = unsplittable;
        }
    }

    /**
     * 把原文切成「不可再分的语义单元」：先圈出表格区间（整段作为一个单元），
     * 区间之外的文本再按「段落 → 句子」切。
     */
    private List<Unit> toUnits(String text) {
        List<Unit> units = new ArrayList<>();
        for (Segment seg : splitTableSegments(text)) {
            if (seg.table) {
                emit(units, seg.content, true);
                continue;
            }
            for (String paragraph : splitParagraphs(seg.content)) {
                if (paragraph.trim().isEmpty()) {
                    continue;
                }
                if (isAtomicParagraph(paragraph)) {
                    emit(units, paragraph, false);
                } else {
                    for (String sentence : splitSentences(paragraph)) {
                        emit(units, sentence, false);
                    }
                }
            }
        }
        return units;
    }

    /**
     * 把原文切成「表格区 / 普通区」交替的片段序列。
     * <p>
     * 先扫 AsciiDoc 表格（{@code |====} 成对），在剩余文本里再扫 Markdown 表格，
     * 保证两类表格都整体成段。表格区片段中间的空行<b>保留原样</b>（表格内部结构就是内容）。
     */
    private List<Segment> splitTableSegments(String text) {
        List<Segment> stage1 = splitByPattern(text, ASCIIDOC_TABLE_BLOCK);
        List<Segment> out = new ArrayList<>(stage1.size());
        for (Segment s : stage1) {
            if (s.table) {
                out.add(s);
            } else {
                out.addAll(splitByPattern(s.content, MD_TABLE_BLOCK));
            }
        }
        return out;
    }

    private List<Segment> splitByPattern(String text, Pattern pattern) {
        List<Segment> out = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        int start = 0;
        while (m.find()) {
            if (m.start() > start) {
                String head = text.substring(start, m.start());
                if (!head.isEmpty()) {
                    out.add(new Segment(head, false));
                }
            }
            out.add(new Segment(m.group(), true));
            start = m.end();
        }
        if (start < text.length()) {
            out.add(new Segment(text.substring(start), false));
        }
        return out;
    }

    /**
     * 按空行切段。分隔符必须<b>整体归属于后一段</b>（而不是被切成两半分别留在前后段落里）。
     * <p>
     * 上一版是 {@code paragraphs.add(text.substring(start, m.end()))} —— 前一段连带吃掉了
     * 整个分隔符 {@code "\n\n"}，而后一段没有任何前导空白。看起来能拼回原文，
     * 但 {@code join()} 里的 {@code strip()} 会把段落首尾空白抹掉，
     * 于是「段落 A 结尾的 \n\n + 段落 B 开头」被压成一个普通换行 ——
     * <b>连续的两行 Markdown 表格行会因此粘成一行</b>，正文内容真的被改坏（实测踩过）。
     * <p>
     * 改成：{@code [start, m.start())} 是前一段，{@code m.group()} 作为后一段的固定前缀。
     * 这样分隔符是「独立的、不可拆的」前缀，{@code strip()} 只作用于它后面的正文，
     * 拼接后与原文逐字一致。
     */
    private List<String> splitParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        Matcher m = PARAGRAPH_BREAK.matcher(text);
        int start = 0;
        String pendingBreak = "";
        while (m.find()) {
            paragraphs.add(pendingBreak + text.substring(start, m.start()));
            pendingBreak = m.group();
            start = m.end();
        }
        paragraphs.add(pendingBreak + text.substring(start));
        return paragraphs;
    }

    /**
     * 代码块 / 表格段落：整段不可切。
     * <p>
     * 只要段落里有<b>一行</b>命中代码块标记或表格行，整段就不切句 ——
     * 宁可少切，也不要把代码/表格切碎（代码里的 {@code foo.bar()} 一旦被切，
     * 块头就会出现半截代码，这是上一版实测踩过的坑）。
     * <p>
     * 判据要按<b>行首</b>匹配：原文里的 {@code "..."} 引用、行内 {@code `code`}
     * 也可能包含 {@code #} 或 {@code |}，按「行内 find」会误判。
     * <p>
     * <b>⚠️ {@link #TABLE_ROW} 必须用 {@code find()} 而不是 {@code matches()}</b>：
     * {@code matches()} 要求整个字符串被正则消费掉，而 {@code ^\s*\|} 只匹配行首那一段，
     * 于是<b>永远返回 false</b>。这个错会导致「所有表格段落都被判成普通正文」——
     * 表格行被按句号切碎（实测 {@code ...embeddings in.} 后断开，
     * {@code Can be either float or base64.} 掉到下一个单元），
     * 表现为覆盖率失配 + 句边界合格率下滑，而<b>编译期毫无提示</b>。
     * {@code CODE_BLOCK} 一直用的 {@code find()}，所以只有表格那一半静默失效。
     */
    private boolean isAtomicParagraph(String paragraph) {
        boolean sawContent = false;
        for (String line : paragraph.split("\n", -1)) {
            String t = line.strip();
            if (t.isEmpty()) {
                continue;
            }
            sawContent = true;
            if (CODE_BLOCK.matcher(t).find() || TABLE_ROW.matcher(t).find()) {
                return true;
            }
        }
        return !sawContent;
    }

    /** 段内按句末标点切句，标点归属前一句 */
    private List<String> splitSentences(String paragraph) {
        List<String> sentences = new ArrayList<>();
        Matcher m = SENTENCE_END.matcher(paragraph);
        int start = 0;
        while (m.find()) {
            int end = m.end();
            sentences.add(paragraph.substring(start, end));
            start = end;
        }
        if (start < paragraph.length()) {
            sentences.add(paragraph.substring(start));
        }
        return sentences;
    }

    /** 把原文切成「表格区 / 普通区」时用的片段类型 */
    private static final class Segment {
        final String content;
        final boolean table;

        Segment(String content, boolean table) {
            this.content = content;
            this.table = table;
        }
    }

    /** 空白单元直接丢弃，避免产生空块（父类也不会输出空块） */
    private void emit(List<Unit> units, String text, boolean unsplittable) {
        if (text != null && !text.trim().isEmpty()) {
            units.add(new Unit(text, unsplittable));
        }
    }

    // ------------------------------------------------------------------ 装箱

    /**
     * 装箱结果。{@code overlapAllowed=false} 表示这一段是「按 token 硬切」出来的
     * （长代码块 / 无标点长文），没有语义边界可依，<b>不要</b>给它补重叠 ——
     * 否则重叠会整段复制一份，单块 token 直接翻倍（实测：800 → 1600）。
     */
    private static final class Piece {
        final List<Unit> units;
        final boolean overlapAllowed;

        Piece(List<Unit> units, boolean overlapAllowed) {
            this.units = units;
            this.overlapAllowed = overlapAllowed;
        }
    }

    /**
     * 按顺序把单元塞进「累计 token ≤ chunkSize」的块。
     * <p>
     * <b>预算就是 chunkSize，不预留重叠空间</b> —— 这是有意取舍：
     * 若把预算压到 {@code chunkSize - overlapTokens}，正文块会变小，
     * 块数从 589 涨到 663（+12.6%）。块数增加是比「最终块略超 800」严重得多的代价：
     * <ul>
     *     <li>向量表行数↑ → 召回池里候选变多、检索变慢；</li>
     *     <li>原本能装进一块的答案被迫跨两块 —— 这正是重叠想要解决的问题，等于自相矛盾。</li>
     * </ul>
     * embedding 真实上限是 8192，{@code chunkSize=800} 只是习惯值，
     * 因此最终块落在 {@code [chunkSize, chunkSize + overlapTokens]}（≈800~900）完全安全。
     * <p>
     * 单个单元就超过 chunkSize 时分两种走法：
     * <ol>
     *     <li>{@code unsplittable=true}（<b>完整表格</b>）→ <b>独占一块，允许超预算</b>。
     *         表格的语义只在「表头 + 数据行」的组合里，拆开就只剩裸数据行。</li>
     *     <li>否则（长代码块 / 无标点长文）→ 走 {@link #expandOversized}，
     *         <b>先按行边界切，只有单行自身超预算才按 token 硬切</b>。</li>
     * </ol>
     */
    private List<Piece> pack(List<Unit> units) {
        List<Piece> pieces = new ArrayList<>();
        List<Unit> current = new ArrayList<>();
        int currentTokens = 0;

        for (Unit unit : units) {
            int unitTokens = countTokens(unit.text);

            if (unitTokens > chunkSize) {
                if (!current.isEmpty()) {
                    pieces.add(new Piece(current, true));
                    current = new ArrayList<>();
                    currentTokens = 0;
                }
                if (unit.unsplittable && unitTokens <= TABLE_MAX_TOKENS) {
                    // 表格整张给：宁可超预算，也不切。
                    // overlapAllowed 仍为 true —— 表格前面常有一句引导语
                    // （"The prefix `spring.ai.retry` is used as..."），
                    // 让表格块把它带进头部，能显著提升「直接问某个配置项」的召回。
                    pieces.add(new Piece(new ArrayList<>(List.of(unit)), true));
                } else {
                    pieces.addAll(expandOversized(unit));
                }
                continue;
            }

            if (!current.isEmpty() && currentTokens + unitTokens > chunkSize) {
                pieces.add(new Piece(current, true));
                current = new ArrayList<>();
                currentTokens = 0;
            }
            current.add(unit);
            currentTokens += unitTokens;
        }

        if (!current.isEmpty()) {
            pieces.add(new Piece(current, true));
        }
        return pieces;
    }

    /**
     * 拆分「单单元超预算」的大块，并<b>保持每个子单元是一整行</b>。
     * <p>
     * 上一版这里是直接 {@code hardSplit(unit)}（纯 token 切），实测踩到真实缺陷：
     * 大 AsciiDoc 表格（{@code |====} 开头的几百行）会被 {@link #CODE_BLOCK} 判定为
     * 「整段不可切」，成为一个 ~2000+ token 的单元 → 走 token 硬切 →
     * <b>把单词切成两半</b>（实测 {@code Compatible} 被切成 {@code Com} | {@code patible}）。
     * 后果有两层：
     * <ul>
     *     <li>该块的 embedding 里混进 {@code patible} 这种无意义碎片；</li>
     *     <li>检索时 query 含 {@code Compatible}，<b>两半都召不回</b> —— 等于该词彻底失联。</li>
     * </ul>
     * 而行边界是<b>天然存在</b>的（表格一行就是一个语义单元），没有理由跨行硬切。
     * <p>
     * <b>为什么返回 {@code List<Piece>} 而不是 {@code List<String>}</b>：
     * 上一版返回预拼好的大字符串，导致「这个子块」在重叠阶段是一个不可分的整体 ——
     * 回吐它 = 整块 800 token 复制一份，所以只能标 {@code overlapAllowed=false}，
     * 于是<b>表格内部的所有行边界都没有重叠</b>（正是这次覆盖率失配的落点）。
     * 现在每个 Piece 只装「一行」，重叠可以精确地回吐最后 1~2 行，
     * 块体积最多 +2 行（几十 token），却把结构边界补上了。
     * <p>
     * 只有<b>单行自身</b>就超预算时（超长单行代码 / 无换行的长段落）才对该行退化为
     * {@link #hardSplit}，那一部分才标 {@code overlapAllowed=false} ——
     * 此时「切在词中间」无法避免，因为该行没有任何可用的结构边界。
     */
    private List<Piece> expandOversized(Unit unit) {
        // 把换行「归属前一行」：这样 join() 直接串接就与原文逐字一致，
        // 且每个子单元（一行）自带行尾换行 —— 表格行不会被粘成一行。
        String[] raw = unit.text.split("\n", -1);
        List<Unit> lines = new ArrayList<>(raw.length);
        for (int i = 0; i < raw.length; i++) {
            lines.add(new Unit(i < raw.length - 1 ? raw[i] + "\n" : raw[i], false));
        }

        List<Piece> out = new ArrayList<>();
        List<Unit> buffer = new ArrayList<>();
        int bufferTokens = 0;

        for (Unit line : lines) {
            int lineTokens = countTokens(line.text);

            if (lineTokens > chunkSize) {
                // 单行就超预算：先把已攒的行刷出去，再对该行做最后的 token 硬切
                if (!buffer.isEmpty()) {
                    out.add(new Piece(buffer, true));
                    buffer = new ArrayList<>();
                    bufferTokens = 0;
                }
                for (String hard : hardSplit(line.text)) {
                    out.add(new Piece(new ArrayList<>(List.of(new Unit(hard, false))), false));
                }
                continue;
            }

            if (!buffer.isEmpty() && bufferTokens + lineTokens > chunkSize) {
                out.add(new Piece(buffer, true));
                buffer = new ArrayList<>();
                bufferTokens = 0;
            }
            buffer.add(line);
            bufferTokens += lineTokens;
        }

        if (!buffer.isEmpty()) {
            out.add(new Piece(buffer, true));
        }
        if (out.isEmpty()) {
            for (String hard : hardSplit(unit.text)) {
                out.add(new Piece(new ArrayList<>(List.of(new Unit(hard, false))), false));
            }
        }
        return out;
    }

    /**
     * 从大单元的<b>末尾</b>取若干整行，累计 token ≤ budget。
     * <p>
     * 用于「整张表格」这种超预算的不可拆单元：整块回吐会让下一块 token 翻倍，
     * 完全不回吐又浪费（表格末尾几行通常是最具体的配置项）。
     * 折中是按行取尾巴 —— 表格<b>每行自成一个语义单元</b>，取尾部若干行不会切坏内容。
     * <p>
     * 同样<b>把换行归属前一行</b>，与 {@link #expandOversized} 保持一致，
     * 保证回吐片段拼接后与原文逐字一致。
     */
    private List<Unit> tailLines(String text, int budget) {
        String[] raw = text.split("\n", -1);
        List<Unit> lines = new ArrayList<>(raw.length);
        for (int i = 0; i < raw.length; i++) {
            lines.add(new Unit(i < raw.length - 1 ? raw[i] + "\n" : raw[i], false));
        }
        List<Unit> picked = new ArrayList<>();
        int tokens = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            // 表格结构标记（|====）是分隔符而非内容，回吐它只会让下一块开头出现
            // 一个孤立标记（实测：块内 |==== 计数从 6 变 7，成为奇数块）。
            if (ASCIIDOC_TABLE_MARKER.matcher(lines.get(i).text.strip()).matches()) {
                continue;
            }
            int t = countTokens(lines.get(i).text);
            if (tokens + t > budget) {
                break;
            }
            picked.add(0, lines.get(i));
            tokens += t;
            if (tokens >= budget) {
                break;
            }
        }
        return picked;
    }

    /** 按 token 切成 ≤ chunkSize 的片段（只在「单行自身就超预算」时使用） */
    private List<String> hardSplit(String unit) {
        List<String> pieces = new ArrayList<>();
        IntArrayList tokens = ENCODING.encode(unit);
        int total = tokens.size();
        for (int from = 0; from < total; from += chunkSize) {
            int to = Math.min(from + chunkSize, total);
            IntArrayList slice = new IntArrayList(to - from);
            for (int i = from; i < to; i++) {
                slice.add(tokens.get(i));
            }
            pieces.add(ENCODING.decode(slice));
        }
        return pieces;
    }

    // ------------------------------------------------------------------ 补重叠

    /**
     * 拼装最终块：第 i 块的头部追加「第 i-1 块末尾若干个<b>完整句子</b>」。
     * <p>
     * 回吐规则：
     * <ol>
     *     <li>从上一块末尾往前累加单元，直到凑够 {@code overlapTokens}；</li>
     *     <li>回吐量不得超过<b>最终块</b>的 {@value #MAX_OVERLAP_RATIO} ——
     *         否则小块的正文会被自己的重叠淹没（上一版踩过：149 token 的块吃进 88 token 重叠）；</li>
     *     <li>起始单元即使超预算也带上（零重叠比「重叠略长」更糟），但最多带一个、
     *         且不超过 {@code 2×overlapTokens}，避免单个巨大表格/代码块把块顶爆；</li>
     *     <li>上一段是硬切产物（{@code overlapAllowed=false}）时不回吐 ——
     *         它没有语义边界，整段回吐会让块翻倍。</li>
     * </ol>
     * 因为回吐单位是整句，块头的起点与终点<b>都在句边界上</b>。
     * <p>
     * <b>token 上界</b>：正文 ≤ {@code chunkSize}（由 {@link #pack} 保证），
     * 回吐量 ≤ {@code max(overlapTokens, 2×overlapTokens)} = {@code 2×overlapTokens}
     * ⇒ 最终块 ≤ {@code chunkSize + 2×overlapTokens}（默认 800 + 200 = 1000）。
     * 远低于 embedding 的 8192 上限。
     */
    private List<String> assemble(List<Piece> packed) {
        List<String> out = new ArrayList<>(packed.size());
        for (int i = 0; i < packed.size(); i++) {
            String body = join(packed.get(i).units);
            if (i == 0 || overlapTokens <= 0 || !packed.get(i).overlapAllowed) {
                out.add(body);
                continue;
            }

            // 回吐上限：既要 ≤ overlapTokens，也要 ≤ 最终块的一半
            //   head ≤ overlapTokens
            //   head ≤ (body + head) / 2  ⇔  head ≤ body
            int budget = Math.min(overlapTokens, countTokens(body));
            if (budget <= 0) {
                out.add(body);
                continue;
            }

            List<Unit> prev = packed.get(i - 1).units;
            List<Unit> carried = new ArrayList<>();
            int carriedTokens = 0;
            for (int j = prev.size() - 1; j >= 0; j--) {
                Unit unit = prev.get(j);
                int unitTokens = countTokens(unit.text);

                // 起始单元：即使单句超预算也带上（零重叠比「重叠略长」更糟）。
                // 但超预算的单元最多只带一个，且必须 ≤ 2×overlapTokens，
                // 否则一个巨大的表格/代码块会把块顶到 800 + 数百 token（实测到 1091）。
                if (!carried.isEmpty() && carriedTokens + unitTokens > budget) {
                    break;
                }
                if (carried.isEmpty() && unitTokens > 2 * budget) {
                    // 太大的整块（多为「整张表格」）：不整块带，退而回吐它的末尾几行。
                    // 表格最后几行往往就是最具体的配置项，值得带进下一块的开头；
                    // 但整块 2000 token 会让块翻倍，所以按行截取。
                    List<Unit> tailLines = tailLines(unit.text, budget);
                    if (tailLines.isEmpty()) {
                        break;
                    }
                    carried.addAll(0, tailLines);
                    carriedTokens = budget;
                    break;
                }

                carried.add(0, unit);
                carriedTokens += unitTokens;
                if (carriedTokens >= budget) {
                    break;
                }
            }
            if (carried.isEmpty()) {
                out.add(body);
                continue;
            }

            // 块头与本块正文之间补一个换行：块头尾部已是句边界，不会出现半句粘连。
            // 注意不要用 stripLeading() 去动 body —— 它会破坏以 "|" 开头的表格行结构。
            String head = join(carried);
            String separator = body.startsWith("\n") ? "" : System.lineSeparator();
            out.add(head + separator + body);
        }
        return out;
    }

    // ------------------------------------------------------------------ 工具

    private int countTokens(String s) {
        return ENCODING.countTokens(s);
    }

    /**
     * 拼接单元。
     * <p>
     * <b>不要用 {@code String.strip()}</b>：它会把段落自带的空白一起吃掉。
     * 段落一级的空白是<b>结构信息</b>（Markdown 表格靠换行分列、代码块靠换行分行），
     * 抹掉就会把两行表格粘成一行 —— 这是真实的内容损坏，不是格式美观问题（实测踩过）。
     * 这里只清理<b>整个块</b>首尾的空白，内部一个字符都不动。
     */
    private String join(List<Unit> units) {
        StringBuilder sb = new StringBuilder();
        for (Unit u : units) {
            sb.append(u.text);
        }
        // 用正则而不是 String.strip()：语义上是「只削块的最外层」，不碰内部
        return sb.toString()
                .replace("\r\n", "\n")
                .replaceAll("^\\s+", "")
                .replaceAll("\\s+$", "");
    }

    /** 供探针/测试断言使用：暴露单元切分结果 */
    public List<String> units(String text) {
        List<String> out = new ArrayList<>();
        for (Unit u : toUnits(text)) {
            out.add(u.text);
        }
        return out;
    }

    /** 供探针/测试断言使用：暴露切块时用的 token 上限 */
    public int chunkSize() {
        return chunkSize;
    }
}
