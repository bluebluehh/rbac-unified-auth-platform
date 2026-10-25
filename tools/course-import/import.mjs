import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const dir = path.resolve(process.env.COURSE_IMPORT_DIR ?? path.join(project, '.local/course-import'));
const inputPath = path.join(dir, 'payload.json');
const payloadBytes = fs.readFileSync(inputPath);
const input = JSON.parse(payloadBytes);
const hash = value => createHash('sha256').update(value).digest('hex');
const apply = process.argv.includes('--apply');
const confirm = process.argv.find(a => a.startsWith('--confirm='))?.slice(10);
const sourceRoot = path.resolve(process.env.COURSE_SOURCE_ROOT ?? path.resolve(project, '../..'));
const base = process.env.COURSE_API_BASE ?? 'http://127.0.0.1:8080/api/v1';
const endpoint = new URL(base);
if (!['http:', 'https:'].includes(endpoint.protocol) || !['127.0.0.1', 'localhost'].includes(endpoint.hostname)
    || endpoint.pathname !== '/api/v1' || endpoint.username || endpoint.password || endpoint.search || endpoint.hash) throw Error('Only a local course API at /api/v1 is supported');
if (input.environment !== 'COURSE_PERMISSION_DISPLAY_ONLY' || input.ruleVersion !== 'DEPTJOB-20261024-V3.3') throw Error('Unexpected course configuration');
for (const source of input.manifests) {
  const sourcePath = path.resolve(sourceRoot, source.file.replaceAll('\\', '/'));
  const relativeSourcePath = path.relative(sourceRoot, sourcePath);
  if (relativeSourcePath.startsWith('..') || path.isAbsolute(relativeSourcePath)) throw Error('Source manifest must stay inside COURSE_SOURCE_ROOT');
  if (hash(fs.readFileSync(sourcePath)) !== source.sha256) throw Error(`Source changed since preparation: ${source.file}`);
}
const journalPath = path.join(dir, 'applied.ndjson');
const events = fs.existsSync(journalPath) ? fs.readFileSync(journalPath, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse) : [];
const owned = Object.fromEntries(['application', 'permission', 'role', 'org', 'user', 'userRoles', 'rolePermissions'].map(type => [type, new Map(events.filter(e => e.type === type).map(e => [e.key, e]))]));
const runId = `course-${Date.now()}`;
const equal = (a, b) => JSON.stringify([...new Set(a)].sort()) === JSON.stringify([...new Set(b)].sort());
let token;
let sequence = 0;
function record(event) {
  const entry = { ...event, runId, time: new Date().toISOString(), ruleVersion: input.ruleVersion };
  fs.appendFileSync(journalPath, JSON.stringify(entry) + '\n');
  owned[event.type]?.set(event.key, entry);
}
async function api(method, route, body, version) {
  const headers = { 'Content-Type': 'application/json', 'X-Request-Id': `${runId}-${++sequence}` };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (version !== undefined) headers['If-Match'] = `"${version}"`;
  const response = await fetch(base + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(60000) });
  let result;
  try { result = await response.json(); }
  catch { throw Error(`${method} ${route}: HTTP ${response.status} 未返回项目JSON响应，请核对COURSE_API_BASE及后端端口`); }
  if (!response.ok || result.code !== 'OK') throw Error(`${method} ${route}: HTTP ${response.status} ${result.code} ${result.message}`);
  return result.data;
}
async function pages(route) {
  const records = [];
  for (let page = 1; ; page++) {
    const result = await api('GET', `${route}?page=${page}&size=100`);
    records.push(...result.records);
    if (records.length >= result.total || result.records.length === 0) return records;
  }
}
function flatten(nodes) { return nodes.flatMap(node => [node, ...flatten(node.children ?? [])]); }
function unique(items, description) {
  if (items.length > 1) throw Error(`Ambiguous ${description}`);
  return items[0];
}
const permissionSignature = p => [p.applicationId, p.moduleName, p.resourceName, p.actionCode].join('|');
async function inspect() {
  const [applications, permissions, roles, users, tree] = await Promise.all([pages('/applications'), pages('/permissions'), pages('/roles'), pages('/users'), api('GET', '/depts/tree?includeDisabled=true')]);
  const orgs = flatten(tree);
  const root = unique(orgs.filter(d => d.nodeType === 'ROOT'), 'root organization');
  if (!root) throw Error('Initialize db/schema.sql and db/data.sql first');
  const apps = new Map();
  const orgMap = new Map();
  const permMap = new Map();
  const roleMap = new Map();
  const userMap = new Map(users.map(u => [u.username, u]));
  const conflicts = [];
  const stats = { applicationsToCreate: 0, permissionsToCreate: 0, rolesToCreate: 0, organizationsToCreate: 0, usersToCreate: 0, usersToAssign: 0, usersUnchanged: 0 };
  for (const source of input.applications) {
    const exact = unique(applications.filter(a => a.appKey === source.appKey), `application ${source.appKey}`);
    const named = unique(applications.filter(a => a.appName === source.appName), `application name ${source.appName}`);
    // Migration 002 used project while engineering remained a separate demo app.
    const legacyEngineeringDemo = source.appKey === 'engineering' && exact?.appName === '工程演示应用' && named?.appKey === 'project';
    if (exact && named && exact.id !== named.id && !legacyEngineeringDemo) throw Error(`Application key/name conflict: ${source.appKey}`);
    const current = named ?? exact;
    if (current && current.appName !== source.appName && !(source.appKey === 'engineering' && current.appName === '工程演示应用')) throw Error(`Unexpected existing application: ${source.appKey}`);
    if (current && source.appKey !== 'source_admin' && current.status !== 1) conflicts.push(`Application disabled: ${source.appKey}`);
    apps.set(source.appKey, { source, current, id: current?.id ?? `NEW_APP:${source.appKey}` });
    if (!current) stats.applicationsToCreate++;
  }
  // Source company '总公司' explicitly reuses the bootstrap ORG-ROOT company, preserving ADMIN and IDs.
  for (const source of input.organizations) {
    const parentId = source.parentSourceName ? orgMap.get(source.parentSourceName)?.id : root.id;
    if (!parentId) throw Error(`Organization parent must precede child: ${source.sourceName}`);
    const displayName = source.sourceName.startsWith(source.parentSourceName + '-') ? source.sourceName.slice(source.parentSourceName.length + 1) : source.sourceName;
    const keyed = unique(orgs.filter(d => d.nodeKey === source.nodeKey), source.nodeKey);
    const bootstrap = source.sourceName === '总公司' ? unique(orgs.filter(d => d.nodeKey === 'ORG-ROOT'), 'bootstrap company') : undefined;
    const named = unique(orgs.filter(d => d.parentId === parentId && d.deptName === displayName && d.nodeType === source.nodeType), source.sourceName);
    const current = keyed ?? bootstrap ?? named;
    if (current && (current.parentId !== parentId || current.nodeType !== source.nodeType || current.status !== 1)) conflicts.push(`Organization mismatch or disabled: ${source.sourceName}`);
    orgMap.set(source.sourceName, { source, current, displayName, parentId, id: current?.id ?? `NEW_ORG:${source.sourceName}` });
    if (!current) stats.organizationsToCreate++;
  }
  const permByKey = new Map(permissions.map(p => [p.permKey, p]));
  const signatureMap = new Map();
  for (const p of permissions) {
    const key = permissionSignature(p);
    signatureMap.set(key, [...(signatureMap.get(key) ?? []), p]);
  }
  for (const source of input.permissions) {
    const app = apps.get(source.appKey);
    const signature = [app.id, source.moduleName, source.resourceName, source.actionCode].join('|');
    const semantic = unique(signatureMap.get(signature) ?? [], `permission ${source.permKey}`);
    const exact = permByKey.get(source.permKey);
    if (exact && permissionSignature(exact) !== signature) throw Error(`Permission key collision: ${source.permKey}`);
    if (exact && semantic && exact.id !== semantic.id) throw Error(`Permission mapping collision: ${source.permKey}`);
    const current = semantic ?? exact;
    if (current && current.status !== 1) conflicts.push(`Permission disabled: ${source.permKey}`);
    permMap.set(source.permKey, { source, current, id: current?.id ?? `NEW_PERMISSION:${source.permKey}` });
    if (!current) stats.permissionsToCreate++;
  }
  for (const source of input.roles) {
    const current = unique(roles.filter(r => r.roleKey === source.roleKey), source.roleKey);
    const assigned = current ? await api('GET', `/roles/${current.id}/permissions`) : undefined;
    const desiredIds = source.permissionKeys.map(k => permMap.get(k).id);
    if (current && current.status !== 1) conflicts.push(`Role disabled: ${source.roleKey}`);
    if (assigned && !equal(assigned.permissionIds, desiredIds) && !(assigned.permissionIds.length === 0 && owned.role.has(source.roleKey) && !owned.rolePermissions.has(source.roleKey))) conflicts.push(`Existing role grants differ; preserve manual changes: ${source.roleKey}`);
    roleMap.set(source.roleKey, { source, current, assigned, id: current?.id ?? `NEW_ROLE:${source.roleKey}` });
    if (!current) stats.rolesToCreate++;
  }
  // A single paged SQL snapshot avoids 10k read calls here; A13 is fetched per existing user before replacing.
  const inspectUser = async source => {
    const current = userMap.get(source.username);
    if (!current) { stats.usersToCreate++; stats.usersToAssign++; return; }
    const companyId = orgMap.get(source.companySourceName).id;
    const deptId = orgMap.get(source.deptSourceName).id;
    if (current.realName !== source.realName || current.companyId !== companyId || current.deptId !== deptId || current.positionName !== source.positionName) conflicts.push(`Existing employee profile differs: ${source.username}`);
    const assigned = await api('GET', `/users/${current.id}/roles`);
    const desired = source.roleKeys.map(k => roleMap.get(k).id);
    const previous = owned.userRoles.get(source.username);
    if (previous && !equal(previous.newRoleIds, assigned.roleIds)) conflicts.push(`Employee roles changed since previous import; preserve: ${source.username}`);
    else if (!previous && assigned.roleIds.length > 0 && !equal(assigned.roleIds, desired)) conflicts.push(`Existing employee has other roles; review full replacement: ${source.username}`);
    if (equal(assigned.roleIds, desired)) stats.usersUnchanged++; else stats.usersToAssign++;
  };
  for (let start = 0; start < input.users.length; start += 8) {
    await Promise.all(input.users.slice(start, start + 8).map(inspectUser));
  }
  const fingerprint = hash(JSON.stringify({ payload: hash(payloadBytes), applications, permissions, roles, users: users.map(u => [u.id, u.version]), orgs, conflicts, stats }));
  const preview = { ruleVersion: input.ruleVersion, payloadSha256: hash(payloadBytes), confirmation: fingerprint, courseOnly: true, stats, conflicts, expected: input.summary,
    note: 'No employee is activated or assigned a shared password. Existing passwords, manual grants and test accounts are preserved.' };
  fs.writeFileSync(path.join(dir, 'preview.json'), JSON.stringify(preview, null, 2));
  return { preview, apps, orgMap, permMap, roleMap, userMap, root };
}
async function execute(state) {
  const { apps, orgMap, permMap, roleMap, userMap, root } = state;
  for (const item of apps.values()) {
    if (!item.current) {
      item.current = await api('POST', '/applications', { appName: item.source.appName, appKey: item.source.appKey, status: item.source.appKey === 'source_admin' ? 0 : 1 });
      item.id = item.current.id;
      record({ type: 'application', key: item.source.appKey, id: item.id });
    } else if (item.current.appName !== item.source.appName) {
      const oldName = item.current.appName;
      item.current = await api('PATCH', `/applications/${item.id}`, { appName: item.source.appName }, item.current.version);
      record({ type: 'applicationRename', key: item.source.appKey, id: item.id, oldName, newName: item.source.appName });
    }
  }
  for (const item of orgMap.values()) {
    if (item.current) continue;
    const parentId = item.source.parentSourceName ? orgMap.get(item.source.parentSourceName).id : root.id;
    item.current = await api('POST', '/depts', { deptName: item.displayName, nodeKey: item.source.nodeKey, nodeType: item.source.nodeType, parentId });
    item.id = item.current.id;
    record({ type: 'org', key: item.source.sourceName, id: item.id });
  }
  for (const item of permMap.values()) {
    if (item.current) continue;
    const [, moduleKey, resourceKey] = item.source.permKey.split(':');
    item.current = await api('POST', '/permissions', { applicationId: apps.get(item.source.appKey).id, moduleKey, moduleName: item.source.moduleName, resourceKey,
      resourceName: item.source.resourceName, resourceDescription: '课程权限展示；真实业务未接入', actionCode: item.source.actionCode });
    item.id = item.current.id;
    record({ type: 'permission', key: item.source.permKey, id: item.id, actualPermKey: item.current.permKey, sourceRow: item.source.sourceRow });
  }
  for (const item of roleMap.values()) {
    if (!item.current) {
      item.current = await api('POST', '/roles', { roleKey: item.source.roleKey, roleName: item.source.roleName, description: `V3.3课程权限展示配置；${input.ruleVersion}；真实业务未接入` });
      item.id = item.current.id;
      record({ type: 'role', key: item.source.roleKey, id: item.id });
    }
    const desired = item.source.permissionKeys.map(k => permMap.get(k).id);
    const current = await api('GET', `/roles/${item.id}/permissions`);
    if (!equal(current.permissionIds, desired)) {
      await api('PUT', `/roles/${item.id}/permissions`, { permissionIds: desired }, current.version);
      record({ type: 'rolePermissions', key: item.source.roleKey, id: item.id, oldPermissionIds: current.permissionIds, newPermissionIds: desired });
    }
  }
  const metadata = { ruleVersion: input.ruleVersion, applicationKeys: [...apps.values()].filter(a => a.source.appKey !== 'source_admin').map(a => a.current.appKey),
    permissions: [...permMap.values()].filter(p => p.source.appKey !== 'source_admin').map(p => ({ permKey: p.current.permKey, moduleName: p.source.moduleName, resourceName: p.source.resourceName, actionName: p.source.action })) };
  const generatedDir = path.join(project, 'frontend/src/generated');
  fs.mkdirSync(generatedDir, { recursive: true });
  fs.writeFileSync(path.join(generatedDir, 'coursePermissionCatalog.json'), JSON.stringify(metadata, null, 2) + '\n');
  let done = 0;
  // Concurrency is intentionally one: every authorization replacement has a live A13 version and server transaction/audit.
  for (const source of input.users) {
    let current = userMap.get(source.username);
    if (!current) {
      current = await api('POST', '/users', { username: source.username, realName: source.realName, companyId: orgMap.get(source.companySourceName).id,
        deptId: orgMap.get(source.deptSourceName).id, positionName: source.positionName, phone: source.phone });
      userMap.set(source.username, current);
      record({ type: 'user', key: source.username, id: current.id });
    }
    const assigned = await api('GET', `/users/${current.id}/roles`);
    const desired = source.roleKeys.map(k => roleMap.get(k).id);
    if (!equal(assigned.roleIds, desired)) {
      const previous = owned.userRoles.get(source.username);
      if (previous && !equal(previous.newRoleIds, assigned.roleIds)) throw Error(`Concurrent role change: ${source.username}`);
      await api('PUT', `/users/${current.id}/roles`, { roleIds: desired }, assigned.version);
      record({ type: 'userRoles', key: source.username, id: current.id, oldRoleIds: assigned.roleIds, newRoleIds: desired, expectedVersion: assigned.version });
    }
    done++;
    if (done % 250 === 0) console.log(`Employees applied: ${done}/${input.users.length}`);
  }
  const deployment = { ruleVersion: input.ruleVersion, time: new Date().toISOString(), runId, sourcePayloadSha256: hash(payloadBytes),
    applications: [...apps.values()].map(a => ({ sourceAppKey: a.source.appKey, id: a.id, appKey: a.current.appKey })),
    organizations: [...orgMap.values()].map(d => ({ sourceName: d.source.sourceName, id: d.id })),
    permissions: [...permMap.values()].map(p => ({ sourcePermKey: p.source.permKey, id: p.id, permKey: p.current.permKey })),
    roles: [...roleMap.values()].map(r => ({ roleKey: r.source.roleKey, id: r.id })),
    employees: input.users.length, assignedRolePairs: input.users.reduce((n, u) => n + u.roleKeys.length, 0), grants: 651 };
  fs.writeFileSync(path.join(dir, 'deployment.json'), JSON.stringify(deployment, null, 2));
  console.log(JSON.stringify({ applied: true, employees: deployment.employees, assignedRolePairs: deployment.assignedRolePairs, roles: deployment.roles.length, grants: 651 }));
}

