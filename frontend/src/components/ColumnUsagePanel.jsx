// ColumnUsagePanel.jsx — "Column usage (live traffic)" on the Dashboard.
// Shows how many recent queries used each column of one table, as a bar chart.
// Bars at or above the "heavily queried" threshold (Settings page) are red: dropping those
// columns would break live traffic. It reloads by itself every 10 seconds.
import { useEffect, useState } from 'react'
import {
  BarChart,
  Bar,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  ResponsiveContainer,
} from 'recharts'
import { getColumnUsage, resetColumnUsage, getErrorMessage } from '../services/api.js'
import { getHeavyThreshold } from '../services/migrationRisk.js'

const REFRESH_INTERVAL_MS = 10000 // reload every 10 seconds
const DEFAULT_TABLE = 'customers'

function ColumnUsagePanel() {
  const [threshold] = useState(getHeavyThreshold) // read from Settings when the panel opens
  const [tableInput, setTableInput] = useState(DEFAULT_TABLE) // what is typed in the box
  const [table, setTable] = useState(DEFAULT_TABLE) // the table actually shown
  const [reloadKey, setReloadKey] = useState(0) // goes up by 1 to force a reload
  // The last answer: { table, usage } on success, or { table, error } on failure.
  const [data, setData] = useState(null)

  // Reset usage (with an inline confirm)
  const [isConfirmingReset, setIsConfirmingReset] = useState(false)
  const [isResetting, setIsResetting] = useState(false)
  const [resetError, setResetError] = useState(null)

  // Load the usage now, then every 10 seconds. Runs again when the table changes or
  // "Load" / Reset asks for a reload. The cleanup stops the timer when the Dashboard closes.
  useEffect(() => {
    let cancelled = false // stops old answers from changing the panel

    function load() {
      getColumnUsage(table)
        .then((usage) => !cancelled && setData({ table, usage: usage || {} }))
        .catch((err) => !cancelled && setData({ table, error: getErrorMessage(err) }))
    }

    load()
    const timer = setInterval(load, REFRESH_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [table, reloadKey])

  // "Load" button (or Enter in the box): show the typed table.
  function handleLoad(event) {
    event.preventDefault() // stop the form from reloading the page
    const name = tableInput.trim()
    if (!name) return
    setTable(name)
    setReloadKey((key) => key + 1) // reload even if it is the same table
  }

  // "Reset usage" → "Yes, reset": clear all recorded usage, then reload.
  async function handleConfirmReset() {
    setIsConfirmingReset(false)
    setIsResetting(true)
    setResetError(null)
    try {
      await resetColumnUsage()
      setReloadKey((key) => key + 1)
    } catch (err) {
      setResetError(`Reset failed: ${getErrorMessage(err)}`)
    } finally {
      setIsResetting(false)
    }
  }

  // The answer for the table on screen (null while the first load is still running).
  const current = data && data.table === table ? data : null
  // Turn { email: 120, phone: 45 } into [{ column, count }], biggest first.
  const rows = current?.usage
    ? Object.entries(current.usage)
        .map(([column, count]) => ({ column, count: Number(count) || 0 }))
        .sort((a, b) => b.count - a.count)
    : []
  const heavyCount = rows.filter((row) => row.count >= threshold).length

  return (
    <div className="chart-panel usage-panel">
      {/* ---- Title, table box, buttons ---- */}
      <div className="usage-toolbar">
        <p className="metric-label usage-title">Column usage (live traffic)</p>
        <form className="usage-form" onSubmit={handleLoad}>
          <input
            type="text"
            className="text-input"
            aria-label="Table name"
            value={tableInput}
            onChange={(event) => setTableInput(event.target.value)}
          />
          <button type="submit" className="small-button">
            Load
          </button>
        </form>
        <button
          className="small-button danger"
          onClick={() => setIsConfirmingReset(true)}
          disabled={isResetting || isConfirmingReset}
        >
          {isResetting ? 'Resetting...' : 'Reset usage'}
        </button>
      </div>

      {/* Inline confirm (instead of a pop-up) */}
      {isConfirmingReset && (
        <div className="inline-confirm">
          <span>Clear all recorded column usage (every table)?</span>
          <button className="small-button danger" onClick={handleConfirmReset}>
            Yes, reset
          </button>
          <button className="small-button" onClick={() => setIsConfirmingReset(false)}>
            Cancel
          </button>
        </div>
      )}
      {resetError && <div className="card-error">{resetError}</div>}

      {/* ---- Loading / error / empty / chart ---- */}
      {!current ? (
        <p className="card-empty">Loading usage for {table}...</p>
      ) : current.error ? (
        <div className="card-error">Could not load column usage: {current.error}</div>
      ) : rows.length === 0 ? (
        <p className="card-empty">
          No usage recorded for {table} yet. Run some queries in the SQL Editor.
        </p>
      ) : (
        <>
          {/* Horizontal bars: one row per column. Taller chart for more columns. */}
          <ResponsiveContainer width="100%" height={Math.max(160, rows.length * 32 + 40)}>
            <BarChart data={rows} layout="vertical" margin={{ top: 10, right: 20, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="#1f2937" horizontal={false} />
              {/* Always show the threshold line, even if every bar is shorter. */}
              <XAxis
                type="number"
                stroke="#94a3b8"
                tick={{ fontSize: 11 }}
                domain={[0, (dataMax) => Math.max(dataMax, threshold)]}
              />
              <YAxis type="category" dataKey="column" stroke="#94a3b8" tick={{ fontSize: 11 }} width={100} />
              <Tooltip
                contentStyle={{ background: '#111827', border: '1px solid #334155' }}
                labelStyle={{ color: '#e2e8f0' }}
                cursor={{ fill: '#1e293b' }}
                formatter={(value) => [`${value} queries`, 'Usage']}
              />
              <ReferenceLine
                x={threshold}
                stroke="#ef4444"
                strokeDasharray="6 4"
                label={{ value: `${threshold}`, fill: '#ef4444', fontSize: 11, position: 'top' }}
              />
              <Bar dataKey="count" isAnimationActive={false}>
                {/* Red at or above the threshold, accent colour below it. */}
                {rows.map((row) => (
                  <Cell key={row.column} fill={row.count >= threshold ? '#ef4444' : '#1f8a8a'} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
          <p className="usage-summary">
            <strong>
              {heavyCount} of {rows.length}
            </strong>{' '}
            columns are heavily queried
          </p>
        </>
      )}
    </div>
  )
}

export default ColumnUsagePanel
