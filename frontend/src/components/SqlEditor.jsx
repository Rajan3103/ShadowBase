// SqlEditor.jsx — a small wrapper around the Monaco code editor.
// It only displays the SQL and reports changes; the page that uses it owns the text.
import Editor from '@monaco-editor/react'

// Props:
//   value    — the SQL text to show
//   onChange — called with the new text every time the user types
function SqlEditor({ value, onChange }) {
  return (
    <Editor
      height="55vh"
      language="sql"
      theme="vs-dark"
      value={value}
      // Monaco can pass "undefined" when the editor is empty, so we send '' instead.
      onChange={(newValue) => onChange(newValue ?? '')}
    />
  )
}

export default SqlEditor
