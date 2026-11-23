// api.js — the one place where the frontend talks to the backend.
// Pages call these functions instead of using axios directly.
// In "mock mode" the functions return fake data, so the app works without a backend.
import axios from 'axios'
// Used only by the mock branch of analyzeMigrationRemote (the browser-side risk check).
// migrationRisk.js also imports this file; that is fine because the functions are only
// called later, after both files have loaded.
import { analyzeMigration } from './migrationRisk.js'

// ---------- Setup ----------

// Our axios "client". baseURL is empty, so we use relative URLs like "/api/sandboxes".
// During development, Vite forwards "/api" requests to http://localhost:8080 (see vite.config.js).
const http = axios.create({ baseURL: '' })

// Keys used to save settings in the browser (the Settings page writes them).
export const USE_MOCK_KEY = 'shadowbase.useMock'
export const USER_ID_KEY = 'shadowbase.userId'
export const CURRENT_SANDBOX_KEY = 'shadowbase.currentSandboxId'

// Returns true when we should use fake data. If the setting was never saved, mock is ON.
// localStorage can throw (e.g. in private mode), so we wrap it in try/catch.
export function isMockMode() {
  try {
    const saved = localStorage.getItem(USE_MOCK_KEY)
    return saved === null ? true : saved === 'true'
  } catch {
    return true
  }
}

// Returns the best error message to show the user.
// The backend sends { message: "..." } in the response body; otherwise use the normal message.
export function getErrorMessage(error) {
  return error?.response?.data?.message || error?.message || 'Something went wrong'
}

// ---------- Mock helpers ----------

// Waits for "ms" milliseconds (pretends the network is slow).
function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

const MOCK_DELAY = 400

// The current time as text, like the backend sends it: "2026-11-16T10:30:00.000"
// (Java's LocalDateTime has no "Z" at the end, so we remove it).
function now() {
  return new Date().toISOString().slice(0, -1)
}

// A fake Docker container ID (64 hex characters), like Testcontainers returns.
function fakeDockerId() {
  return crypto.randomUUID().replaceAll('-', '') + crypto.randomUUID().replaceAll('-', '')
}

// Makes an error that looks like a real axios error from the backend,
// so getErrorMessage works the same way in mock mode.
function mockError(status, message) {
  const error = new Error(message)
  error.response = { status, data: { message } }
  return error
}

// Returns a copy, so pages can't accidentally change our mock "database".
function copy(value) {
  return structuredClone(value)
}

// Builds a fake container from a container request.
function makeMockContainer(sandboxId, request, status) {
  const isPostgres = request.databaseType === 'POSTGRESQL'
  const port = 49152 + Math.floor(Math.random() * 10000) // random port, like Testcontainers
  return {
    id: fakeDockerId(), // the backend uses the Docker container ID (text), not a UUID
    sandboxId,
    imageName: isPostgres ? 'postgres:16-alpine' : 'mysql:8.4', // same default images as the backend
    host: 'localhost',
    port,
    jdbcUrl: isPostgres
      ? `jdbc:postgresql://localhost:${port}/${request.databaseName}`
      : `jdbc:mysql://localhost:${port}/${request.databaseName}`,
    username: request.username,
    databaseName: request.databaseName,
    databaseType: request.databaseType,
    status,
    schemaStatus: 'NOT_INITIALIZED', // NOT_INITIALIZED | IN_PROGRESS | SUCCESS | FAILED
    credentialReference: null,
    dataSourceId: null,
    createdAt: now(),
    updatedAt: now(),
    expiresAt: null,
    stoppedAt: status === 'STOPPED' ? now() : null,
  }
}

// Our fake "database" of sandboxes. It lives as long as the page is open
// (refreshing the browser resets it). It starts with one example sandbox.
// Exported so pages can show the demo sandbox in mock mode when no user ID is saved.
export const DEMO_USER_ID = '00000000-0000-0000-0000-000000000001'
const DEMO_SANDBOX_ID = crypto.randomUUID()
const mockSandboxes = [
  {
    id: DEMO_SANDBOX_ID,
    name: 'demo-sandbox',
    userId: DEMO_USER_ID,
    status: 'READY',
    schemaStatus: 'NOT_INITIALIZED',
    containers: [
      makeMockContainer(
        DEMO_SANDBOX_ID,
        { databaseType: 'POSTGRESQL', databaseName: 'shop', username: 'shadow' },
        'RUNNING',
      ),
    ],
    createdAt: now(),
    updatedAt: now(),
  },
]

