// metrics.js — live shadow-traffic metrics and SQL errors for the Dashboard.
// The Dashboard only calls subscribeToMetrics(onUpdate) / subscribeToErrors(onError) and gets
// back an "unsubscribe" function. Where the data comes from is decided here:
//   "mock"    — fake numbers made up in the browser (the default)
//   "polling" — ask the backend every few seconds (GET /api/metrics, GET /api/metrics/errors)
// If polling fails 3 times in a row, we stop asking and switch to mock data ("mock-fallback").
//
// Every metrics update looks like: { replayed, failed, errorRate, ts, source, reason? }
//   replayed  — total queries replayed against the shadow database
//   failed    — total queries that failed
//   errorRate — a percentage (2.4 means 2.4%). The backend already sends it as a
//               percentage, so we never multiply it by 100 again.
//   ts        — time of the update in milliseconds (the backend's lastUpdatedTs, or Date.now())
//   source    — "mock" | "polling" | "mock-fallback"
//   reason    — only with "mock-fallback": why live data stopped
import { getMetrics, getRecentErrors, getErrorMessage } from './api.js'

const UPDATE_INTERVAL_MS = 2000 // metrics: every 2 seconds
const ERRORS_POLL_INTERVAL_MS = 3000 // errors: every 3 seconds
const MAX_FAILURES = 3 // failed requests in a row before we give up on live data
const ERRORS_LIMIT = 50 // how many recent errors to ask the backend for
const MAX_SEEN_IDS = 1000 // how many error ids we remember (oldest are forgotten first)

// ---------- The data-source setting (written by the Settings page) ----------

export const METRICS_SOURCE_KEY = 'shadowbase.metricsSource'

// Returns "polling" or "mock" (the default). localStorage can throw, so try/catch.
export function getMetricsSource() {
  try {
    return localStorage.getItem(METRICS_SOURCE_KEY) === 'polling' ? 'polling' : 'mock'
  } catch {
    return 'mock'
  }
}

// ---------- Small helpers ----------

// Turns raw counts into the update object the Dashboard expects.
// "extra" adds the source (and maybe a reason) to the object.
function buildUpdate(replayed, failed, extra) {
  const errorRate = replayed === 0 ? 0 : Math.round((failed / replayed) * 1000) / 10
  return { replayed, failed, errorRate, ts: Date.now(), ...extra }
}

// A random whole number from min to max (both included).
function randomInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1))
}

// Turns a value from the backend into a number (0 if it isn't one).
function toNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

// Turns a backend timestamp (epoch milliseconds, or a date text) into milliseconds.
// Falls back to "now" if it is missing or can't be read.
function toTimestamp(value) {
  if (value == null || value === '') return Date.now()
  const asNumber = Number(value)
  if (Number.isFinite(asNumber)) return asNumber
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : Date.now()
}

// =====================================================================
// Metrics
// =====================================================================

// ---------- Mock source ----------
// Every 2 seconds: replayed grows by 5–20, failed grows by 0–2.
// Each subscription starts again from zero.
// "extra" is { source: 'mock' } normally, or { source: 'mock-fallback', reason } after a failure.
function subscribeMock(onUpdate, extra = { source: 'mock' }) {
  let replayed = 0
  let failed = 0

  const timer = setInterval(() => {
    replayed += randomInt(5, 20)
    failed += randomInt(0, 2)
    onUpdate(buildUpdate(replayed, failed, extra))
  }, UPDATE_INTERVAL_MS)

  // The unsubscribe function: stops the timer.
  return () => clearInterval(timer)
}

