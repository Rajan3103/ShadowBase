// api.js — the one place where the frontend talks to the backend.
// Pages call these functions instead of using axios directly.
// In "mock mode" the functions return fake data, so the app works without a backend.
import axios from 'axios'

// ---------- Setup ----------

// Our axios "client". baseURL is empty, so we use relative URLs like "/api/sandboxes".
// During development, Vite forwards "/api" requests to http://localhost:8080 (see vite.config.js).
const http = axios.create({ baseURL: '' })

// Keys used to save settings in the browser (the Settings page writes them).
export const USE_MOCK_KEY = 'shadowbase.useMock'
export const USER_ID_KEY = 'shadowbase.userId'

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
const DEMO_USER_ID = '00000000-0000-0000-0000-000000000001'
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

// Runs SQL and returns { columns, rows }.
// ALWAYS mocked for now — the backend has no "run SQL" endpoint yet.
// If the SQL contains the word DROP (any case), it throws an error instead.
export async function runQuery(sql) {
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
  }
}
