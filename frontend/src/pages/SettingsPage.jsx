// SettingsPage.jsx — lets the user switch mock mode on/off and enter their user ID.
// Both settings are saved in the browser (localStorage), so they survive a page refresh.
import { useEffect, useState } from 'react'
import { isMockMode, USE_MOCK_KEY, USER_ID_KEY } from '../services/api.js'

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
  // Goes up by 1 on every save; used to show the "Saved" message.
  const [saveCount, setSaveCount] = useState(0)
  const [showSaved, setShowSaved] = useState(false)

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

  return (
    <div className="settings">
      <h2>Settings</h2>

      <label className="settings-checkbox">
        <input type="checkbox" checked={useMock} onChange={handleMockChange} />
        Use mock data (no backend needed)
      </label>

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
      <p className="settings-note">Ask the backend team for a valid user ID.</p>

      {showSaved && <p className="saved-message">Saved</p>}
    </div>
  )
}

export default SettingsPage