// ---------- Polling source ----------
// Every 2 seconds: GET /api/metrics → { replayed, failed, errorRate, lastUpdatedTs }.
// After 3 failures in a row: stop polling and switch to mock data.
function subscribePolling(onUpdate) {
  let failures = 0 // failed requests in a row
  let busy = false // true while a request is in flight (so we never send two at once)
  let stopped = false // true after unsubscribe
  let stopFallback = null // the mock's unsubscribe function, once we fall back

  async function poll() {
    if (busy) return
    busy = true
    try {
      const data = await getMetrics()
      if (stopped) return // the page closed while we were waiting
      failures = 0
      const update = buildUpdate(toNumber(data?.replayed), toNumber(data?.failed), {
        source: 'polling',
      })
      // Use the backend's error rate if it sent one (it is ALREADY a percentage, so we
      // use it as it is); otherwise keep the one we calculated.
      if (data?.errorRate != null) update.errorRate = toNumber(data.errorRate)
      // Use the backend's own "last updated" time if it sent one.
      update.ts = toTimestamp(data?.lastUpdatedTs)
      onUpdate(update)
    } catch (err) {
      if (stopped) return
      failures += 1
      if (failures >= MAX_FAILURES) {
        // Give up on live data: stop polling and start the mock source instead.
        clearInterval(timer)
        const reason = `GET /api/metrics failed ${MAX_FAILURES} times: ${getErrorMessage(err)}`
        stopFallback = subscribeMock(onUpdate, { source: 'mock-fallback', reason })
      }
    } finally {
      busy = false
    }
  }

  // Ask every 2 seconds, and once straight away.
  const timer = setInterval(poll, UPDATE_INTERVAL_MS)
  poll()

  // The unsubscribe function: stops polling (and the fallback mock, if it started).
  return () => {
    stopped = true
    clearInterval(timer)
    stopFallback?.()
  }
}

// Starts sending metric updates to onUpdate. Returns a function that stops them.
// The setting is read each time, so Pause/Resume or Reset picks up a changed setting.
export function subscribeToMetrics(onUpdate) {
  return getMetricsSource() === 'polling' ? subscribePolling(onUpdate) : subscribeMock(onUpdate)
}

// =====================================================================
// Live SQL errors from replayed shadow traffic (for the Error Console)
// =====================================================================
//
// Every error looks like:
//   { id, ts, table, operation: "INSERT" | "UPDATE" | "DELETE", sqlState, message, source, reason? }
//   sqlState is the PostgreSQL error code, e.g. "42703" = undefined column.

// Realistic PostgreSQL errors a migration might cause on the shadow database.
const MOCK_ERRORS = [
  { table: 'customers', operation: 'INSERT', sqlState: '42703', message: 'column "phone" does not exist' },
  { table: 'customers', operation: 'UPDATE', sqlState: '42703', message: 'column "phone" does not exist' },
  { table: 'customer_profiles', operation: 'INSERT', sqlState: '42P01', message: 'relation "customer_profiles" does not exist' },
  { table: 'customer_profiles', operation: 'UPDATE', sqlState: '42P01', message: 'relation "customer_profiles" does not exist' },
  { table: 'customers', operation: 'INSERT', sqlState: '23502', message: 'null value in column "email" of relation "customers" violates not-null constraint' },
  { table: 'customers', operation: 'INSERT', sqlState: '23505', message: 'duplicate key value violates unique constraint "customers_email_key"' },
  { table: 'orders', operation: 'INSERT', sqlState: '23503', message: 'insert or update on table "orders" violates foreign key constraint "orders_customer_id_fkey"' },
  { table: 'customers', operation: 'DELETE', sqlState: '23503', message: 'update or delete on table "customers" violates foreign key constraint "orders_customer_id_fkey" on table "orders"' },
  { table: 'orders', operation: 'UPDATE', sqlState: '23514', message: 'new row for relation "orders" violates check constraint "orders_total_check"' },
  { table: 'orders', operation: 'UPDATE', sqlState: '42804', message: 'column "created_at" is of type timestamp without time zone but expression is of type text' },
  { table: 'order_items', operation: 'DELETE', sqlState: '42P01', message: 'relation "order_items" does not exist' },
  { table: 'orders', operation: 'INSERT', sqlState: '22P02', message: 'invalid input syntax for type integer: "abc"' },
]

