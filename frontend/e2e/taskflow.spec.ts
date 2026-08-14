import { expect, request as playwrightRequest, test, type APIRequestContext, type Page } from '@playwright/test'

type ApiEnvelope<T> = { code: string | number; message?: string; data: T }
type Session = { token: string; userId: number }
type UserSummary = { userId: number; version: number; status: string }
type TaskSummary = { taskId: number; taskNo: string; title: string; status: string; version: number }
type NotificationSummary = { notificationId: number; sourceMessageId: string; content: string }

const adminUsername = process.env.TASKFLOW_ACCEPTANCE_ADMIN_USERNAME
const adminPassword = process.env.TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD
const e2eUserPassword = process.env.TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD
const configuredRunId = process.env.TASKFLOW_ACCEPTANCE_RUN_ID ?? String(Date.now())
// A restarted Playwright worker gets a new process. Including that process id
// prevents a failed worker's fixture user from colliding with its replacement.
const runId = `${configuredRunId}-${process.pid}`
const taskRunId = runId.replace(/[^A-Za-z0-9_-]/g, '_').toUpperCase().slice(-39)
const taskPrefix = `TASKFLOW_E2E_${taskRunId}`

let api: APIRequestContext
let adminSession: Session
let e2eUser: { username: string; password: string; userId: number; version: number }
const createdTaskIds: number[] = []

test.beforeAll(async () => {
  if (!adminUsername) throw new Error('TASKFLOW_ACCEPTANCE_ADMIN_USERNAME must be supplied; no credential is stored in the repository')
  if (!adminPassword) throw new Error('TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD must be supplied; no password is stored in the repository')
  if (!e2eUserPassword) throw new Error('TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD must be supplied; no password is stored in the repository')

  api = await playwrightRequest.newContext({ baseURL: process.env.TASKFLOW_ACCEPTANCE_BASE_URL ?? process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173' })
  adminSession = await loginApi(adminUsername, adminPassword)
  const username = `e2e_${runId}`
  const created = await api.post('/api/users', {
    headers: authHeader(adminSession.token),
    data: {
      username,
      employeeNo: `E2E${runId}`.slice(0, 64),
      displayName: `E2E User ${runId}`,
      password: e2eUserPassword,
      roleCodes: ['employee'],
    },
  })
  const body = await readApi<UserSummary>(created)
  e2eUser = { username, password: e2eUserPassword, userId: body.userId, version: body.version }
})

test.afterAll(async () => {
  if (!api) return
  try {
    if (e2eUser) {
      const userSession = await loginApi(e2eUser.username, e2eUser.password)
      await api.post('/api/notifications/read-all', { headers: authHeader(userSession.token) })
      await api.patch(`/api/users/${e2eUser.userId}/status`, {
        headers: authHeader(adminSession.token),
        data: { status: 'DISABLED', version: e2eUser.version },
      })
    }
    for (const taskId of [...new Set(createdTaskIds)]) {
      const response = await api.get(`/api/tasks/${taskId}`, { headers: authHeader(adminSession.token) })
      if (!response.ok()) continue
      const task = await readApi<TaskSummary>(response)
      if (task.status === 'DRAFT') {
        await api.delete(`/api/tasks/${taskId}`, {
          headers: authHeader(adminSession.token),
          data: { version: task.version },
        })
      }
    }
  } finally {
    await api.dispose()
  }
})

test('登录成功、进入系统并获取当前用户', async ({ page }) => {
  await loginUi(page, adminUsername, adminPassword!)
  const persistedAccessKeys = await page.evaluate(() => Object.keys(window.localStorage)
    .filter((key) => /token|access/i.test(key)))
  expect(persistedAccessKeys).toEqual([])
  await expect(page.getByText(/System Administrator|管理员|admin/i).first()).toBeVisible()
  const me = await page.request.get('/api/auth/me')
  expect(me.status()).toBe(200)
})

test('未登录访问受保护 API 返回 401，并回到登录页', async ({ page }) => {
  const response = await page.request.get('/api/auth/me')
  expect(response.status()).toBe(401)
  await page.goto('/tasks')
  await expect(page.getByLabel('用户名或工号')).toBeVisible()
})

test('普通用户无审批权限时 UI 隐藏操作且后端返回 403', async ({ page }) => {
  const userSession = await loginApi(e2eUser.username, e2eUser.password)
  const task = await createTask(userSession.token, `${taskPrefix}_FORBIDDEN`, e2eTitle('E2E Forbidden Task'))
  await loginUi(page, e2eUser.username, e2eUser.password)
  await page.goto('/tasks')
  await page.getByRole('button', { name: task.title }).click()
  await expect(page.getByText(task.title).last()).toBeVisible()
  await expect(page.getByRole('button', { name: 'approve', exact: true })).toHaveCount(0)
  const response = await page.evaluate(async ({ taskId, token }) => {
    const result = await fetch(`/api/tasks/${taskId}/approve`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ version: 0 }),
    })
    return result.status
  }, { taskId: task.taskId, token: userSession.token })
  expect(response).toBe(403)
})

