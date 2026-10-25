import fs from 'node:fs';
import path from 'node:path';
import { project, runPrepared } from './run-prepared.mjs';

// Public, non-personal registry. No source Excel or employee plan is required.
const payloadBytes = fs.readFileSync(path.join(project, 'db/course-catalog-v3.3.json'));
const data = JSON.parse(payloadBytes);
if (data.roles.length !== 43 || data.permissions.length !== 220 || data.users.length || data.organizations.length
    || data.roles.reduce((n, role) => n + role.permissionKeys.length, 0) !== 651) throw Error('Unexpected catalog contents');
runPrepared({ payloadBytes, directory: path.resolve(process.env.COURSE_IMPORT_DIR ?? path.join(project, '.local/course-catalog')),
  apply: process.argv.includes('--apply') });
