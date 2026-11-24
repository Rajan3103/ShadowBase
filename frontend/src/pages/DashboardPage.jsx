// DashboardPage.jsx — live shadow-traffic dashboard.
// Shows (top to bottom): the CDC pipeline status, the active sandbox, four metric cards,
// a line chart of the error rate, column usage of live traffic, and the error console.
// The numbers come from subscribeToMetrics() in services/metrics.js: mock data, or live data
// from the backend if "Dashboard data source" is set to Live on the Settings page.
// A badge next to the title shows which one you are looking at.
// Reset: in live mode it asks the backend to reset (POST /api/metrics/reset) and clears the
// chart and the console; in mock mode it just starts counting again from zero.
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ResponsiveContainer,
} from 'recharts'
import { subscribeToMetrics, getMetricsSource } from '../services/metrics.js'
import ErrorConsole from '../components/ErrorConsole.jsx'
import ColumnUsagePanel from '../components/ColumnUsagePanel.jsx'
import PipelineStatus from '../components/PipelineStatus.jsx'
import { CURRENT_SANDBOX_KEY, resetMetrics, getErrorMessage } from '../services/api.js'

const MAX_POINTS = 30 // how many updates the chart keeps
const BREAKING_THRESHOLD = 5 // error rate (%) shown as the dashed line
const SHOW_CONSOLE_KEY = 'shadowbase.showConsole'

// Reads the active sandbox id from localStorage ('' if missing or blocked).
function readCurrentSandbox() {
  try {
    return localStorage.getItem(CURRENT_SANDBOX_KEY) || ''
  } catch {
    return ''
  }
}

// Whether the error console is shown (on unless it was turned off before).
function readShowConsole() {
  try {
    return localStorage.getItem(SHOW_CONSOLE_KEY) !== 'false'
  } catch {
    return true
  }
}

// Turns an error rate into a status label and a CSS colour class.
function getStatus(errorRate) {
  if (errorRate < 1) return { label: 'Healthy', className: 'status-healthy' }
  if (errorRate < BREAKING_THRESHOLD) return { label: 'Warning', className: 'status-warning' }
  return { label: 'Breaking', className: 'status-breaking' }
}

// The badge next to the title: text, colour class and (for the fallback) a tooltip.
// "source" comes from the latest update; before the first update we use the setting.
function getSourceBadge(source, reason) {
  if (source === 'polling') return { label: 'Live', className: 'badge badge-green source-badge' }
  if (source === 'mock-fallback') {
    return {
      label: 'Live unavailable – showing mock data',
      className: 'badge badge-amber source-badge',
      title: reason,
    }
  }
  return { label: 'Mock data', className: 'badge badge-grey source-badge' }
}

// Formats a timestamp as hh:mm:ss (24-hour), e.g. "14:05:09".
function formatTime(ts) {
  return new Date(ts).toLocaleTimeString('en-GB')
}

