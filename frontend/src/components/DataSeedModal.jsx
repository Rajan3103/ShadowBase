// DataSeedModal.jsx — Interactive Data Seeding Modal.
// Provides preflight schema compatibility checks, topological table ordering preview,
// job initiation, live row progress tracking, and seeding execution history.
import { useEffect, useState } from 'react'
import {
  startDataSeed,
  validateDataSeed,
  getDataSeedJob,
  listDataSeedJobs,
  cancelDataSeedJob,
  getErrorMessage,
} from '../services/api.js'

function badgeClass(status) {
  switch (status) {
    case 'SUCCESS':
    case 'COMPLETED':
      return 'badge badge-green'
    case 'RUNNING':
    case 'IN_PROGRESS':
    case 'STARTING':
      return 'badge badge-amber'
    case 'FAILED':
      return 'badge badge-red'
    case 'CANCELLED':
    case 'SKIPPED':
      return 'badge badge-grey'
    default:
      return 'badge badge-grey'
  }
}

function DataSeedModal({ sandbox, containers, onClose, onJobStarted }) {
  const [activeTab, setActiveTab] = useState('new') // 'new' | 'progress' | 'history'

  // New Job Configuration
  const [selectedContainerId, setSelectedContainerId] = useState(
    containers.length > 0 ? containers[0].id : ''
  )
  const [mode, setMode] = useState('FULL_DATABASE') // 'FULL_DATABASE' | 'SELECTED_TABLES'
  const [tableListText, setTableListText] = useState('')
  const [consistencyMode, setConsistencyMode] = useState('SNAPSHOT') // 'SNAPSHOT' | 'STANDARD'

  // Preflight validation state
  const [isValidating, setIsValidating] = useState(false)
  const [validationResult, setValidationResult] = useState(null)
  const [validationError, setValidationError] = useState(null)

  // Starting job state
  const [isStarting, setIsStarting] = useState(false)
  const [startError, setStartError] = useState(null)

  // Active / Selected Job Tracker state
  const [currentJob, setCurrentJob] = useState(null)
  const [isCancelling, setIsCancelling] = useState(false)

  // History state
  const [history, setHistory] = useState([])
  const [historyLoading, setHistoryLoading] = useState(false)

  // Poll active job while it is RUNNING or QUEUED
  useEffect(() => {
    if (!currentJob) return
    const isJobActive = currentJob.status === 'RUNNING' || currentJob.status === 'QUEUED'
    if (!isJobActive) return

    const timer = setInterval(async () => {
      try {
        const updated = await getDataSeedJob(sandbox.id, currentJob.id)
        setCurrentJob(updated)
      } catch {
        // Polling failure — retry next tick
      }
    }, 2000)

    return () => clearInterval(timer)
  }, [currentJob, sandbox.id])

  // Load history on mount or tab switch
  async function loadHistory() {
    setHistoryLoading(true)
    try {
      const jobs = await listDataSeedJobs(sandbox.id)
      setHistory(jobs)
      // If there is an active job, track it
      const active = jobs.find((j) => j.status === 'RUNNING' || j.status === 'QUEUED')
      if (active && !currentJob) {
        setCurrentJob(active)
      }
    } catch {
      // Ignore
    } finally {
      setHistoryLoading(false)
    }
  }

  useEffect(() => {
    loadHistory()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sandbox.id])

  function getTablesArray() {
    if (mode === 'FULL_DATABASE') return []
    return tableListText
      .split(',')
      .map((t) => t.trim())
      .filter((t) => t.length > 0)
  }

  async function handleValidate() {
    setIsValidating(true)
    setValidationError(null)
    setValidationResult(null)
    try {
      const req = {
        mode,
        tables: getTablesArray(),
        consistencyMode,
        containerId: selectedContainerId || null,
      }
      const res = await validateDataSeed(sandbox.id, req)
      setValidationResult(res)
    } catch (err) {
      setValidationError(getErrorMessage(err))
    } finally {
      setIsValidating(false)
    }
  }

  async function handleStart() {
    setIsStarting(true)
    setStartError(null)
    try {
      const req = {
        mode,
        tables: getTablesArray(),
        consistencyMode,
        containerId: selectedContainerId || null,
      }
      const job = await startDataSeed(sandbox.id, req)
      setCurrentJob(job)
      setActiveTab('progress')
      if (onJobStarted) onJobStarted(job)
      loadHistory()
    } catch (err) {
      setStartError(getErrorMessage(err))
    } finally {
      setIsStarting(false)
    }
  }

  async function handleCancelJob() {
    if (!currentJob) return
    setIsCancelling(true)
    try {
      const cancelled = await cancelDataSeedJob(sandbox.id, currentJob.id)
      setCurrentJob(cancelled)
      loadHistory()
    } catch {
      // Handle error
    } finally {
      setIsCancelling(false)
    }
  }

  const isJobRunning = currentJob && (currentJob.status === 'RUNNING' || currentJob.status === 'QUEUED')
  const progressPercent = currentJob && currentJob.totalTables > 0
    ? Math.round(((currentJob.completedTables || 0) / currentJob.totalTables) * 100)
    : 0

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-box"
        style={{ maxWidth: '750px', width: '92%' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* ---- Header ---- */}
        <div className="card-header" style={{ marginBottom: '16px' }}>
          <div>
            <h3 style={{ margin: 0 }}>Data Seeding Management</h3>
            <p className="card-meta">Sandbox: {sandbox.name} ({sandbox.id.slice(0, 8)})</p>
          </div>
          <button className="small-button" onClick={onClose}>
            ✕
          </button>
        </div>

        {/* ---- Tabs ---- */}
        <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid #334155', marginBottom: '16px' }}>
          <button
            className={`small-button ${activeTab === 'new' ? 'active' : ''}`}
            style={{
              borderRadius: '6px 6px 0 0',
              background: activeTab === 'new' ? '#1e3a5f' : '#1f2937',
            }}
            onClick={() => setActiveTab('new')}
          >
            Start Seeding Job
          </button>
          <button
            className={`small-button ${activeTab === 'progress' ? 'active' : ''}`}
            style={{
              borderRadius: '6px 6px 0 0',
              background: activeTab === 'progress' ? '#1e3a5f' : '#1f2937',
            }}
            onClick={() => setActiveTab('progress')}
          >
            Job Progress {isJobRunning && <span className="status-dot green" style={{ display: 'inline-block', marginLeft: '6px' }} />}
          </button>
          <button
            className={`small-button ${activeTab === 'history' ? 'active' : ''}`}
            style={{
              borderRadius: '6px 6px 0 0',
              background: activeTab === 'history' ? '#1e3a5f' : '#1f2937',
            }}
            onClick={() => {
              setActiveTab('history')
              loadHistory()
            }}
          >
            History ({history.length})
          </button>
        </div>

        {/* ---- Tab 1: Start New Seeding Job ---- */}
        {activeTab === 'new' && (
          <div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                  Target Database Container
                </label>
                <select
                  className="text-input"
                  style={{ width: '100%' }}
                  value={selectedContainerId}
                  onChange={(e) => setSelectedContainerId(e.target.value)}
                >
                  {containers.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.databaseType} - {c.databaseName} (ID: {c.id.slice(0, 8)})
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'flex', gap: '16px' }}>
                <div style={{ flex: 1 }}>
                  <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                    Copy Mode
                  </label>
                  <select
                    className="text-input"
                    style={{ width: '100%' }}
                    value={mode}
                    onChange={(e) => setMode(e.target.value)}
                  >
                    <option value="FULL_DATABASE">Full Database Copy</option>
                    <option value="SELECTED_TABLES">Selected Tables Only</option>
                  </select>
                </div>

                <div style={{ flex: 1 }}>
                  <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                    Read Consistency
                  </label>
                  <select
                    className="text-input"
                    style={{ width: '100%' }}
                    value={consistencyMode}
                    onChange={(e) => setConsistencyMode(e.target.value)}
                  >
                    <option value="SNAPSHOT">SNAPSHOT (Repeatable Read)</option>
                    <option value="STANDARD">STANDARD (Read Committed)</option>
                  </select>
                </div>
              </div>

              {mode === 'SELECTED_TABLES' && (
                <div>
                  <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                    Tables to Copy (Comma-separated)
                  </label>
                  <input
                    type="text"
                    className="text-input"
                    style={{ width: '100%' }}
                    placeholder="e.g. users, products, orders, order_items"
                    value={tableListText}
                    onChange={(e) => setTableListText(e.target.value)}
                  />
                </div>
              )}

              {/* Preflight Validation Section */}
              <div
                style={{
                  background: '#111827',
                  border: '1px solid #334155',
                  borderRadius: '8px',
                  padding: '14px',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <strong style={{ fontSize: '14px' }}>Preflight Schema Validation</strong>
                    <p className="card-meta" style={{ margin: '2px 0 0' }}>
                      Validates database compatibility and foreign key topological order before transferring data.
                    </p>
                  </div>
                  <button
                    type="button"
                    className="small-button"
                    onClick={handleValidate}
                    disabled={isValidating || isStarting}
                  >
                    {isValidating ? 'Validating...' : 'Validate Preflight'}
                  </button>
                </div>

                {validationError && (
                  <div className="card-error" style={{ marginTop: '10px' }}>
                    {validationError}
                  </div>
                )}

                {validationResult && (
                  <div style={{ marginTop: '12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                      <span className={validationResult.valid ? 'badge badge-green' : 'badge badge-red'}>
                        {validationResult.valid ? 'Preflight Passed' : 'Preflight Failed'}
                      </span>
                      <span className="card-meta">
                        Engines: {validationResult.sourceEngine || 'SOURCE'} ➔ {validationResult.targetEngine || 'TARGET'}
                      </span>
                    </div>

                    {validationResult.plannedOrder && validationResult.plannedOrder.length > 0 && (
                      <div style={{ marginTop: '8px' }}>
                        <span className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                          Topological Insertion Order:
                        </span>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', alignItems: 'center' }}>
                          {validationResult.plannedOrder.map((tbl, i) => (
                            <span key={tbl} style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                              <span
                                style={{
                                  background: '#1f2937',
                                  padding: '2px 8px',
                                  borderRadius: '4px',
                                  fontSize: '12px',
                                  fontFamily: 'monospace',
                                }}
                              >
                                {tbl}
                              </span>
                              {i < validationResult.plannedOrder.length - 1 && (
                                <span style={{ color: '#94a3b8' }}>➔</span>
                              )}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {validationResult.validationWarnings && validationResult.validationWarnings.length > 0 && (
                      <div style={{ marginTop: '8px', color: '#f59e0b', fontSize: '12px' }}>
                        {validationResult.validationWarnings.map((w, idx) => (
                          <div key={idx}>⚠ {w}</div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {startError && <div className="card-error">{startError}</div>}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '10px' }}>
                <button className="small-button" type="button" onClick={onClose}>
                  Cancel
                </button>
                <button
                  className="run-button"
                  type="button"
                  onClick={handleStart}
                  disabled={isStarting || isValidating || containers.length === 0}
                >
                  {isStarting ? 'Starting Seeding...' : 'Launch Seeding Job'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ---- Tab 2: Live Progress Tracker ---- */}
        {activeTab === 'progress' && (
          <div>
            {!currentJob ? (
              <div style={{ padding: '36px', textAlign: 'center' }}>
                <p className="card-empty">No active seeding job to display.</p>
                <button className="small-button" onClick={() => setActiveTab('new')}>
                  Start a New Seeding Job
                </button>
              </div>
            ) : (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span className={badgeClass(currentJob.status)}>{currentJob.status}</span>
                      <strong style={{ fontSize: '15px' }}>Job {currentJob.id.slice(0, 8)}</strong>
                      <span className="card-meta">({currentJob.mode})</span>
                    </div>
                    <div className="card-meta" style={{ marginTop: '2px' }}>
                      Started: {currentJob.startedAt ? new Date(currentJob.startedAt).toLocaleTimeString() : 'Pending'}
                      {currentJob.completedAt && ` · Completed: ${new Date(currentJob.completedAt).toLocaleTimeString()}`}
                    </div>
                  </div>
                  {isJobRunning && (
                    <button
                      className="small-button danger"
                      onClick={handleCancelJob}
                      disabled={isCancelling}
                    >
                      {isCancelling ? 'Cancelling...' : 'Cancel Job'}
                    </button>
                  )}
                </div>

                {/* Progress Bar */}
                <div style={{ margin: '14px 0' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', marginBottom: '4px' }}>
                    <span>Tables Completed: {currentJob.completedTables || 0} / {currentJob.totalTables || 0}</span>
                    <span>{progressPercent}%</span>
                  </div>
                  <div style={{ height: '8px', background: '#334155', borderRadius: '4px', overflow: 'hidden' }}>
                    <div
                      style={{
                        width: `${progressPercent}%`,
                        height: '100%',
                        background: currentJob.status === 'FAILED' ? '#ef4444' : '#22c55e',
                        transition: 'width 0.3s ease',
                      }}
                    />
                  </div>
                </div>

                {/* Counter Cards */}
                <div className="metric-grid" style={{ marginBottom: '16px' }}>
                  <div className="metric-card">
                    <div className="metric-label">Rows Inserted</div>
                    <div className="metric-value" style={{ color: '#22c55e' }}>
                      {(currentJob.totalRowsInserted || 0).toLocaleString()}
                    </div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Rows Read</div>
                    <div className="metric-value">
                      {(currentJob.totalRowsRead || 0).toLocaleString()}
                    </div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Consistency</div>
                    <div className="metric-value" style={{ fontSize: '16px' }}>
                      {currentJob.consistencyMode || 'SNAPSHOT'}
                    </div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Status</div>
                    <div className="metric-value" style={{ fontSize: '16px' }}>
                      {currentJob.status}
                    </div>
                  </div>
                </div>

                {currentJob.errorMessage && (
                  <div className="card-error" style={{ marginBottom: '12px' }}>
                    {currentJob.errorMessage}
                  </div>
                )}

                {/* Table-by-Table Progress Breakdown */}
                {currentJob.tables && currentJob.tables.length > 0 && (
                  <div className="results-scroll">
                    <table className="results-table">
                      <thead>
                        <tr>
                          <th>Table Name</th>
                          <th>Status</th>
                          <th>Rows Read</th>
                          <th>Rows Inserted</th>
                        </tr>
                      </thead>
                      <tbody>
                        {currentJob.tables.map((tbl, idx) => (
                          <tr key={tbl.id || idx}>
                            <td>
                              <code>{tbl.tableName}</code>
                            </td>
                            <td>
                              <span className={badgeClass(tbl.status)}>{tbl.status}</span>
                            </td>
                            <td>{(tbl.rowsRead || 0).toLocaleString()}</td>
                            <td style={{ color: tbl.status === 'COMPLETED' ? '#22c55e' : 'inherit' }}>
                              {(tbl.rowsInserted || 0).toLocaleString()}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* ---- Tab 3: History ---- */}
        {activeTab === 'history' && (
          <div>
            {historyLoading && <p className="card-meta">Loading past seeding jobs...</p>}
            {!historyLoading && history.length === 0 && (
              <p className="card-empty">No seeding jobs recorded for this sandbox yet.</p>
            )}

            {!historyLoading && history.length > 0 && (
              <div className="results-scroll">
                <table className="results-table">
                  <thead>
                    <tr>
                      <th>Job ID</th>
                      <th>Mode</th>
                      <th>Status</th>
                      <th>Tables</th>
                      <th>Rows</th>
                      <th>Timestamp</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((j) => (
                      <tr key={j.id}>
                        <td>
                          <code>{j.id.slice(0, 8)}</code>
                        </td>
                        <td>{j.mode}</td>
                        <td>
                          <span className={badgeClass(j.status)}>{j.status}</span>
                        </td>
                        <td>
                          {j.completedTables || 0} / {j.totalTables || 0}
                        </td>
                        <td style={{ color: '#22c55e' }}>
                          {(j.totalRowsInserted || 0).toLocaleString()}
                        </td>
                        <td className="card-meta" style={{ fontSize: '12px' }}>
                          {j.createdAt ? new Date(j.createdAt).toLocaleString() : '-'}
                        </td>
                        <td>
                          <button
                            className="small-button"
                            onClick={() => {
                              setCurrentJob(j)
                              setActiveTab('progress')
                            }}
                          >
                            View
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default DataSeedModal