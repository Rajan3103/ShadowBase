// DataSourcesPage.jsx — External Source Database Management.
// Lets users register, inspect, and bind external production/staging databases to sandbox containers
// for isolated replication, schema validation, and data seeding.
import { useEffect, useState } from 'react'
import {
  listDataSources,
  createDataSource,
  deleteDataSource,
  linkDataSourceToContainer,
  listSandboxes,
  getErrorMessage,
  USER_ID_KEY,
  DEMO_USER_ID,
  isMockMode,
} from '../services/api.js'

function DataSourcesPage() {
  const [dataSources, setDataSources] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  const [successMessage, setSuccessMessage] = useState(null)

  // Registration modal / form state
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [form, setForm] = useState({
    connectionName: '',
    host: 'localhost',
    port: 5432,
    databaseName: '',
    databaseUsername: 'postgres',
    databasePassword: '',
  })
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  // Bind to container modal state
  const [bindingDataSource, setBindingDataSource] = useState(null)
  const [sandboxes, setSandboxes] = useState([])
  const [selectedSandboxId, setSelectedSandboxId] = useState('')
  const [selectedContainerId, setSelectedContainerId] = useState('')
  const [isBinding, setIsBinding] = useState(false)
  const [bindError, setBindError] = useState(null)

  // Delete confirmation
  const [deletingId, setDeletingId] = useState(null)

  async function loadDataSources() {
    setIsLoading(true)
    setError(null)
    try {
      const list = await listDataSources()
      setDataSources(list)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    let ignore = false
    async function init() {
      setIsLoading(true)
      setError(null)
      try {
        const list = await listDataSources()
        if (!ignore) setDataSources(list)
      } catch (err) {
        if (!ignore) setError(getErrorMessage(err))
      } finally {
        if (!ignore) setIsLoading(false)
      }
    }
    init()
    return () => {
      ignore = true
    }
  }, [])

  function handleOpenCreate() {
    setForm({
      connectionName: '',
      host: 'localhost',
      port: 5432,
      databaseName: '',
      databaseUsername: 'postgres',
      databasePassword: '',
    })
    setFormError(null)
    setShowCreateModal(true)
  }

  async function handleCreateSubmit(e) {
    e.preventDefault()
    setIsSubmitting(true)
    setFormError(null)
    try {
      const created = await createDataSource({
        ...form,
        port: Number(form.port) || 5432,
      })
      setDataSources((prev) => [...prev, created])
      setShowCreateModal(false)
      setSuccessMessage(`Data source "${created.connectionName}" registered successfully`)
      setTimeout(() => setSuccessMessage(null), 3000)
    } catch (err) {
      setFormError(getErrorMessage(err))
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleDelete(id) {
    try {
      await deleteDataSource(id)
      setDataSources((prev) => prev.filter((ds) => ds.id !== id))
      setDeletingId(null)
      setSuccessMessage('Data source deleted')
      setTimeout(() => setSuccessMessage(null), 2500)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  async function handleOpenBind(ds) {
    setBindingDataSource(ds)
    setBindError(null)
    setSelectedSandboxId('')
    setSelectedContainerId('')
    try {
      let uid = ''
      try {
        uid = localStorage.getItem(USER_ID_KEY) || (isMockMode() ? DEMO_USER_ID : '')
      } catch {
        uid = DEMO_USER_ID
      }
      if (uid) {
        const sbs = await listSandboxes(uid)
        setSandboxes(sbs)
        if (sbs.length > 0) {
          setSelectedSandboxId(sbs[0].id)
          const containers = sbs[0].containers || []
          if (containers.length > 0) {
            setSelectedContainerId(containers[0].id)
          }
        }
      }
    } catch (err) {
      setBindError(getErrorMessage(err))
    }
  }

  function handleSandboxSelectChange(sbId) {
    setSelectedSandboxId(sbId)
    const sb = sandboxes.find((s) => s.id === sbId)
    const containers = sb?.containers || []
    setSelectedContainerId(containers.length > 0 ? containers[0].id : '')
  }

  async function handleBindSubmit(e) {
    e.preventDefault()
    if (!selectedSandboxId || !selectedContainerId) return
    setIsBinding(true)
    setBindError(null)
    try {
      const updated = await linkDataSourceToContainer(
        selectedSandboxId,
        selectedContainerId,
        bindingDataSource.id
      )
      setDataSources((prev) =>
        prev.map((ds) => (ds.id === bindingDataSource.id ? { ...ds, containerId: selectedContainerId } : ds))
      )
      setBindingDataSource(null)
      setSuccessMessage(
        `Linked "${updated.connectionName}" to container ${selectedContainerId.slice(0, 8)}`
      )
      setTimeout(() => setSuccessMessage(null), 3000)
    } catch (err) {
      setBindError(getErrorMessage(err))
    } finally {
      setIsBinding(false)
    }
  }

  const selectedSandbox = sandboxes.find((s) => s.id === selectedSandboxId)
  const availableContainers = selectedSandbox?.containers || []

  return (
    <div>
      <div className="toolbar">
        <div>
          <h2>External Data Sources</h2>
          <p className="card-meta" style={{ margin: '4px 0 0' }}>
            Configure source database connections and bind them to sandbox containers for automated seeding.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button className="small-button" onClick={loadDataSources} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
          <button className="run-button" onClick={handleOpenCreate}>
            + Register Data Source
          </button>
        </div>
      </div>

      {successMessage && <div className="saved-message" style={{ margin: '12px 0' }}>{successMessage}</div>}
      {error && <div className="card-error" style={{ margin: '12px 0' }}>{error}</div>}

      {/* ---- Data Sources List ---- */}
      {!isLoading && dataSources.length === 0 && (
        <div className="sandbox-card" style={{ padding: '36px', textAlign: 'center' }}>
          <p className="card-empty">No external DataSources registered yet.</p>
          <button className="run-button" onClick={handleOpenCreate} style={{ marginTop: '12px' }}>
            Register Your First Source Database
          </button>
        </div>
      )}

      {dataSources.length > 0 && (
        <div className="results-scroll" style={{ marginTop: '16px' }}>
          <table className="results-table">
            <thead>
              <tr>
                <th>Connection Name</th>
                <th>Host & Port</th>
                <th>Database</th>
                <th>Username</th>
                <th>Bound Container</th>
                <th>Registered</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {dataSources.map((ds) => (
                <tr key={ds.id}>
                  <td>
                    <strong>{ds.connectionName}</strong>
                    <div className="card-meta" style={{ fontSize: '11px' }}>
                      ID: {ds.id.slice(0, 8)}...
                    </div>
                  </td>
                  <td>
                    <code>{ds.host}:{ds.port}</code>
                  </td>
                  <td>{ds.databaseName}</td>
                  <td>{ds.databaseUsername}</td>
                  <td>
                    {ds.containerId ? (
                      <span className="badge badge-green" title={ds.containerId}>
                        Container: {ds.containerId.slice(0, 8)}
                      </span>
                    ) : (
                      <span className="badge badge-grey">Unbound</span>
                    )}
                  </td>
                  <td className="card-meta" style={{ fontSize: '12px' }}>
                    {new Date(ds.createdAt).toLocaleDateString()}
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                      <button
                        className="small-button"
                        onClick={() => handleOpenBind(ds)}
                        title="Bind to a sandbox container"
                      >
                        {ds.containerId ? 'Re-bind' : 'Bind Container'}
                      </button>
                      {deletingId === ds.id ? (
                        <div style={{ display: 'flex', gap: '4px' }}>
                          <button
                            className="small-button danger"
                            onClick={() => handleDelete(ds.id)}
                          >
                            Confirm
                          </button>
                          <button
                            className="small-button"
                            onClick={() => setDeletingId(null)}
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <button
                          className="small-button danger"
                          onClick={() => setDeletingId(ds.id)}
                        >
                          Delete
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ---- Register Data Source Modal ---- */}
      {showCreateModal && (
        <div className="modal-backdrop" onClick={() => setShowCreateModal(false)}>
          <div
            className="modal-box"
            style={{ maxWidth: '520px', width: '90%' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="card-header" style={{ marginBottom: '16px' }}>
              <h3 style={{ margin: 0 }}>Register Source Database</h3>
              <button className="small-button" onClick={() => setShowCreateModal(false)}>
                ✕
              </button>
            </div>

            {formError && <div className="card-error" style={{ marginBottom: '12px' }}>{formError}</div>}

            <form onSubmit={handleCreateSubmit}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div>
                  <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                    Connection Display Name
                  </label>
                  <input
                    type="text"
                    className="text-input"
                    style={{ width: '100%' }}
                    placeholder="e.g. prod-replica-us-east"
                    required
                    value={form.connectionName}
                    onChange={(e) => setForm({ ...form, connectionName: e.target.value })}
                  />
                </div>

                <div style={{ display: 'flex', gap: '12px' }}>
                  <div style={{ flex: 3 }}>
                    <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                      Hostname / IP
                    </label>
                    <input
                      type="text"
                      className="text-input"
                      style={{ width: '100%' }}
                      placeholder="db.example.com or localhost"
                      required
                      value={form.host}
                      onChange={(e) => setForm({ ...form, host: e.target.value })}
                    />
                  </div>
                  <div style={{ flex: 1 }}>
                    <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                      Port
                    </label>
                    <input
                      type="number"
                      className="text-input"
                      style={{ width: '100%' }}
                      placeholder="5432"
                      required
                      value={form.port}
                      onChange={(e) => setForm({ ...form, port: e.target.value })}
                    />
                  </div>
                </div>

                <div>
                  <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                    Database Name
                  </label>
                  <input
                    type="text"
                    className="text-input"
                    style={{ width: '100%' }}
                    placeholder="e.g. ecommerce_production"
                    required
                    value={form.databaseName}
                    onChange={(e) => setForm({ ...form, databaseName: e.target.value })}
                  />
                </div>

                <div style={{ display: 'flex', gap: '12px' }}>
                  <div style={{ flex: 1 }}>
                    <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                      Username
                    </label>
                    <input
                      type="text"
                      className="text-input"
                      style={{ width: '100%' }}
                      placeholder="e.g. read_only_user"
                      required
                      value={form.databaseUsername}
                      onChange={(e) => setForm({ ...form, databaseUsername: e.target.value })}
                    />
                  </div>
                  <div style={{ flex: 1 }}>
                    <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                      Password
                    </label>
                    <input
                      type="password"
                      className="text-input"
                      style={{ width: '100%' }}
                      placeholder="Database Password"
                      value={form.databasePassword}
                      onChange={(e) => setForm({ ...form, databasePassword: e.target.value })}
                    />
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
                <button
                  type="button"
                  className="small-button"
                  onClick={() => setShowCreateModal(false)}
                  disabled={isSubmitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="run-button"
                  disabled={isSubmitting || !form.connectionName.trim() || !form.databaseName.trim()}
                >
                  {isSubmitting ? 'Registering...' : 'Register Connection'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ---- Bind to Container Modal ---- */}
      {bindingDataSource && (
        <div className="modal-backdrop" onClick={() => setBindingDataSource(null)}>
          <div
            className="modal-box"
            style={{ maxWidth: '500px', width: '90%' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="card-header" style={{ marginBottom: '16px' }}>
              <div>
                <h3 style={{ margin: 0 }}>Bind DataSource to Container</h3>
                <p className="card-meta">Source: {bindingDataSource.connectionName}</p>
              </div>
              <button className="small-button" onClick={() => setBindingDataSource(null)}>
                ✕
              </button>
            </div>

            {bindError && <div className="card-error" style={{ marginBottom: '12px' }}>{bindError}</div>}

            <form onSubmit={handleBindSubmit}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div>
                  <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                    Target Sandbox
                  </label>
                  <select
                    className="text-input"
                    style={{ width: '100%' }}
                    value={selectedSandboxId}
                    onChange={(e) => handleSandboxSelectChange(e.target.value)}
                  >
                    {sandboxes.length === 0 && <option value="">No sandboxes found</option>}
                    {sandboxes.map((sb) => (
                      <option key={sb.id} value={sb.id}>
                        {sb.name} ({sb.status})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="card-meta" style={{ display: 'block', marginBottom: '4px' }}>
                    Target Database Container
                  </label>
                  <select
                    className="text-input"
                    style={{ width: '100%' }}
                    value={selectedContainerId}
                    onChange={(e) => setSelectedContainerId(e.target.value)}
                    disabled={availableContainers.length === 0}
                  >
                    {availableContainers.length === 0 ? (
                      <option value="">No running containers in this sandbox</option>
                    ) : (
                      availableContainers.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.databaseType} - {c.databaseName} ({c.id.slice(0, 8)})
                        </option>
                      ))
                    )}
                  </select>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '20px' }}>
                <button
                  type="button"
                  className="small-button"
                  onClick={() => setBindingDataSource(null)}
                  disabled={isBinding}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="run-button"
                  disabled={isBinding || !selectedContainerId}
                >
                  {isBinding ? 'Binding...' : 'Bind Container'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

export default DataSourcesPage