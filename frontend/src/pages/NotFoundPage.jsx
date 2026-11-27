// NotFoundPage.jsx — shown for any address the app doesn't know, e.g. /unknown.
import { Link, useLocation } from 'react-router-dom'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'

function NotFoundPage() {
  useDocumentTitle('Page not found')
  const location = useLocation() // tells us which address was typed

  return (
    <div className="not-found">
      <h2>Page not found</h2>
      <p>
        There is no page at <code className="not-found-path">{location.pathname}</code>.
      </p>
      <p className="card-meta">Try one of these instead:</p>
      <nav className="not-found-links" aria-label="Main pages">
        <Link to="/editor" className="small-button">
          SQL Editor
        </Link>
        <Link to="/sandboxes" className="small-button">
          Sandboxes
        </Link>
        <Link to="/dashboard" className="small-button">
          Dashboard
        </Link>
      </nav>
    </div>
  )
}

export default NotFoundPage
