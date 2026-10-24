import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';

if (process.env.RBAC_ALLOW_TEST_FIXTURES !== '1') {
  throw new Error('测试账号已从日常演示库清理。仅在独立验收环境设置 RBAC_ALLOW_TEST_FIXTURES=1 后运行。');
}

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const dir = path.join(root, '.local/course-import');
const input = JSON.parse(fs.readFileSync(path.join(dir, 'payload.json'), 'utf8'));
const deployment = JSON.parse(fs.readFileSync(path.join(dir, 'deployment.json'), 'utf8'));
const roleIds = new Map(deployment.roles.map(r => [r.roleKey, r.id]));
const orgIds = new Map(deployment.organizations.map(d => [d.sourceName, d.id]));
const permKeys = new Map(deployment.permissions.map(p => [p.sourcePermKey, p.permKey]));
const samples = [
  { username: 'QA_COURSE_ACCOUNTANT', name: '课程演示：财务经办', dept: '财务部', roles: ['COMMON_EMPLOYEE', 'FIN_ACCOUNT_SOURCE'] },
  { username: 'QA_COURSE_FIN_MANAGER', name: '课程演示：财务负责人', dept: '财务部', roles: ['COMMON_EMPLOYEE', 'FIN_MANAGER_SOURCE'] },
  { username: 'QA_COURSE_ENGINEER', name: '课程演示：工程经办', dept: '工程技术部', roles: ['COMMON_EMPLOYEE', 'ENGINEERING_STAFF_SOURCE', 'GIS_STAFF_SOURCE'] },
];
const credentialsPath = path.join(dir, '演示账号.json');
const credentials = fs.existsSync(credentialsPath) ? JSON.parse(fs.readFileSync(credentialsPath, 'utf8')) : [];
const checks = [];
async function api(method, route, body, token, version) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (version !== undefined) headers['If-Match'] = `"${version}"`;
  const response = await fetch('http://127.0.0.1:8080/api/v1' + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: response.status, body: await response.json() };
}
function ok(response, status = 200) {
  assert.equal(response.status, status, JSON.stringify(response.body));
  assert.equal(response.body.code, 'OK');
  return response.body.data;
}
const sorted = a => [...new Set(a)].sort();
const admin = ok(await api('POST', '/auth/login', { username: 'ADMIN', password: 'Admin@2026rbac' })).token;
try {
  for (const sample of samples) {
    let saved = credentials.find(c => c.username === sample.username);
    if (!saved) {
      const found = ok(await api('GET', `/users?keyword=${sample.username}`, undefined, admin));
      assert.equal(found.records.length, 0, 'Existing demo account is not owned by this script; do not reset its password');
      let user = ok(await api('POST', '/users', { username: sample.username, realName: sample.name, companyId: orgIds.get('总公司'), deptId: orgIds.get(sample.dept), positionName: '课程合成演示' }, admin), 201);
      const beforePolicy = ok(await api('GET', '/auth/permissions', undefined, admin)).policyVersion;
      const assigned = ok(await api('PUT', `/users/${user.id}/roles`, { roleIds: sample.roles.map(k => roleIds.get(k)) }, admin, user.version));
      const afterPolicy = ok(await api('GET', '/auth/permissions', undefined, admin)).policyVersion;
      assert.equal(BigInt(afterPolicy), BigInt(beforePolicy) + 1n);
      const temporary = randomBytes(18).toString('base64url') + '!aA7';
      const password = randomBytes(18).toString('base64url') + '!bB8';
      user = ok(await api('POST', `/users/${user.id}/password-reset`, { temporaryPassword: temporary }, admin, assigned.version));
      const limited = ok(await api('POST', '/auth/login', { username: sample.username, password: temporary }));
      assert.equal(limited.restricted, true);
      ok(await api('PUT', '/auth/password', { currentPassword: temporary, newPassword: password }, limited.token));
      assert.equal((await api('GET', '/auth/apps', undefined, limited.token)).status, 401);
      assert.equal((await api('POST', '/auth/login', { username: sample.username, password: temporary })).status, 401);
      saved = { username: sample.username, name: sample.name, password, roleKeys: sample.roles, note: '仅本机课程演示账号，已完成首次改密；不是源员工账号' };
      credentials.push(saved);
      fs.writeFileSync(credentialsPath, JSON.stringify(credentials, null, 2));
    }
    const login = ok(await api('POST', '/auth/login', { username: sample.username, password: saved.password }));
    assert.equal(login.restricted, false);
    const token = login.token;
    const permissions = ok(await api('GET', '/auth/permissions', undefined, token));
    const expected = sorted(input.roles.filter(r => sample.roles.includes(r.roleKey)).flatMap(r => r.permissionKeys).map(k => permKeys.get(k)));
    assert.deepEqual(sorted(permissions.permissionKeys), expected);
    const apps = ok(await api('GET', '/auth/apps', undefined, token));
    assert.deepEqual(sorted(apps.map(a => a.appKey)), sorted(expected.map(k => k.split(':')[0])));
    assert.ok(!apps.some(a => ['platform', 'source_admin'].includes(a.appKey)));
    const finance = apps.find(a => a.appKey === 'finance');
    if (finance) {
      const filtered = ok(await api('GET', `/auth/permissions?applicationId=${finance.id}`, undefined, token));
      assert.deepEqual(sorted(filtered.permissionKeys), expected.filter(k => k.startsWith('finance:')));
      assert.equal(filtered.permissionKeys.length, sample.username.endsWith('ACCOUNTANT') ? 3 : 17);
    }
    assert.equal((await api('GET', '/permissions', undefined, token)).status, 403);
    assert.equal((await api('GET', '/demo/projects', undefined, token)).status, 403);
    assert.equal((await api('POST', '/demo/projects/submit', undefined, token)).status, 403);
    checks.push({ username: sample.username, roles: sample.roles, permissionCount: expected.length, appKeys: apps.map(a => a.appKey).sort(), protectedAdminAndDemoEndpointsDenied: true });
    ok(await api('POST', '/auth/logout', undefined, token));
  }
  fs.writeFileSync(path.join(dir, 'demo-verification.json'), JSON.stringify({ passed: true, samples: checks }, null, 2));
  console.log(JSON.stringify({ passed: true, samples: checks }));
} finally {
  await api('POST', '/auth/logout', undefined, admin);
}
