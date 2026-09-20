/**
 * node_modules 完整性校验
 *
 * 为什么需要它：npm install / npm ls 只校验「目录是否存在」，不检查包内文件是否完整。
 * 当 node_modules 被外部进程掏空（目录在、文件没了）时，npm 会认为一切正常。
 * 本脚本按 package-lock.json 逐包校验三层：
 *   ① 包目录是否存在
 *   ② package.json 是否存在且可解析
 *   ③ main / module / bin 声明的入口文件是否存在
 * 并自动跳过「与当前平台不匹配的 optional 依赖」（如 darwin/linux 的原生二进制包）。
 *
 * 用法: node check-nm-integrity.js <项目根目录>
 */
const fs = require('fs');
const path = require('path');

const root = process.argv[2] || '.';
const nm = path.join(root, 'node_modules');
const lockPath = path.join(root, 'package-lock.json');

if (!fs.existsSync(lockPath)) {
  console.error('找不到 package-lock.json: ' + lockPath);
  process.exit(2);
}

const lock = JSON.parse(fs.readFileSync(lockPath, 'utf8'));
const pkgs = lock.packages || {};
const platform = process.platform; // 'win32'
const arch = process.arch;         // 'x64'

const missingDir = [];
const missingPkgJson = [];
const missingEntry = [];
const skippedPlatform = [];
let ok = 0;

/** 判断该包是否属于「当前平台不需要」的 optional 依赖 */
function isForeignPlatform(meta) {
  if (!meta.optional) return false;
  const osList = meta.os;
  const cpuList = meta.cpu;
  if (Array.isArray(osList) && osList.length && !osList.includes(platform)) return true;
  if (Array.isArray(cpuList) && cpuList.length && !cpuList.includes(arch)) return true;
  return false;
}

/** 检查一个入口路径（考虑 npm 的省略扩展名 / 目录 index 规则） */
function entryExists(absBase) {
  const cands = [
    absBase,
    absBase + '.js',
    absBase + '.mjs',
    absBase + '.cjs',
    absBase + '.json',
    path.join(absBase, 'index.js'),
    path.join(absBase, 'index.cjs'),
    path.join(absBase, 'index.mjs'),
  ];
  return cands.some((p) => fs.existsSync(p));
}

for (const [rel, meta] of Object.entries(pkgs)) {
  if (!rel || meta.link) continue;

  const abs = path.join(root, rel);

  if (!fs.existsSync(abs)) {
    if (isForeignPlatform(meta)) skippedPlatform.push(rel);
    else missingDir.push(rel);
    continue;
  }

  const pjPath = path.join(abs, 'package.json');
  if (!fs.existsSync(pjPath)) {
    missingPkgJson.push(rel);
    continue;
  }

  let pj;
  try {
    pj = JSON.parse(fs.readFileSync(pjPath, 'utf8'));
  } catch (e) {
    missingPkgJson.push(rel + '  (package.json 解析失败)');
    continue;
  }

  // 入口检查
  const entries = [];
  if (typeof pj.main === 'string') entries.push(pj.main);
  if (typeof pj.module === 'string') entries.push(pj.module);
  if (typeof pj.bin === 'string') entries.push(pj.bin);
  else if (pj.bin && typeof pj.bin === 'object') {
    Object.values(pj.bin).forEach((v) => typeof v === 'string' && entries.push(v));
  }

  let bad = null;
  for (const e of entries) {
    if (!entryExists(path.join(abs, e))) { bad = e; break; }
  }

  if (bad) missingEntry.push(rel + '  → 入口缺失: ' + bad);
  else ok++;
}

const total = ok + missingDir.length + missingPkgJson.length + missingEntry.length;

console.log('平台: ' + platform + '/' + arch);
console.log('校验包数: ' + total + '（另有 ' + skippedPlatform.length + ' 个异平台 optional 包已跳过）');
console.log('');
console.log('✅ 完整:            ' + ok);
console.log('❌ 包目录缺失:      ' + missingDir.length);
console.log('❌ package.json 缺失: ' + missingPkgJson.length);
console.log('❌ 入口文件缺失:    ' + missingEntry.length);
console.log('');

if (missingDir.length) {
  console.log('--- 包目录缺失（前 40）---');
  missingDir.slice(0, 40).forEach((m) => console.log('  ' + m));
  console.log('');
}
if (missingPkgJson.length) {
  console.log('--- package.json 缺失（前 40）---');
  missingPkgJson.slice(0, 40).forEach((m) => console.log('  ' + m));
  console.log('');
}
if (missingEntry.length) {
  console.log('--- 入口文件缺失（前 40）---');
  missingEntry.slice(0, 40).forEach((m) => console.log('  ' + m));
  console.log('');
}

const broken = missingDir.length + missingPkgJson.length + missingEntry.length;
if (broken === 0) {
  console.log('结论: node_modules 完整 ✅');
  process.exit(0);
} else {
  console.log('结论: node_modules 不完整 ❌  共 ' + broken + ' 个问题');
  process.exit(1);
}