// Gives every mock error a unique id (counts up). The "mock-" prefix keeps these ids
// different from the backend's ids, so the console never sees the same id twice.
let nextErrorId = 1

// ---------- Mock error source ----------
// Sends one random error every 3–6 seconds.
function subscribeErrorsMock(onError, extra = { source: 'mock' }) {
  let timer
  let stopped = false // set to true by unsubscribe

  // Waits a random 3–6 seconds, sends an error, then schedules the next one.
  function scheduleNext() {
    timer = setTimeout(() => {
      const template = MOCK_ERRORS[randomInt(0, MOCK_ERRORS.length - 1)]
      onError({ id: `mock-${nextErrorId++}`, ts: Date.now(), ...template, ...extra })
      // onError might have unsubscribed us; if so, don't schedule another error.
      if (!stopped) scheduleNext()
    }, randomInt(3000, 6000))
  }

  scheduleNext()

  // The unsubscribe function: cancels the next scheduled error and stops the loop.
  return () => {
    stopped = true
    clearTimeout(timer)
  }
}

// ---------- Polling error source ----------
// Every 3 seconds: GET /api/metrics/errors?limit=50 → array of errors.
// Only errors whose id we have not seen before are sent on, oldest first,
// so the console reads from top to bottom.
// After 3 failures in a row: stop polling and switch to mock errors.
// Each subscription has its own list of seen ids, so subscribing again (e.g. after a
// Dashboard Reset) starts with an empty list and every new error shows up.
function subscribeErrorsPolling(onError) {
  const seenIds = new Set() // ids already sent to the console (max 1000)
  let failures = 0
  let busy = false
  let stopped = false
  let stopFallback = null

  async function poll() {
    if (busy) return
    busy = true
    try {
      const list = await getRecentErrors(ERRORS_LIMIT)
      if (stopped) return
      failures = 0

      // 1. Keep only the errors we have not sent before (and that have an id).
      const newErrors = (Array.isArray(list) ? list : [])
        .filter((error) => error?.id != null && !seenIds.has(error.id))
        .map((error) => ({
          ...error,
          ts: toTimestamp(error.ts), // epoch ms → a number the console can format
          sqlState: error.sqlState ?? '-',
          message: error.message ?? '',
          source: 'polling',
          // table / operation may be null or empty; the console shows "—" for those.
        }))

      // 2. Oldest first, so the newest error ends up at the bottom of the console.
      newErrors.sort((a, b) => a.ts - b.ts)

      // 3. Remember the ids and send the errors on.
      for (const error of newErrors) {
        seenIds.add(error.id)
        onError(error)
        if (stopped) return // onError might have unsubscribed us
      }

      // 4. Forget the oldest ids if we remember more than 1000 (a Set keeps insertion order).
      for (const id of seenIds) {
        if (seenIds.size <= MAX_SEEN_IDS) break
        seenIds.delete(id)
      }
    } catch (err) {
      if (stopped) return
      failures += 1
      if (failures >= MAX_FAILURES) {
        clearInterval(timer)
        const reason = `GET /api/metrics/errors failed ${MAX_FAILURES} times: ${getErrorMessage(err)}`
        stopFallback = subscribeErrorsMock(onError, { source: 'mock-fallback', reason })
      }
    } finally {
      busy = false
    }
  }

  // Ask every 3 seconds, and once straight away.
  const timer = setInterval(poll, ERRORS_POLL_INTERVAL_MS)
  poll()

  return () => {
    stopped = true
    clearInterval(timer)
    stopFallback?.()
  }
}

// Starts sending SQL errors to onError. Returns a function that stops them.
export function subscribeToErrors(onError) {
  return getMetricsSource() === 'polling'
    ? subscribeErrorsPolling(onError)
    : subscribeErrorsMock(onError)
}
