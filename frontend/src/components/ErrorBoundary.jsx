// ErrorBoundary.jsx — catches a crash inside a page, so the rest of the app keeps working.
// Without it, one error while drawing a page would leave the whole screen blank.
// Error boundaries must be "class components" (React has no hook for this yet).
//
// App.jsx gives it key={pathname}: going to another page makes React create a fresh
// boundary, so the error screen disappears by itself.
import { Component } from 'react'
import { Link } from 'react-router-dom'

class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null } // null = no crash, show the page normally
    this.handleReset = this.handleReset.bind(this)
  }

  // React calls this when something inside crashes; the returned object becomes the state.
  static getDerivedStateFromError(error) {
    return { error }
  }

  // Also called after a crash: log it so it can be found in the browser console (F12).
  componentDidCatch(error, info) {
    console.error('Page crashed:', error, info?.componentStack)
  }

  // "Try again": forget the error and draw the page again.
  handleReset() {
    this.setState({ error: null })
  }

  render() {
    const { error } = this.state
    if (!error) return this.props.children // no crash: show the page

    return (
      <div className="error-boundary" role="alert">
        <h2>Something went wrong on this page</h2>
        <pre className="error-boundary-message">{error?.message || String(error)}</pre>
        <div className="toolbar-buttons">
          <button className="run-button" onClick={this.handleReset}>
            Try again
          </button>
          {/* Also reset, in case the crash happened on the SQL Editor page itself. */}
          <Link to="/editor" className="small-button error-boundary-link" onClick={this.handleReset}>
            Go to SQL Editor
          </Link>
        </div>
      </div>
    )
  }
}

export default ErrorBoundary
