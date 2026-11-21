// EditorPage.jsx — the SQL editor page.
// - Pick which database container (in the active sandbox) the SQL runs against.
// - Run a query (button or Ctrl+Enter / Cmd+Enter) and see the results + a summary.
// - "Apply Migration" runs the whole script after an in-page confirmation.
// - A collapsible history list keeps the last 20 scripts you ran.
// - Save / load / delete named scripts, export to a .sql file, open a .sql file.
// - Layout toggles (Split / Side by side / Editor only) and Focus mode.
import { useEffect, useRef, useState } from 'react'
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
import { listScripts, saveScript, deleteScript, getScript } from '../services/scripts.js'

const DEFAULT_SQL = '-- Write your migration script here\nSELECT * FROM customers;'
const CURRENT_CONTAINER_KEY = 'shadowbase.currentContainerId'
const HISTORY_KEY = 'shadowbase.queryHistory'
const MAX_HISTORY = 20
const LAYOUT_KEY = 'shadowbase.editorLayout'
const MAX_FILE_BYTES = 1024 * 1024 // 1 MB limit for "Open .sql"

// The three layout options for the toggle group.
const LAYOUTS = [
  { value: 'split', label: 'Split' }, // editor on top, results below
  { value: 'side', label: 'Side by side' }, // editor left, results right
  { value: 'editor', label: 'Editor only' }, // results hidden, taller editor
]

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

// The saved layout, or 'split' if none (or an unknown value) is saved.
function readLayout() {
  const saved = readStorage(LAYOUT_KEY)
  return LAYOUTS.some((option) => option.value === saved) ? saved : 'split'
}

// Turns a script name into a safe file name (letters, numbers, _ - . only).
function toFileName(name) {
  return name.replace(/[^\w.-]+/g, '_') || 'script'
}

