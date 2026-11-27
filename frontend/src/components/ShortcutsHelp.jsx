// ShortcutsHelp.jsx — a small dialog ("modal") listing the keyboard shortcuts.
// App.jsx shows it when "?" is pressed (or the Sidebar link is clicked) and passes onClose.
// While it is open:
//   - Esc, the × button, or a click on the dark background closes it
//   - Tab / Shift+Tab stay inside the dialog ("focus trap")
//   - when it closes, the keyboard focus goes back to where it was before
import { useEffect, useRef } from 'react'

// The shortcuts shown in the list.
const SHORTCUTS = [
  { keys: ['Ctrl', 'Enter'], mac: 'Cmd + Enter on Mac', action: 'Run the query (SQL Editor)' },
  { keys: ['Esc'], action: 'Close dialogs / exit focus mode' },
  { keys: ['?'], action: 'Show this help' },
]

// Everything inside the dialog that can get keyboard focus.
const FOCUSABLE = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'

// Props:
//   onClose — called when the dialog should close
function ShortcutsHelp({ onClose }) {
  const dialogRef = useRef(null)
  const closeButtonRef = useRef(null)
  // onClose can change between renders; keep the newest one for the key listener.
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    // 1. Remember what had focus, then move focus into the dialog.
    const previouslyFocused = document.activeElement
    closeButtonRef.current?.focus()

    // 2. Keys while the dialog is open.
    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseRef.current()
        return
      }
      if (event.key !== 'Tab') return

      // Focus trap: from the last item Tab goes to the first, and Shift+Tab the other way.
      const items = dialogRef.current?.querySelectorAll(FOCUSABLE)
      if (!items || items.length === 0) return
      const first = items[0]
      const last = items[items.length - 1]
      const inside = dialogRef.current.contains(document.activeElement)
      if (event.shiftKey && (document.activeElement === first || !inside)) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && (document.activeElement === last || !inside)) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', handleKeyDown)

    // 3. When the dialog closes: stop listening and give focus back.
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      if (previouslyFocused && typeof previouslyFocused.focus === 'function') {
        previouslyFocused.focus()
      }
    }
  }, [])

  // A click on the dark background (not on the dialog itself) closes it.
  function handleBackdropClick(event) {
    if (event.target === event.currentTarget) onClose()
  }

  return (
    <div className="modal-backdrop" onClick={handleBackdropClick}>
      <div
        ref={dialogRef}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="shortcuts-title"
      >
        <div className="modal-header">
          <h2 id="shortcuts-title">Keyboard shortcuts</h2>
          <button
            ref={closeButtonRef}
            className="modal-close"
            onClick={onClose}
            aria-label="Close keyboard shortcuts"
          >
            ×
          </button>
        </div>

        <dl className="shortcut-list">
          {SHORTCUTS.map((shortcut) => (
            <div key={shortcut.action} className="shortcut-row">
              <dt>
                {/* Each key in its own <kbd> box, joined with "+" */}
                {shortcut.keys.map((key, index) => (
                  <span key={key}>
                    {index > 0 && ' + '}
                    <kbd>{key}</kbd>
                  </span>
                ))}
                {shortcut.mac && <span className="shortcut-mac"> ({shortcut.mac})</span>}
              </dt>
              <dd>{shortcut.action}</dd>
            </div>
          ))}
        </dl>
      </div>
    </div>
  )
}

export default ShortcutsHelp
