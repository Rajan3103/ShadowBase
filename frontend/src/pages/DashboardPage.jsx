// DashboardPage.jsx — live shadow-traffic dashboard.
// Shows the active sandbox, four metric cards, and a line chart of the error rate.
// The numbers come from subscribeToMetrics() in services/metrics.js (mock data for now).
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
import { subscribeToMetrics } from '../services/metrics.js'
import ErrorConsole from '../components/ErrorConsole.jsx'
import { CURRENT_SANDBOX_KEY } from '../services/api.js'

const MAX_POINTS = 30 // how many updates the chart keeps
const BREAKING_THRESHOLD = 5 // error rate (%) shown as the dashed line

// Reads the active sandbox id from localStorage ('' if missing or blocked).
function readCurrentSandbox() {
  try {
    return localStorage.getItem(CURRENT_SANDBOX_KEY) || ''
  } catch {
    return ''
  }
}

// Turns an error rate into a status label and a CSS colour class.
function getStatus(errorRate) {
  if (errorRate < 1) return { label: 'Healthy', className: 'status-healthy' }
  if (errorRate < BREAKING_THRESHOLD) return { label: 'Warning', className: 'status-warning' }
  return { label: 'Breaking', className: 'status-breaking' }
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

  // Subscribe to metrics while not paused. React runs the cleanup (unsubscribe) when
  // the page closes, when Pause is clicked, or when Reset restarts it.
  useEffect(() => {
    if (isPaused) return

    const unsubscribe = subscribeToMetrics((update) => {
      setLatest(update)
      // Add the new point and keep only the last 30.
      setHistory((points) =>
        [...points, { time: formatTime(update.ts), errorRate: update.errorRate }].slice(-MAX_POINTS),
      )
    })

    return unsubscribe
  }, [isPaused, resetKey])

  // Reset: clear everything and start counting again from zero.
  function handleReset() {
    setLatest(null)
    setHistory([])
    setResetKey((key) => key + 1)
  }

  const status = latest ? getStatus(latest.errorRate) : null

  return (
    <div>
      <div className="toolbar">
        <h2>Dashboard</h2>
        <div className="toolbar-buttons">
          <button className="small-button" onClick={() => setIsPaused((paused) => !paused)}>
            {isPaused ? 'Resume' : 'Pause'}
          </button>
          <button className="small-button" onClick={handleReset}>
            Reset
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

      {/* ---- Live SQL errors from replayed traffic (has its own Pause/Clear) ---- */}
      <ErrorConsole />
    </div>
  )
}

export default DashboardPage