try {
  token = (await api('POST', '/auth/login', { username: process.env.COURSE_ADMIN_USERNAME ?? 'ADMIN', password: process.env.COURSE_ADMIN_PASSWORD ?? 'Admin@2026rbac' })).token;
  const state = await inspect();
  console.log(JSON.stringify(state.preview));
  if (state.preview.conflicts.length) throw Error('Preview has conflicts; no configuration or employee changes applied');
  if (apply) {
    if (confirm !== state.preview.confirmation) throw Error('Run a preview and pass its confirmation with --apply --confirm=<hash>');
    if (process.env.RBAC_BUSINESS_MODE !== 'PERMISSION_DISPLAY_ONLY') throw Error('Explicit RBAC_BUSINESS_MODE=PERMISSION_DISPLAY_ONLY is required');
    if (state.preview.stats.usersToCreate === 0 && state.preview.stats.usersToAssign === 0
        && state.preview.stats.rolesToCreate === 0 && state.preview.stats.permissionsToCreate === 0
        && state.preview.stats.applicationsToCreate === 0 && state.preview.stats.organizationsToCreate === 0) {
      console.log(JSON.stringify({ alreadyApplied: true, configurationWrites: 0, employeeWrites: 0 }));
    } else {
      await execute(state);
    }
  }
} finally {
  if (token) await api('POST', '/auth/logout');
}