// Finds a mock sandbox by id, or throws a 404 like the backend would.
function findMockSandbox(id) {
  const sandbox = mockSandboxes.find((s) => s.id === id)
  if (!sandbox) {
    throw mockError(404, `Sandbox not found with ID: ${id}`)
  }
  return sandbox
}

// The backend refuses to start (or add containers to) a deleted sandbox with a 400 error.
function rejectIfDestroyed(sandbox, action) {
  if (sandbox.status === 'DESTROYED') {
    throw mockError(400, `Cannot ${action} destroyed sandbox: ${sandbox.id}`)
  }
}

// ---------- Sandbox endpoints ----------

// POST /api/sandboxes — create a new sandbox.
export async function createSandbox(name, userId) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    const sandbox = {
      id: crypto.randomUUID(),
      name,
      userId,
      status: 'READY',
      schemaStatus: 'NOT_INITIALIZED',
      containers: [],
      createdAt: now(),
      updatedAt: now(),
    }
    mockSandboxes.push(sandbox)
    return copy(sandbox)
  }
  const response = await http.post('/api/sandboxes', { name, userId, initialContainers: [] })
  return response.data
}

// GET /api/sandboxes/{id} — get one sandbox.
export async function getSandbox(id) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    return copy(findMockSandbox(id))
  }
  const response = await http.get(`/api/sandboxes/${id}`)
  return response.data
}

// GET /api/sandboxes/user/{userId} — list all sandboxes for a user.
export async function listSandboxes(userId) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    return copy(mockSandboxes.filter((s) => s.userId === userId))
  }
  const response = await http.get(`/api/sandboxes/user/${userId}`)
  return response.data
}

// POST /api/sandboxes/{id}/start — start a sandbox and its containers.
export async function startSandbox(id) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    const sandbox = findMockSandbox(id)
    rejectIfDestroyed(sandbox, 'start')
    sandbox.status = 'READY'
    sandbox.containers.forEach((c) => {
      c.status = 'RUNNING'
      c.stoppedAt = null
      c.updatedAt = now()
    })
    sandbox.updatedAt = now()
    return copy(sandbox)
  }
  const response = await http.post(`/api/sandboxes/${id}/start`)
  return response.data
}

// POST /api/sandboxes/{id}/stop — stop a sandbox and its containers.
export async function stopSandbox(id) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    const sandbox = findMockSandbox(id)
    sandbox.status = 'STOPPED'
    sandbox.containers.forEach((c) => {
      c.status = 'STOPPED'
      c.stoppedAt = now()
      c.updatedAt = now()
    })
    sandbox.updatedAt = now()
    return copy(sandbox)
  }
  const response = await http.post(`/api/sandboxes/${id}/stop`)
  return response.data
}

// DELETE /api/sandboxes/{id} — delete a sandbox. The backend returns nothing (204).
// Note: the backend keeps the sandbox record with status DESTROYED (it still shows up
// in lists), and marks its containers REMOVED. The mock does the same.
export async function deleteSandbox(id) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    const sandbox = findMockSandbox(id)
    sandbox.status = 'DESTROYED'
    sandbox.containers.forEach((c) => {
      c.status = 'REMOVED'
      c.updatedAt = now()
    })
    sandbox.updatedAt = now()
    return
  }
  await http.delete(`/api/sandboxes/${id}`)
}

// ---------- Container endpoints ----------

// POST /api/sandboxes/{id}/containers — add a database container to a sandbox.
// containerRequest = { databaseType: "POSTGRESQL" | "MYSQL", databaseName, username, password }
export async function addContainer(sandboxId, containerRequest) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    const sandbox = findMockSandbox(sandboxId)
    rejectIfDestroyed(sandbox, 'add container to')
    // A new container runs only if its sandbox is running.
    const status = sandbox.status === 'READY' ? 'RUNNING' : 'STOPPED'
    const container = makeMockContainer(sandboxId, containerRequest, status)
    sandbox.containers.push(container)
    sandbox.updatedAt = now()
    return copy(container)
  }
  const response = await http.post(`/api/sandboxes/${sandboxId}/containers`, containerRequest)
  return response.data
}

