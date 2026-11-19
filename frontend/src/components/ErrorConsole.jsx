// ErrorConsole.jsx — a terminal-style panel showing live SQL errors from replayed traffic.
// Newest errors appear at the bottom. It scrolls down by itself, unless you have
// scrolled up to read older lines.
import { useEffect, useRef, useState } from 'react'
import { subscribeToErrors } from '../services/metrics.js'

const MAX_LINES = 500 // older lines are dropped after this

// Formats a timestamp as hh:mm:ss (24-hour), e.g. "14:05:09".
function formatTime(ts) {
  return new Date(ts).toLocaleTimeString('en-GB')
}

function ErrorConsole() {
  const [lines, setLines] = useState([]) // the errors kept in the console (max 500)
  const [totalCount, setTotalCount] = useState(0) // all errors received since the last Clear
  const [isPaused, setIsPaused] = useState(false)
  const [textFilter, setTextFilter] = useState('')
  const [operationFilter, setOperationFilter] = useState('ALL')

  // A "ref" points at the scrolling box, so we can read and set its scroll position.
  const scrollBoxRef = useRef(null)
  // Remembers whether the user is at the bottom. A ref (not state) because
  // changing it should not re-draw the console.
  const isAtBottomRef = useRef(true)

  // Listen for errors while not paused. The cleanup unsubscribes (on Pause or leaving the page).
  useEffect(() => {
    if (isPaused) return

    const unsubscribe = subscribeToErrors((error) => {
      setLines((current) => [...current, error].slice(-MAX_LINES))
      setTotalCount((count) => count + 1)
    })

    return unsubscribe
  }, [isPaused])

  // When the user scrolls, check whether they are (almost) at the bottom.
  function handleScroll() {
    const box = scrollBoxRef.current
    isAtBottomRef.current = box.scrollHeight - box.scrollTop - box.clientHeight < 20
  }

  // Keep only the lines that match both filters.
  const search = textFilter.trim().toLowerCase()
  const visibleLines = lines.filter((line) => {
    if (operationFilter !== 'ALL' && line.operation !== operationFilter) return false
    if (!search) return true
    // The text filter looks in the table, error code and message.
    return `${line.table} ${line.sqlState} ${line.message}`.toLowerCase().includes(search)
  })

  // After new lines are drawn (or the filters change), scroll to the bottom —
  // but only if the user was already there.
  useEffect(() => {
    const box = scrollBoxRef.current
    if (box && isAtBottomRef.current) {
      box.scrollTop = box.scrollHeight
    }
  }, [lines, search, operationFilter])

  // Clear: empty the console and reset the count.
  function handleClear() {
    setLines([])
    setTotalCount(0)
    isAtBottomRef.current = true
  }

  const isFiltered = search !== '' || operationFilter !== 'ALL'

  return (
    <div className="error-console">
      {/* ---- Toolbar ---- */}
      <div className="console-toolbar">
        <span className="console-title">SQL Error Console</span>

        <input
          type="text"
          className="text-input console-filter"
          placeholder="Filter (table, code, message)"
          value={textFilter}
          onChange={(event) => setTextFilter(event.target.value)}
        />
        <select
          className="text-input"
          value={operationFilter}
          onChange={(event) => setOperationFilter(event.target.value)}
        >
          <option value="ALL">All</option>
          <option value="INSERT">INSERT</option>
          <option value="UPDATE">UPDATE</option>
          <option value="DELETE">DELETE</option>
        </select>

        <button className="small-button" onClick={() => setIsPaused((paused) => !paused)}>
          {isPaused ? 'Resume' : 'Pause'}
        </button>
        <button className="small-button" onClick={handleClear}>
          Clear
        </button>

        <span className="console-count">
          {totalCount} {totalCount === 1 ? 'error' : 'errors'}
          {isFiltered && ` (showing ${visibleLines.length})`}
          {isPaused && ' · Paused'}
        </span>
      </div>

      {/* ---- The scrolling log ---- */}
      <div className="console-body" ref={scrollBoxRef} onScroll={handleScroll}>
        {visibleLines.length === 0 ? (
          <div className="console-empty">
            {lines.length === 0 ? 'Waiting for errors...' : 'No errors match the filter.'}
          </div>
        ) : (
          visibleLines.map((line) => (
            <div key={line.id} className="console-line">
              <span className="console-time">{formatTime(line.ts)}</span>
              {/* e.g. "op-insert" gives INSERT its own colour */}
              <span className={`console-op op-${line.operation.toLowerCase()}`}>
                {line.operation}
              </span>
              <span className="console-table">{line.table}</span>
              <span className="console-state">[{line.sqlState}]</span>
              <span className="console-message">{line.message}</span>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

export default ErrorConsole
