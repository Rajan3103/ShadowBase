// ToastProvider.jsx — small pop-up notifications ("toasts") in the bottom-right corner.
//
// How to use it in any page or component:
//   import { useToast } from '../components/ToastProvider.jsx'
//   const toast = useToast()
//   toast.success('Script saved')     // green, hides after 4 s
//   toast.info('Metrics reset')        // teal, hides after 4 s
//   toast.error('Migration failed')    // red, hides after 7 s
//
// It works with React "context": <ToastProvider> wraps the whole app (see main.jsx) and
// keeps the list of toasts; useToast() lets any component inside it add one.
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'

const MAX_VISIBLE = 4 // older toasts are removed when a 5th one arrives
const HIDE_AFTER_MS = { success: 4000, info: 4000, error: 7000 }

// The context holds the { success, error, info } functions. If a component is used outside
// the provider, the toasts simply do nothing (instead of crashing the page).
const noop = () => {}
const ToastContext = createContext({ success: noop, error: noop, info: noop })

// The hook components call to get the toast functions.
// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  return useContext(ToastContext)
}

function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]) // [{ id, type, message }], oldest first
  const nextIdRef = useRef(1) // gives every toast a unique id
  const timersRef = useRef(new Map()) // id → its auto-hide timer

  // Removes one toast (close button or timer) and stops its timer.
  const removeToast = useCallback((id) => {
    clearTimeout(timersRef.current.get(id))
    timersRef.current.delete(id)
    setToasts((list) => list.filter((toast) => toast.id !== id))
  }, [])

  // Adds a toast and starts its auto-hide timer. Keeps at most 4 on screen.
  const addToast = useCallback(
    (type, message) => {
      const id = nextIdRef.current++
      setToasts((list) => {
        const updated = [...list, { id, type, message }]
        // Too many? Drop the oldest ones (and their timers).
        const dropped = updated.slice(0, Math.max(0, updated.length - MAX_VISIBLE))
        for (const toast of dropped) {
          clearTimeout(timersRef.current.get(toast.id))
          timersRef.current.delete(toast.id)
        }
        return updated.slice(-MAX_VISIBLE)
      })
      timersRef.current.set(id, setTimeout(() => removeToast(id), HIDE_AFTER_MS[type]))
    },
    [removeToast],
  )

  // Stop every timer if the app closes.
  useEffect(() => {
    const timers = timersRef.current
    return () => timers.forEach((timer) => clearTimeout(timer))
  }, [])

  // The object components get from useToast(). useMemo keeps it the same between renders.
  const toast = useMemo(
    () => ({
      success: (message) => addToast('success', message),
      error: (message) => addToast('error', message),
      info: (message) => addToast('info', message),
    }),
    [addToast],
  )

  return (
    <ToastContext.Provider value={toast}>
      {children}

      {/* The stack of toasts (newest at the bottom). */}
      <div className="toast-stack">
        {toasts.map((item) => (
          <div
            key={item.id}
            className={`toast toast-${item.type}`}
            // Screen readers read "status" politely and "alert" (errors) straight away.
            role={item.type === 'error' ? 'alert' : 'status'}
          >
            <span className="toast-message">{item.message}</span>
            <button
              className="toast-close"
              onClick={() => removeToast(item.id)}
              aria-label="Close notification"
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export default ToastProvider