// GET /api/sandboxes/{id}/containers — list a sandbox's containers.
export async function getContainers(sandboxId) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    return copy(findMockSandbox(sandboxId).containers)
  }
  const response = await http.get(`/api/sandboxes/${sandboxId}/containers`)
  return response.data
}

// ---------- Running SQL ----------

// Runs SQL and returns { columns, rows, rowCount, executionTimeMs, message }.
// When not in mock mode, executes against the active sandbox container: POST /api/sandboxes/{sandboxId}/query
export async function runQuery(sql, sandboxId, containerId) {
  if (isMockMode()) {
    await wait(500)
    if (/\bdrop\b/i.test(sql)) {
      throw new Error('ERROR: column "email" does not exist (mock)')
    }
    return {
      columns: ['id', 'name', 'email'],
      rows: [
        [1, 'Asha', 'asha@example.com'],
        [2, 'Ravi', 'ravi@example.com'],
        [3, 'Meena', 'meena@example.com'],
      ],
      rowCount: 3,
      executionTimeMs: 14,
    }
  }

  let activeSandboxId = sandboxId
  if (!activeSandboxId) {
    try {
      activeSandboxId = localStorage.getItem(CURRENT_SANDBOX_KEY)
    } catch {
      // storage unavailable
    }
  }

  if (!activeSandboxId) {
    throw new Error(
      'No sandbox selected. Please go to the Sandboxes page and click "Use in editor" on an active sandbox.'
    )
  }

  const endpoint = containerId
    ? `/api/sandboxes/${activeSandboxId}/containers/${containerId}/query`
    : `/api/sandboxes/${activeSandboxId}/query`

  const response = await http.post(endpoint, {
    sql,
    containerId: containerId || null,
  })
  return response.data
}

// ---------- User Management endpoints ----------

// GET /api/users — list all user accounts.
export async function listUsers() {
  if (isMockMode()) {
    return [{ id: DEMO_USER_ID, emailId: 'demo@shadowbase.local', createdAt: now() }]
  }
  const response = await http.get('/api/users')
  return response.data
}

// POST /api/users — create a new user account.
export async function createUser(emailId, password) {
  if (isMockMode()) {
    return { id: crypto.randomUUID(), emailId, createdAt: now() }
  }
  const response = await http.post('/api/users', { emailId, password })
  return response.data
}

// GET /api/users/demo — fetch or create the default demo user.
export async function getDemoUser() {
  if (isMockMode()) {
    return { id: DEMO_USER_ID, emailId: 'demo@shadowbase.local', createdAt: now() }
  }
  const response = await http.get('/api/users/demo')
  return response.data
}

// ---------- Live metrics + health check ----------
// These always call the real backend (they are used by "Live" dashboard mode and the
// sidebar status dot). The 5-second timeout stops a hung request from waiting forever.

// GET /api/metrics — returns { replayed, failed, errorRate }.
export async function getMetrics() {
  const response = await http.get('/api/metrics', { timeout: 5000 })
  return response.data
}

// GET /api/metrics/errors?limit=50 — returns an array of
// { id, ts, table, operation, sqlState, message } (ts = epoch milliseconds).
export async function getRecentErrors(limit = 50) {
  const response = await http.get('/api/metrics/errors', { params: { limit }, timeout: 5000 })
  return response.data
}

// POST /api/metrics/reset — sets the counters to 0 and clears the error buffer.
// Returns the same shape as getMetrics(): { replayed, failed, errorRate, lastUpdatedTs }.
export async function resetMetrics() {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    return { replayed: 0, failed: 0, errorRate: 0, lastUpdatedTs: Date.now() }
  }
  const response = await http.post('/api/metrics/reset')
  return response.data
}

