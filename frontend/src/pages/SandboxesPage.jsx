// SandboxesPage.jsx — lists your shadow database sandboxes and lets you create new ones.
// Each sandbox is shown with a SandboxCard. The page keeps the list; each card
// handles its own buttons and containers.
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import SandboxCard from '../components/SandboxCard.jsx'
import {
  createSandbox,
  listSandboxes,
  getErrorMessage,
  isMockMode,
  USER_ID_KEY,
  CURRENT_SANDBOX_KEY,
  DEMO_USER_ID,
} from '../services/api.js'
import { useToast } from '../components/ToastProvider.jsx'
import { useDocumentTitle } from '../hooks/useDocumentTitle.js'

// Reads a value from localStorage, or '' if missing or blocked.
function readStorage(key) {
  try {
    return localStorage.getItem(key) ?? ''
  } catch {
    return ''
  }
}

// Saves a value to localStorage; if storage is blocked we just skip it.
function writeStorage(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // Not saved — fine, the page still works.
  }
}

// Which user's sandboxes to show. In mock mode with no saved ID we use the demo user,
// so the example sandbox appears. With the real backend, a saved ID is required.
function getUserId() {
  const saved = readStorage(USER_ID_KEY)
  if (saved) return saved
  return isMockMode() ? DEMO_USER_ID : ''
}

function SandboxesPage() {
  useDocumentTitle('Sandboxes') // browser tab: "Sandboxes · ShadowBase"
  const toast = useToast() // pop-up notifications
  const [userId] = useState(getUserId)
  const [sandboxes, setSandboxes] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [listError, setListError] = useState(null)
  // Goes up by 1 on each Refresh, telling every card to reload its containers.
  const [refreshKey, setRefreshKey] = useState(0)
  // The sandbox the editor should use.
  const [currentSandboxId, setCurrentSandboxId] = useState(() => readStorage(CURRENT_SANDBOX_KEY))

  // "Create sandbox" form
  const [newName, setNewName] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [createError, setCreateError] = useState(null)

  // Loads the list of sandboxes from the API.
  async function loadSandboxes() {
    if (!userId) return
    setIsLoading(true)
    setListError(null)
    try {
      setSandboxes(await listSandboxes(userId))
    } catch (err) {
      setListError(getErrorMessage(err))
    } finally {
      setIsLoading(false)
    }
  }

  // Load once when the page opens.
  useEffect(() => {
    loadSandboxes()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Refresh: reload the list and tell every card to reload its containers.
  function handleRefresh() {
    loadSandboxes()
    setRefreshKey((key) => key + 1)
  }

  // Create a sandbox, then add it to the list.
  async function handleCreate(event) {
    event.preventDefault() // stop the form from reloading the page
    setIsCreating(true)
    setCreateError(null)
    try {
      const created = await createSandbox(newName.trim(), userId)
      setSandboxes((list) => [...list, created])
      toast.success(`Sandbox "${created.name}" created`)
      setNewName('')
    } catch (err) {
      setCreateError(getErrorMessage(err))
    } finally {
      setIsCreating(false)
    }
  }

  // A card sends back an updated sandbox (after Start/Stop/Delete); replace the old one.
  function handleUpdate(updated) {
    setSandboxes((list) => list.map((s) => (s.id === updated.id ? updated : s)))
    // A deleted sandbox can't stay active.
    if (updated.status === 'DESTROYED' && updated.id === currentSandboxId) {
      setCurrentSandboxId('')
      writeStorage(CURRENT_SANDBOX_KEY, '')
    }
  }

  // Make a sandbox the active one for the editor.
  function handleUseInEditor(id) {
    setCurrentSandboxId(id)
    writeStorage(CURRENT_SANDBOX_KEY, id)
  }

  // No user ID and real backend: we can't load anything yet.
  if (!userId) {
    return (
      <div>
        <h2>Sandboxes</h2>
        <div className="notice">
          No user ID is set. Add one on the <Link to="/settings">Settings</Link> page, or turn
          on mock data there.
        </div>
      </div>
    )
  }

  return (
    <div>
      <div className="toolbar">
        <h2>Sandboxes</h2>
        <button className="run-button" onClick={handleRefresh} disabled={isLoading}>
          {isLoading ? 'Loading...' : 'Refresh'}
        </button>
      </div>

      {/* ---- Create sandbox form ---- */}
      <form className="create-form" onSubmit={handleCreate}>
        <input
          type="text"
          className="text-input"
          placeholder="New sandbox name"
          value={newName}
          onChange={(event) => setNewName(event.target.value)}
        />
        <button className="run-button" type="submit" disabled={isCreating || !newName.trim()}>
          {isCreating ? 'Creating...' : 'Create sandbox'}
        </button>
      </form>
      {createError && <div className="card-error">{createError}</div>}

      {listError && <div className="card-error">{listError}</div>}

      {/* ---- The cards ---- */}
      {!isLoading && sandboxes.length === 0 && !listError && (
        <p className="card-empty">No sandboxes yet. Create one above.</p>
      )}
      <div className="sandbox-grid">
        {sandboxes.map((sandbox) => (
          <SandboxCard
            key={sandbox.id}
            sandbox={sandbox}
            isActive={sandbox.id === currentSandboxId}
            refreshKey={refreshKey}
            onUpdate={handleUpdate}
            onUseInEditor={() => handleUseInEditor(sandbox.id)}
          />
        ))}
      </div>
    </div>
  )
}

export default SandboxesPage
