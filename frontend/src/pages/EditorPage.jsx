// EditorPage.jsx — the SQL editor page.
// It puts together the SqlEditor (top) and the ResultsTable (bottom).
// Clicking Run sends the SQL to a fake (mock) function and shows what comes back.
// Later the mock will be replaced by POST /api/environments/{id}/query on the backend.
import { useState } from 'react'
import SqlEditor from '../components/SqlEditor.jsx'
import ResultsTable from '../components/ResultsTable.jsx'

const DEFAULT_SQL = '-- Write your migration script here\nSELECT * FROM customers;'

// Mock "backend": waits 500 ms, then returns fake data.
// If the SQL contains the word DROP (any case), it returns an error instead.
// (Tomorrow this moves to its own API service file.)
function mockRunQuery(sql) {
  return new Promise((resolve) => {
    setTimeout(() => {
      if (/\bdrop\b/i.test(sql)) {
        resolve({ error: 'ERROR: column "email" does not exist (mock)' })
      } else {
        resolve({
          columns: ['id', 'name', 'email'],
          rows: [
            [1, 'Asha', 'asha@example.com'],
            [2, 'Ravi', 'ravi@example.com'],
            [3, 'Meena', 'meena@example.com'],
          ],
        })
      }
    }, 500)
  })
}

function EditorPage() {
  // The values this page remembers between renders:
  const [sql, setSql] = useState(DEFAULT_SQL) // text in the editor
  const [columns, setColumns] = useState([]) // result column names
  const [rows, setRows] = useState([]) // result rows
  const [error, setError] = useState(null) // error message, if any
  const [isRunning, setIsRunning] = useState(false) // true while waiting for a result

  async function handleRun() {
    setIsRunning(true)
    setError(null)

    const result = await mockRunQuery(sql)

    if (result.error) {
      setError(result.error)
      setColumns([])
      setRows([])
    } else {
      setColumns(result.columns)
      setRows(result.rows)
    }

    setIsRunning(false)
  }

  return (
    <div>
      <div className="toolbar">
        <h2>SQL Editor</h2>
        {/* Disabled while running, so the query can't be sent twice. */}
        <button className="run-button" onClick={handleRun} disabled={isRunning}>
          {isRunning ? 'Running...' : 'Run'}
        </button>
      </div>

      <SqlEditor value={sql} onChange={setSql} />

      <ResultsTable columns={columns} rows={rows} error={error} />
    </div>
  )
}

export default EditorPage
