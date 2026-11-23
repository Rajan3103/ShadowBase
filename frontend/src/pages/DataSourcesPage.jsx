// DataSourcesPage.jsx — "DataSource Diagnostics": your saved database connections.
// - A table of data sources, each with Test, "Link to active container" and Delete.
// - "Test all" tests every data source one after another and shows "3 of 4 connected".
// - A form to add a new data source; "Test connection" tries it BEFORE saving.
// The password is only sent to the backend: it is never shown, stored or logged here.
import { useEffect, useState } from 'react'
import {
  listDataSources,
  createDataSource,
  deleteDataSource,
  testDataSource,
  testDataSourceConfig,
  linkDataSource,
  getErrorMessage,
  CURRENT_SANDBOX_KEY,
} from '../services/api.js'

const CURRENT_CONTAINER_KEY = 'shadowbase.currentContainerId' // set by the SQL Editor

// The empty "Add data source" form (host and port have sensible defaults).
const EMPTY_FORM = {
  connectionName: '',
  host: 'localhost',
  port: '5432',
  databaseName: '',
  databaseUsername: '',
  databasePassword: '',
}

// Reads a value from localStorage, or '' if missing or blocked.
function readStorage(key) {
  try {
    return localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

// Formats a timestamp as hh:mm:ss (24-hour), e.g. "14:05:09".
function formatTime(ts) {
  return new Date(ts).toLocaleTimeString('en-GB')
}

// Checks the form and returns { fieldName: "error text" } (empty object = all good).
// The password is optional (some local databases don't need one).
function validateForm(form) {
  const errors = {}
  if (!form.connectionName.trim()) errors.connectionName = 'Enter a connection name.'
  if (!form.host.trim()) errors.host = 'Enter a host.'
  if (!form.databaseName.trim()) errors.databaseName = 'Enter a database name.'
  if (!form.databaseUsername.trim()) errors.databaseUsername = 'Enter a username.'
  // The port must be a whole number from 1 to 65535.
  const port = Number(form.port)
  if (!/^\d+$/.test(form.port.trim()) || port < 1 || port > 65535) {
    errors.port = 'Port must be a number from 1 to 65535.'
  }
  return errors
}

// Turns the form into the request body the backend expects (port as a number).
function toRequest(form) {
  return {
    connectionName: form.connectionName.trim(),
    host: form.host.trim(),
    port: Number(form.port),
    databaseName: form.databaseName.trim(),
    databaseUsername: form.databaseUsername.trim(),
    databasePassword: form.databasePassword, // sent as typed, never shown
  }
}

// Colour class for a latency: < 50 ms green, < 200 ms amber, otherwise red.
function latencyClass(ms) {
  if (ms < 50) return 'latency fast'
  if (ms < 200) return 'latency medium'
  return 'latency slow'
}

// Runs a test request and always returns a result (a failed request becomes a failed
// result), with the time it was tested added.
async function runTest(request) {
  try {
    const result = await request()
    return { ...result, testedAt: Date.now() }
  } catch (err) {
    return { success: false, message: getErrorMessage(err), testedAt: Date.now() }
  }
}

// Shows one test result: ✓ Connected / ✗ Failed, latency, database type, message, time.
function TestResult({ result }) {
  return (
    <div className="test-result">
      <span className={result.success ? 'test-badge ok' : 'test-badge fail'}>
        {result.success ? '✓ Connected' : '✗ Failed'}
      </span>
      {result.latencyMs != null && (
        <span className={latencyClass(result.latencyMs)}>{result.latencyMs} ms</span>
      )}
      <div className="test-details">
        {result.databaseType && <span>{result.databaseType}</span>}
        {result.message && (
          <span className={result.success ? '' : 'test-message-fail'}>{result.message}</span>
        )}
        <span>Tested at {formatTime(result.testedAt)}</span>
      </div>
    </div>
  )
}

function DataSourcesPage() {
  // ---- The list ----
  const [dataSources, setDataSources] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)

  // ---- Per-row state (keyed by data source id) ----
  const [testResults, setTestResults] = useState({}) // id → last test result
  const [busy, setBusy] = useState({}) // id → 'test' | 'link' | 'delete' while working
  const [rowMessages, setRowMessages] = useState({}) // id → { ok, text } after link/delete
  const [pendingDeleteId, setPendingDeleteId] = useState(null) // row asking "Delete?"

  // ---- Test all ----
  const [isTestingAll, setIsTestingAll] = useState(false)
  const [testAllSummary, setTestAllSummary] = useState(null) // "3 of 4 connected"

  // ---- The active sandbox and container (for "Link to active container") ----
  const [sandboxId] = useState(() => readStorage(CURRENT_SANDBOX_KEY))
  const [containerId] = useState(() => readStorage(CURRENT_CONTAINER_KEY))

  // ---- The "Add data source" form ----
  const [form, setForm] = useState(EMPTY_FORM)
  const [formErrors, setFormErrors] = useState({})
  const [formTestResult, setFormTestResult] = useState(null)
  const [isFormTesting, setIsFormTesting] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [saveMessage, setSaveMessage] = useState(null)

  // Asks the API for the list and stores it (or the error).
  async function fetchDataSources() {
    try {
      setDataSources(await listDataSources())
      setLoadError(null)
    } catch (err) {
      setLoadError(getErrorMessage(err))
    } finally {
      setIsLoading(false)
    }
  }

  // Refresh / Retry / after Save: show "Loading..." and fetch the list again.
  function loadDataSources() {
    setIsLoading(true)
    setLoadError(null)
    return fetchDataSources()
  }

  // Load once when the page opens (isLoading already starts as true).
  // "cancelled" stops us updating the page if you leave it before the answer arrives.
  useEffect(() => {
    let cancelled = false
    listDataSources()
      .then((list) => !cancelled && setDataSources(list))
      .catch((err) => !cancelled && setLoadError(getErrorMessage(err)))
      .finally(() => !cancelled && setIsLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  // Small helpers to change one entry in the per-row state objects.
  function setRowBusy(id, value) {
    setBusy((current) => ({ ...current, [id]: value }))
  }
  function setRowMessage(id, message) {
    setRowMessages((current) => ({ ...current, [id]: message }))
  }

  // ================= Row actions =================

  // Test one saved data source and remember the result for its row.
  async function testOne(id) {
    setRowBusy(id, 'test')
    const result = await runTest(() => testDataSource(id))
    setTestResults((current) => ({ ...current, [id]: result }))
    setRowBusy(id, null)
    return result
  }

  // "Test all": test every data source one after another, then show a summary.
  async function handleTestAll() {
    setIsTestingAll(true)
    setTestAllSummary(null)
    let connected = 0
    for (const dataSource of dataSources) {
      const result = await testOne(dataSource.id) // wait for each before the next
      if (result.success) connected += 1
    }
    setTestAllSummary(`${connected} of ${dataSources.length} connected`)
    setIsTestingAll(false)
  }

  // Link a data source to the active container (from the Sandboxes page / SQL Editor).
  async function handleLink(id) {
    setRowBusy(id, 'link')
    setRowMessage(id, null)
    try {
      const updated = await linkDataSource(sandboxId, containerId, id)
      // Show the new link in the table (the response is the updated data source).
      setDataSources((list) =>
        list.map((d) => (d.id === id ? { ...d, ...updated, containerId: updated?.containerId ?? containerId } : d)),
      )
      setRowMessage(id, { ok: true, text: `Linked to container ${containerId.slice(0, 12)}` })
    } catch (err) {
      setRowMessage(id, { ok: false, text: `Link failed: ${getErrorMessage(err)}` })
    } finally {
      setRowBusy(id, null)
    }
  }

  // "Delete" in the inline confirm: delete, then remove the row.
  async function handleConfirmDelete(id) {
    setPendingDeleteId(null)
    setRowBusy(id, 'delete')
    setRowMessage(id, null)
    try {
      await deleteDataSource(id)
      setDataSources((list) => list.filter((d) => d.id !== id))
      setTestResults((current) => ({ ...current, [id]: undefined }))
    } catch (err) {
      setRowMessage(id, { ok: false, text: `Delete failed: ${getErrorMessage(err)}` })
    } finally {
      setRowBusy(id, null)
    }
  }

  // ================= Add data source form =================

  // Typing in a field: update it and clear that field's error message.
  function handleFieldChange(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
    setFormErrors((current) => ({ ...current, [name]: undefined }))
    setSaveMessage(null)
  }

  // Checks the form; shows the errors and returns false if something is wrong.
  function checkForm() {
    const errors = validateForm(form)
    setFormErrors(errors)
    return Object.keys(errors).length === 0
  }

  // "Test connection": try the connection before saving it.
  async function handleFormTest() {
    if (!checkForm()) return
    setIsFormTesting(true)
    setFormTestResult(null)
    const result = await runTest(() => testDataSourceConfig(toRequest(form)))
    setFormTestResult(result)
    setIsFormTesting(false)
  }

  // "Save": create the data source, then reload the table and empty the form.
  async function handleSave(event) {
    event.preventDefault() // stop the form from reloading the page
    if (!checkForm()) return
    setIsSaving(true)
    setSaveError(null)
    setSaveMessage(null)
    try {
      const created = await createDataSource(toRequest(form))
      setForm(EMPTY_FORM) // also clears the password from memory
      setFormTestResult(null)
      setSaveMessage(`Saved "${created?.connectionName ?? 'data source'}" ✓`)
      await loadDataSources()
    } catch (err) {
      setSaveError(getErrorMessage(err))
    } finally {
      setIsSaving(false)
    }
  }

  // Why "Link to active container" is disabled (or null if it can be used).
  const linkDisabledReason = !sandboxId
    ? 'No active sandbox. Click "Use in editor" on the Sandboxes page first.'
    : !containerId
      ? 'No active container. Pick a container in the SQL Editor first.'
      : null

  // One text field of the form, with its label and error message.
  function renderField(name, label, type = 'text') {
    return (
      <label className="settings-field">
        {label}
        <input
          type={type}
          name={name}
          className="text-input"
          value={form[name]}
          onChange={handleFieldChange}
          // Browsers should not fill in or remember the password field.
          autoComplete={type === 'password' ? 'new-password' : 'off'}
        />
        {formErrors[name] && <span className="field-error">{formErrors[name]}</span>}
      </label>
    )
  }

  return (
    <div>
      <div className="toolbar">
        <h2>Data Sources</h2>
        <div className="toolbar-buttons">
          <button className="small-button" onClick={loadDataSources} disabled={isLoading}>
            {isLoading ? 'Loading...' : 'Refresh'}
          </button>
          <button
            className="run-button"
            onClick={handleTestAll}
            disabled={isTestingAll || isLoading || dataSources.length === 0}
          >
            {isTestingAll ? 'Testing...' : 'Test all'}
          </button>
        </div>
      </div>

      {testAllSummary && <p className="test-all-summary">{testAllSummary}</p>}

      {/* ---- The table (loading / error / empty / rows) ---- */}
      {loadError ? (
        <div className="card-error">
          Could not load data sources: {loadError}{' '}
          <button className="small-button" onClick={loadDataSources}>
            Retry
          </button>
        </div>
      ) : isLoading && dataSources.length === 0 ? (
        <p className="card-empty">Loading data sources...</p>
      ) : dataSources.length === 0 ? (
        <p className="card-empty">No data sources yet – add one below</p>
      ) : (
        // On narrow screens the table scrolls inside this box, not the whole page.
        <div className="table-scroll">
          <table className="ds-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Host:Port</th>
                <th>Database</th>
                <th>User</th>
                <th>Linked container</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {dataSources.map((d) => {
                const rowBusy = busy[d.id]
                const result = testResults[d.id]
                const message = rowMessages[d.id]
                return (
                  <tr key={d.id}>
                    <td className="ds-name">{d.connectionName}</td>
                    <td>
                      <code>
                        {d.host}:{d.port}
                      </code>
                    </td>
                    <td>{d.databaseName}</td>
                    <td>{d.databaseUsername}</td>
                    <td>
                      {d.containerId ? (
                        <code title={d.containerId}>{d.containerId.slice(0, 12)}</code>
                      ) : (
                        <span className="muted">Not linked</span>
                      )}
                    </td>
                    <td className="ds-status">
                      {rowBusy === 'test' ? (
                        <span className="muted">Testing...</span>
                      ) : result ? (
                        <TestResult result={result} />
                      ) : (
                        <span className="muted">Not tested</span>
                      )}
                      {message && (
                        <div className={message.ok ? 'row-message ok' : 'row-message fail'}>
                          {message.text}
                        </div>
                      )}
                    </td>
                    <td>
                      {pendingDeleteId === d.id ? (
                        // Inline confirm (instead of a pop-up).
                        <div className="ds-actions">
                          <span>Delete &quot;{d.connectionName}&quot;?</span>
                          <button className="small-button danger" onClick={() => handleConfirmDelete(d.id)}>
                            Delete
                          </button>
                          <button className="small-button" onClick={() => setPendingDeleteId(null)}>
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <div className="ds-actions">
                          <button
                            className="small-button"
                            onClick={() => testOne(d.id)}
                            disabled={Boolean(rowBusy) || isTestingAll}
                          >
                            Test
                          </button>
                          {/* The tooltip sits on a <span>, because disabled buttons may not show one. */}
                          <span className="button-tip" title={linkDisabledReason || 'Link this data source to the active container'}>
                            <button
                              className="small-button"
                              onClick={() => handleLink(d.id)}
                              disabled={Boolean(linkDisabledReason) || Boolean(rowBusy)}
                            >
                              {rowBusy === 'link' ? 'Linking...' : 'Link to active container'}
                            </button>
                          </span>
                          <button
                            className="small-button danger"
                            onClick={() => setPendingDeleteId(d.id)}
                            disabled={Boolean(rowBusy) || isTestingAll}
                          >
                            {rowBusy === 'delete' ? 'Deleting...' : 'Delete'}
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* ---- Add data source form ---- */}
      <form className="ds-form" onSubmit={handleSave} noValidate>
        <h3>Add data source</h3>
        <div className="ds-form-grid">
          {renderField('connectionName', 'Connection name')}
          {renderField('host', 'Host')}
          {renderField('port', 'Port')}
          {renderField('databaseName', 'Database name')}
          {renderField('databaseUsername', 'Username')}
          {renderField('databasePassword', 'Password', 'password')}
        </div>

        <div className="toolbar-buttons ds-form-buttons">
          <button type="button" className="small-button" onClick={handleFormTest} disabled={isFormTesting || isSaving}>
            {isFormTesting ? 'Testing...' : 'Test connection'}
          </button>
          <button type="submit" className="run-button" disabled={isSaving || isFormTesting}>
            {isSaving ? 'Saving...' : 'Save'}
          </button>
        </div>

        {/* Result of "Test connection" */}
        {formTestResult && <TestResult result={formTestResult} />}
        {saveError && <div className="card-error">Could not save: {saveError}</div>}
        {saveMessage && <p className="saved-message">{saveMessage}</p>}
      </form>
    </div>
  )
}

export default DataSourcesPage
