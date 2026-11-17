// EditorPage.jsx — the SQL editor page.
// It puts together the SqlEditor (top) and the ResultsTable (bottom).
// Clicking Run sends the SQL to runQuery() from our API service and shows what comes back.
import { useState } from 'react'
import { Link } from 'react-router-dom'
import SqlEditor from '../components/SqlEditor.jsx'
import ResultsTable from '../components/ResultsTable.jsx'
import { runQuery, getErrorMessage, CURRENT_SANDBOX_KEY, isMockMode } from '../services/api.js'

const DEFAULT_SQL = '-- Write your migration script here\nSELECT * FROM customers;'

function readCurrentSandbox() {
  try {
    return localStorage.getItem(CURRENT_SANDBOX_KEY) || ''
  } catch {
    return ''
  }
}

function EditorPage() {
  // The values this page remembers between renders:
  const [sql, setSql] = useState(DEFAULT_SQL) // text in the editor
  const [columns, setColumns] = useState([]) // result column names
  const [rows, setRows] = useState([]) // result rows
  const [error, setError] = useState(null) // error message, if any
  const [statusMessage, setStatusMessage] = useState(null)
  const [isRunning, setIsRunning] = useState(false) // true while waiting for a result
  const [currentSandboxId] = useState(readCurrentSandbox)

  async function handleRun() {
    setIsRunning(true)
    setError(null)
    setStatusMessage(null)

    try {
      // Success: show the returned columns and rows.
      const result = await runQuery(sql, currentSandboxId)
      setColumns(result.columns || [])
      setRows(result.rows || [])
      if (result.message) {
        setStatusMessage(result.message)
      }
    } catch (err) {
      // Failure: clear the table and show the error message.
      setError(getErrorMessage(err))
      setColumns([])
      setRows([])
    } finally {
      // Runs in both cases: re-enable the Run button.
      setIsRunning(false)
    }
  }

  return (
    <div>
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
        {/* Disabled while running, so the query can't be sent twice. */}
        <button className="run-button" onClick={handleRun} disabled={isRunning}>
          {isRunning ? 'Running...' : 'Run'}
        </button>
      </div>

      {statusMessage && <div className="saved-message">{statusMessage}</div>}

      <SqlEditor value={sql} onChange={setSql} />

      <ResultsTable columns={columns} rows={rows} error={error} />
    </div>
  )
}

export default EditorPage