test('任务列表、详情、创建和更新真实写链路', async ({ page }) => {
  await loginUi(page, adminUsername, adminPassword!)
  await page.goto('/tasks')
  const taskNo = `${taskPrefix}_CRUD`
  const title = e2eTitle('E2E CRUD Task')
  const updatedTitle = e2eTitle('E2E CRUD Task Updated')
  await page.getByRole('button', { name: '创建任务' }).click()
  const dialog = page.getByRole('dialog', { name: '创建任务' })
  await dialog.getByLabel('任务编号').fill(taskNo)
  await dialog.getByLabel('标题').fill(title)
  await dialog.getByLabel('描述').fill('Created through a real browser flow')
  await submitCreateForm(page)
  await expect(page.getByRole('button', { name: title, exact: true })).toBeVisible()
  const task = await findTask(title)
  createdTaskIds.push(task.taskId)
  await page.getByRole('button', { name: title, exact: true }).click()
  await expect(page.getByText(`${taskNo} · ${title}`)).toBeVisible()
  await page.getByRole('button', { name: '编辑' }).click()
  const editDialog = page.getByRole('dialog', { name: '编辑任务草稿' })
  await editDialog.getByLabel('标题').fill(updatedTitle)
  await editDialog.locator('form button[type="submit"]').click()
  await expect(page.getByText(`${taskNo} · ${updatedTitle}`)).toBeVisible()
})

test('重复提交保护只产生一条创建请求', async ({ page }) => {
  await loginUi(page, adminUsername, adminPassword!)
  await page.goto('/tasks')
  const taskNo = `${taskPrefix}_DUPLICATE`
  const title = e2eTitle('E2E Duplicate Submit Task')
  let requestCount = 0
  let releaseRequest: (() => void) | undefined
  await page.route('**/api/tasks', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    requestCount += 1
    await new Promise<void>((resolve) => { releaseRequest = resolve })
    await route.continue()
  })
  await page.getByRole('button', { name: '创建任务' }).click()
  const dialog = page.getByRole('dialog', { name: '创建任务' })
  await dialog.getByLabel('任务编号').fill(taskNo)
  await dialog.getByLabel('标题').fill(title)
  await submitCreateForm(page)
  await expect.poll(() => requestCount).toBe(1)
  const submitButton = page.locator('.ant-modal-content form button[type="submit"]')
  await expect(submitButton).toHaveClass(/ant-btn-loading/)
  await submitButton.click({ timeout: 1_000 }).catch(() => undefined)
  expect(requestCount).toBe(1)
  releaseRequest?.()
  await expect(page.getByRole('button', { name: title, exact: true })).toBeVisible()
  const task = await findTask(title)
  createdTaskIds.push(task.taskId)
})

test('登出后旧会话失效', async ({ page }) => {
  await loginUi(page, adminUsername, adminPassword!)
  await page.getByRole('button', { name: '退出' }).click()
  await expect(page.getByLabel('用户名或工号')).toBeVisible()
  const response = await page.request.get('/api/auth/me')
  expect(response.status()).toBe(401)
})

