import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

export const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
export const digest = bytes => createHash('sha256').update(bytes).digest('hex');

export function localEnvironment() {
  const fileEnv = {};
  const envFile = path.join(project, '.env.local');
  if (fs.existsSync(envFile)) {
    for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
      const match = line.trim().match(/^([A-Z][A-Z0-9_]*)=(.*)$/);
      if (match) fileEnv[match[1]] = match[2];
    }
  }
  return { ...fileEnv, ...process.env };
}

export function runPrepared({ payloadBytes, directory, sourceRoot, apply }) {
  const env = { ...localEnvironment(), COURSE_IMPORT_DIR: directory, COURSE_SOURCE_ROOT: sourceRoot ?? project };
  if (apply && env.RBAC_BUSINESS_MODE !== 'PERMISSION_DISPLAY_ONLY') {
    throw Error('先按README配置并启动course展示环境：RBAC_BUSINESS_MODE=PERMISSION_DISPLAY_ONLY');
  }
  fs.mkdirSync(directory, { recursive: true });
  const payloadPath = path.join(directory, 'payload.json');
  if (fs.existsSync(payloadPath) && digest(fs.readFileSync(payloadPath)) !== digest(payloadBytes)) {
    throw Error(`导入目录已有不同的资料，已保留，未覆盖：${directory}`);
  }
  fs.writeFileSync(payloadPath, payloadBytes);
  const importer = path.join(project, 'tools/course-import/import.mjs');
  const run = args => {
    // Show progress during a large import instead of buffering it until completion.
    const result = spawnSync(process.execPath, [importer, ...args], { env, stdio: 'inherit' });
    if (result.error) throw result.error;
    if (result.status !== 0) throw Error('导入已停止。请先处理上方冲突或连接错误，不要重建已有数据库。');
  };
  run([]);
  if (!apply) {
    console.log('当前仅预览；确认资料和目标库后添加 --apply 执行。');
    return;
  }
  const preview = JSON.parse(fs.readFileSync(path.join(directory, 'preview.json'), 'utf8'));
  if (preview.conflicts.length) throw Error('预览存在冲突，未提交修改。');
  run(['--apply', `--confirm=${preview.confirmation}`]);
}
