# ShadowBase – Frontend

The browser UI for **ShadowBase**, a zero-downtime schema migration sandbox.
Write and test migration scripts against shadow database containers, and watch live shadow-traffic errors before anything reaches production.

## Features

- **SQL Editor**
  - Choose the target container in the active sandbox
  - Run queries (button or **Ctrl+Enter** / **Cmd+Enter**) and see results with a row count, time and message
  - **Apply Migration** with an in-page safety confirmation
  - **Check Risk**: lists risky operations (DROP, RENAME, type changes, …) with level, line, table and statement; click a finding to jump to its line. Risky lines are underlined in the editor. Apply Migration requires a tick-box when there are high / breaking risks
  - **Load demo script**: a breaking example migration for demos
  - History of the last 20 scripts you ran
  - Save, load and delete named scripts; **Open** a `.sql` file and **Export** to `.sql`
  - Layouts: **Split**, **Side by side**, **Editor only**, plus **Focus mode** (hides the sidebar, Esc to exit)
- **Sandboxes**
  - Create, start, stop and delete sandboxes
  - Add containers and see their status and JDBC URLs
  - Import a schema (`.sql`) and seed data
  - Pick the active sandbox for the editor and dashboard
- **Data Sources** (DataSource Diagnostics)
  - Table of saved database connections with their linked container
  - **Test** one connection or **Test all** ("3 of 4 connected"), with latency (green < 50 ms, amber < 200 ms, red otherwise), database type and message
  - **Link to active container** and **Delete** (with an inline confirm)
  - **Add data source** form with **Test connection** before saving (the password is never shown or stored in the browser)
- **Dashboard**
  - Live metrics: queries replayed, failed queries, error rate and status
  - Badge showing the data source: Mock data / Live / Live unavailable (fallback); "Last updated" time and a pulse dot while live
  - Error-rate chart with a 5% warning line
  - **Reset**: in live mode also resets the backend counters and clears the console
  - Live SQL error console with filters and a **Copy** button per line (can be shown or hidden)
- **Settings**
  - Turn mock mode on or off
  - Dashboard data source: Mock or Live from backend
  - Choose, enter or create a user ID
- **Sidebar**: backend status (Online / Offline / Mock mode), checked every 15 seconds

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

### Dashboard: Mock vs Live

The dashboard has its own setting: **Settings → Dashboard data source**.

- **Mock** (default): numbers and errors are made up in the browser.
- **Live from backend**: the dashboard polls `GET /api/metrics` every 2 seconds and the error console polls `GET /api/metrics/errors?limit=50` every 3 seconds (only errors with new ids are added, oldest first).
- If a live request fails **3 times in a row**, the dashboard switches to mock data and shows an amber **"Live unavailable – showing mock data"** badge (hover it to see why). Press **Reset** (or leave and reopen the page) to try live again.

### Risk checker: server vs local

- **Real backend** (mock mode off): **Check Risk** and **Apply Migration** send the script to `POST /api/analysis/migration-risk` and show **"Checked by server"**.
- **Mock mode, or the server can't be reached**: a simpler regex check runs in the browser and the panel shows **"Checked locally"** (hover it to see why).

### Backend endpoints the UI uses

| Area | Endpoints |
|---|---|
| Sandboxes | `POST /api/sandboxes`, `GET /api/sandboxes/{id}`, `GET /api/sandboxes/user/{userId}`, `POST /api/sandboxes/{id}/start`, `POST /api/sandboxes/{id}/stop`, `DELETE /api/sandboxes/{id}` |
| Containers | `POST /api/sandboxes/{id}/containers`, `GET /api/sandboxes/{id}/containers` |
| Queries | `POST /api/sandboxes/{id}/containers/{containerId}/query` (or `/api/sandboxes/{id}/query`) |
| Schema & seeding | `POST …/schema/import`, `GET …/schema/status`, `POST /api/sandboxes/{id}/data-seeds`, `GET /api/sandboxes/{id}/data-seeds/{jobId}` |
| Users | `GET /api/users`, `POST /api/users`, `GET /api/users/demo` (also used as the health check) |
| Metrics | `GET /api/metrics`, `POST /api/metrics/reset`, `GET /api/metrics/errors?limit=50` |
| Risk check | `POST /api/analysis/migration-risk` |
| Data sources | `GET /api/datasources`, `POST /api/datasources`, `DELETE /api/datasources/{id}`, `POST /api/datasources/{id}/test`, `POST /api/datasources/test`, `POST /api/sandboxes/{sandboxId}/containers/{containerId}/datasource/{dataSourceId}` |

## Folder structure

```
src/
├── main.jsx                  # Entry point: renders <App> inside BrowserRouter
├── App.jsx                   # Layout (sidebar + page), routes, focus mode
├── index.css                 # All styles (dark theme)
├── components/
│   ├── Sidebar.jsx           # Title, navigation links, backend status
│   ├── SqlEditor.jsx         # Monaco SQL editor + Ctrl/Cmd+Enter shortcut
│   ├── ResultsTable.jsx      # Query results, empty message or error
│   ├── SandboxCard.jsx       # One sandbox: actions, containers, schema import, seeding
│   └── ErrorConsole.jsx      # Live SQL error console with filters
├── pages/
│   ├── EditorPage.jsx        # SQL editor, migrations, history, saved scripts, layouts
│   ├── SandboxesPage.jsx     # List and create sandboxes
│   ├── DataSourcesPage.jsx   # Data sources: test, link, delete, add
│   ├── DashboardPage.jsx     # Live metrics, error-rate chart, error console
│   └── SettingsPage.jsx      # Mock mode and user settings
└── services/
    ├── api.js                # Backend REST calls, with mock mode
    ├── metrics.js            # Live metrics and error streams (mock or polling)
    ├── migrationRisk.js      # Migration risk check (server, with local fallback)
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
| `shadowbase.metricsSource` | Dashboard data source: `mock` (default) or `polling` (live) |

To reset everything, clear the site data in your browser (DevTools → Application → Local Storage).

## Screenshots

![Editor](docs/editor.png)
![Sandboxes](docs/sandboxes.png)
![Dashboard](docs/dashboard.png)