// Returns true if the backend answers GET /api/users/demo, false otherwise. Never throws.
export async function checkBackendHealth() {
  try {
    await http.get('/api/users/demo', { timeout: 5000 })
    return true
  } catch {
    return false
  }
}

// ---------- Schema Management endpoints ----------

// POST /api/sandboxes/{sandboxId}/schema/import — upload SQL schema file.
export async function importSchemaFile(sandboxId, containerId, file) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    const sandbox = findMockSandbox(sandboxId)
    sandbox.schemaStatus = 'SUCCESS'
    sandbox.containers.forEach((c) => {
      c.schemaStatus = 'SUCCESS'
    })
    return {
      id: crypto.randomUUID(),
      sandboxId,
      containerId,
      fileName: file.name,
      status: 'SUCCESS',
      statementsExecuted: 5,
      executionTimeMs: 120,
    }
  }

  const formData = new FormData()
  formData.append('file', file)
  if (containerId) {
    formData.append('containerId', containerId)
  }

  const endpoint = containerId
    ? `/api/sandboxes/${sandboxId}/containers/${containerId}/schema/import`
    : `/api/sandboxes/${sandboxId}/schema/import`

  const response = await http.post(endpoint, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data
}

// GET /api/sandboxes/{sandboxId}/schema/status — get schema status.
export async function getSchemaStatus(sandboxId, containerId) {
  if (isMockMode()) {
    const sandbox = findMockSandbox(sandboxId)
    return { status: sandbox.schemaStatus || 'NOT_INITIALIZED' }
  }
  const endpoint = containerId
    ? `/api/sandboxes/${sandboxId}/containers/${containerId}/schema/status`
    : `/api/sandboxes/${sandboxId}/schema/status`
  const response = await http.get(endpoint)
  return response.data
}

// ---------- Data Seeding endpoints ----------

// POST /api/sandboxes/{sandboxId}/data-seeds — start background data seeding.
export async function startDataSeed(sandboxId, seedRequest = {}) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    return {
      id: crypto.randomUUID(),
      sandboxId,
      status: 'QUEUED',
      totalTables: 2,
      mode: seedRequest.mode || 'FULL_DATABASE',
    }
  }
  const response = await http.post(`/api/sandboxes/${sandboxId}/data-seeds`, seedRequest)
  return response.data
}

// GET /api/sandboxes/{sandboxId}/data-seeds/{jobId} — get seeding job progress.
export async function getDataSeedJob(sandboxId, jobId) {
  if (isMockMode()) {
    return {
      id: jobId,
      sandboxId,
      status: 'SUCCESS',
      totalTables: 2,
      completedTables: 2,
      totalRowsInserted: 150,
    }
  }
  const response = await http.get(`/api/sandboxes/${sandboxId}/data-seeds/${jobId}`)
  return response.data
}

// ---------- Migration risk analysis ----------

// POST /api/analysis/migration-risk — the server checks a migration script for risky operations.
// Returns { totalRisks, highRisks, mediumRisks, lowRisks, hasBreakingRisks,
//           findings: [{ level, rule, message, line, statement, table }] }
export async function analyzeMigrationRemote(sql) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    // Mock: run the browser-side check and shape the answer like the server's.
    const findings = analyzeMigration(sql).map((f) => ({ ...f, level: f.level.toUpperCase() }))
    const count = (level) => findings.filter((f) => f.level === level).length
    return {
      totalRisks: findings.length,
      highRisks: count('HIGH'),
      mediumRisks: count('MEDIUM'),
      lowRisks: count('LOW'),
      hasBreakingRisks: count('HIGH') > 0,
      findings,
    }
  }
  const response = await http.post('/api/analysis/migration-risk', { sql })
  return response.data
}

// ---------- Data sources (saved database connections) ----------
// A data source looks like:
//   { id, connectionName, host, port, databaseName, databaseUsername, containerId, createdAt, updatedAt }
// The password is sent when creating or testing, but is never returned.

