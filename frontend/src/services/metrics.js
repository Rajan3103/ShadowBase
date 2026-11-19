// metrics.js — live shadow-traffic metrics for the Dashboard.
// The Dashboard only calls subscribeToMetrics(onUpdate) and gets back an "unsubscribe" function.
// Where the numbers come from (mock now; later GET /api/metrics or a WebSocket) is decided
// here, so the Dashboard never needs to change.
//
// Every update looks like: { replayed, failed, errorRate, ts }
//   replayed  — total queries replayed against the shadow database
//   failed    — total queries that failed
//   errorRate — failed / replayed * 100, rounded to 1 decimal
//   ts        — time of the update in milliseconds (Date.now())

const UPDATE_INTERVAL_MS = 2000

// Which data source to use. Only 'mock' exists for now.
// Later: add 'polling' (GET /api/metrics) or 'websocket' (/topic/metrics) below and switch this.
const METRICS_SOURCE = 'mock'

// Turns raw counts into the update object the Dashboard expects.
function buildUpdate(replayed, failed) {
  const errorRate = replayed === 0 ? 0 : Math.round((failed / replayed) * 1000) / 10
  return { replayed, failed, errorRate, ts: Date.now() }
}

// A random whole number from min to max (both included).
function randomInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1))
}

// ---------- Mock source ----------
// Every 2 seconds: replayed grows by 5–20, failed grows by 0–2.
// Each subscription starts again from zero.
function subscribeMock(onUpdate) {
  let replayed = 0
  let failed = 0

  const timer = setInterval(() => {
    replayed += randomInt(5, 20)
    failed += randomInt(0, 2)
    onUpdate(buildUpdate(replayed, failed))
  }, UPDATE_INTERVAL_MS)

  // The unsubscribe function: stops the timer.
  return () => clearInterval(timer)
}

// ---------- Future sources (not built yet) ----------
// Polling:   setInterval → GET /api/metrics → onUpdate(buildUpdate(data.replayed, data.failed))
//            return () => clearInterval(timer)
// WebSocket: subscribe to /topic/metrics → on each message call onUpdate(...)
//            return () => close the connection

// Starts sending metric updates to onUpdate. Returns a function that stops them.
export function subscribeToMetrics(onUpdate) {
  switch (METRICS_SOURCE) {
    case 'mock':
    default:
      return subscribeMock(onUpdate)
  }
}

// =====================================================================
// Live SQL errors from replayed shadow traffic (for the Error Console)
// =====================================================================
//
// Every error looks like:
//   { id, ts, table, operation: "INSERT" | "UPDATE" | "DELETE", sqlState, message }
//   sqlState is the PostgreSQL error code, e.g. "42703" = undefined column.

// Which data source to use for errors. Only 'mock' exists for now.
// Later: add 'websocket' (subscribe to /topic/errors) below and switch this.
const ERRORS_SOURCE = 'mock'

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

// Gives every error a unique id (counts up).
let nextErrorId = 1

// ---------- Mock error source ----------
// Sends one random error every 3–6 seconds.
function subscribeErrorsMock(onError) {
  let timer
  let stopped = false // set to true by unsubscribe

  // Waits a random 3–6 seconds, sends an error, then schedules the next one.
  function scheduleNext() {
    timer = setTimeout(() => {
      const template = MOCK_ERRORS[randomInt(0, MOCK_ERRORS.length - 1)]
      onError({ id: nextErrorId++, ts: Date.now(), ...template })
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

// ---------- Future source (not built yet) ----------
// WebSocket: subscribe to /topic/errors → for each message call
//            onError({ id, ts, table, operation, sqlState, message })
//            return () => close the subscription

// Starts sending SQL errors to onError. Returns a function that stops them.
export function subscribeToErrors(onError) {
  switch (ERRORS_SOURCE) {
    case 'mock':
    default:
      return subscribeErrorsMock(onError)
  }
}
