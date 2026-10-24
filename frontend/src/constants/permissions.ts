/**
 * 固定四段式技术权限码（文档 §10.1 / §10.10）。
 * 红线：按钮权限一律使用此常量，不从显示名称拼权限。
 */
export const P = {
  user: {
    view: 'platform:user:account:view',
    create: 'platform:user:account:create',
    edit: 'platform:user:account:edit',
    status: 'platform:user:account:status',
    resetPassword: 'platform:user:account:reset_password',
    assignRole: 'platform:user:account:assign_role',
  },
  dept: {
    view: 'platform:org:department:view',
    create: 'platform:org:department:create',
    edit: 'platform:org:department:edit',
    status: 'platform:org:department:status',
  },
  role: {
    view: 'platform:role:role:view',
    create: 'platform:role:role:create',
    edit: 'platform:role:role:edit',
    status: 'platform:role:role:status',
    grant: 'platform:role:role:grant',
  },
  app: {
    view: 'platform:app:application:view',
    create: 'platform:app:application:create',
    edit: 'platform:app:application:edit',
    status: 'platform:app:application:status',
  },
  permission: {
    view: 'platform:resource:permission:view',
    create: 'platform:resource:permission:create',
    status: 'platform:resource:permission:status',
  },
  log: {
    view: 'platform:audit:log:view',
  },
  demo: {
    view: 'engineering:demo:project:view',
    submit: 'engineering:demo:project:submit',
  },
} as const
