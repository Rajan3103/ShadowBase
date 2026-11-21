// SqlEditor.jsx — a small wrapper around the Monaco code editor.
// It only displays the SQL and reports changes; the page that uses it owns the text.
// It also adds the Ctrl+Enter (Cmd+Enter on Mac) shortcut to run the query.
import { useEffect, useRef } from 'react'
import Editor from '@monaco-editor/react'

// Props:
//   value    — the SQL text to show
//   onChange — called with the new text every time the user types
//   onRun    — (optional) called when the user presses Ctrl+Enter / Cmd+Enter
//   height   — (optional) editor height, e.g. "55vh" (the default) or "75vh"
function SqlEditor({ value, onChange, onRun, height = '55vh' }) {
  // The shortcut is registered only once (when the editor loads), so we keep the
  // latest onRun in a ref. Otherwise the shortcut would call an old version of it.
  const onRunRef = useRef(onRun)
  // After every render, store the newest onRun in the ref.
  useEffect(() => {
    onRunRef.current = onRun
  })

  // Runs once when Monaco has loaded. "monaco" gives us key codes for the shortcut.
  function handleMount(editor, monaco) {
    // KeyMod.CtrlCmd means Ctrl on Windows/Linux and Cmd on Mac.
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
      onRunRef.current?.()
    })
  }

  return (
    <Editor
      height={height}
      language="sql"
      theme="vs-dark"
      value={value}
      // Monaco can pass "undefined" when the editor is empty, so we send '' instead.
      onChange={(newValue) => onChange(newValue ?? '')}
      onMount={handleMount}
    />
  )
}

export default SqlEditor
