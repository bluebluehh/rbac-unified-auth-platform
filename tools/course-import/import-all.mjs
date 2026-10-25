import fs from 'node:fs';
import path from 'node:path';
import { project, digest, runPrepared } from './run-prepared.mjs';

const args = process.argv.slice(2);
const index = args.indexOf('--source-root');
if (index < 0 || !args[index + 1]) throw Error('用法：node tools/course-import/import-all.mjs --source-root <本地员工导入包解压目录> [--apply]');
const root = path.resolve(args[index + 1]);
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'package-manifest.json'), 'utf8'));
const payloadBytes = fs.readFileSync(path.join(root, 'import-payload.json'));
if (digest(payloadBytes) !== manifest.payloadSha256) throw Error('员工导入包摘要不匹配，已停止。');
const data = JSON.parse(payloadBytes);
if (data.ruleVersion !== 'DEPTJOB-20261024-V3.3' || data.users.length !== 10000 || data.organizations.length !== 111
    || data.roles.length !== 43 || data.roles.reduce((n, role) => n + role.permissionKeys.length, 0) !== 651) throw Error('员工导入包数量或版本不匹配。');
runPrepared({ payloadBytes, directory: path.resolve(process.env.COURSE_IMPORT_DIR ?? path.join(project, '.local/course-import')),
  sourceRoot: root, apply: args.includes('--apply') });