// Props (from App.jsx):
//   focusMode         — true when the sidebar is hidden
//   onToggleFocusMode — turns focus mode on/off
function EditorPage({ focusMode, onToggleFocusMode }) {
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

  // ---- Saved scripts ----
  const [scriptName, setScriptName] = useState('migration_1')
  const [savedScripts, setSavedScripts] = useState(listScripts) // newest first
  const [selectedName, setSelectedName] = useState('') // script chosen in the dropdown
  // The text of the last saved / loaded version. null = not saved anywhere.
  const [baselineSql, setBaselineSql] = useState(DEFAULT_SQL)
  const [scriptError, setScriptError] = useState(null)
  const [showSavedTick, setShowSavedTick] = useState(false) // "Saved ✓"
  const savedTimerRef = useRef(null)
  // An action waiting for "Load anyway" / "Delete": { action: 'load' | 'delete', name }
  const [pending, setPending] = useState(null)
  const fileInputRef = useRef(null) // the hidden <input type="file">

  // ---- Layout ----
  const [layout, setLayout] = useState(readLayout)

  // "● Unsaved" shows when the editor differs from the last saved/loaded version.
  const isUnsaved = baselineSql === null || sql !== baselineSql

  // Stop the "Saved ✓" timer if the page closes while it's running.
  useEffect(() => () => clearTimeout(savedTimerRef.current), [])

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

  // ================= Saved scripts =================

  // Save button: store the editor text under the script name.
  function handleSave() {
    const name = scriptName.trim()
    if (!name) {
      setScriptError('Enter a script name before saving.')
      return
    }
    if (!saveScript(name, sql)) {
      setScriptError('Could not save: browser storage is unavailable.')
      return
    }
    setScriptError(null)
    setPending(null)
    setScriptName(name)
    setSelectedName(name)
    setBaselineSql(sql) // the editor now matches the saved version
    setSavedScripts(listScripts())

    // Show "Saved ✓" for 2 seconds (restart the timer if Save is clicked again).
    setShowSavedTick(true)
    clearTimeout(savedTimerRef.current)
    savedTimerRef.current = setTimeout(() => setShowSavedTick(false), 2000)
  }

  // Puts a saved script into the editor.
  function loadScript(name) {
    const script = getScript(name)
    if (!script) {
      setScriptError(`Script "${name}" was not found.`)
      setSavedScripts(listScripts())
      return
    }
    setSql(script.sql)
    setScriptName(script.name)
    setSelectedName(script.name)
    setBaselineSql(script.sql)
    setScriptError(null)
  }

  // A script was chosen in the dropdown. With unsaved changes, ask first.
  function handleSelectScript(event) {
    const name = event.target.value
    if (!name) return
    setScriptError(null)
    if (isUnsaved) {
      setPending({ action: 'load', name })
    } else {
      setPending(null)
      loadScript(name)
    }
  }

  // Delete button: ask first (inline), using the script chosen in the dropdown.
  function handleDeleteClick() {
    if (!selectedName) return
    setScriptError(null)
    setPending({ action: 'delete', name: selectedName })
  }

  // "Load anyway" or "Delete" in the inline confirm panel.
  function handleConfirmPending() {
    if (pending.action === 'load') {
      loadScript(pending.name)
    } else if (!deleteScript(pending.name)) {
      setScriptError('Could not delete: browser storage is unavailable.')
    } else {
      setSavedScripts(listScripts())
      setSelectedName('')
      setBaselineSql(null) // the editor text is no longer saved anywhere
    }
    setPending(null)
  }

  // ================= Export / Open =================

  // Downloads the editor text as <scriptName>.sql, with a header comment on top.
  function handleExport() {
    const name = scriptName.trim() || 'script'
    const header =
      `-- ShadowBase migration script\n` +
      `-- Name: ${name}\n` +
      `-- Exported: ${new Date().toISOString()}\n\n`

    // A Blob is file data held in memory; an object URL lets a link point at it.
    const blob = new Blob([header + sql], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)

    // Make a temporary <a download> link, click it, then clean up.
    const link = document.createElement('a')
    link.href = url
    link.download = `${toFileName(name)}.sql`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 0) // free the memory
  }

  // "Open .sql" button: open the browser's file picker via the hidden input.
  function handleOpenClick() {
    fileInputRef.current.click()
  }

  // A file was picked: check its size, read it as text, put it in the editor.
  function handleFileChosen(event) {
    const file = event.target.files?.[0]
    event.target.value = '' // so the same file can be picked again later
    if (!file) return

    if (file.size > MAX_FILE_BYTES) {
      setScriptError(`"${file.name}" is larger than 1 MB.`)
      return
    }

    const reader = new FileReader()
    reader.onload = () => {
      const text = String(reader.result ?? '')
      setSql(text)
      setScriptName(file.name.replace(/\.[^.]+$/, '') || 'script') // name without ".sql"
      setSelectedName('')
      setBaselineSql(text)
      setScriptError(null)
      setPending(null)
    }
    reader.onerror = () => setScriptError(`Could not read "${file.name}".`)
    reader.readAsText(file)
  }

  // ================= Layout =================

  function handleLayoutChange(value) {
    setLayout(value)
    writeStorage(LAYOUT_KEY, value)
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

      {/* ---- Second toolbar row: scripts, files, layout ---- */}
      <div className="script-bar">
        {/* Name + Save */}
        <div className="script-group">
          <input
            type="text"
            className="text-input script-name"
            aria-label="Script name"
            placeholder="Script name"
            value={scriptName}
            onChange={(event) => setScriptName(event.target.value)}
          />
          <button className="small-button" onClick={handleSave}>
            Save
          </button>
          {showSavedTick && <span className="saved-tick">Saved ✓</span>}
          {isUnsaved && <span className="unsaved-label">● Unsaved</span>}
        </div>

        {/* Saved scripts dropdown + Delete */}
        <div className="script-group">
          <select
            className="text-input"
            aria-label="Saved scripts"
            value={selectedName}
            onChange={handleSelectScript}
          >
            <option value="">
              {savedScripts.length > 0 ? `Saved scripts (${savedScripts.length})` : 'No saved scripts'}
            </option>
            {savedScripts.map((script) => (
              <option key={script.name} value={script.name}>
                {script.name}
              </option>
            ))}
          </select>
          <button
            className="small-button danger"
            onClick={handleDeleteClick}
            disabled={!selectedName}
          >
            Delete
          </button>
        </div>

        {/* Open / Export files */}
        <div className="script-group">
          <button className="small-button" onClick={handleOpenClick}>
            Open .sql
          </button>
          {/* Hidden file picker, opened by the button above */}
          <input
            ref={fileInputRef}
            type="file"
            accept=".sql,.txt"
            hidden
            onChange={handleFileChosen}
          />
          <button className="small-button" onClick={handleExport}>
            Export .sql
          </button>
        </div>

        {/* Layout toggles + Focus mode */}
        <div className="script-group">
          <div className="toggle-group" role="group" aria-label="Editor layout">
            {LAYOUTS.map((option) => (
              <button
                key={option.value}
                className={layout === option.value ? 'toggle-button active' : 'toggle-button'}
                aria-pressed={layout === option.value}
                onClick={() => handleLayoutChange(option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>
          <button
            className={focusMode ? 'small-button active' : 'small-button'}
            aria-pressed={focusMode}
            onClick={onToggleFocusMode}
          >
            {focusMode ? 'Exit focus (Esc)' : 'Focus mode'}
          </button>
        </div>
      </div>

      {/* ---- Inline confirm for "load with unsaved changes" or "delete" ---- */}
      {pending && (
        <div className="inline-confirm">
          <span>
            {pending.action === 'load'
              ? `You have unsaved changes. Load "${pending.name}" and lose them?`
              : `Delete saved script "${pending.name}"?`}
          </span>
          <button className="small-button danger" onClick={handleConfirmPending}>
            {pending.action === 'load' ? 'Load anyway' : 'Delete'}
          </button>
          <button className="small-button" onClick={() => setPending(null)}>
            Cancel
          </button>
        </div>
      )}

      {scriptError && <div className="card-error">{scriptError}</div>}

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
        {/* The layout class decides where the results go (or hides them). */}
        <div className={`editor-main layout-${layout}`}>
          <div className="editor-pane">
            <SqlEditor
              value={sql}
              onChange={setSql}
              onRun={handleRun}
              height={layout === 'editor' ? '75vh' : '55vh'}
            />
          </div>

          {/* "Editor only" hides the summary and results. */}
          {layout !== 'editor' && (
            <div className="results-pane">
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
          )}
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
