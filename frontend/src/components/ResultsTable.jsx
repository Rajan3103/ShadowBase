// ResultsTable.jsx — shows the result of a query under the editor.
// It shows one of three things: an error, an empty message, or a table of rows.

// Props:
//   columns — column names, e.g. ["id", "name", "email"]
//   rows    — one array per row, e.g. [[1, "Asha", "asha@example.com"], ...]
//   error   — an error message, or null when there is no error
function ResultsTable({ columns, rows, error }) {
  // 1) Something went wrong: show the error in a red box.
  if (error) {
    return <div className="results-error">{error}</div>
  }

  // 2) Nothing to show yet.
  if (rows.length === 0) {
    return <div className="results-empty">No results yet. Write a query and click Run.</div>
  }

  // 3) Show the row count and the table.
  return (
    <div className="results">
      <p className="results-count">
        {rows.length} {rows.length === 1 ? 'row' : 'rows'}
      </p>

      {/* This box scrolls if the table is too tall or too wide. */}
      <div className="results-scroll">
        <table className="results-table">
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={column}>{column}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <td key={cellIndex}>{String(cell)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default ResultsTable