test('浏览器真实收到 STOMP 通知消息', async ({ page }) => {
  const playwrightReceivedFrames: string[] = []
  page.on('websocket', (websocket) => {
    websocket.on('framereceived', (data) => {
      const frame = data.payload
      playwrightReceivedFrames.push(typeof frame === 'string' ? frame : `BINARY:${frame.toString('utf8')}`)
    })
  })
  await installWebSocketTracker(page, process.env.TASKFLOW_E2E_DIRECT_WS === 'true')
  await loginUi(page, adminUsername, adminPassword!)
  await openNotificationCenter(page)
  await waitForNotificationSubscription(page)
  const taskNo = `${taskPrefix}_WS`
  const title = e2eTitle('E2E WebSocket Task')
  const task = await createTask(adminSession.token, taskNo, title)
  createdTaskIds.push(task.taskId)
  await submitTask(adminSession.token, task.taskId, task.version)
  await expect.poll(async () => {
    const response = await page.request.get('/api/notifications?page=1&size=50&status=UNREAD')
    const body = await response.json() as ApiEnvelope<{ records: Array<{ content: string }> }>
    return body.data?.records?.some((item) => item.content.includes(taskNo)) ?? false
  }, { timeout: 15_000 }).toBe(true)
  await expect.poll(async () => {
    const debug = await page.evaluate((frames) => {
      const state = window as unknown as { __taskflowWsMessages?: string[]; __taskflowWsStates?: string[]; __taskflowWsSentFrames?: string[] }
      return {
        messages: state.__taskflowWsMessages ?? [],
        states: state.__taskflowWsStates ?? [],
        sentFrames: state.__taskflowWsSentFrames ?? [],
        playwrightReceivedFrames: frames,
      }
    }, playwrightReceivedFrames)
    const notificationsResponse = await page.request.get('/api/notifications?page=1&size=50&status=UNREAD')
    const notificationsBody = await notificationsResponse.json() as ApiEnvelope<{ records: Array<{ content: string }> }>
    const notificationContents = notificationsBody.data?.records?.slice(0, 10).map((item) => item.content) ?? []
    if (!debug.messages.some((item) => item.startsWith('MESSAGE\n') && item.includes(taskNo))) {
      throw new Error(`STOMP debug: ${JSON.stringify({ ...debug, restStatus: notificationsResponse.status(), notificationContents })}`)
    }
    return true
  }, { timeout: 15_000 }).toBe(true)
  await expect(page.getByText(taskNo, { exact: false })).toBeVisible()
})

test('WebSocket 断线重连后通过 HTTP 补拉恢复通知', async ({ page }) => {
  await installWebSocketTracker(page)
  await loginUi(page, adminUsername, adminPassword!)
  await openNotificationCenter(page)
  await waitForNotificationSubscription(page)
  await page.evaluate(() => {
    const sockets = (window as unknown as { __taskflowSockets?: WebSocket[] }).__taskflowSockets ?? []
    sockets[sockets.length - 1]?.close()
  })
  const task = await createTask(adminSession.token, `${taskPrefix}_RECONNECT`, e2eTitle('E2E Reconnect Task'))
  createdTaskIds.push(task.taskId)
  await submitTask(adminSession.token, task.taskId, task.version)
  await expect.poll(async () => {
    const response = await page.request.get('/api/notifications?page=1&size=50&status=UNREAD')
    const body = await response.json() as ApiEnvelope<{ records: Array<{ content: string }> }>
    return body.data.records.some((item) => item.content.includes(task.taskNo))
  }, { timeout: 20_000 }).toBe(true)
  await expect(page.getByText('实时连接')).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText(task.taskNo, { exact: false })).toBeVisible({ timeout: 20_000 })
})

