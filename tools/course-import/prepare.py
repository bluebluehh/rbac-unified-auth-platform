"""Read-only source verification and API-import payload preparation. Never edits Excel."""
from pathlib import Path
from collections import Counter
import argparse, csv, hashlib, json, re
from openpyxl import load_workbook

PROJECT = Path(__file__).resolve().parents[2]

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def read_excel(path):
    book = load_workbook(path, read_only=True, data_only=True)
    sheet = book.active
    sheet.reset_dimensions()
    rows = list(sheet.values)
    book.close()
    return rows

def read_csv(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))

def prepare(root, output):
    source = root / '交付文档/部门岗位权限映射_V3.3'
    config_path = source / '角色配置与编码基线.json'
    config = json.loads(config_path.read_text(encoding='utf-8'))
    assert config['ruleVersion'] == 'DEPTJOB-20261024-V3.3'
    assert config['deploymentPolicy']['courseOnly'] and not config['deploymentPolicy']['productionAllowed']
    manifests = list(config['sourceManifests'])
    for entry in manifests:
        assert digest(root / entry['file']) == entry['sha256'], f"Source changed: {entry['file']}"
    for name in ['角色配置与编码基线.json', '员工角色卡片计划.csv', '部门岗位角色卡片完整对照.csv', '角色原子权限逐项对照.csv']:
        manifests.append({'file': str((source / name).relative_to(root)), 'sha256': digest(source / name)})

    organizations = read_excel(root / '市政公司部门信息表.xlsx')[1:]
    employee_rows = read_excel(root / '市政公司员工信息表.xlsx')[1:]
    function_rows = read_excel(root / '市政公司应用软件功能权限清单.xlsx')
    plans = read_csv(source / '员工角色卡片计划.csv')
    traces = read_csv(source / '角色原子权限逐项对照.csv')
    combos = read_csv(source / '部门岗位角色卡片完整对照.csv')
    assert len(organizations) == 111 and len(employee_rows) == len(plans) == 10000
    assert len(config['roles']) == 43 and len(combos) == 869
    assert len({row[4] for row in employee_rows}) == 54
    assert Counter((r[2], r[3], r[4]) for r in employee_rows) == Counter({(r['company'], r['department'], r['positionName']): int(r['employeeCount']) for r in combos})
    plan_map = {p['username']: p for p in plans}
    assert len(plan_map) == 10000
    org_names = {r[0] for r in organizations}
    assert len(org_names) == 111
    companies = {str(r[2]) for r in employee_rows}
    orgs = []
    for name, _manager_name, parent in organizations:
        assert parent is None or parent in org_names
        node_type = 'COMPANY' if name in companies else 'PROJECT_DEPT' if '项目经理部' in name else 'DEPARTMENT'
        orgs.append({'sourceName': name, 'parentSourceName': parent, 'nodeType': node_type,
                     'nodeKey': 'COURSE_ORG_' + hashlib.sha256(name.encode()).hexdigest()[:20].upper()})

    grants = {key: [] for key in config['roles']}
    trace_counts = Counter()
    for trace in traces:
        trace_counts[trace['status']] += 1
        cell = str(function_rows[int(trace['sourceRow']) - 1][ord(trace['sourceColumn']) - 65] or '')
        assert cell == trace['sourceText']
        if '本人' in cell or '本部门' in cell:
            assert trace['status'] == 'DEFERRED_SCOPE'
        if trace['status'] != 'COURSE_PERMISSION_DISPLAY_ONLY':
            continue
        assert trace['roleKey'] in grants and trace['permKey']
        assert not trace['permKey'].startswith(('platform:', 'source_admin:'))
        assert trace['action'] in re.split('[、，,]', cell)
        grants[trace['roleKey']].append(trace['permKey'])
    assert sum(map(len, grants.values())) == 651
    assert all(len(values) == len(set(values)) for values in grants.values())
    role_names = ['员工公共权限','公司领导职责','综合办公负责人','综合办公经办','党群负责人','党群经办',
                  '人事负责人','人事综合','薪酬绩效','招聘培训','财务负责人','会计','出纳','预算成本','财务综合',
                  '市场经营负责人','招投标经办','合同经办','经营成本','工程管理负责人','工程管理经办',
                  'GIS负责人','GIS经办','安全质量负责人','安全质量综合','现场安全','现场质量',
                  '物资设备负责人','物资综合','设备管理','采购经办','仓储管理','审计负责人','审计经办',
                  '工会负责人','工会经办','项目负责人','项目技术','项目质量','项目安全','项目物资','项目成本','项目资料档案']
    assert len(role_names) == len(grants)
    roles = [{'roleKey': key, 'roleName': name, 'permissionKeys': grants[key]}
             for key, name in zip(grants, role_names)]
    permission_keys = {p['permKey'] for p in config['permissions']}
    assert len(permission_keys) == len(config['permissions'])
    assert set().union(*(set(v) for v in grants.values())) <= permission_keys
    users = []
    for name, raw_username, company, department, position, phone in employee_rows:
        username = str(raw_username).strip().upper()
        assert re.fullmatch('[A-Z0-9_]{1,50}', username)
        plan = plan_map[username]
        assert [company, department, position] == [plan['company'], plan['department'], plan['positionName']]
        assert plan['ruleVersion'] == config['ruleVersion'] and plan['planStatus'] == 'PLANNED_NOT_ASSIGNED'
        org_name = department if company == '总公司' or company == department else company + '-' + department
        assert company in org_names and org_name in org_names, (company, department)
        keys = plan['roleKeys'].split(';')
        assert set(keys) <= set(grants)
        expected_cards = sorted({p['card'] for key in keys for p in config['permissions'] if p['permKey'] in grants[key]})
        assert expected_cards == plan['cardCodes'].split(';') and 'S15' not in expected_cards
        phone_text = None if phone is None else str(phone).strip()
        assert phone_text is None or re.fullmatch(r'\d{11}', phone_text)
        users.append({'username': username, 'realName': name, 'companySourceName': company,
                      'deptSourceName': org_name, 'positionName': position, 'phone': phone_text,
                      'roleKeys': keys, 'cardCodes': expected_cards})
    assert len({u['username'] for u in users}) == 10000
    payload = {'ruleVersion': config['ruleVersion'], 'environment': config['environment'], 'manifests': manifests,
               'organizations': orgs, 'applications': [{'appName': name, **info} for name, info in config['apps'].items()],
               'permissions': config['permissions'], 'roles': roles, 'users': users,
               'summary': {'organizations': 111, 'employees': 10000, 'positions': 54, 'combinations': 869,
                           'roles': 43, 'rolePermissionPairs': 651, 'traceStatuses': dict(trace_counts)}}
    serialized = json.dumps(payload, ensure_ascii=False, indent=2)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(serialized, encoding='utf-8')
    print(json.dumps({'prepared': True, 'payloadSha256': digest(output), **payload['summary']}, ensure_ascii=False))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-root', type=Path, default=PROJECT.parents[1])
    parser.add_argument('--output', type=Path, default=PROJECT / '.local/course-import/payload.json')
    args = parser.parse_args()
    prepare(args.source_root, args.output)
