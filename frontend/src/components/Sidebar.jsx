// Sidebar.jsx — the left navigation bar with the app title and page links.
// NavLink is like a normal link, but it knows when its page is open,
// so we can give the active link the "active" CSS class.
// At the bottom it shows whether the backend is reachable:
//   green "Online", red "Offline", or grey "Mock mode" (no backend needed).
import { useEffect, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { isMockMode, checkBackendHealth } from '../services/api.js'

const HEALTH_CHECK_INTERVAL_MS = 15000 // check the backend every 15 seconds

// The links shown in the sidebar: where they go and what they say.
const links = [
  { to: '/editor', label: 'SQL Editor' },
  { to: '/sandboxes', label: 'Sandboxes' },
  { to: '/datasources', label: 'Data Sources' },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/settings', label: 'Settings' },
]

// Text and dot colour for each status.
const STATUS_INFO = {
  mock: { label: 'Mock mode', dot: 'status-dot grey' },
  checking: { label: 'Checking...', dot: 'status-dot grey' },
  online: { label: 'Online', dot: 'status-dot green' },
  offline: { label: 'Offline', dot: 'status-dot red' },
}

function Sidebar() {
  // useLocation makes the Sidebar re-draw on every page change, so a mock-mode change
  // made on the Settings page is picked up as soon as you go to another page.
  useLocation()
  const mock = isMockMode()

  // 'checking' | 'online' | 'offline' (only used when not in mock mode)
  const [health, setHealth] = useState('checking')

  // Outside mock mode: check now, then every 15 seconds. The cleanup stops the timer
  // (when mock mode is turned on, or the app closes).
  useEffect(() => {
    if (mock) return

    let stopped = false
    async function check() {
      const ok = await checkBackendHealth() // never throws
      if (!stopped) setHealth(ok ? 'online' : 'offline')
    }

    check()
    const timer = setInterval(check, HEALTH_CHECK_INTERVAL_MS)
    return () => {
      stopped = true
      clearInterval(timer)
    }
  }, [mock])

  const status = STATUS_INFO[mock ? 'mock' : health]

  return (
    <aside className="sidebar">
      <h1 className="sidebar-title">ShadowBase</h1>
      <p className="sidebar-subtitle">Schema Migration Sandbox</p>

      <nav className="sidebar-nav">
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
          >
            {link.label}
          </NavLink>
        ))}
      </nav>

      {/* Backend status (bottom of the sidebar). The title gives the full text on hover. */}
      <div className="backend-status" title={`Backend: ${status.label}`}>
        <span className={status.dot} />
        {/* The label is hidden in focus mode (see index.css). */}
        <span className="backend-status-label">Backend: {status.label}</span>
      </div>
    </aside>
  )
}

export default Sidebar
