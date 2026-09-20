#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
按「最终送进 prompt 的条数 k」离线重算检索层指标。

为什么能离线算、不必重跑：
  EvalHarness 调用精排时把 finalTopK 设成 pool.size()-1，即**只重排、不截断**，
  并把完整排序记进 record.rerankedIds（无精排组则用 poolIds，就是原始召回序）。
  而 chunks-*.jsonl 快照里有每个 chunkId 的正文 → 指纹匹配可以在 Python 里复刻。
  所以「同一个排序的前 k 条」的指标 = 纯计算，零 LLM 调用。

指标口径与 EvalHarness.java 严格一致（norm / hitCount / firstHitRank / averagePrecision）。

用法：
  python analyze-by-k.py --run <eval-xxx.json> [--ks 1,2,3,4,6] [--md out.md]
  python analyze-by-k.py --validate <eval-C-topK4-recall20-llm.json>   # 自校验
"""
import argparse
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RUNS = os.path.normpath(os.path.join(HERE, '..', '_local', 'runs'))

CHARS_PER_TOKEN = 3.28        # 本语料（英文 Spring AI 文档）实测值，见 src/TokenCount.java。
                              # ⚠️ 2026-09-20 订正：原先取 1.5（中英混排口径），把 token 放大了 2.2 倍。
                              # 这里只是「快速估算」；要精确数字请用 TokenCount（jtokkit cl100k_base）。
RETRIEVAL_BUCKETS = ('easy', 'multi_hop', 'false_premise')
BUCKET_CN = {'easy': '简单', 'multi_hop': '推理跳步', 'false_premise': '错误前提'}


def norm(s):
    """与 Java 侧 norm() 一致：小写 + 连续空白压成单空格 + 去首尾空白。"""
    return re.sub(r'\s+', ' ', s or '').strip().lower()


def load_snapshots():
    """chunkId -> (content, charLen)"""
    table = {}
    for f in glob.glob(os.path.join(RUNS, 'chunks-*.jsonl')):
        with open(f, encoding='utf-8') as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                d = json.loads(line)
                table[d['chunkId']] = (d.get('content', ''), d.get('charLen', len(d.get('content', ''))))
    return table


def hit_count(texts, fps, limit):
    """命中指纹数。一个 chunk 可能同时覆盖多个指纹（多跳题）。"""
    remaining = list(fps)
    for i in range(min(limit, len(texts))):
        t = norm(texts[i])
        remaining = [fp for fp in remaining if norm(fp) not in t]
    return len(fps) - len(remaining)


def first_hit_rank(texts, fps):
    """第一个命中任一指纹的位置（1-based）；未命中返回 0。"""
    if not fps:
        return 0
    for i, t in enumerate(texts):
        nt = norm(t)
        for fp in fps:
            if norm(fp) in nt:
                return i + 1
    return 0


def average_precision(texts, fps, k):
    """排名加权 Context Precision（Average Precision 口径），与 Java 侧一致。"""
    remaining = list(fps)
    s, found = 0.0, 0
    n = min(k, len(texts))
    for i in range(n):
        t = norm(texts[i])
        rel = False
        for fp in list(remaining):
            if norm(fp) in t:
                remaining.remove(fp)
                rel = True
        if rel:
            found += 1
            s += found / (i + 1)
    denom = min(len(fps), k)
    return 0.0 if denom == 0 else s / denom


def order_of(rec):
    """该题的排序（id 列表），按可用信息优先级取。

    三种情况：
      rerankedIds 存在  → 精排后的完整排序（harness ≥ 17:59 才写），任意 k 都可用；
      有精排但无 rerankedIds → 退回 finalIds（= 精排后前 topK 条），只对 k ≤ len(finalIds) 有效；
      无精排            → poolIds 就是原始召回序。
    """
    if rec.get('rerankApplied'):
        if rec.get('rerankedIds'):
            return rec['rerankedIds']
        return rec.get('finalIds') or []
    return rec.get('poolIds') or []


def metrics_at(rec, k, snap):
    """某题在最终条数 = k 时的指标。"""
    fps = rec.get('expectedFingerprints') or []
    ids = order_of(rec)[:k]
    texts = [snap[i][0] if i in snap else '' for i in ids]
    hits = hit_count(texts, fps, k)
    rank = first_hit_rank(texts, fps)
    chars = sum(snap[i][1] for i in ids if i in snap)
    return {
        'k': k,
        'hits': hits,
        'recall': hits / len(fps) if fps else 0.0,
        'precision': hits / len(ids) if ids else 0.0,
        'mrr': (1.0 / rank) if rank else 0.0,
        'ctxPrec': average_precision(texts, fps, k),
        'allHit': hits == len(fps),
        'chars': chars,
        'ids': ids,
    }


def mean(xs):
    return sum(xs) / len(xs) if xs else 0.0


def aggregate(recs, k, snap):
    ms = [metrics_at(r, k, snap) for r in recs]
    if not ms:
        return None
    return {
        'n': len(ms),
        'recall': mean([m['recall'] for m in ms]),
        'precision': mean([m['precision'] for m in ms]),
        'mrr': mean([m['mrr'] for m in ms]),
        'ctxPrec': mean([m['ctxPrec'] for m in ms]),
        'allHitRate': mean([1.0 if m['allHit'] else 0.0 for m in ms]),
        'hitRate': mean([1.0 if m['hits'] > 0 else 0.0 for m in ms]),
        'chars': mean([m['chars'] for m in ms]),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--run', default=None)
    ap.add_argument('--validate', default=None)
    ap.add_argument('--ks', default='1,2,3,4,5,6')
    ap.add_argument('--md', default=None)
    args = ap.parse_args()

    snap = load_snapshots()
    ks = [int(x) for x in args.ks.split(',')]

    if args.validate:
        # 自校验：用快照重算 k=topK 的指标，与 harness 落盘的原始值比对
        d = json.load(open(os.path.join(RUNS, args.validate), encoding='utf-8'))
        topk = d['config']['topK']
        bad = 0
        for r in d['records']:
            if not r.get('retrievalScored'):
                continue
            m = metrics_at(r, topk, snap)
            for key, got in (('recall', m['recall']), ('mrr', m['mrr']),
                             ('contextPrecision', m['ctxPrec']), ('hits', m['hits'])):
                if abs(got - r[key]) > 1e-6:
                    bad += 1
                    print(f"  不一致 {r['id']} {key}: 快照={got} 落盘={r[key]}")
        print(f"[校验] {args.validate} k={topk}：{'全部一致 ✅' if bad == 0 else str(bad) + ' 处不一致 ❌'}")
        return 0 if bad == 0 else 1

    d = json.load(open(os.path.join(RUNS, args.run), encoding='utf-8'))
    cfg = d['config']
    recs = [r for r in d['records'] if r.get('retrievalScored')]
    label = f"{d['tag']} (topK={cfg['topK']} recallK={cfg['recallK']} rerank={cfg['rerank']} docChars={cfg.get('rerankDocChars')})"
    print(f"== {label} ==  参与检索评分 {len(recs)} 条")
    # 该 run 的排序信息最多能支撑到 k = 多少（finalIds 路径只到 topK）
    maxk = min((len(order_of(r)) for r in recs), default=0)
    if max(k for k in ks) > maxk:
        ks = [k for k in ks if k <= maxk]
        print(f"   ⚠️ 该 run 只记录了前 {maxk} 条排序，k 只算到 {maxk}")
    print()
    hdr = f"{'k':>3} {'Recall':>9} {'Prec':>9} {'MRR':>9} {'CtxPrec':>9} {'全中率':>9} {'上下文(字符)':>12} {'≈token':>9}"
    print(hdr)
    print('-' * len(hdr))
    rows = {}
    for k in ks:
        a = aggregate(recs, k, snap)
        rows[k] = a
        print(f"{k:>3} {a['recall']*100:>8.2f}% {a['precision']*100:>8.2f}% {a['mrr']*100:>8.2f}% "
              f"{a['ctxPrec']*100:>8.2f}% {a['allHitRate']*100:>8.2f}% {a['chars']:>12.0f} {a['chars']/CHARS_PER_TOKEN:>9.0f}")
    print()
    for b in RETRIEVAL_BUCKETS:
        sub = [r for r in recs if r['bucket'] == b]
        if not sub:
            continue
        print(f"-- {BUCKET_CN[b]} (n={len(sub)}) --")
        for k in ks:
            a = aggregate(sub, k, snap)
            print(f"   k={k}  Recall {a['recall']*100:6.2f}%  MRR {a['mrr']*100:6.2f}%  "
                  f"CtxPrec {a['ctxPrec']*100:6.2f}%  全中率 {a['allHitRate']*100:6.2f}%  ctx {a['chars']:>7.0f} 字符")
        print()

    if args.md:
        out = [f"### {label}", '', '| 最终条数 k | Recall@k | Precision@k | MRR@k | CtxPrecision@k | 全中率 | 上下文均值(字符) | ≈token |',
               '| --- | --- | --- | --- | --- | --- | --- | --- |']
        for k in ks:
            a = rows[k]
            out.append(f"| **{k}** | {a['recall']*100:.2f}% | {a['precision']*100:.2f}% | {a['mrr']*100:.2f}% | "
                       f"{a['ctxPrec']*100:.2f}% | {a['allHitRate']*100:.2f}% | {a['chars']:.0f} | ≈{a['chars']/CHARS_PER_TOKEN:.0f} |")
        out.append('')
        with open(args.md, 'w', encoding='utf-8') as fh:
            fh.write('\n'.join(out) + '\n')
        print(f"[已写出] {args.md}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
