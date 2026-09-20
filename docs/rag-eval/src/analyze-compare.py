#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
精排组之间的对比分析：逐条胜负、名次迁移矩阵、答案可见率。

答案可见率是本轮的关键解释变量：
  生产类 LlmDocumentPostProcessor.truncate() 先把 chunk 文本的连续空白压成单空格，
  再取前 docChars 个字符。所以「golden 指纹在压缩后文本里的偏移」> docChars
  ⇒ 精排模型根本看不到答案，只能凭开头猜主题。
  把这个偏移分布算出来，就能解释 1200 vs 3600 的指标差。

用法：
  python analyze-compare.py --base <runA.json> --new <runB.json> \
      [--baseChars 1200] [--newChars 3600] [--k 4] [--md out.md]
"""
import argparse
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RUNS = os.path.normpath(os.path.join(HERE, '..', '_local', 'runs'))
BUCKET_CN = {'easy': '简单', 'multi_hop': '推理跳步', 'false_premise': '错误前提'}


def norm(s):
    return re.sub(r'\s+', ' ', s or '').strip().lower()


def flatten(s):
    """与生产 truncate() 的预处理一致：连续空白 → 单空格 + 去首尾。"""
    return re.sub(r'\s+', ' ', s or '').strip()


def load_snapshots():
    table = {}
    for f in glob.glob(os.path.join(RUNS, 'chunks-*.jsonl')):
        with open(f, encoding='utf-8') as fh:
            for line in fh:
                line = line.strip()
                if line:
                    d = json.loads(line)
                    table[d['chunkId']] = d.get('content', '')
    return table


def load(fn):
    return json.load(open(os.path.join(RUNS, fn), encoding='utf-8'))


def order_of(rec):
    if rec.get('rerankApplied'):
        return rec.get('rerankedIds') or rec.get('finalIds') or []
    return rec.get('poolIds') or []


def first_hit_rank(texts, fps):
    if not fps:
        return 0
    for i, t in enumerate(texts):
        nt = norm(t)
        if any(norm(fp) in nt for fp in fps):
            return i + 1
    return 0


def mrr_at(rec, k, snap):
    ids = order_of(rec)[:k]
    texts = [snap.get(i, '') for i in ids]
    r = first_hit_rank(texts, rec.get('expectedFingerprints') or [])
    return 0.0 if r == 0 else 1.0 / r


def answer_offsets(rec, snap):
    """golden 指纹所在 chunk 里，指纹在「压缩后文本」中的偏移。返回 (offset, chunkLen) 列表。"""
    out = []
    for fp in rec.get('expectedFingerprints') or []:
        nfp = norm(fp)
        best = None
        for cid in rec.get('poolIds') or []:
            flat = flatten(snap.get(cid, ''))
            pos = flat.lower().find(nfp)
            if pos >= 0:
                best = (pos, len(flat))
                break
        if best:
            out.append(best)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True)
    ap.add_argument('--new', required=True)
    ap.add_argument('--baseChars', type=int, default=1200)
    ap.add_argument('--newChars', type=int, default=3600)
    ap.add_argument('--k', type=int, default=4)
    ap.add_argument('--md', default=None)
    args = ap.parse_args()

    snap = load_snapshots()
    A, B = load(args.base), load(args.new)
    a = {r['id']: r for r in A['records'] if r.get('retrievalScored')}
    b = {r['id']: r for r in B['records'] if r.get('retrievalScored')}
    ids = [i for i in a if i in b]
    k = args.k

    out = []
    w = out.append

    # ---------- 1. 逐条胜负 ----------
    win = lose = tie = 0
    win_ids, lose_ids = [], []
    wb, lb = {}, {}
    for i in ids:
        ma, mb = mrr_at(a[i], k, snap), mrr_at(b[i], k, snap)
        if mb > ma + 1e-9:
            win += 1
            win_ids.append((i, ma, mb, a[i]['bucket']))
            wb[a[i]['bucket']] = wb.get(a[i]['bucket'], 0) + 1
        elif mb < ma - 1e-9:
            lose += 1
            lose_ids.append((i, ma, mb, a[i]['bucket']))
            lb[a[i]['bucket']] = lb.get(a[i]['bucket'], 0) + 1
        else:
            tie += 1
    w(f"### 逐条胜负（{A['tag']} → {B['tag']}，判据 = MRR@{k}）")
    w('')
    w(f"- 变好：**{win}** 条（" + '，'.join(f'{BUCKET_CN.get(x, x)} {v}' for x, v in wb.items()) + '）')
    w(f"- 变差：**{lose}** 条（" + '，'.join(f'{BUCKET_CN.get(x, x)} {v}' for x, v in lb.items()) + '）')
    w(f"- 持平：**{tie}** 条")
    w(f"- 合计 {win + lose + tie} 条；净改善 **{win - lose}** 条")
    w('')
    if win_ids:
        w('变好的题（按 MRR 提升幅度排序，前 12 条）：')
        w('')
        w('| 题号 | 桶 | MRR 前 | MRR 后 | 变化 |')
        w('| --- | --- | --- | --- | --- |')
        for i, ma, mb, bk in sorted(win_ids, key=lambda x: -(x[2] - x[1]))[:12]:
            w(f'| {i} | {BUCKET_CN.get(bk, bk)} | {ma*100:.1f}% | {mb*100:.1f}% | +{(mb-ma)*100:.1f}pt |')
        w('')
    if lose_ids:
        w('变差的题（前 12 条）：')
        w('')
        w('| 题号 | 桶 | MRR 前 | MRR 后 | 变化 |')
        w('| --- | --- | --- | --- | --- |')
        for i, ma, mb, bk in sorted(lose_ids, key=lambda x: (x[2] - x[1]))[:12]:
            w(f'| {i} | {BUCKET_CN.get(bk, bk)} | {ma*100:.1f}% | {mb*100:.1f}% | {(mb-ma)*100:.1f}pt |')
        w('')

    # ---------- 2. 名次迁移矩阵（池内名次 → 精排后名次）----------
    w(f"### 名次迁移矩阵（{B['tag']}：原始召回序名次 → 精排后名次）")
    w('')
    mig = {}
    for i in ids:
        r = b[i]
        pool_rank = r.get('goldRankInPool') or 0
        idsk = order_of(r)[:k]
        texts = [snap.get(x, '') for x in idsk]
        new_rank = first_hit_rank(texts, r.get('expectedFingerprints') or [])
        key = pool_rank if pool_rank else 0
        mig.setdefault(key, {'->rank1': 0, '->rank2-4': 0, '->挤出topK': 0, 'n': 0})
        mig[key]['n'] += 1
        if new_rank == 1:
            mig[key]['->rank1'] += 1
        elif 2 <= new_rank <= k:
            mig[key]['->rank2-4'] += 1
        else:
            mig[key]['->挤出topK'] += 1
    w('| 池内名次 | 题数 | 精排后 rank1 | rank2-4 | 挤出 topK（未命中） |')
    w('| --- | --- | --- | --- | --- |')
    for rk in sorted(mig):
        m = mig[rk]
        label = '0（池里没 gold）' if rk == 0 else str(rk)
        w(f"| {label} | {m['n']} | {m['->rank1']} | {m['->rank2-4']} | {m['->挤出topK']} |")
    w('')
    build = sum(m['->rank1'] for rk, m in mig.items() if rk >= 2)
    destroy = mig.get(1, {}).get('->rank2-4', 0) + mig.get(1, {}).get('->挤出topK', 0)
    w(f"- **建设力**：原始序 rank≥2 被提到 rank1 的题 = **{build}**")
    w(f"- **破坏力**：原始序 rank1 被打下去的题 = **{destroy}**")
    w('')

    # ---------- 3. 答案可见率 ----------
    w('### 答案可见率（精排模型能看到答案的题占比）')
    w('')
    w('> 口径：把 chunk 文本按生产 `truncate()` 的方式压成单空格后，')
    w('> 算 golden 指纹在其中的字符偏移；偏移 > 截断长度 ⇒ 精排看不见答案。')
    w('')
    w('| 截断长度 | 能看见答案的指纹占比 | 偏移中位数 | p90 | 最大偏移 |')
    w('| --- | --- | --- | --- | --- |')
    for chars, tag in ((args.baseChars, A['tag']), (args.newChars, B['tag'])):
        offs = []
        for i in ids:
            offs += [o for o, _ in answer_offsets(a[i] if tag == A['tag'] else b[i], snap)]
        if not offs:
            continue
        offs.sort()
        visible = sum(1 for o in offs if o <= chars) / len(offs)
        med = offs[len(offs) // 2]
        p90 = offs[int(len(offs) * 0.9)]
        w(f"| {chars}（{tag}） | **{visible*100:.1f}%** | {med} | {p90} | {offs[-1]} |")
    w('')

    text = '\n'.join(out)
    print(text)
    if args.md:
        with open(args.md, 'w', encoding='utf-8') as fh:
            fh.write(text + '\n')
        print(f'[已写出] {args.md}')


if __name__ == '__main__':
    sys.exit(main())
