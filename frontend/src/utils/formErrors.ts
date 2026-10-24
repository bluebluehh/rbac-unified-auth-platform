/** 将后端 400 fieldErrors 和 409 唯一键错误映射到 Element Plus 表单项。 */
import type { FormInstance } from 'element-plus'
import type { ApiError } from '@/api/errors'

const CONFLICT_FIELDS: Record<string, string> = {
  USERNAME_EXISTS: 'username',
  ROLE_KEY_EXISTS: 'roleKey',
  APP_KEY_EXISTS: 'appKey',
  NODE_KEY_EXISTS: 'nodeKey',
  PERMISSION_KEY_EXISTS: 'resourceKey',
}

export function applyFieldErrors(form: FormInstance | undefined, error: ApiError): void {
  const fieldErrors =
    error.httpStatus === 400
      ? error.fieldErrors
      : error.httpStatus === 409 && CONFLICT_FIELDS[error.code]
        ? [{ field: CONFLICT_FIELDS[error.code], message: error.message }]
        : []
  if (fieldErrors.length === 0) return

  for (const fieldError of fieldErrors) {
    const field = form?.fields.find(
      (item) => item.propString === fieldError.field || item.propString.endsWith(`.${fieldError.field}`),
    )
    if (!field) continue
    field.validateState = 'error'
    field.validateMessage = fieldError.message
  }

  const firstName = fieldErrors[0]?.field
  if (!firstName) return
  const first = form?.fields.find(
    (item) => item.propString === firstName || item.propString.endsWith(`.${firstName}`),
  )
  if (first?.prop) form?.scrollToField(first.prop)
}