test('附件上传正向链路', async ({ page }) => {
  await loginUi(page, adminUsername, adminPassword!)
  const task = await createTaskThroughApi(`${taskPrefix}_ATTACHMENT`, e2eTitle('E2E Attachment Task'))
  await page.goto('/tasks')
  await page.getByRole('button', { name: task.title, exact: true }).click()
  await expect(page.getByRole('button', { name: '上传附件' })).toBeVisible()
  await page.locator('input[type="file"]').setInputFiles({
    name: 'e2e-proof.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('TaskFlow browser E2E attachment'),
  })
  await expect(page.getByText('e2e-proof.txt')).toBeVisible({ timeout: 15_000 })
})

const targetedIterations = Number.parseInt(process.env.TASKFLOW_E2E_TARGETED_ITERATIONS ?? '10', 10)
for (let iteration = 1; iteration <= targetedIterations; iteration += 1) {
  test(`通知定向闭环第 ${iteration} 次：READY 后单次业务事件收到真实 MESSAGE`, async ({ page }, testInfo) => {
    const browserMessages: string[] = []
    page.on('websocket', (websocket) => {
      websocket.on('framereceived', (data) => {
        const frame = data.payload
        browserMessages.push(typeof frame === 'string' ? frame : `BINARY:${frame.toString('utf8')}`)
      })
    })
    await installWebSocketTracker(page, process.env.TASKFLOW_E2E_DIRECT_WS === 'true')
    await loginUi(page, adminUsername, adminPassword!)
    await openNotificationCenter(page)
    await waitForNotificationSubscription(page)
    const taskNo = `${taskPrefix}_TARGET_${iteration}`
    const task = await createTask(adminSession.token, taskNo, e2eTitle(`E2E Target ${iteration}`))
    await submitTask(adminSession.token, task.taskId, task.version)

    const notification = await findNotificationForTask(taskNo)
    let browserReceivedAt: string | undefined
    await expect.poll(async () => {
      const message = await page.evaluate((notificationId) => {
        const state = window as unknown as { __taskflowWsMessages?: string[] }
        return (state.__taskflowWsMessages ?? []).find((frame) =>
          frame.startsWith('MESSAGE\n') && frame.includes(`"notificationId":${notificationId}`))
      }, notification.notificationId)
      if (!message) {
        throw new Error(`No browser MESSAGE for notificationId=${notification.notificationId}; frames=${JSON.stringify(browserMessages)}`)
      }
      browserReceivedAt = new Date().toISOString()
      return message
    }, { timeout: 15_000 }).toBeTruthy()

    await expect(page.getByText(taskNo, { exact: false })).toBeVisible({ timeout: 10_000 })
    const uiAppliedAt = new Date().toISOString()
    const diagnostics = await page.request.get(`/api/acceptance/notification-diagnostics/${notification.notificationId}`)
    const diagnosticsBody = diagnostics.ok() ? await diagnostics.json() : { status: diagnostics.status() }
    const rawMessage = browserMessages.find((frame) => frame.includes(`"notificationId":${notification.notificationId}`))
    const stompMessage = await page.evaluate((notificationId) => {
      const state = window as unknown as { __taskflowWsMessages?: string[] }
      return (state.__taskflowWsMessages ?? []).find((frame) =>
        frame.startsWith('MESSAGE\n') && frame.includes(`"notificationId":${notificationId}`))
    }, notification.notificationId)
    const uiApplied = await page.getByText(taskNo, { exact: false }).isVisible()
    console.log(JSON.stringify({
      mode: process.env.TASKFLOW_E2E_DIRECT_WS === 'true' ? 'direct' : 'proxy',
      iteration,
      notificationId: notification.notificationId,
      browserReceivedAt,
      uiAppliedAt,
      diagnostics: diagnosticsBody,
      browserCheckpoints: {
        C5_CHROMIUM_RAW_WS_FRAME: Boolean(rawMessage),
        C6_STOMP_CALLBACK: Boolean(stompMessage),
        C7_UI_APPLIED: uiApplied,
      },
    }))
    await testInfo.attach('notification-delivery-trace.json', {
      body: Buffer.from(JSON.stringify({
        mode: process.env.TASKFLOW_E2E_DIRECT_WS === 'true' ? 'direct' : 'proxy',
        iteration,
        notificationId: notification.notificationId,
        browserReceivedAt,
        uiAppliedAt,
        diagnostics: diagnosticsBody,
        browserCheckpoints: {
          C5_CHROMIUM_RAW_WS_FRAME: Boolean(rawMessage),
          C6_STOMP_CALLBACK: Boolean(stompMessage),
          C7_UI_APPLIED: uiApplied,
        },
      }, null, 2)),
      contentType: 'application/json',
    })
  })
}

