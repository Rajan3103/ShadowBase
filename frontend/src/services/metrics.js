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
