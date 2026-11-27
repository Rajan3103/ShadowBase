// useDocumentTitle.js — sets the browser tab title for a page, e.g. "Sandboxes · ShadowBase".
// Usage inside a page component:  useDocumentTitle('Sandboxes')
import { useEffect } from 'react'

export function useDocumentTitle(title) {
  // Runs when the page appears (and again if the title changes).
  useEffect(() => {
    document.title = `${title} · ShadowBase`
  }, [title])
}