// Our fake list of data sources for mock mode. "legacy_prod" always fails its test.
const mockDataSources = [
  {
    id: crypto.randomUUID(),
    connectionName: 'shop_primary',
    host: 'localhost',
    port: 5432,
    databaseName: 'shop',
    databaseUsername: 'shadow',
    containerId: null,
    createdAt: now(),
    updatedAt: now(),
  },
  {
    id: crypto.randomUUID(),
    connectionName: 'analytics_replica',
    host: 'localhost',
    port: 5432,
    databaseName: 'analytics',
    databaseUsername: 'reporter',
    containerId: null,
    createdAt: now(),
    updatedAt: now(),
  },
  {
    id: crypto.randomUUID(),
    connectionName: 'orders_mysql',
    host: 'localhost',
    port: 3306,
    databaseName: 'orders',
    databaseUsername: 'app',
    containerId: null,
    createdAt: now(),
    updatedAt: now(),
  },
  {
    id: crypto.randomUUID(),
    connectionName: 'legacy_prod',
    host: 'localhost',
    port: 5433,
    databaseName: 'legacy',
    databaseUsername: 'admin',
    containerId: null,
    createdAt: now(),
    updatedAt: now(),
  },
]

// Finds a mock data source by id, or throws a 404 like the backend would.
function findMockDataSource(id) {
  const dataSource = mockDataSources.find((d) => d.id === id)
  if (!dataSource) {
    throw mockError(404, `Data source not found with ID: ${id}`)
  }
  return dataSource
}

// Builds a fake connection test result. Port 5433 (used by "legacy_prod") is "refused".
function mockTestResult({ host, port, databaseName }) {
  const success = Number(port) !== 5433
  return {
    success,
    message: success ? 'Connection successful' : `Connection refused: ${host}:${port}`,
    databaseType: Number(port) === 3306 ? 'MySQL' : 'PostgreSQL',
    latencyMs: success ? 12 + Math.floor(Math.random() * 69) : null, // 12–80 ms
    host,
    port: Number(port),
    databaseName,
  }
}

// GET /api/datasources — list all data sources.
export async function listDataSources() {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    return copy(mockDataSources)
  }
  const response = await http.get('/api/datasources')
  return response.data
}

// POST /api/datasources — save a new data source.
// request = { connectionName, host, port, databaseName, databaseUsername, databasePassword }
export async function createDataSource(request) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    if (mockDataSources.some((d) => d.connectionName === request.connectionName)) {
      throw mockError(409, `A data source named "${request.connectionName}" already exists`)
    }
    const dataSource = {
      ...request,
      port: Number(request.port),
      id: crypto.randomUUID(),
      containerId: null,
      createdAt: now(),
      updatedAt: now(),
    }
    // Never keep the password (the backend never returns it either).
    delete dataSource.databasePassword
    mockDataSources.push(dataSource)
    return copy(dataSource)
  }
  const response = await http.post('/api/datasources', request)
  return response.data
}

// DELETE /api/datasources/{id} — delete a data source. The backend returns nothing (204).
export async function deleteDataSource(id) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    const index = mockDataSources.indexOf(findMockDataSource(id))
    mockDataSources.splice(index, 1)
    return
  }
  await http.delete(`/api/datasources/${id}`)
}

// POST /api/datasources/{id}/test — try to connect with a saved data source.
// Returns { success, message, databaseType, latencyMs, host, port, databaseName }.
export async function testDataSource(id) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    return mockTestResult(findMockDataSource(id))
  }
  const response = await http.post(`/api/datasources/${id}/test`)
  return response.data
}

// POST /api/datasources/test — try a connection BEFORE saving it (same body as create).
export async function testDataSourceConfig(request) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    return mockTestResult(request)
  }
  const response = await http.post('/api/datasources/test', request)
  return response.data
}

// POST /api/sandboxes/{sandboxId}/containers/{containerId}/datasource/{dataSourceId}
// Links a data source to a container. Returns the updated data source.
export async function linkDataSource(sandboxId, containerId, dataSourceId) {
  if (isMockMode()) {
    await wait(MOCK_DELAY)
    const dataSource = findMockDataSource(dataSourceId)
    dataSource.containerId = containerId
    dataSource.updatedAt = now()
    return copy(dataSource)
  }
  const response = await http.post(
    `/api/sandboxes/${sandboxId}/containers/${containerId}/datasource/${dataSourceId}`,
  )
  return response.data
}