function DashboardPage() {
  const [sandboxId] = useState(readCurrentSandbox)
  const [latest, setLatest] = useState(null) // the most recent update, or null before the first
  const [history, setHistory] = useState([]) // last 30 points for the chart
  const [isPaused, setIsPaused] = useState(false)
  // Goes up by 1 on Reset; this restarts the subscription from zero.
  const [resetKey, setResetKey] = useState(0)
  const [showConsole, setShowConsole] = useState(readShowConsole)
  // The data source chosen in Settings ("mock" or "polling"), read when the page opens.
  const [configuredSource] = useState(getMetricsSource)
  // Goes up by 1 after a live Reset; giving the console a new "key" starts it again empty.
  const [consoleKey, setConsoleKey] = useState(0)
  const [isResetting, setIsResetting] = useState(false)
  const [resetError, setResetError] = useState(null)
  // When the "replayed" number last went up (for the Replayer stage of the pipeline).
  const [lastReplayIncreaseTs, setLastReplayIncreaseTs] = useState(null)

  // Subscribe to metrics while not paused. React runs the cleanup (unsubscribe) when
  // the page closes, when Pause is clicked, or when Reset restarts it.
  useEffect(() => {
    if (isPaused) return

    let previousReplayed = null // the replayed count of the previous update
    const unsubscribe = subscribeToMetrics((update) => {
      // Remember when the replayed count went up (the Replayer is working).
      if (previousReplayed !== null && update.replayed > previousReplayed) {
        setLastReplayIncreaseTs(Date.now())
      }
      previousReplayed = update.replayed
      setLatest(update)
      // Add the new point and keep only the last 30.
      setHistory((points) =>
        [...points, { time: formatTime(update.ts), errorRate: update.errorRate }].slice(-MAX_POINTS),
      )
    })

    return unsubscribe
  }, [isPaused, resetKey])

  // Clears the cards and chart and restarts the subscription (starts again from zero).
  function resetLocally() {
    setLatest(null)
    setHistory([])
    setLastReplayIncreaseTs(null)
    setResetKey((key) => key + 1)
  }

  // Reset button.
  // Live data (polling is working): ask the backend to reset, then clear the chart and console.
  // Mock data, or live data that fell back to mock: just reset locally, as before.
  async function handleReset() {
    setResetError(null)
    const isLive = configuredSource === 'polling' && latest?.source !== 'mock-fallback'
    if (!isLive) {
      resetLocally()
      return
    }

    setIsResetting(true)
    try {
      await resetMetrics()
      resetLocally()
      setConsoleKey((key) => key + 1) // empty console + a fresh list of seen error ids
    } catch (err) {
      setResetError(`Reset failed: ${getErrorMessage(err)}`)
    } finally {
      setIsResetting(false)
    }
  }

  // Show or hide the error console, and remember the choice.
  function handleToggleConsole() {
    const next = !showConsole
    setShowConsole(next)
    try {
      localStorage.setItem(SHOW_CONSOLE_KEY, String(next))
    } catch {
      // Storage blocked — it just won't be remembered.
    }
  }

  const status = latest ? getStatus(latest.errorRate) : null
  const badge = getSourceBadge(latest?.source ?? configuredSource, latest?.reason)
  // The pulse dot shows only while live polling is working (and not paused).
  const isLiveWorking = latest?.source === 'polling' && !isPaused

  return (
    <div>
      <div className="toolbar">
        {/* Title + data-source badge */}
        <div className="title-row">
          <h2>Dashboard</h2>
          <span className={badge.className} title={badge.title}>
            {badge.label}
          </span>
        </div>
        <div className="toolbar-buttons">
          <button className="small-button" onClick={() => setIsPaused((paused) => !paused)}>
            {isPaused ? 'Resume' : 'Pause'}
          </button>
          <button className="small-button" onClick={handleReset} disabled={isResetting}>
            {isResetting ? 'Resetting...' : 'Reset'}
          </button>
          <button className="small-button" onClick={handleToggleConsole} aria-pressed={showConsole}>
            Show console: {showConsole ? 'On' : 'Off'}
          </button>
        </div>
      </div>

      {/* ---- Active sandbox ---- */}
      <p className="card-meta">
        {sandboxId ? (
          <>
            Active sandbox: <code>{sandboxId}</code>
          </>
        ) : (
          <>
            No sandbox selected. <Link to="/sandboxes">Choose one on the Sandboxes page</Link>.
          </>
        )}
        {isPaused && <span className="paused-label"> · Paused</span>}
      </p>

      {/* Shown only if the live Reset request failed */}
      {resetError && <div className="card-error">{resetError}</div>}

      {/* ---- CDC pipeline: MySQL → Debezium → Kafka → Replayer → Shadow DB ---- */}
      <PipelineStatus
        latest={latest}
        lastReplayIncreaseTs={lastReplayIncreaseTs}
        sandboxId={sandboxId}
      />

      {/* ---- The four metric cards ("—" until the first update arrives) ---- */}
      <div className="metric-grid">
        <div className="metric-card">
          <p className="metric-label">Queries Replayed</p>
          <p className="metric-value">{latest ? latest.replayed.toLocaleString() : '—'}</p>
        </div>
        <div className="metric-card">
          <p className="metric-label">Failed Queries</p>
          <p className="metric-value">{latest ? latest.failed.toLocaleString() : '—'}</p>
        </div>
        <div className="metric-card">
          <p className="metric-label">Error Rate (%)</p>
          <p className="metric-value">{latest ? latest.errorRate.toFixed(1) : '—'}</p>
        </div>
        <div className="metric-card">
          <p className="metric-label">Status</p>
          <p className={status ? `metric-value ${status.className}` : 'metric-value'}>
            {status ? status.label : '—'}
          </p>
        </div>
      </div>

      {/* ---- When the numbers were last updated (+ pulse dot while live) ---- */}
      <p className="last-updated">
        {isLiveWorking && (
          <span className="live-indicator">
            <span className="live-pulse" aria-hidden="true" /> Live
          </span>
        )}
        Last updated: {latest ? formatTime(latest.ts) : '—'}
      </p>

      {/* ---- Error-rate chart ---- */}
      <div className="chart-panel">
        <p className="metric-label">Error rate over the last {MAX_POINTS} updates</p>
        {history.length === 0 ? (
          <p className="card-empty">Waiting for data...</p>
        ) : (
          // ResponsiveContainer makes the chart fill the panel's width.
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={history} margin={{ top: 10, right: 20, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="#1f2937" />
              <XAxis dataKey="time" stroke="#94a3b8" tick={{ fontSize: 11 }} minTickGap={20} />
              <YAxis
                stroke="#94a3b8"
                tick={{ fontSize: 11 }}
                unit="%"
                // Always show at least 0–10% so the 5% line is visible.
                domain={[0, (dataMax) => Math.max(10, Math.ceil(dataMax))]}
              />
              <Tooltip
                contentStyle={{ background: '#111827', border: '1px solid #334155' }}
                labelStyle={{ color: '#e2e8f0' }}
                formatter={(value) => [`${value}%`, 'Error rate']}
              />
              <ReferenceLine
                y={BREAKING_THRESHOLD}
                stroke="#ef4444"
                strokeDasharray="6 4"
                label={{ value: `${BREAKING_THRESHOLD}%`, fill: '#ef4444', fontSize: 11, position: 'insideTopRight' }}
              />
              <Line
                type="monotone"
                dataKey="errorRate"
                stroke="#1f8a8a"
                strokeWidth={2}
                dot={false}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* ---- Column usage of live traffic (not affected by "Show console") ---- */}
      <ColumnUsagePanel />

      {/* ---- Live SQL errors from replayed traffic (has its own Pause/Clear) ---- */}
      {/* Hiding it removes it from the page, which also stops its subscription. */}
      {showConsole && <ErrorConsole key={consoleKey} />}
    </div>
  )
}

export default DashboardPage
