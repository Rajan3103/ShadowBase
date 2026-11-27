// App.jsx — the main layout: Sidebar on the left, the current page on the right.
// <Routes> decides which page to show based on the URL.
// It also owns:
// - "focus mode": when on, the .app element gets the "focus-mode" class and CSS hides
//   the Sidebar. Esc turns it off.
// - the keyboard shortcuts help: "?" opens it (not while typing). Esc closes the help
//   first; only the next Esc exits focus mode.
// - an ErrorBoundary around the pages, so one crashing page doesn't blank the whole app.
import { useEffect, useState } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import Sidebar from './components/Sidebar.jsx'
import ErrorBoundary from './components/ErrorBoundary.jsx'
import ShortcutsHelp from './components/ShortcutsHelp.jsx'
import EditorPage from './pages/EditorPage.jsx'
import SandboxesPage from './pages/SandboxesPage.jsx'
import DataSourcesPage from './pages/DataSourcesPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import SettingsPage from './pages/SettingsPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'

const FOCUS_KEY = 'shadowbase.focusMode'

// Reads the saved focus mode (off if missing or storage is blocked).
function readFocusMode() {
  try {
    return localStorage.getItem(FOCUS_KEY) === 'true'
  } catch {
    return false
  }
}

// true if the user is typing somewhere (a text box, a list, or the Monaco editor),
// so "?" should type a "?" there instead of opening the help.
function isTypingTarget(element) {
  if (!element || typeof element.closest !== 'function') return false
  return Boolean(element.closest('input, textarea, select, [contenteditable="true"], .monaco-editor'))
}

function App() {
  const [focusMode, setFocusMode] = useState(readFocusMode)
  const [showHelp, setShowHelp] = useState(false) // keyboard shortcuts dialog open?
  const location = useLocation() // the current address, e.g. { pathname: '/editor' }

  // Remember focus mode whenever it changes.
  useEffect(() => {
    try {
      localStorage.setItem(FOCUS_KEY, String(focusMode))
    } catch {
      // Storage blocked — it just won't be remembered.
    }
  }, [focusMode])

  // Global keys:
  //   "?" (Shift + /) opens the help, unless you are typing.
  //   Esc exits focus mode — but NOT while the help is open (the help closes itself first).
  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === '?' && !event.ctrlKey && !event.metaKey && !event.altKey) {
        if (!showHelp && !isTypingTarget(event.target)) {
          event.preventDefault()
          setShowHelp(true)
        }
        return
      }
      if (event.key === 'Escape' && focusMode && !showHelp) {
        setFocusMode(false)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown) // clean up
  }, [focusMode, showHelp])

  function toggleFocusMode() {
    setFocusMode((on) => !on)
  }

  return (
    <div className={focusMode ? 'app focus-mode' : 'app'}>
      <Sidebar onOpenShortcuts={() => setShowHelp(true)} />
      <main className="content">
        {/* key = the address: a new page gets a fresh boundary (an old error disappears). */}
        <ErrorBoundary key={location.pathname}>
          <Routes>
            {/* Visiting "/" sends the user to "/editor" */}
            <Route path="/" element={<Navigate to="/editor" replace />} />
            <Route
              path="/editor"
              element={<EditorPage focusMode={focusMode} onToggleFocusMode={toggleFocusMode} />}
            />
            <Route path="/sandboxes" element={<SandboxesPage />} />
            <Route path="/datasources" element={<DataSourcesPage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            {/* Any other address: the 404 page */}
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </ErrorBoundary>
      </main>

      {showHelp && <ShortcutsHelp onClose={() => setShowHelp(false)} />}
    </div>
  )
}

export default App
