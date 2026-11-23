// App.jsx — the main layout: Sidebar on the left, the current page on the right.
// <Routes> decides which page to show based on the URL.
// It also owns "focus mode": when on, the .app element gets the "focus-mode" class
// and CSS hides the Sidebar. Esc turns it off.
import { useEffect, useState } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import Sidebar from './components/Sidebar.jsx'
import EditorPage from './pages/EditorPage.jsx'
import SandboxesPage from './pages/SandboxesPage.jsx'
import DataSourcesPage from './pages/DataSourcesPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import SettingsPage from './pages/SettingsPage.jsx'

const FOCUS_KEY = 'shadowbase.focusMode'

// Reads the saved focus mode (off if missing or storage is blocked).
function readFocusMode() {
  try {
    return localStorage.getItem(FOCUS_KEY) === 'true'
  } catch {
    return false
  }
}

function App() {
  const [focusMode, setFocusMode] = useState(readFocusMode)

  // Remember focus mode whenever it changes.
  useEffect(() => {
    try {
      localStorage.setItem(FOCUS_KEY, String(focusMode))
    } catch {
      // Storage blocked — it just won't be remembered.
    }
  }, [focusMode])

  // While focus mode is on, pressing Esc anywhere turns it off.
  useEffect(() => {
    if (!focusMode) return
    function handleKeyDown(event) {
      if (event.key === 'Escape') setFocusMode(false)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown) // clean up
  }, [focusMode])

  function toggleFocusMode() {
    setFocusMode((on) => !on)
  }

  return (
    <div className={focusMode ? 'app focus-mode' : 'app'}>
      <Sidebar />
      <main className="content">
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
        </Routes>
      </main>
    </div>
  )
}

export default App
