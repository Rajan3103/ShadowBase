// EditorPage.jsx — the SQL editor page.
// It shows a Monaco code editor (the same editor used in VS Code) and a Run button.
// For now, Run just shows the SQL in an alert.
// Later it will call POST /api/environments/{id}/query on the Spring Boot backend.
import { useState } from 'react'
import Editor from '@monaco-editor/react'

const DEFAULT_SQL = '-- Write your migration script here\nSELECT * FROM customers;'

function EditorPage() {
  // "sql" holds whatever text is currently in the editor.
  const [sql, setSql] = useState(DEFAULT_SQL)

  function handleRun() {
    alert(sql)
  }

  return (
    <div>
      <div className="toolbar">
        <h2>SQL Editor</h2>
        <button className="run-button" onClick={handleRun}>
          Run
        </button>
      </div>

      <Editor
        height="75vh"
        language="sql"
        theme="vs-dark"
        value={sql}
        // Monaco calls onChange every time you type; we save the new text.
        onChange={(value) => setSql(value ?? '')}
      />
    </div>
  )
}

export default EditorPage