async function loginApi(username: string, password: string): Promise<Session> {
  const response = await api.post('/api/auth/login', { data: { login: username, password } })
  const body = await readApi<{ accessToken: string; userId: number }>(response)
  return { token: body.accessToken, userId: body.userId }
}

async function loginUi(page: Page, username: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('用户名或工号').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.locator('button[type="submit"]').click()
  await expect(page.getByRole('menuitem', { name: /任务中心/ })).toBeVisible()
}

function authHeader(token: string) { return token ? { Authorization: `Bearer ${token}` } : {} }

async function createTask(token: string, taskNo: string, title: string): Promise<TaskSummary> {
  const response = await api.post('/api/tasks', {
    headers: authHeader(token),
    data: { taskNo, title, description: 'Created by browser E2E fixture', priority: 'MEDIUM' },
  })
  const task = await readApi<TaskSummary>(response)
  createdTaskIds.push(task.taskId)
  return task
}

async function createTaskThroughApi(taskNo: string, title: string) {
  return createTask(adminSession.token, taskNo, title)
}

async function submitTask(token: string, taskId: number, version: number) {
  const response = await api.post(`/api/tasks/${taskId}/submit`, {
    headers: authHeader(token),
    data: { version },
  })
  return readApi<TaskSummary>(response)
}

async function findTask(title: string) {
  const response = await api.get(`/api/tasks?page=1&size=100&title=${encodeURIComponent(title)}`, { headers: authHeader(adminSession.token) })
  const body = await readApi<{ records: TaskSummary[] }>(response)
  const task = body.records.find((item) => item.title === title)
  if (!task) throw new Error(`E2E task not found: ${title}`)
  return task
}

async function findNotificationForTask(taskNo: string): Promise<NotificationSummary> {
  const response = await api.get('/api/notifications?page=1&size=100&status=UNREAD', {
    headers: authHeader(adminSession.token),
  })
  const body = await readApi<{ records: NotificationSummary[] }>(response)
  const notification = body.records.find((item) => item.content.includes(taskNo))
  if (!notification) throw new Error(`Notification for task ${taskNo} not found`)
  return notification
}

async function createTaskThroughUi(page: Page, taskNo: string, title: string) {
  await page.goto('/tasks')
  await page.getByRole('button', { name: '创建任务' }).click()
  const dialog = page.getByRole('dialog', { name: '创建任务' })
  await dialog.getByLabel('任务编号').fill(taskNo)
  await dialog.getByLabel('标题').fill(title)
  await submitCreateForm(page)
  await expect(page.getByRole('button', { name: title, exact: true })).toBeVisible()
}

async function submitCreateForm(page: Page) {
  await page.locator('.ant-modal-content form button[type="submit"]').click()
}

function e2eTitle(base: string) {
  return `${base} ${runId}`
}

async function openNotificationCenter(page: Page) {
  await page.getByRole('button', { name: '打开通知中心' }).click()
  try {
    await expect(page.getByText('实时连接')).toBeVisible({ timeout: 15_000 })
  } catch (error) {
    const debug = await page.evaluate(() => {
      const state = window as unknown as { __taskflowWsMessages?: string[]; __taskflowWsStates?: string[]; __taskflowWsSentFrames?: string[] }
      return {
        messages: state.__taskflowWsMessages ?? [],
        states: state.__taskflowWsStates ?? [],
        sentFrames: state.__taskflowWsSentFrames ?? [],
      }
    })
    throw new Error(`Notification connection debug: ${JSON.stringify(debug)}; cause=${String(error)}`)
  }
}

