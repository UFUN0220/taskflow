export type ApiEnvelope<T> = { code: string | number; message?: string; data: T; traceId?: string }
export type Page<T> = { records: T[]; total: number; current: number; size: number; pages: number }

export type CurrentUser = {
  userId: number; username: string; employeeNo: string; displayName: string; departmentId: number | null; authorities: string[]
}
export type LoginResult = { tokenType: string; accessToken: string; expiresIn: number; userId: number; username: string; displayName: string }
export type Project = { projectId: number; projectCode: string; projectName: string; departmentId: number | null; ownerUserId: number; status: string; startAt: string | null; endAt: string | null; version: number }
export type Assignee = { userId: number; displayName: string; assigneeType: string; acceptedAt: string | null }
export type Task = { taskId: number; taskNo: string; title: string; description: string | null; projectId: number | null; departmentId: number | null; creatorId: number; status: string; priority: string; dueAt: string | null; version: number; assignees: Assignee[] }
export type Comment = { commentId: number; taskId: number; authorUserId: number; authorDisplayName: string; commentType: string; content: string; createdAt: string }
export type Attachment = { attachmentId: number; taskId: number; uploaderUserId: number; originalFilename: string; contentType: string; sizeBytes: number; checksum: string | null; status: string; createdAt: string }
export type User = { userId: number; username: string; employeeNo: string; displayName: string; departmentId: number | null; status: string; version: number }
export type Role = { roleId: number; roleCode: string; roleName: string; status: string; builtIn: boolean; version: number; permissionCodes: string[]; scopeType: string | null }
export type DepartmentNode = { id: number; parentId: number | null; departmentCode: string; departmentName: string; path: string; level: number; children: DepartmentNode[] }

const ACCESS_TOKEN_KEYS = ['taskflow.accessToken', 'accessToken']

export function getAccessToken() {
  for (const key of ACCESS_TOKEN_KEYS) {
    const value = window.localStorage.getItem(key)
    if (value) return value
  }
  return null
}

export function setAccessToken(token: string) {
  window.localStorage.setItem(ACCESS_TOKEN_KEYS[0], token)
  window.localStorage.removeItem(ACCESS_TOKEN_KEYS[1])
}

export function clearAccessToken() {
  ACCESS_TOKEN_KEYS.forEach((key) => window.localStorage.removeItem(key))
}

export class ApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly traceId?: string) { super(message) }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, token = getAccessToken()): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { ...init, headers })
  const payload = await response.json().catch(() => ({ message: '服务器返回了无法解析的响应' })) as ApiEnvelope<T>
  if (response.status === 401) {
    clearAccessToken()
    window.dispatchEvent(new Event('taskflow:unauthorized'))
  }
  if (!response.ok || String(payload.code) !== '0') {
    throw new ApiError(payload.message || `请求失败（${response.status}）`, response.status, payload.traceId)
  }
  return payload.data
}

export const login = (loginName: string, password: string) => apiRequest<LoginResult>('/api/auth/login', { method: 'POST', body: JSON.stringify({ login: loginName, password }) }, null)
export const logout = () => apiRequest<null>('/api/auth/logout', { method: 'POST' })
export const currentUser = () => apiRequest<CurrentUser>('/api/auth/me')

export async function listTasks(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams()
  Object.entries({ page: 1, size: 20, ...params }).forEach(([key, value]) => { if (value !== undefined && String(value) !== '') query.set(key, String(value)) })
  return apiRequest<Page<Task>>(`/api/tasks?${query}`)
}
export const getTask = (id: number) => apiRequest<Task>(`/api/tasks/${id}`)
export const createTask = (body: object) => apiRequest<Task>('/api/tasks', { method: 'POST', body: JSON.stringify(body) })
export const updateTask = (id: number, body: object) => apiRequest<Task>(`/api/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(body) })
export const deleteTask = (id: number, version: number) => apiRequest<null>(`/api/tasks/${id}`, { method: 'DELETE', body: JSON.stringify({ version }) })
export const transitionTask = (id: number, action: string, version: number) => apiRequest<Task>(`/api/tasks/${id}/${action}`, { method: 'POST', body: JSON.stringify({ version }) })
export const transferTask = (id: number, body: object) => apiRequest<Task>(`/api/tasks/${id}/transfer`, { method: 'POST', body: JSON.stringify(body) })

export const listProjects = () => apiRequest<Project[]>('/api/projects')
export const createProject = (body: object) => apiRequest<Project>('/api/projects', { method: 'POST', body: JSON.stringify(body) })
export const addProjectMember = (id: number, body: object) => apiRequest<null>(`/api/projects/${id}/members`, { method: 'PUT', body: JSON.stringify(body) })

export const listComments = (taskId: number) => apiRequest<Page<Comment>>(`/api/tasks/${taskId}/comments?page=1&size=50`)
export const addComment = (taskId: number, content: string) => apiRequest<Comment>(`/api/tasks/${taskId}/comments`, { method: 'POST', body: JSON.stringify({ content }) })
export const listAttachments = (taskId: number) => apiRequest<Page<Attachment>>(`/api/tasks/${taskId}/attachments?page=1&size=50`)
export const uploadAttachment = (taskId: number, file: File) => { const body = new FormData(); body.append('file', file); return apiRequest<Attachment>(`/api/tasks/${taskId}/attachments`, { method: 'POST', body }) }
export const deleteAttachment = (taskId: number, attachmentId: number) => apiRequest<null>(`/api/tasks/${taskId}/attachments/${attachmentId}`, { method: 'DELETE' })

export const listUsers = (departmentId?: number) => apiRequest<Page<User>>(`/api/users?page=1&size=100${departmentId ? `&departmentId=${departmentId}` : ''}`)
export const createUser = (body: object) => apiRequest<User>('/api/users', { method: 'POST', body: JSON.stringify(body) })
export const updateUserStatus = (id: number, body: object) => apiRequest<User>(`/api/users/${id}/status`, { method: 'PATCH', body: JSON.stringify(body) })
export const assignUserRoles = (id: number, body: object) => apiRequest<User>(`/api/users/${id}/roles`, { method: 'PUT', body: JSON.stringify(body) })
export const listRoles = () => apiRequest<Role[]>('/api/roles')
export const createRole = (body: object) => apiRequest<Role>('/api/roles', { method: 'POST', body: JSON.stringify(body) })
export const updateRole = (id: number, body: object) => apiRequest<Role>(`/api/roles/${id}`, { method: 'PUT', body: JSON.stringify(body) })
export const listDepartments = () => apiRequest<DepartmentNode[]>('/api/departments/tree')
export const createDepartment = (body: object) => apiRequest<DepartmentNode>('/api/departments', { method: 'POST', body: JSON.stringify(body) })
