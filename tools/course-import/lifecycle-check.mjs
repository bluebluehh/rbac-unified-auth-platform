import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';

if (process.env.RBAC_ALLOW_TEST_FIXTURES !== '1') {
  throw new Error('此回归脚本会创建临时账号和角色。仅在独立验收环境设置 RBAC_ALLOW_TEST_FIXTURES=1 后运行。');
}

const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const dir = path.join(project, '.local/course-import');
const deployment = JSON.parse(fs.readFileSync(path.join(dir, 'deployment.json'), 'utf8'));
const common = deployment.roles.find(r => r.roleKey === 'COMMON_EMPLOYEE').id;
const companyId = deployment.organizations.find(d => d.sourceName === '总公司').id;
const deptId = deployment.organizations.find(d => d.sourceName === '财务部').id;
const run = String(Date.now()).slice(-9);
const results = [];
async function api(method, route, body, token, version) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (version !== undefined) headers['If-Match'] = `"${version}"`;
  const response = await fetch('http://127.0.0.1:8080/api/v1' + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: response.status, body: await response.json() };
}
function check(name, response, status = 200, predicate = () => true) {
  const passed = response.status === status && predicate(response.body);
  results.push({ name, passed, status: response.status, code: response.body.code });
  assert.ok(passed, name + ': ' + JSON.stringify(response.body));
  return response.body.data;
}
const admin = check('管理员登录', await api('POST', '/auth/login', { username: 'ADMIN', password: 'Admin@2026rbac' })).token;
async function account(suffix, roles) {
  const username = `QA_LIFE_${run}_${suffix}`;
  let user = check('创建独立合成账号 ' + suffix, await api('POST', '/users', { username, realName: '生命周期合成验收', companyId, deptId, positionName: '初始合成岗位' }, admin), 201);
  const assigned = check('预分配角色 ' + suffix, await api('PUT', `/users/${user.id}/roles`, { roleIds: roles }, admin, user.version));
  const initial = randomBytes(16).toString('hex') + 'a!';
  const password = randomBytes(16).toString('hex') + 'b!';
  user = check('初始化临时密码 ' + suffix, await api('POST', `/users/${user.id}/password-reset`, { temporaryPassword: initial }, admin, assigned.version));
  const limited = check('受限登录 ' + suffix, await api('POST', '/auth/login', { username, password: initial })).token;
  check('首次改密 ' + suffix, await api('PUT', '/auth/password', { currentPassword: initial, newPassword: password }, limited));
  const token = check('普通登录 ' + suffix, await api('POST', '/auth/login', { username, password })).token;
  return { ...user, username, password, token };
}
try {
  const permissions = check('读取技术权限目录', await api('GET', '/permissions?size=100', undefined, admin)).records;
  const editor = check('创建仅编辑角色', await api('POST', '/roles', { roleKey: `QA_PROFILE_EDITOR_${run}`, roleName: '验收：仅编辑资料' }, admin), 201);
  check('配置编辑角色不含分配角色权限', await api('PUT', `/roles/${editor.id}/permissions`, { permissionIds: permissions.filter(p => ['platform:user:account:edit', 'platform:user:account:view'].includes(p.permKey)).map(p => p.id) }, admin, editor.version));
  const target = await account('TARGET', [common]);
  const operator = await account('EDITOR', [editor.id]);
  let user = check('读取目标资料', await api('GET', `/users/${target.id}`, undefined, admin));
  check('修改姓名保留角色', await api('PATCH', `/users/${target.id}`, { realName: '生命周期合成验收改名' }, admin, user.version));
  check('姓名变化未清角色', await api('GET', `/users/${target.id}/roles`, undefined, admin), 200, b => b.data.roleIds.includes(common));
  user = check('重新读取版本', await api('GET', `/users/${target.id}`, undefined, admin));
  check('缺分配权限不能调岗', await api('PATCH', `/users/${target.id}`, { positionName: '受阻的合成岗位' }, operator.token, user.version), 403);
  check('拒绝后原角色保持', await api('GET', `/users/${target.id}/roles`, undefined, admin), 200, b => b.data.roleIds.includes(common));
  check('拒绝后原会话保持', await api('GET', '/auth/me', undefined, target.token));
  const moved = check('管理员调岗成功', await api('PATCH', `/users/${target.id}`, { positionName: '新合成岗位' }, admin, user.version), 200, b => BigInt(b.data.version) === BigInt(user.version) + 1n);
  check('调岗清空全部旧角色', await api('GET', `/users/${target.id}/roles`, undefined, admin), 200, b => b.data.roleIds.length === 0);
  check('调岗撤销旧会话', await api('GET', '/auth/me', undefined, target.token), 401);
  check('旧版本不能恢复角色', await api('PUT', `/users/${target.id}/roles`, { roleIds: [common] }, admin, user.version), 412);
  check('管理员可用新版本明确重授', await api('PUT', `/users/${target.id}/roles`, { roleIds: [common] }, admin, moved.version));
  user = check('读取停用前版本', await api('GET', `/users/${target.id}`, undefined, admin));
  check('用户停用不再空指针', await api('PATCH', `/users/${target.id}/status`, { status: 'DISABLED' }, admin, user.version));
  check('停用后不能登录', await api('POST', '/auth/login', { username: target.username, password: target.password }), 401);
  const me = check('读取管理员版本', await api('GET', '/auth/me', undefined, admin));
  check('调岗不能清空最后管理员角色', await api('PATCH', `/users/${me.id}`, { positionName: '不应写入的岗位' }, admin, me.version), 409, b => b.code === 'LAST_ADMIN_REQUIRED');
  await api('POST', '/auth/logout', undefined, operator.token);
  console.log(JSON.stringify({ passed: true, assertions: results.length }));
} finally {
  await api('POST', '/auth/logout', undefined, admin);
  fs.writeFileSync(path.join(dir, 'lifecycle-verification.json'), JSON.stringify({ results, passed: results.every(r => r.passed) }, null, 2));
}