async function waitForNotificationSubscription(page: Page) {
  await expect.poll(async () => page.evaluate(() => {
    const state = window as unknown as { __taskflowWsMessages?: string[] }
    return (state.__taskflowWsMessages ?? []).some((frame) =>
      frame.startsWith('MESSAGE\n') && frame.includes('SUBSCRIPTION_READY'))
  }), { timeout: 10_000 }).toBe(true)
}

async function installWebSocketTracker(page: Page, directBackend = false) {
  const directBackendPort = process.env.TASKFLOW_E2E_DIRECT_WS_PORT ?? '28080'
  await page.addInitScript((useDirectBackend) => {
    const NativeWebSocket = window.WebSocket
    const sockets: WebSocket[] = []
    const messages: string[] = []
    const states: string[] = []
    const sentFrames: string[] = []
    class TrackedWebSocket extends NativeWebSocket {
      constructor(url: string | URL, protocols?: string | string[]) {
        const actualUrl = useDirectBackend ? (() => {
          const directUrl = new URL(String(url))
          directUrl.port = (window as unknown as { __taskflowDirectWsPort?: string }).__taskflowDirectWsPort ?? '28080'
          return directUrl.toString()
        })() : url
        super(actualUrl, protocols)
        sockets.push(this)
        states.push(`CREATED:${String(actualUrl)}`)
        this.addEventListener('open', () => states.push('OPEN'))
        this.addEventListener('error', () => states.push('ERROR'))
        this.addEventListener('close', (event) => states.push(`CLOSE:${event.code}`))
        this.addEventListener('message', (event) => {
          states.push(`RAW:${typeof event.data}:${event.data?.constructor?.name ?? 'unknown'}`)
          try {
            if (typeof event.data === 'string') {
              messages.push(event.data)
            } else if (event.data instanceof ArrayBuffer) {
              messages.push(new TextDecoder().decode(event.data))
            } else if (ArrayBuffer.isView(event.data)) {
              messages.push(new TextDecoder().decode(event.data.buffer))
            } else if (event.data instanceof Blob) {
              void event.data.text().then((data) => messages.push(data))
            } else {
              void new Response(event.data).text().then((data) => messages.push(data)).catch((error) => {
                states.push(`DATA_ERROR:${String(error)}`)
              })
            }
          } catch (error) {
            states.push(`DECODE_ERROR:${String(error)}`)
          }
        })
        const nativeSend = this.send.bind(this)
        this.send = (data: string | ArrayBufferLike | Blob | ArrayBufferView) => {
          if (typeof data === 'string') sentFrames.push(data)
          nativeSend(data)
        }
      }
    }
    window.WebSocket = TrackedWebSocket
    ;(window as unknown as { __taskflowSockets: WebSocket[]; __taskflowWsMessages: string[]; __taskflowWsStates: string[] }).__taskflowSockets = sockets
    ;(window as unknown as { __taskflowSockets: WebSocket[]; __taskflowWsMessages: string[]; __taskflowWsStates: string[] }).__taskflowWsMessages = messages
    ;(window as unknown as { __taskflowSockets: WebSocket[]; __taskflowWsMessages: string[]; __taskflowWsStates: string[]; __taskflowWsSentFrames: string[] }).__taskflowWsStates = states
    ;(window as unknown as { __taskflowSockets: WebSocket[]; __taskflowWsMessages: string[]; __taskflowWsStates: string[]; __taskflowWsSentFrames: string[] }).__taskflowWsSentFrames = sentFrames
  }, directBackend)
  if (directBackend) {
    await page.addInitScript((port) => {
      ;(window as unknown as { __taskflowDirectWsPort?: string }).__taskflowDirectWsPort = port
    }, directBackendPort)
  }
}

async function readApi<T>(response: { ok(): boolean; status(): number; json(): Promise<unknown> }): Promise<T> {
  const body = await response.json() as ApiEnvelope<T>
  if (!response.ok() || String(body.code) !== '0') {
    throw new Error(`E2E API request failed with HTTP ${response.status()}: ${body.message ?? 'unknown error'}`)
  }
  return body.data
}
