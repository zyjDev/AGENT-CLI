#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 runs/eval-*.json 生成评测报告用的 markdown 数据段。

为什么单独写这个脚本：
  1. EvalHarness 只输出原始 metrics，不做「跨组对比」——那是报告的事。混进 harness 会让
     评测程序承担呈现职责，也让「改报告」变成「改评测代码」；
  2. 上下文规模（送 prompt 的字符数）在 harness 里没统计，但每条 record 都存了 finalIds，
     而 chunk 快照里有 charLen —— 离线 join 即可算出，不必为此重跑（重跑要花钱且慢）。

用法：
  python make-report.py                 # 打印全部表格
  python make-report.py --md <out.md>   # 写出 markdown 片段
"""
import argparse
import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RUNS = os.path.normpath(os.path.join(HERE, '..', '_local', 'runs'))

# 组的顺序 = 报告里表格的行顺序，刻意按「基线 → 扩池不精排 → 精排 → topK 消融」排
GROUPS = [
    ('A',   'eval-A-topK4-recall4-none.json',      '无精排',         4,  4),
    ('C0',  'eval-C0-topK4-recall20-none.json',    '扩池20·不精排',  4,  20),
    ('C',   'eval-C-topK4-recall20-llm.json',      'LLM精排·截断1200', 4,  20),
    ('C3600', 'eval-C3600-topK4-recall20-llm.json', 'LLM精排·截断3600', 4, 20),
    ('B8',  'eval-B8-topK8-recall8-none.json',     '无精排',         8,  8),
    ('B10', 'eval-B10-topK10-recall10-none.json',  '无精排',         10, 10),
    ('B20', 'eval-B20-topK20-recall20-none.json',  '无精排',         20, 20),
]

BUCKET_CN = {
    'easy': '简单',
    'multi_hop': '推理跳步',
    'false_premise': '错误前提',
    'no_answer': '无答案',
    'retrievalOverall': '检索层总体',
    'allBuckets': '全部样本',
}

# 本语料（英文 Spring AI 文档）实测值，见 src/TokenCount.java。
# ⚠️ 2026-09-21 订正：原先取 1.5（中英混排口径），把 token 放大了 2.2 倍。
#    这里只是「快速估算」；要精确数字请用 TokenCount（jtokkit cl100k_base）。
#    改这个值必须同步 analyze-by-k.py 的同名常量，否则两份报告对不上。
CHARS_PER_TOKEN = 3.28


def load_chunk_lens():
    """chunkId -> charLen。两份快照合并，key 不冲突（UUID）。"""
    lens = {}
    for f in glob.glob(os.path.join(RUNS, 'chunks-*.jsonl')):
        with open(f, encoding='utf-8') as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                d = json.loads(line)
                lens[d['chunkId']] = d.get('charLen', len(d.get('content', '')))
    return lens


def ctx_chars(record, lens):
    """该 query 最终送进 prompt 的检索片段字符数。

    ⚠️ 口径：只算「检索片段」，不含 system prompt / 用户问题 / 记忆消息。
    报告里必须写清，否则会被误读成端到端 token 用量。
    """
    return sum(lens.get(i, 0) for i in (record.get('finalIds') or []))


def load_group(fname):
    p = os.path.join(RUNS, fname)
    if not os.path.exists(p):
        return None
    with open(p, encoding='utf-8') as fh:
        return json.load(fh)


def pct(x):
    return f"{x * 100:.2f}%" if isinstance(x, (int, float)) else "-"


def pp(x):
    """百分点。"""
    return f"{x * 100:+.2f}pt" if isinstance(x, (int, float)) else "-"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--md', default=None, help='写出 markdown 片段到该路径')
    args = ap.parse_args()

    lens = load_chunk_lens()
    loaded = []
    for tag, fname, desc, topk, reck in GROUPS:
        d = load_group(fname)
        if d is None:
            print(f'[跳过] {tag}：{fname} 不存在')
            continue
        loaded.append((tag, desc, topk, reck, d))

    if not loaded:
        print('没有找到任何 eval-*.json，先跑评测。')
        return

    out = []
    w = out.append

    def avg_ctx(d):
        cs = [ctx_chars(r, lens) for r in d['records'] if r.get('retrievalScored')]
        return sum(cs) / len(cs) if cs else 0.0

    # ---------- 表 1：总体对比 ----------
    w('### 表 1 · 检索层总体对比')
    w('')
    w('> 参与检索评分的是 90 条（简单 50 + 推理跳步 30 + 错误前提 10）。')
    w('> 无答案桶（10 条）没有 golden chunk 可锚，不参与检索层指标。')
    w('')
    w('| 组 | 配置 | Recall@topK | Precision@topK | **MRR@topK** | **CtxPrecision@topK** | 全中率 | 池内召回率 | 上下文均值(字符) |')
    w('| --- | --- | --- | --- | --- | --- | --- | --- | --- |')
    for tag, desc, topk, reck, d in loaded:
        s = d['summary']['retrievalOverall']
        w(f"| **{tag}** | {desc} topK={topk} recallK={reck} | {pct(s['recall'])} | {pct(s['precision'])} | "
          f"{pct(s['mrr'])} | {pct(s['contextPrecision'])} | {pct(s['allHitRate'])} | "
          f"{pct(s.get('poolRecall'))} | {avg_ctx(d):.0f} |")
    w('')

    # ---------- 表 2：分桶 ----------
    w('### 表 2 · 分桶明细')
    w('')
    w('> Context Precision 是**排名加权**口径（Average Precision）：命中越靠前分越高，')
    w('> 不是简单的「相关条数 / k」。')
    w('')
    w('| 桶 | 组 | n | Recall | MRR | CtxPrecision | 全中率 |')
    w('| --- | --- | --- | --- | --- | --- | --- |')
    for b in ['easy', 'multi_hop', 'false_premise']:
        for tag, desc, topk, reck, d in loaded:
            s = d['summary'].get(b) or {}
            if not s.get('nScored'):
                continue
            w(f"| {BUCKET_CN.get(b, b)} | {tag} | {s['nScored']} | {pct(s['recall'])} | "
              f"{pct(s['mrr'])} | {pct(s['contextPrecision'])} | {pct(s['allHitRate'])} |")
    w('')

    # ---------- 表 3：排序瓶颈量化 ----------
    c0 = load_group('eval-C0-topK4-recall20-none.json')
    a = load_group('eval-A-topK4-recall4-none.json')
    if c0:
        scored = [r for r in c0['records'] if r.get('retrievalScored')]
        lost = [r for r in scored if r['poolRecall'] == 1.0 and r['finalGoldRank'] == 0]
        partial = [r for r in scored if 0 < r['poolRecall'] < 1.0]
        w('### 表 3 · 排序瓶颈量化（C0 组：召回池 20，但不精排）')
        w('')
        w(f'- 候选池（20 条）里**含正确片段**的题：**{len(scored) - len([r for r in scored if r["poolRecall"] == 0])}'
          f' / {len(scored)}**（池内召回率 {pct(c0["summary"]["retrievalOverall"].get("poolRecall"))}）')
        w(f'- 其中**被 top4 截断挤出**的题：**{len(lost)} / {len(scored)}**（{pct(len(lost) / len(scored))}）'
          f' —— 正确片段明明召回到了，却排不进送 prompt 的 4 条')
        w(f'- 部分命中的题（多片段答案只召回一部分）：**{len(partial)} / {len(scored)}**')
        w(f'- 正确片段在池中的平均名次：**{c0["summary"]["retrievalOverall"].get("goldRankInPoolAvg", 0):.2f}**')
        w('')
        if lost:
            w('被挤出时，正确片段在池中的名次分布：')
            ranks = {}
            for r in lost:
                ranks[r['goldRankInPool']] = ranks.get(r['goldRankInPool'], 0) + 1
            w('')
            w('| 池中名次 | ' + ' | '.join(str(k) for k in sorted(ranks)) + ' |')
            w('| --- | ' + ' | '.join('---' for _ in sorted(ranks)) + ' |')
            w('| 题数 | ' + ' | '.join(str(ranks[k]) for k in sorted(ranks)) + ' |')
            w('')
        w('**理论上限**：若精排完美，这 90 条的 Recall@4 上限就是池内召回率 '
          f'**{pct(c0["summary"]["retrievalOverall"].get("poolRecall"))}**，MRR@4 上限同为该值'
          '（池里没 gold 的题，再好的排序也救不回来）。')
        w('')

    # ---------- 表 4：A → C 逐条胜负 ----------
    c = load_group('eval-C-topK4-recall20-llm.json')
    if a and c:
        amap = {r['id']: r for r in a['records']}
        win = lose = tie = 0
        win_b, lose_b = {}, {}
        for r in c['records']:
            if not r.get('retrievalScored'):
                continue
            ra = amap.get(r['id'])
            if not ra or not ra.get('retrievalScored'):
                continue
            da, dc = ra['mrr'], r['mrr']
            if dc > da + 1e-9:
                win += 1
                win_b[r['bucket']] = win_b.get(r['bucket'], 0) + 1
            elif dc < da - 1e-9:
                lose += 1
                lose_b[r['bucket']] = lose_b.get(r['bucket'], 0) + 1
            else:
                tie += 1
        total = win + lose + tie
        w('### 表 4 · 精排逐条胜负（A vs C，判据 = MRR 变化）')
        w('')
        w(f'- MRR 变好：**{win}** 条（' + '，'.join(f'{BUCKET_CN.get(k, k)} {v}' for k, v in win_b.items()) + '）')
        w(f'- MRR 变差：**{lose}** 条（' + '，'.join(f'{BUCKET_CN.get(k, k)} {v}' for k, v in lose_b.items()) + '）')
        w(f'- 持平：**{tie}** 条')
        if total:
            w(f'- 合计 {total} 条；净改善 **{win - lose}** 条（{pct((win - lose) / total)}）')
        w('')
        w('> `MRR 变差` 是精排的真实代价 —— LLM 打分有噪声，会把本来排对的打下去。')
        w('> 只报净提升、不报变差条数，被追问「有没有变差的」时会答不上来。')
        w('')

        # ---------- 表 4b：单跳 vs 多跳（指标适用性）----------
        w('### 表 4b · MRR 不适用于多跳题（重要指标方法论）')
        w('')
        w('把 90 条按「答案需要几个片段」拆开看，会得到方向相反的结论：')
        w('')
        w('| 分组 | n | 指标 | A 组 | C 组 | 变化 |')
        w('| --- | --- | --- | --- | --- | --- |')
        single = [r for r in a['records'] if r.get('retrievalScored') and r['bucket'] in ('easy', 'false_premise')]
        multi = [r for r in a['records'] if r.get('retrievalScored') and r['bucket'] == 'multi_hop']
        for label, rs in (('单跳题（简单+错误前提）', single), ('多跳题（需 2 个片段）', multi)):
            if not rs:
                continue
            ids = {r['id'] for r in rs}
            cs = [r for r in c['records'] if r.get('id') in ids and r.get('retrievalScored')]
            for metric, cn in (('mrr', 'MRR@4'), ('allHitRate', '全中率'),
                               ('contextPrecision', 'CtxPrecision@4'), ('recall', 'Recall@4')):
                if metric == 'allHitRate':
                    va = sum(1 for r in rs if r['allHit']) / len(rs)
                    vc = sum(1 for r in cs if r['allHit']) / len(cs) if cs else 0
                else:
                    va = sum(r[metric] for r in rs) / len(rs)
                    vc = sum(r[metric] for r in cs) / len(cs) if cs else 0
                w(f"| {label} | {len(rs)} | {cn} | {pct(va)} | {pct(vc)} | {pp(vc - va)} |")
        w('')
        w('> **多跳题的 MRR 下降不是精排变差**：MRR 只奖励「第一个命中」，而多跳题要的是')
        w('> 「两个片段都覆盖」。精排把第二个片段提前、第一个推后，MRR 就掉，但全中率和')
        w('> Context Precision 同时上升 —— 后者才是多跳场景该看的指标。')
        w('> 只看 MRR 会得出「精排让多跳变差」的错误结论。')
        w('')

        # 精排运行健康度
        sa = a['summary']
        sc = c['summary']
        w('### 表 5 · 精排运行健康度（确认精排真的跑了）')
        w('')
        w('| 指标 | A 组 | C 组 |')
        w('| --- | --- | --- |')
        w(f"| 平均精排耗时 | {sa.get('avgRerankMs', 0):.0f} ms | **{sc.get('avgRerankMs', 0):.0f} ms** |")
        w(f"| 排序与原始召回序完全一致的题数 | {sa.get('rerankOrderUnchangedCount', '-')} | "
          f"**{sc.get('rerankOrderUnchangedCount', '-')}** |")
        w('')
        w('> `排序未变` 的题数包含两种情况：① 本来第 1 名就对（精排无需改动）；')
        w('> ② 精排超时/异常走了降级分支。**必须结合平均耗时一起看** —— ')
        w('> 若平均耗时贴齐超时值，说明是成片降级而不是「本来就排对了」。')
        w('')

    # ---------- 表 6：上下文成本 ----------
    w('### 表 6 · 上下文成本（只算检索片段，不含 system prompt / 用户问题 / 记忆）')
    w('')
    w(f'| 组 | 最终条数 | 上下文均值(字符) | 约合 token（按 {CHARS_PER_TOKEN} 字符/token 估算） |')
    w('| --- | --- | --- | --- |')
    for tag, desc, topk, reck, d in loaded:
        s = d['summary']['retrievalOverall']
        ac = avg_ctx(d)
        w(f"| {tag} | {topk} | {ac:.0f} | ≈{ac / CHARS_PER_TOKEN:.0f} |")
    w('')

    text = '\n'.join(out)
    print(text)
    if args.md:
        with open(args.md, 'w', encoding='utf-8') as fh:
            fh.write(text + '\n')
        print(f'\n[已写出] {args.md}')


if __name__ == '__main__':
    main()
