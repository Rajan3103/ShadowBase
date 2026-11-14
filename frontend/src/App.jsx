// App.jsx — the main layout: Sidebar on the left, the current page on the right.
// <Routes> decides which page to show based on the URL.
import { Routes, Route, Navigate } from 'react-router-dom'
import Sidebar from './components/Sidebar.jsx'
import EditorPage from './pages/EditorPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import SettingsPage from './pages/SettingsPage.jsx'

function App() {
  return (
    <div className="app">
      <Sidebar />
      <main className="content">
        <Routes>
          {/* Visiting "/" sends the user to "/editor" */}
          <Route path="/" element={<Navigate to="/editor" replace />} />
          <Route path="/editor" element={<EditorPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
