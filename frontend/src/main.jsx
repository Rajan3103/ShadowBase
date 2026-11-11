// main.jsx — the entry point. It finds the <div id="root"> in index.html
// and renders our whole React app inside it.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {/* BrowserRouter lets the app change pages based on the URL (e.g. /editor) */}
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
