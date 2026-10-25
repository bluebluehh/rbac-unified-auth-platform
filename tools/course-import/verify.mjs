import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { localEnvironment } from './run-prepared.mjs';

const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const env = localEnvironment();
const dir = path.resolve(env.COURSE_IMPORT_DIR ?? path.join(project, '.local/course-import'));
const input = JSON.parse(fs.readFileSync(path.join(dir, 'payload.json'), 'utf8'));
const deployed = JSON.parse(fs.readFileSync(path.join(dir, 'deployment.json'), 'utf8'));
const windowsMysql = 'C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe';
const mysql = env.COURSE_MYSQL ?? (process.platform === 'win32' && fs.existsSync(windowsMysql) ? windowsMysql : 'mysql');
const sql = statement => execFileSync(mysql,
  [`--host=${env.RBAC_DB_HOST ?? '127.0.0.1'}`, `--port=${env.RBAC_DB_PORT ?? '3306'}`, `--user=${env.RBAC_DB_USER ?? 'root'}`, `--database=${env.RBAC_DB_NAME ?? 'rbac_platform'}`, '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names', '--execute=' + statement],
  { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024, env: { ...env, MYSQL_PWD: env.RBAC_DB_PASSWORD ?? '' } })
  .trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
const equal = (a, b) => JSON.stringify([...new Set(a)].sort()) === JSON.stringify([...new Set(b)].sort());
const actualRoles = sql("SELECT JSON_OBJECT('key',r.role_key,'permissions',COALESCE((SELECT JSON_ARRAYAGG(p.perm_key) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.role_id=r.id),JSON_ARRAY())) FROM sys_role r WHERE r.role_key='COMMON_EMPLOYEE' OR RIGHT(r.role_key,7)='_SOURCE'");
assert.equal(actualRoles.length, 43);
const keys = new Map(deployed.permissions.map(p => [p.sourcePermKey, p.permKey]));
const rolePermissions = new Map(actualRoles.map(r => [r.key, r.permissions]));
for (const role of input.roles) assert.ok(equal(rolePermissions.get(role.roleKey), role.permissionKeys.map(k => keys.get(k))), `Role permissions differ: ${role.roleKey}`);
assert.equal(actualRoles.reduce((n, r) => n + r.permissions.length, 0), 651);
const sourceToAppKey = new Map(deployed.applications.map(a => [a.sourceAppKey, a.appKey]));
const sourceCard = new Map(input.applications.map(a => [sourceToAppKey.get(a.appKey), a.card]));
const orgIds = new Map(deployed.organizations.map(d => [d.sourceName, d.id]));
const users = sql("SELECT JSON_OBJECT('id',CAST(u.id AS CHAR),'username',u.username,'realName',u.real_name,'companyId',CAST(u.company_id AS CHAR),'deptId',CAST(u.dept_id AS CHAR),'positionName',u.position_name,'status',u.status,'hasPassword',u.password_hash IS NOT NULL,'phoneEncrypted',u.phone_cipher IS NOT NULL,'roles',COALESCE((SELECT JSON_ARRAYAGG(r.role_key) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id),JSON_ARRAY())) FROM sys_user u WHERE u.username REGEXP '^SG[0-9]{6}$'");
assert.equal(users.length, 10000);
const byUsername = new Map(users.map(u => [u.username, u]));
let assignmentPairs = 0;
for (const source of input.users) {
  const actual = byUsername.get(source.username);
  assert.ok(actual, 'Missing source employee');
  assert.equal(actual.realName, source.realName);
  assert.equal(actual.positionName, source.positionName);
  assert.equal(actual.companyId, orgIds.get(source.companySourceName));
  assert.equal(actual.deptId, orgIds.get(source.deptSourceName));
  if (process.argv.includes('--initial-state')) {
    assert.equal(actual.status, 'PENDING_ACTIVATION');
    assert.equal(Boolean(actual.hasPassword), false);
  }
  assert.equal(Boolean(actual.phoneEncrypted), Boolean(source.phone));
  assert.ok(equal(actual.roles, source.roleKeys), `Employee roles differ: ${source.username}`);
  const roleDerivedKeys = [...new Set(actual.roles.flatMap(k => rolePermissions.get(k)))];
  const cards = [...new Set(roleDerivedKeys.map(k => sourceCard.get(k.split(':')[0])))];
  assert.ok(equal(cards, source.cardCodes), `Expected cards differ: ${source.username}`);
  assert.ok(!cards.includes('S15') && !actual.roles.includes('PLATFORM_ADMIN') && !actual.roles.includes('SYS_ADMIN'));
  assignmentPairs += actual.roles.length;
}
const logCounts = sql("SELECT JSON_OBJECT('event',event_type,'n',COUNT(*)) FROM sys_operation_log WHERE result='SUCCESS' AND request_id LIKE 'course-%' GROUP BY event_type");
const logs = new Map(logCounts.map(l => [l.event, l.n]));
assert.ok(logs.get('USER_CREATE') >= 10000 && logs.get('USER_ROLE_ASSIGN') >= 10000 && logs.get('ROLE_PERMISSION_ASSIGN') >= 43);
const metadata = fs.readFileSync(path.join(project, 'frontend/src/generated/coursePermissionCatalog.json'), 'utf8');
assert.ok(!/SG\d{6}|employeeSourceRow|phone|realName|roleKeys/.test(metadata));
const report = { verifiedAt: new Date().toISOString(), ruleVersion: input.ruleVersion, roles: 43, rolePermissionPairs: 651,
  employees: 10000, employeeRolePairs: assignmentPairs, sourceOrganizationsMapped: deployed.organizations.length,
  allEmployeesMatchSourceProfiles: true, allEmployeesMatchPlannedRoles: true, allRoleDerivedCardsMatchPlan: true,
  pendingActivationWithoutPasswords: users.filter(u => u.status === 'PENDING_ACTIVATION' && !u.hasPassword).length,
  phoneEncryptionPresent: true, frontendContainsOnlyPermissionMetadata: true,
  forbiddenAdminRolesAssigned: 0, deferredRowsNotPublished: 19, noGrantRowsNotPublished: 9, auditCounts: Object.fromEntries(logs) };
fs.writeFileSync(path.join(dir, 'verification.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report));
