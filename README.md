# ShadowBase 🛡️
### Zero-Downtime Schema Migration Sandbox

> Test risky database schema changes against **real, live production traffic** in a safe, temporary clone — before they ever touch production.

![Status](https://img.shields.io/badge/status-in%20development-yellow)
![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green)
![React](https://img.shields.io/badge/React-19-blue)
![Kafka](https://img.shields.io/badge/Apache-Kafka-black)
![Testcontainers](https://img.shields.io/badge/Testcontainers-PostgreSQL%20%7C%20MySQL-2496ED)

---

## 📌 Table of Contents
- [The Problem](#-the-problem)
- [The Solution](#-the-solution)
- [Example Use Case](#-example-use-case)
- [How It Works](#️-how-it-works)
- [Features](#-features)
- [Tech Stack](#️-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Configuration](#️-configuration)
- [API Reference](#-api-reference)
- [Running Tests](#-running-tests)
- [Known Limitations](#️-known-limitations)
- [Development Roadmap](#️-development-roadmap)
- [Team](#-team)
- [Contribution Rules](#-contribution-rules)

---

## ❗ The Problem

Changing a database schema in a large production system is risky.
"Breaking changes" — such as **dropping a column**, **renaming a table**, **changing a constraint** or **merging two tables** — can cause cascading failures in live applications.

Testing these changes locally does not help much, because a local database **never sees real production traffic**. A migration can pass every local test and still break the live app.

## ✅ The Solution

**ShadowBase** creates a temporary **"shadow" copy** of the database, streams the **real changes happening in production** into it, and lets you apply your migration script to the shadow first.

If the new schema breaks anything, you see the **SQL errors live on a dashboard** — and fix your script **before** it reaches production.
Production is never modified.

## 💡 Example Use Case

A database admin wants to **merge two tables** (`customers` and `customer_profiles`).

1. Instead of running the change on production, they open **ShadowBase** and create a **sandbox**.
2. The backend spins up a **temporary PostgreSQL (or MySQL) clone** using **Testcontainers**, imports the schema and seeds it with data.
3. Change events from production are sent through **Kafka**.
4. The **Stream Replayer** replays that traffic against the shadow clone.
5. The admin writes the merge script in the browser's **Monaco SQL editor** and applies it to the shadow.
6. The **React dashboard** shows whether the shadowed traffic starts producing **SQL exceptions** on the new schema.
7. The admin fixes the script, re-tests, and only runs it on production once the error rate is **zero**.

## ⚙️ How It Works

```
 ┌────────────────────┐   change events   ┌──────────────┐   ┌──────────────────────┐
 │ Production DB      │ ────────────────► │  Debezium    │──►│ Kafka                │
 │ (mock, PostgreSQL) │   (WAL / CDC)     │  (planned)   │   │ topic:               │
 └────────────────────┘                   └──────────────┘   │ shadowbase-topic     │
                                                             └──────────┬───────────┘
   (today: replayer/ProducerMain publishes sample events) ──────────────┤
                                                                        ▼
                                                             ┌──────────────────────┐
                                                             │ Stream Replayer      │
                                                             │ (replayer/, Java)    │
                                                             └──────────┬───────────┘
                                                                        │ replays SQL
                                                                        ▼
 ┌────────────────────┐        REST          ┌──────────────────────────────────────┐
 │ React Developer UI │ ◄──────────────────► │ Spring Boot Backend (backend/)       │
 │ • Sandboxes        │                      │ • Sandboxes + Testcontainers DBs     │
 │ • Monaco SQL editor│   WebSocket          │ • Schema import + data seeding       │
 │ • Metrics + errors │ ◄ ─ ─ (planned) ─ ─  │ • Runs queries / migrations on shadow│
 └────────────────────┘                      └──────────────────────────────────────┘
```

**Flow in short:**
`Production changes → (Debezium) → Kafka → Stream Replayer → Shadow DB (new schema) → errors & metrics → React dashboard`

> **Note:** Debezium captures **row-level changes** (INSERT / UPDATE / DELETE), not the original SQL text. The Stream Replayer rebuilds equivalent SQL statements from these change events and executes them on the shadow database.

## ✨ Features

### Backend (`backend/`)
- **Sandboxes** — create, start, stop and delete isolated environments, each owned by a user.
- **Shadow database containers** — PostgreSQL (`postgres:16-alpine`) and MySQL (`mysql:8.4`) containers started on demand with **Testcontainers**.
- **Schema import** — upload a `.sql` file and run it against a sandbox container, with import status and history.
- **Data seeding** — background jobs that copy data from a registered DataSource into a shadow container, with validation, progress per table and cancellation.
- **SQL execution** — run queries or migration scripts on a chosen container; returns columns, rows, row count, execution time and a message.
- **Users and DataSources** — manage user accounts and external source databases, and bind a DataSource to a container.

### Stream Replayer (`replayer/`)
- Kafka **producer** and **consumer** for ShadowBase change events (with the sandbox ID attached).
- Validates each event and replays it as `INSERT` / `UPDATE` / `DELETE` SQL on a PostgreSQL shadow database.
- Tracks replay metrics (replayed / failed).

### Frontend (`frontend/`)
- **Sandboxes page** — create sandboxes, start / stop / delete them, add PostgreSQL containers, copy JDBC URLs, and pick the active sandbox.
- **SQL editor** — Monaco editor with SQL highlighting, container picker, **Ctrl+Enter** to run, result table and summary, **Apply Migration** with an in-page safety confirmation, and a history of the last 20 scripts.
- **Dashboard** — live cards for queries replayed, failed queries, error rate and status, an error-rate chart, and a terminal-style **SQL error console** with filters.
- **Settings** — switch **mock mode** on or off and choose or create a user.
- **Mock mode (on by default)** — the whole UI works without a backend, using realistic fake data.

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend language | Java 25 |
| Backend framework | Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator) |
| Build tool | Maven (wrapper included in `backend/`) |
| Metadata database | MySQL (sandboxes, containers, users, jobs) |
| Shadow databases | PostgreSQL 16 and MySQL 8.4 via Testcontainers |
| Message streaming | Apache Kafka (`kafka-clients` 4.3) |
| Change Data Capture | Debezium MySQL Connector (Docker-based CDC setup) |
| SQL analysis | JSqlParser *(planned, bonus)* |
| Testing | JUnit 5 + Testcontainers |
| Frontend | React 19 + Vite 8 + React Router 7 |
| Code editor | Monaco Editor (`@monaco-editor/react`) |
| Charts | Recharts 3 |
| HTTP client | Axios |
| Containers | Docker |
| Version control | Git + GitHub |

## 📁 Project Structure

```
ShadowBase/
├── backend/                          # Spring Boot application (Java 25, Maven)
│   ├── src/main/java/com/shadowbase/backend/
│   │   ├── controller/               # REST controllers (sandboxes, query, schema, seeding, users, datasources)
│   │   ├── service/                  # Business logic, Testcontainers services, seeding engine
│   │   ├── Entity/                   # JPA entities (Sandbox, Container, User, DataSource, SeedJob, ...)
│   │   ├── Repository/               # Spring Data repositories
│   │   ├── dto/                      # Request / response objects
│   │   ├── Exception/                # Custom exceptions + GlobalExceptionHandler
│   │   └── heap/                     # Heap-dump (HPROF) analysis APIs (experimental)
│   ├── src/main/resources/application.properties
│   └── pom.xml
├── replayer/                         # Kafka producer / consumer + SQL replay (Java 25, Maven)
│   ├── src/main/java/com/shadowbase/
│   │   ├── kafka/                    # KafkaConfig, producer and consumer services
│   │   ├── main/                     # ProducerMain, ConsumerMain (entry points)
│   │   ├── model/                    # ShadowBaseMessage (change event)
│   │   └── service/                  # SqlReplayService, ReplayMetrics, ReplayResult
│   └── pom.xml
├── frontend/                         # React + Vite application
│   ├── src/
│   │   ├── components/               # Sidebar, SqlEditor, ResultsTable, SandboxCard, ErrorConsole
│   │   ├── pages/                    # EditorPage, SandboxesPage, DashboardPage, SettingsPage
│   │   ├── services/                 # api.js (REST + mock mode), metrics.js (live metrics + errors)
│   │   ├── App.jsx                   # Layout and routes
│   │   └── index.css                 # All styles (dark theme)
│   ├── vite.config.js                # Dev server, proxies /api to localhost:8080
│   └── package.json
└── README.md
```

## 🚀 Getting Started

### Prerequisites

| Tool | Version | Check with |
|---|---|---|
| Java JDK | 25 | `java -version` |
| Maven | 3.9+ (only for `replayer/`) | `mvn -version` |
| Node.js | 20.19+ or 22.12+ | `node -v` |
| Docker Desktop | latest | `docker --version` |
| MySQL | 8.x | `mysql --version` |
| Git | latest | `git --version` |

> Docker Desktop must be **running** — every shadow database is a Docker container started by Testcontainers.
> The backend includes a Maven wrapper (`./mvnw`, or `mvnw.cmd` on Windows), so Maven itself is only needed for the replayer.

### 1. Clone the repository

```bash
git clone https://github.com/Rajan3103/ShadowBase.git
cd ShadowBase
```

### 2. Frontend only (quickest — no backend needed)

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. **Mock mode** is on by default, so every page works with fake data.

### 3. Run the backend

1. Create a MySQL database called `shadowbase` (default login `root` / `root`, or set the variables in [Configuration](#️-configuration)).
2. Make sure Docker Desktop is running.
3. Start the backend:

```bash
cd backend
./mvnw spring-boot:run
```

Backend runs at **http://localhost:8080**. See [Known Limitations](#️-known-limitations) for two setup steps that are still manual.

### 4. Connect the frontend to the backend

1. Start the frontend (`npm run dev`). The Vite dev server forwards every `/api` request to `http://localhost:8080`, so there are no CORS issues.
2. Open **Settings**, untick **Use mock data**, and choose or create a user.
3. Open **Sandboxes**, create a sandbox, add a container, and click **Use in editor**.

### 5. Run the Stream Replayer (optional)

The replayer needs a Kafka broker on `localhost:9092` and a PostgreSQL database to replay into. For example, start Kafka with Docker:

```bash
docker run -d --name kafka -p 9092:9092 apache/kafka:3.9.0
```

Then, in two terminals:

```bash
cd replayer
mvn compile exec:java -Dexec.mainClass=com.shadowbase.main.ConsumerMain   # listens and replays
mvn compile exec:java                                                      # ProducerMain: sends a sample event
```

### Default ports

| Service | Port |
|---|---|
| React frontend (Vite) | 5173 |
| Spring Boot backend | 8080 |
| MySQL (backend metadata) | 3306 |
| Kafka | 9092 |
| PostgreSQL (replayer target) | 5432 |
| Shadow databases | random (assigned by Testcontainers) |

## ⚙️ Configuration

**Backend** (`backend/src/main/resources/application.properties`, override with environment variables):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/shadowbase` | Backend metadata database |
| `DB_USERNAME` | `root` | MySQL user |
| `DB_PASSWORD` | `root` | MySQL password |

Uploads are limited to **10 MB**; data seeding uses batches of 500 rows (max 5000) and a 1-hour timeout.

**Stream Replayer** (environment variables):

| Variable | Default | Purpose |
|---|---|---|
| `SHADOWBASE_DB_URL` | `jdbc:postgresql://localhost:5432/shadowbase` | Database the events are replayed into |
| `SHADOWBASE_DB_USER` | `postgres` | Database user |
| `SHADOWBASE_DB_PASSWORD` | `postgres` | Database password |

Kafka settings are in `replayer/.../kafka/KafkaConfig.java`: broker `localhost:9092`, topic `shadowbase-topic`.

**Frontend** (saved in the browser's localStorage from the UI):

| Key | Purpose |
|---|---|
| `shadowbase.useMock` | Mock mode on / off (default on) |
| `shadowbase.userId` | Current user |
| `shadowbase.currentSandboxId` | Active sandbox for the editor and dashboard |
| `shadowbase.currentContainerId` | Container the SQL editor runs against |
| `shadowbase.queryHistory` | Last 20 scripts run in the editor |

## 📡 API Reference

Base URL: `http://localhost:8080`. Errors return `{ timestamp, status, error, message }`.

**Sandboxes**

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/sandboxes` | Create a sandbox (`{ name, userId, initialContainers }`) |
| `GET` | `/api/sandboxes/{id}` | Get a sandbox and its containers |
| `GET` | `/api/sandboxes/user/{userId}` | List a user's sandboxes |
| `POST` | `/api/sandboxes/{id}/start` | Start all containers in a sandbox |
| `POST` | `/api/sandboxes/{id}/stop` | Stop all containers in a sandbox |
| `DELETE` | `/api/sandboxes/{id}` | Delete a sandbox (kept with status `DESTROYED`) |
| `POST` | `/api/sandboxes/{id}/containers` | Add a `POSTGRESQL` or `MYSQL` container |
| `GET` | `/api/sandboxes/{id}/containers` | List a sandbox's containers |

**SQL execution**

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/sandboxes/{sandboxId}/query` | Run SQL on the sandbox's default container |
| `POST` | `/api/sandboxes/{sandboxId}/containers/{containerId}/query` | Run SQL on a specific container |

Body: `{ sql, maxRows }` → `{ columns, rows, rowCount, executionTimeMs, message, hasResultSet }`

**Schema import** (each also available under `/api/sandboxes/{sandboxId}/containers/{containerId}/...`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/sandboxes/{sandboxId}/schema/import` | Upload and run a `.sql` file (multipart `file`) |
| `GET` | `/api/sandboxes/{sandboxId}/schema/status` | Latest import status |
| `GET` | `/api/sandboxes/{sandboxId}/schema/imports` | Import history |

**Data seeding**

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/sandboxes/{sandboxId}/data-seeds` | Start a seeding job |
| `POST` | `/api/sandboxes/{sandboxId}/data-seeds/validate` | Check a seeding request without copying data |
| `GET` | `/api/sandboxes/{sandboxId}/data-seeds` | Seeding history |
| `GET` | `/api/sandboxes/{sandboxId}/data-seeds/{jobId}` | Job status and per-table progress |
| `POST` | `/api/sandboxes/{sandboxId}/data-seeds/{jobId}/cancel` | Cancel a running job |

**Users**

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/users` | List users |
| `GET` | `/api/users/{id}` | Get a user |
| `POST` | `/api/users` | Create a user |
| `GET` | `/api/users/demo` | Get (or create) the demo user |

**DataSources**

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/datasources` | List registered source databases |
| `GET` | `/api/datasources/{id}` | Get a DataSource |
| `POST` | `/api/datasources` | Register a DataSource |
| `DELETE` | `/api/datasources/{id}` | Delete a DataSource |
| `POST` | `/api/sandboxes/{sandboxId}/containers/{containerId}/datasource/{dataSourceId}` | Bind an existing DataSource to a container |
| `POST` | `/api/sandboxes/{sandboxId}/containers/{containerId}/datasource` | Create a DataSource and bind it to a container |

**Heap-dump analysis** *(experimental)*

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/heap-dumps/analyze` | Upload a Java `.hprof` heap dump |
| `GET` | `/api/heap-dumps/{snapshotId}/summary` | Analysis summary |
| `GET` | `/api/heap-dumps/{snapshotId}/dominators` | Dominator tree |
| `GET` | `/api/heap-dumps/{snapshotId}/objects/{objectId}/retained-size` | Retained size of an object |
| `GET` | `/api/heap-dumps/{snapshotId}/objects/{objectId}/reference-chain` | Reference chain to a GC root |

**Planned:** `GET /api/metrics` and a WebSocket with `/topic/metrics` and `/topic/errors` for the live dashboard (the frontend currently uses mock data for these).

## 🧪 Running Tests

```bash
# Backend (Docker must be running — tests start real containers)
cd backend
./mvnw test

# Stream Replayer
cd replayer
mvn test

# Frontend: lint and production build
cd frontend
npm run lint
npm run build
```

## ⚠️ Known Limitations

- **Security is not configured yet.** Spring Security is on the classpath with no configuration, so Spring Boot protects every endpoint with its default login (user `user`, password printed in the backend log at startup). The frontend does not send credentials, so with mock mode off its requests are rejected until a security configuration allows `/api/**`.
- **Metadata tables are not created automatically.** `spring.jpa.hibernate.ddl-auto=none` and there is no schema script in the repo, so the MySQL tables must be created before the backend can store data.
- **End-to-end CDC replay integration is pending.** The Docker-based MySQL + Debezium + Kafka CDC pipeline is available and tested. Connecting this source pipeline to the complete production-to-shadow replay workflow remains pending.
- **Live metrics and the error console use mock data** until the backend exposes metrics and errors.
- **Multi-statement migrations:** a script is run with one JDBC `execute()` call. MySQL needs `allowMultiQueries=true` for several statements, and the statements are not wrapped in a transaction.

## 🗓️ Development Roadmap

| Week | Backend (Java, Testcontainers, Kafka) | Frontend (React, Monaco Editor) |
|---|---|---|
| **Week 1** | **Container Engine:** Testcontainers integration; REST endpoints to spin up, seed and destroy databases | **IDE Scaffolding:** Monaco Editor in React with SQL syntax highlighting |
| **Week 2** | **CDC Setup:** Debezium on mock production DB, streaming transaction logs to Kafka | **Metrics Dashboard:** "Queries Replayed" and "Error Rate" components |
| **Mid Review** | **Cloning Audit:** fresh DB spin-up, base schema applied, production logs intercepted | **UI Synchronization:** Monaco editor executes queries against backend container APIs |
| **Week 3** | **Traffic Replay:** Java consumer reads the Kafka stream and replays it on the shadow container | **Error Logging:** live console showing SQL exceptions from shadow traffic |
| **Week 4** | **AST Analysis (Bonus):** JSqlParser warns when a script drops a heavily queried column | **Refine & Polish:** layout toggles, save / export migration scripts |
| **Final Review** | Complete DevOps tool: Docker orchestration + Kafka CDC + Java backend | Polished developer tool UI |

### Progress

- [x] Testcontainers environment APIs (PostgreSQL + MySQL sandboxes)
- [x] Schema import and data seeding
- [x] SQL query / apply-migration API
- [x] Kafka producer / consumer
- [x] Stream Replayer (INSERT / UPDATE / DELETE replay + metrics)
- [x] Docker-based MySQL → Debezium → Kafka CDC pipeline
- [ ] Production-to-shadow CDC replay integration
- [ ] Mock production DB + traffic generator
- [ ] Metrics + errors API / WebSocket
- [ ] Security configuration
- [x] Monaco SQL editor (container picker, Ctrl+Enter, Apply Migration, history)
- [x] Sandboxes management page
- [x] Metrics dashboard *(mock data)*
- [x] Live error console *(mock data)*
- [ ] Connect dashboard and console to live backend data
- [ ] Export scripts, layout toggles
- [ ] JSqlParser analysis (bonus)

## 👥 Team

| Name | Role | Main work | Branch |
|---|---|---|---|
| **Karthig Rajan S** | Team Lead + Frontend | React UI, Monaco editor, sandboxes page, dashboard, error console; repo and merge management | `Karthig_Rajan_S` |
| **Yaswanth** | Backend – Sandboxes & APIs | Sandbox and container services, schema import, data seeding, SQL query, user and DataSource APIs | `yaswanth` |
| **Koteeswari R** | Backend – Database Environments | MySQL Testcontainers environments and lifecycle, multi-database sandbox containers | `Koteeswari_R` |
| **Rohan Naik** | Stream Replayer & Kafka | Kafka producer / consumer, event validation, SQL replay and metrics | `Rohan` |

## 🤝 Contribution Rules

1. **Each member works on their own branch** (see [Team](#-team)) — never push directly to `main`.
2. **Commit every day** with a clear message, e.g. `feat(ui): add error console filter`.
3. Open a **Pull Request** to `main`; the Team Lead reviews and merges it.
4. Branches are merged into `main` **every Friday** for progress tracking.
5. Pull the latest `main` into your branch before starting new work:
   ```bash
   git checkout <your-branch>
   git pull origin main
   ```
6. **Don't commit build output** (`target/`, `dist/`, `node_modules/`).
7. Respond to team messages within **24 hours**.

### Commit message format

| Prefix | Use for |
|---|---|
| `feat:` | A new feature |
| `fix:` | A bug fix |
| `docs:` | README or documentation changes |
| `test:` | Adding or updating tests |
| `refactor:` | Code cleanup without changing behaviour |
| `chore:` | Build, config or project setup |

---

## 📄 License

This project was built as part of an internship program at **Infotact Solutions** for learning purposes.

---

<p align="center"><b>ShadowBase</b> — break your schema in the shadows, not in production.</p>

## MySQL Change Data Capture (CDC) - Docker + Debezium

ShadowBase includes a Docker-based MySQL Change Data Capture (CDC) environment using MySQL 8.4, Debezium Connect, and Apache Kafka.

### Services

| Service | Container | Host Port |
|---|---|---|
| Kafka | `shadowbase-kafka` | 9092, 9094 |
| Debezium Connect | `shadowbase-debezium` | 8083 |
| MySQL CDC | `shadowbase-mysql-cdc` | 3309 |

### Architecture

```text
MySQL 8.4
    |
    | Binary Log (ROW format)
    v
Debezium MySQL Connector
    |
    v
Apache Kafka
    |
    v
ShadowBase Java Replayer
```

### Configuration Files
- `docker/debezium/validate-config.ps1` - Validates the Debezium connector configuration.
- `docker/debezium/health-check.ps1` - Checks CDC containers, REST API, connector, and task health.
- `docker/debezium/docker-compose.yml` - Docker services, networking, persistent storage, health checks, and startup dependencies.
- `docker/debezium/connector-config.json` - MySQL Debezium connector configuration.
- `.env.example` - Example environment configuration.

### Prerequisites

- Docker Desktop installed and running.
- A local `.env` file in the repository root.
- `MYSQL_ROOT_PASSWORD` configured in `.env`.

Never commit the local `.env` file or expose database passwords.

### Start CDC Infrastructure

Run from the repository root:

```powershell
docker compose --env-file .\.env -f .\docker\debezium\docker-compose.yml up -d
```

### Verify Container Health

```powershell
docker compose --env-file .\.env -f .\docker\debezium\docker-compose.yml ps
```

Kafka, Debezium Connect, and MySQL CDC have Docker health checks. Debezium Connect waits for Kafka to become healthy before starting.

### Access Debezium REST API

Check the Debezium Connect service:

```powershell
Invoke-RestMethod http://localhost:8083/
```

List registered connectors:

```powershell
Invoke-RestMethod http://localhost:8083/connectors
```

Check the MySQL connector status:

```powershell
Invoke-RestMethod http://localhost:8083/connectors/shadowbase-mysql-connector/status
```

### Kafka Listeners

- Internal Docker applications: `kafka:9092`
- Applications running on the host: `localhost:9094`

### Stop CDC Infrastructure

```powershell
docker compose --env-file .\.env -f .\docker\debezium\docker-compose.yml down
```

The command preserves the named MySQL data volume. Avoid `down -v` unless you intentionally want to delete the stored CDC data.
