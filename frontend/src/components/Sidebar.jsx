// Sidebar.jsx — the left navigation bar with the app title and page links.
// NavLink is like a normal link, but it knows when its page is open,
// so we can give the active link the "active" CSS class.
import { NavLink } from 'react-router-dom'

// The links shown in the sidebar: where they go and what they say.
const links = [
  { to: '/editor', label: 'SQL Editor' },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/settings', label: 'Settings' },
]

function Sidebar() {
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
    </aside>
  )
}

export default Sidebar
