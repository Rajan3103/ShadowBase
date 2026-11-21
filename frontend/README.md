# ShadowBase – Frontend

The browser UI for **ShadowBase**, a zero-downtime schema migration sandbox.
Write and test migration scripts against shadow database containers, and watch live shadow-traffic errors before anything reaches production.

## Features

- **SQL Editor**
  - Choose the target container in the active sandbox
  - Run queries (button or **Ctrl+Enter** / **Cmd+Enter**) and see results with a row count, time and message
  - **Apply Migration** with an in-page safety confirmation
  - History of the last 20 scripts you ran
  - Save, load and delete named scripts; **Open** a `.sql` file and **Export** to `.sql`
  - Layouts: **Split**, **Side by side**, **Editor only**, plus **Focus mode** (hides the sidebar, Esc to exit)
- **Sandboxes**
  - Create, start, stop and delete sandboxes
  - Add containers and see their status and JDBC URLs
  - Import a schema (`.sql`) and seed data
  - Pick the active sandbox for the editor and dashboard
- **Dashboard**
  - Live metrics: queries replayed, failed queries, error rate and status
  - Error-rate chart with a 5% warning line
  - Live SQL error console with filters (can be shown or hidden)
- **Settings**
  - Turn mock mode on or off
  - Choose, enter or create a user ID

## Tech stack

- [React 19](https://react.dev)
- [Vite](https://vite.dev)
- [React Router](https://reactrouter.com)
- [Monaco Editor](https://github.com/suren-atoyan/monaco-react) (`@monaco-editor/react`)
- [Axios](https://axios-http.com)
- [Recharts](https://recharts.org)

## Prerequisites

- **Node.js 20+** (Vite needs 20.19+ or 22.12+). Check with `node -v`.

## Run it

```bash
npm install      # install dependencies (first time, or after package.json changes)
npm run dev      # start the dev server at http://localhost:5173
npm run build    # production build into dist/
```

## Mock mode vs real backend

**Mock mode is on by default.** Every page works with realistic fake data, so no backend is needed.

To use the real backend:

1. Start the Spring Boot backend on **http://localhost:8080** (see the main [README](../README.md)).
2. Run `npm run dev`. The Vite dev server forwards every request starting with `/api` to `http://localhost:8080` (see `vite.config.js`), so there are no CORS problems.
3. Open **Settings**, untick **Use mock data**, and choose or create a user.

> The live metrics and the error console still use mock data, because the backend has no metrics or errors endpoint yet.

## Folder structure

```
src/
├── main.jsx                  # Entry point: renders <App> inside BrowserRouter
├── App.jsx                   # Layout (sidebar + page), routes, focus mode
├── index.css                 # All styles (dark theme)
├── components/
│   ├── Sidebar.jsx           # Title and navigation links
│   ├── SqlEditor.jsx         # Monaco SQL editor + Ctrl/Cmd+Enter shortcut
│   ├── ResultsTable.jsx      # Query results, empty message or error
│   ├── SandboxCard.jsx       # One sandbox: actions, containers, schema import, seeding
│   └── ErrorConsole.jsx      # Live SQL error console with filters
├── pages/
│   ├── EditorPage.jsx        # SQL editor, migrations, history, saved scripts, layouts
│   ├── SandboxesPage.jsx     # List and create sandboxes
│   ├── DashboardPage.jsx     # Live metrics, error-rate chart, error console
│   └── SettingsPage.jsx      # Mock mode and user settings
└── services/
    ├── api.js                # Backend REST calls, with mock mode
    ├── metrics.js            # Live metrics and error streams (mock for now)
    └── scripts.js            # Saved scripts in localStorage
```

## Browser storage (localStorage)

| Key | What it stores |
|---|---|
| `shadowbase.useMock` | Mock mode on / off (`"true"` / `"false"`, default on) |
| `shadowbase.userId` | The current user ID |
| `shadowbase.currentSandboxId` | The active sandbox for the editor and dashboard |
| `shadowbase.currentContainerId` | The container the SQL editor runs against |
| `shadowbase.queryHistory` | The last 20 scripts run in the editor (text, time, success) |
| `shadowbase.savedScripts` | Saved scripts: `{ name, sql, updatedAt }` |
| `shadowbase.editorLayout` | Editor layout: `split`, `side` or `editor` |
| `shadowbase.focusMode` | Focus mode on / off |
| `shadowbase.showConsole` | Dashboard error console shown / hidden |

To reset everything, clear the site data in your browser (DevTools → Application → Local Storage).

## Screenshots

![Editor](docs/editor.png)
![Sandboxes](docs/sandboxes.png)
![Dashboard](docs/dashboard.png)
