// SettingsPage.jsx — lets the user switch mock mode on/off and select/create a user ID.
// Both settings are saved in the browser (localStorage), so they survive a page refresh.
import { useEffect, useState } from 'react'
import {
  isMockMode,
  USE_MOCK_KEY,
  USER_ID_KEY,
  listUsers,
  createUser,
  getDemoUser,
  DEMO_USER_ID,
  getErrorMessage,
} from '../services/api.js'

// Reads the saved user ID ('' if none). localStorage can throw, so we use try/catch.
function loadUserId() {
  try {
    return localStorage.getItem(USER_ID_KEY) ?? ''
  } catch {
    return ''
  }
}

// Saves a value to localStorage, ignoring errors if storage is blocked.
function save(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // Storage not available — the setting just won't be remembered.
  }
}

function SettingsPage() {
  // Start with the values that were saved before.
  const [useMock, setUseMock] = useState(isMockMode)
  const [userId, setUserId] = useState(loadUserId)
  const [users, setUsers] = useState([])
  const [newUserEmail, setNewUserEmail] = useState('')
  const [isCreatingUser, setIsCreatingUser] = useState(false)
  const [userError, setUserError] = useState(null)
  // Goes up by 1 on every save; used to show the "Saved" message.
  const [saveCount, setSaveCount] = useState(0)
  const [showSaved, setShowSaved] = useState(false)

  // Load available users from backend / mock
  useEffect(() => {
    async function fetchUsers() {
      try {
        const list = await listUsers()
        setUsers(list)
        if (!userId && list.length > 0) {
          const firstId = list[0].id
          setUserId(firstId)
          save(USER_ID_KEY, firstId)
        }
      } catch (err) {
        setUserError(getErrorMessage(err))
      }
    }
    fetchUsers()
  }, [useMock, userId])

  // After each save, show "Saved" for 1.5 seconds, then hide it.
  useEffect(() => {
    if (saveCount === 0) return
    setShowSaved(true)
    const timer = setTimeout(() => setShowSaved(false), 1500)
    return () => clearTimeout(timer) // cancel the old timer if the user saves again quickly
  }, [saveCount])

  function handleMockChange(event) {
    const checked = event.target.checked
    setUseMock(checked)
    save(USE_MOCK_KEY, String(checked)) // stored as the text "true" or "false"
    setSaveCount((count) => count + 1)
  }

  function handleUserIdChange(event) {
    const value = event.target.value.trim()
    setUserId(value)
    save(USER_ID_KEY, value)
    setSaveCount((count) => count + 1)
  }

  function handleSelectUser(event) {
    const selected = event.target.value
    setUserId(selected)
    save(USER_ID_KEY, selected)
    setSaveCount((count) => count + 1)
  }

  async function handleUseDemo() {
    try {
      const demo = await getDemoUser()
      setUserId(demo.id || DEMO_USER_ID)
      save(USER_ID_KEY, demo.id || DEMO_USER_ID)
      setSaveCount((count) => count + 1)
    } catch {
      setUserId(DEMO_USER_ID)
      save(USER_ID_KEY, DEMO_USER_ID)
      setSaveCount((count) => count + 1)
    }
  }

  async function handleCreateUser(event) {
    event.preventDefault()
    if (!newUserEmail.trim()) return
    setIsCreatingUser(true)
    setUserError(null)
    try {
      const created = await createUser(newUserEmail.trim())
      setUsers((prev) => [...prev, created])
      setUserId(created.id)
      save(USER_ID_KEY, created.id)
      setNewUserEmail('')
      setSaveCount((count) => count + 1)
    } catch (err) {
      setUserError(getErrorMessage(err))
    } finally {
      setIsCreatingUser(false)
    }
  }

  return (
    <div className="settings">
      <h2>Settings</h2>

      <label className="settings-checkbox">
        <input type="checkbox" checked={useMock} onChange={handleMockChange} />
        Use mock data (no backend needed)
      </label>

      {/* Available users dropdown */}
      {users.length > 0 && (
        <label className="settings-field">
          Select Existing User
          <select className="text-input" value={userId} onChange={handleSelectUser}>
            <option value="">-- Choose a user account --</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.emailId} ({u.id.slice(0, 8)}...)
              </option>
            ))}
          </select>
        </label>
      )}

      <label className="settings-field">
        User ID (UUID)
        <input
          type="text"
          className="text-input"
          value={userId}
          onChange={handleUserIdChange}
          placeholder="e.g. 3f2b8c1e-0000-0000-0000-000000000000"
        />
      </label>

      <div style={{ marginTop: '8px', marginBottom: '16px' }}>
        <button type="button" className="small-button" onClick={handleUseDemo}>
          Use Default Demo User
        </button>
      </div>

      {/* Create new user account form */}
      <form onSubmit={handleCreateUser} style={{ marginTop: '16px', borderTop: '1px solid #334155', paddingTop: '16px' }}>
        <label className="settings-field">
          Create New User Account
          <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
            <input
              type="email"
              className="text-input"
              value={newUserEmail}
              onChange={(e) => setNewUserEmail(e.target.value)}
              placeholder="e.g. developer@company.com"
            />
            <button
              type="submit"
              className="small-button"
              disabled={isCreatingUser || !newUserEmail.trim()}
            >
              {isCreatingUser ? 'Creating...' : 'Create'}
            </button>
          </div>
        </label>
      </form>

      {userError && <p className="card-error" style={{ marginTop: '12px' }}>{userError}</p>}
      {showSaved && <p className="saved-message">Saved</p>}
    </div>
  )
}

export default SettingsPage
