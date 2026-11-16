// SandboxCard.jsx — one card for one sandbox.
// Shows the sandbox details, its action buttons and a table of its database containers.
// The card loads its own containers and shows its own errors.
import { useEffect, useState } from 'react'
import {
  startSandbox,
  stopSandbox,
  deleteSandbox,
  getSandbox,
  addContainer,
  getContainers,
  getErrorMessage,
} from '../services/api.js'

// The container that "Add PostgreSQL container" creates.
const POSTGRES_REQUEST = {
  databaseType: 'POSTGRESQL',
  databaseName: 'shadow_db',
  username: 'shadow',
  password: 'shadow',
}

// Picks the badge colour class for a sandbox or container status.
function badgeClass(status) {
  switch (status) {
    case 'READY':
    case 'RUNNING':
      return 'badge badge-green'
    case 'CREATING':
    case 'STARTING':
    case 'STOPPING':
      return 'badge badge-amber'
    case 'FAILED':
    case 'DEGRADED':
    case 'UNHEALTHY':
      return 'badge badge-red'
    default: // STOPPED, DESTROYED, REMOVED
      return 'badge badge-grey'
  }
}

// Props:
//   sandbox        — the SandboxResponse to show
//   isActive       — true if this is the sandbox chosen for the editor
//   refreshKey     — a number; when it changes, the card reloads its containers
//   onUpdate       — call with an updated sandbox so the page can replace it in its list
//   onUseInEditor  — call to make this sandbox the active one
function SandboxCard({ sandbox, isActive, refreshKey, onUpdate, onUseInEditor }) {
  const [containers, setContainers] = useState([])
  const [busyAction, setBusyAction] = useState(null) // which button is loading, e.g. 'start'
  const [error, setError] = useState(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [copiedId, setCopiedId] = useState(null) // container whose URL was just copied

  const isDestroyed = sandbox.status === 'DESTROYED'
  const isBusy = busyAction !== null

  // Loads this sandbox's containers. Used on first show, on Refresh, and after actions.
  async function loadContainers() {
    try {
      setContainers(await getContainers(sandbox.id))
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  // Load containers when the card appears and whenever the page's Refresh is clicked.
  useEffect(() => {
    loadContainers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sandbox.id, refreshKey])

  // Shared helper for every button: shows the loading state, runs the action,
  // and puts any error message inside the card.
  async function runAction(name, action) {
    setBusyAction(name)
    setError(null)
    try {
      await action()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setBusyAction(null)
    }
  }

  function handleStart() {
    runAction('start', async () => {
      onUpdate(await startSandbox(sandbox.id))
      await loadContainers()
    })
  }

  function handleStop() {
    runAction('stop', async () => {
      onUpdate(await stopSandbox(sandbox.id))
      await loadContainers()
    })
  }

  function handleDelete() {
    setConfirmingDelete(false)
    runAction('delete', async () => {
      await deleteSandbox(sandbox.id)
      // The backend keeps deleted sandboxes with status DESTROYED, so we reload it.
      onUpdate(await getSandbox(sandbox.id))
      await loadContainers()
    })
  }

  function handleAddPostgres() {
    runAction('add', async () => {
      await addContainer(sandbox.id, POSTGRES_REQUEST)
      await loadContainers()
    })
  }

  // Copies the JDBC URL to the clipboard and shows "Copied!" for 1.5 seconds.
  async function handleCopy(container) {
    try {
      await navigator.clipboard.writeText(container.jdbcUrl)
      setCopiedId(container.id)
      setTimeout(() => setCopiedId(null), 1500)
    } catch {
      setError('Could not copy to the clipboard.')
    }
  }

  return (
    <div className={isActive ? 'sandbox-card active' : 'sandbox-card'}>
      {/* ---- Header: name, badges, id and date ---- */}
      <div className="card-header">
        <h3>{sandbox.name}</h3>
        <div className="card-badges">
          {isActive && <span className="badge badge-active">Active</span>}
          <span className={badgeClass(sandbox.status)}>{sandbox.status}</span>
        </div>
      </div>
      <p className="card-meta">
        ID {sandbox.id.slice(0, 8)} · Created {new Date(sandbox.createdAt).toLocaleString()}
      </p>

      {/* ---- Action buttons (all disabled while one is running) ---- */}
      <div className="card-actions">
        <button className="small-button" onClick={handleStart} disabled={isBusy || isDestroyed}>
          {busyAction === 'start' ? 'Starting...' : 'Start'}
        </button>
        <button className="small-button" onClick={handleStop} disabled={isBusy || isDestroyed}>
          {busyAction === 'stop' ? 'Stopping...' : 'Stop'}
        </button>
        <button
          className="small-button"
          onClick={handleAddPostgres}
          disabled={isBusy || isDestroyed}
        >
          {busyAction === 'add' ? 'Adding...' : 'Add PostgreSQL container'}
        </button>
        <button
          className="small-button"
          onClick={onUseInEditor}
          disabled={isBusy || isDestroyed || isActive}
        >
          Use in editor
        </button>
        <button
          className="small-button danger"
          onClick={() => setConfirmingDelete(true)}
          disabled={isBusy || isDestroyed}
        >
          {busyAction === 'delete' ? 'Deleting...' : 'Delete'}
        </button>
      </div>

      {/* ---- Delete confirmation, shown inside the card ---- */}
      {confirmingDelete && (
        <div className="confirm-box">
          <span>Delete this sandbox and its containers?</span>
          <button className="small-button danger" onClick={handleDelete}>
            Yes, delete
          </button>
          <button className="small-button" onClick={() => setConfirmingDelete(false)}>
            Cancel
          </button>
        </div>
      )}

      {/* ---- Error message for anything that went wrong in this card ---- */}
      {error && <div className="card-error">{error}</div>}

      {/* ---- Containers table ---- */}
      {containers.length === 0 ? (
        <p className="card-empty">No containers yet.</p>
      ) : (
        <div className="results-scroll">
          <table className="results-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Database</th>
                <th>Host</th>
                <th>Port</th>
                <th>Status</th>
                <th>JDBC URL</th>
              </tr>
            </thead>
            <tbody>
              {containers.map((container) => (
                <tr key={container.id}>
                  <td>{container.databaseType}</td>
                  <td>{container.databaseName}</td>
                  <td>{container.host}</td>
                  <td>{container.port}</td>
                  <td>
                    <span className={badgeClass(container.status)}>{container.status}</span>
                  </td>
                  <td>
                    <code>{container.jdbcUrl}</code>{' '}
                    <button className="small-button" onClick={() => handleCopy(container)}>
                      {copiedId === container.id ? 'Copied!' : 'Copy'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default SandboxCard
