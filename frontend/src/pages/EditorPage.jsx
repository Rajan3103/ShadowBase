// EditorPage.jsx — the SQL editor page.
// - Pick which database container (in the active sandbox) the SQL runs against.
// - Run a query (button or Ctrl+Enter / Cmd+Enter) and see the results + a summary.
// - "Apply Migration" runs the whole script after an in-page confirmation.
// - A collapsible history list keeps the last 20 scripts you ran.
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import SqlEditor from '../components/SqlEditor.jsx'
import ResultsTable from '../components/ResultsTable.jsx'
import {
  runQuery,
  getContainers,
  getErrorMessage,
  CURRENT_SANDBOX_KEY,
  isMockMode,
} from '../services/api.js'

const DEFAULT_SQL = '-- Write your migration script here\nSELECT * FROM customers;'
const CURRENT_CONTAINER_KEY = 'shadowbase.currentContainerId'
const HISTORY_KEY = 'shadowbase.queryHistory'
const MAX_HISTORY = 20

// ---------- localStorage helpers (storage can throw, so always try/catch) ----------

function readStorage(key) {
  try {
    return localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

function writeStorage(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // Storage blocked — the value just won't be remembered.
  }
}

// History is saved as JSON text. If it's missing or broken, start with an empty list.
function readHistory() {
  try {
    const parsed = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

// A short label for a history entry: the first line that isn't empty or a "--" comment.
function previewOf(script) {
  const lines = script.split('\n').map((line) => line.trim())
  return lines.find((line) => line && !line.startsWith('--')) || lines[0] || '(empty)'
}

function EditorPage() {
  // ---- Editor and results ----
  const [sql, setSql] = useState(DEFAULT_SQL) // text in the editor
  const [columns, setColumns] = useState([]) // result column names
  const [rows, setRows] = useState([]) // result rows
  const [error, setError] = useState(null) // error message, if any
  const [summary, setSummary] = useState(null) // { rowCount, executionTimeMs, message }
  const [isRunning, setIsRunning] = useState(false) // true while waiting for a result

  // ---- Target sandbox and container ----
  const [currentSandboxId] = useState(() => readStorage(CURRENT_SANDBOX_KEY))
  const [containers, setContainers] = useState([])
  const [containerId, setContainerId] = useState(() => readStorage(CURRENT_CONTAINER_KEY))
  const [containersError, setContainersError] = useState(null)

  // ---- Apply Migration ----
  const [isConfirming, setIsConfirming] = useState(false) // confirmation panel open?
  const [migrationResult, setMigrationResult] = useState(null) // { ok, text }

  // ---- History ----
  const [history, setHistory] = useState(readHistory)
  const [showHistory, setShowHistory] = useState(true)

  // Load the containers of the active sandbox (once, when the page opens).
  useEffect(() => {
    if (!currentSandboxId) return

    async function loadContainers() {
      try {
        const list = await getContainers(currentSandboxId)
        setContainers(list)
        // Keep the saved choice if it's still in this sandbox; otherwise pick the
        // first RUNNING container (or just the first one).
        setContainerId((saved) => {
          if (list.some((c) => c.id === saved)) return saved
          const fallback = (list.find((c) => c.status === 'RUNNING') || list[0])?.id || ''
          writeStorage(CURRENT_CONTAINER_KEY, fallback)
          return fallback
        })
      } catch (err) {
        setContainersError(getErrorMessage(err))
      }
    }

    loadContainers()
  }, [currentSandboxId])

  // The user picked a different container in the dropdown.
  function handleContainerChange(event) {
    setContainerId(event.target.value)
    writeStorage(CURRENT_CONTAINER_KEY, event.target.value)
  }

  // Adds one run to the history (newest first, max 20) and saves it.
  function addToHistory(script, ok) {
    setHistory((current) => {
      // id only needs to be unique within the list (used as the React "key").
      const entry = { id: `${Date.now()}-${Math.random()}`, sql: script, ts: Date.now(), ok }
      const updated = [entry, ...current].slice(0, MAX_HISTORY)
      writeStorage(HISTORY_KEY, JSON.stringify(updated))
      return updated
    })
  }

  // Runs the editor's script and shows the results. Used by Run, Ctrl+Enter and
  // Apply Migration. Returns { ok: true, result } or { ok: false, message }.
  async function executeScript() {
    const script = sql
    setIsRunning(true)
    setError(null)
    setSummary(null)

    try {
      const result = await runQuery(script, currentSandboxId, containerId)
      setColumns(result.columns || [])
      setRows(result.rows || [])
      setSummary({
        rowCount: result.rowCount ?? (result.rows || []).length,
        executionTimeMs: result.executionTimeMs,
        message: result.message,
      })
      addToHistory(script, true)
      return { ok: true, result }
    } catch (err) {
      const message = getErrorMessage(err)
      setError(message)
      setColumns([])
      setRows([])
      addToHistory(script, false)
      return { ok: false, message }
    } finally {
      setIsRunning(false)
    }
  }

  // Run button and Ctrl+Enter. Ignored if something is already running.
  function handleRun() {
    if (isRunning) return
    setMigrationResult(null)
    executeScript()
  }

  // "Yes, apply" in the confirmation panel.
  async function handleConfirmMigration() {
    setIsConfirming(false)
    setMigrationResult(null)
    const outcome = await executeScript()
    setMigrationResult(
      outcome.ok
        ? { ok: true, text: outcome.result.message || 'Migration applied to the shadow database.' }
        : { ok: false, text: `Migration failed: ${outcome.message}` },
    )
  }

  const selectedContainer = containers.find((c) => c.id === containerId)

  return (
    <div>
      {/* ---- Header: title, target, buttons ---- */}
      <div className="toolbar">
        <div>
          <h2>SQL Editor</h2>
          {currentSandboxId ? (
            <p className="card-meta">
              Target Sandbox: <code>{currentSandboxId.slice(0, 8)}...</code>{' '}
              <Link to="/sandboxes" style={{ fontSize: '12px', marginLeft: '6px' }}>
                Change
              </Link>
            </p>
          ) : !isMockMode() ? (
            <p className="card-meta" style={{ color: '#d97706' }}>
              No sandbox selected.{' '}
              <Link to="/sandboxes">Go to Sandboxes</Link> and click &quot;Use in editor&quot;.
            </p>
          ) : null}
        </div>

        <div className="toolbar-buttons">
          <button className="small-button" onClick={() => setShowHistory((open) => !open)}>
            {showHistory ? 'Hide history' : `History (${history.length})`}
          </button>
          <button
            className="small-button danger"
            onClick={() => setIsConfirming(true)}
            disabled={isRunning || isConfirming}
          >
            Apply Migration
          </button>
          {/* Disabled while running, so the query can't be sent twice. */}
          <button
            className="run-button"
            onClick={handleRun}
            disabled={isRunning}
            title="Ctrl+Enter (Cmd+Enter on Mac)"
          >
            {isRunning ? 'Running...' : 'Run'}
          </button>
        </div>
      </div>

      {/* ---- Container picker (only when a sandbox is selected) ---- */}
      {currentSandboxId && (
        <div className="container-picker">
          <label htmlFor="container-select">Container</label>
          {containersError ? (
            <span className="picker-error">{containersError}</span>
          ) : containers.length === 0 ? (
            <span className="card-meta">
              No containers in this sandbox. <Link to="/sandboxes">Add one</Link>.
            </span>
          ) : (
            <select
              id="container-select"
              className="text-input"
              value={containerId}
              onChange={handleContainerChange}
            >
              {containers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.databaseType} – {c.databaseName} – {c.status}
                </option>
              ))}
            </select>
          )}
          {selectedContainer && selectedContainer.status !== 'RUNNING' && (
            <span className="picker-warning">This container is not running.</span>
          )}
        </div>
      )}

      {/* ---- Apply Migration confirmation (inside the page, not a pop-up) ---- */}
      {isConfirming && (
        <div className="migration-confirm">
          <p>
            <strong>Apply this migration?</strong> This will change the schema of the shadow
            database only. Production is never touched.
          </p>
          <div className="toolbar-buttons">
            <button className="run-button" onClick={handleConfirmMigration}>
              Yes, apply
            </button>
            <button className="small-button" onClick={() => setIsConfirming(false)}>
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* ---- Result of the last migration ---- */}
      {migrationResult && (
        <div className={migrationResult.ok ? 'migration-result success' : 'migration-result failure'}>
          {migrationResult.text}
        </div>
      )}

      {/* ---- Editor + history side by side ---- */}
      <div className={showHistory ? 'editor-layout with-history' : 'editor-layout'}>
        <div className="editor-main">
          <SqlEditor value={sql} onChange={setSql} onRun={handleRun} />

          {/* Summary of the last run */}
          {summary && (
            <div className="summary-bar">
              <span>
                <strong>{summary.rowCount}</strong> {summary.rowCount === 1 ? 'row' : 'rows'}
              </span>
              {summary.executionTimeMs != null && (
                <span>
                  <strong>{summary.executionTimeMs}</strong> ms
                </span>
              )}
              {summary.message && <span className="summary-message">{summary.message}</span>}
            </div>
          )}

          <ResultsTable columns={columns} rows={rows} error={error} />
        </div>

        {showHistory && (
          <aside className="history-panel">
            <p className="metric-label">Recent scripts</p>
            {history.length === 0 ? (
              <p className="card-empty">Nothing run yet.</p>
            ) : (
              <ul className="history-list">
                {history.map((entry) => (
                  <li key={entry.id}>
                    {/* Clicking an entry loads its script back into the editor. */}
                    <button
                      className="history-item"
                      onClick={() => setSql(entry.sql)}
                      title={entry.sql}
                    >
                      <span className={entry.ok ? 'history-dot ok' : 'history-dot fail'} />
                      <span className="history-text">{previewOf(entry.sql)}</span>
                      <span className="history-time">
                        {new Date(entry.ts).toLocaleTimeString('en-GB')}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </aside>
        )}
      </div>
    </div>
  )
}

export default EditorPage
