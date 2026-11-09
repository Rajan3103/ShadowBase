# ShadowBase 🛡️
### Zero-Downtime Schema Migration Sandbox

> Test risky database schema changes against **real, live production traffic** in a safe, temporary clone — before they ever touch production.

![Status](https://img.shields.io/badge/status-in%20development-yellow)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)
![React](https://img.shields.io/badge/React-18-blue)
![Kafka](https://img.shields.io/badge/Kafka-Debezium%20CDC-black)

---

## 📌 Table of Contents
- [The Problem](#-the-problem)
- [The Solution](#-the-solution)
- [Example Use Case](#-example-use-case)
- [How It Works](#-how-it-works)
- [Key Modules](#-key-modules)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [API Reference](#-api-reference)
- [Development Roadmap](#-development-roadmap)
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

1. Instead of running the change on production, they open **ShadowBase**.
2. The backend spins up a **temporary PostgreSQL clone** using **Testcontainers**.
3. **Debezium (CDC)** captures every insert, update and delete happening on production and sends it through **Kafka**.
4. The **Stream Replayer** replays that traffic against the shadow clone.
5. The admin writes the merge script in the browser's **Monaco SQL editor** and applies it to the shadow.
6. The **React dashboard** instantly shows whether the shadowed traffic starts producing **SQL exceptions** on the new schema.
7. The admin fixes the script, re-tests, and only runs it on production once the error rate is **zero**.

## ⚙️ How It Works

```
 ┌────────────────────┐   change events   ┌──────────────┐   ┌───────────────┐
 │ Production DB      │ ────────────────► │  Debezium    │──►│    Kafka      │
 │ (mock, PostgreSQL) │   (WAL / CDC)     │  Connector   │   │   Topics      │
 └────────────────────┘                   └──────────────┘   └───────┬───────┘
          ▲                                                          │
          │ live traffic                                             ▼
 ┌────────────────────┐                                    ┌──────────────────┐
 │ Traffic Generator  │                                    │ Stream Replayer  │
 │ (simulated app)    │                                    │ (Java consumer)  │
 └────────────────────┘                                    └────────┬─────────┘
                                                                    │ replays SQL
                                                                    ▼
 ┌────────────────────┐   REST + WebSocket   ┌──────────────────────────────────┐
 │ React Developer UI │ ◄──────────────────► │ Spring Boot Backend              │
 │ Monaco SQL editor  │                      │  • Testcontainers: Shadow DB     │
 │ Metrics dashboard  │                      │  • Applies migration to shadow   │
 │ Live error console │                      │  • Tracks replays & SQL errors   │
 └────────────────────┘                      └──────────────────────────────────┘
```

**Flow in short:**
`Production changes → Debezium → Kafka → Stream Replayer → Shadow DB (new schema) → errors & metrics → React dashboard`

> **Note:** Debezium captures **row-level changes** (INSERT / UPDATE / DELETE), not the original SQL text. The Stream Replayer rebuilds equivalent SQL statements from these change events and executes them on the shadow database.

## 🧩 Key Modules

| Module | Technology | What it does |
|---|---|---|
| **Dynamic Environments** | Testcontainers (Java API) | Spins up, seeds and destroys Dockerized PostgreSQL databases on demand through REST calls |
| **Change Data Capture** | Debezium + Kafka | Intercepts live transactions from the production source and streams them as events |
| **Stream Replayer** | Java Stream API + Kafka Consumer | Reads CDC events and replays them against the isolated shadow database, capturing every SQL exception |
| **Developer UI** | React + Monaco Editor | Browser-based, IDE-like interface to write migration scripts and watch live shadow-traffic error rates |
| **AST Analysis** *(bonus)* | JSqlParser | Warns the user if a migration script drops or changes a column that is heavily used by live traffic |

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend language | Java 21 |
| Backend framework | Spring Boot 3 (REST + WebSocket) |
| Build tool | Maven |
| Database | PostgreSQL 16 |
| Dynamic databases | Testcontainers |
| Change Data Capture | Debezium |
| Message streaming | Apache Kafka |
| SQL analysis | JSqlParser |
| Testing | JUnit 5 + Testcontainers |
| Frontend | React 18 + Vite |
| Code editor | Monaco Editor (`@monaco-editor/react`) |
| Charts | Recharts |
| HTTP / live updates | Axios, STOMP over WebSocket (`@stomp/stompjs`) |
| Containers | Docker + Docker Compose |
| Version control | Git + GitHub |

## 📁 Project Structure

```
shadowbase/
├── backend/                     # Spring Boot application (Java 21, Maven)
│   ├── src/main/java/.../
│   │   ├── environment/         # Testcontainers: create / seed / destroy shadow DBs
│   │   ├── cdc/                 # Kafka consumer for Debezium events
│   │   ├── replayer/            # Converts events to SQL and replays on shadow DB
│   │   ├── migration/           # Applies migration scripts to the shadow DB
│   │   ├── metrics/             # Queries replayed, error rate, error log
│   │   ├── analysis/            # JSqlParser warnings (bonus)
│   │   └── websocket/           # Live metrics & errors to the UI
│   └── pom.xml
├── frontend/                    # React + Vite application
│   ├── src/
│   │   ├── components/          # Editor, MetricsCards, ErrorConsole, EnvironmentPanel
│   │   ├── pages/               # Dashboard, Editor, Settings
│   │   ├── services/            # api.js (REST), socket.js (WebSocket)
│   │   └── App.jsx
│   └── package.json
├── infra/
│   ├── docker-compose.yml       # Mock production DB, Kafka, Kafka Connect + Debezium
│   ├── debezium-connector.json  # Debezium connector configuration
│   └── seed/                    # Base schema and sample data
├── traffic-generator/           # Simulated app sending live traffic to production DB
└── README.md
```

## 🚀 Getting Started

### Prerequisites

Install these before you begin:

| Tool | Version | Check with |
|---|---|---|
| Java JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 20+ | `node -v` |
| Docker Desktop | latest | `docker --version` |
| Git | latest | `git --version` |

> Docker Desktop must be **running** — Testcontainers, Kafka and the databases all run inside Docker.

### 1. Clone the repository

```bash
git clone https://github.com/<your-org>/shadowbase.git
cd shadowbase
```

### 2. Start the infrastructure (production DB, Kafka, Debezium)

```bash
cd infra
docker compose up -d
```

Register the Debezium connector:

```bash
curl -X POST -H "Content-Type: application/json" \
     --data @debezium-connector.json \
     http://localhost:8083/connectors
```

### 3. Run the backend

```bash
cd ../backend
mvn spring-boot:run
```

Backend runs at **http://localhost:8080**

### 4. Run the frontend

```bash
cd ../frontend
npm install
npm run dev
```

Frontend runs at **http://localhost:5173**

### 5. Start live traffic (optional, for the demo)

```bash
cd ../traffic-generator
mvn exec:java
```

### Default ports

| Service | Port |
|---|---|
| React frontend | 5173 |
| Spring Boot backend | 8080 |
| Mock production PostgreSQL | 5432 |
| Kafka | 9092 |
| Kafka Connect (Debezium) | 8083 |
| Shadow databases | random (assigned by Testcontainers) |

## 📡 API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/environments` | Create a new shadow database |
| `POST` | `/api/environments/{id}/seed` | Load the base schema and sample data |
| `DELETE` | `/api/environments/{id}` | Destroy the shadow database |
| `POST` | `/api/environments/{id}/query` | Run a SQL query from the editor |
| `POST` | `/api/environments/{id}/migration` | Apply a migration script to the shadow database |
| `POST` | `/api/environments/{id}/replay/start` | Start replaying production traffic |
| `POST` | `/api/environments/{id}/replay/stop` | Stop replaying traffic |
| `GET` | `/api/metrics` | Get queries replayed, failures and error rate |
| `POST` | `/api/analysis` | *(Bonus)* Analyze a migration script with JSqlParser |

**WebSocket** — connect to `/ws`, then subscribe to:
- `/topic/metrics` — live counters (queries replayed, error rate)
- `/topic/errors` — live SQL exceptions from shadow traffic

## 🗓️ Development Roadmap

| Week | Backend (Java, Testcontainers, Debezium) | Frontend (React, Monaco Editor) |
|---|---|---|
| **Week 1** | **Container Engine:** Testcontainers integration; REST endpoints to spin up, seed and destroy databases | **IDE Scaffolding:** Monaco Editor in React with SQL syntax highlighting |
| **Week 2** | **CDC Setup:** Debezium on mock production DB, streaming transaction logs to Kafka | **Metrics Dashboard:** "Queries Replayed" and "Error Rate" components |
| **Mid Review** | **Cloning Audit:** fresh DB spin-up, base schema applied, production logs intercepted | **UI Synchronization:** Monaco editor executes queries against backend container APIs |
| **Week 3** | **Traffic Replay:** Java consumer reads the Kafka CDC stream and replays it on the shadow container | **Error Logging:** live console showing SQL exceptions from shadow traffic |
| **Week 4** | **AST Analysis (Bonus):** JSqlParser warns when a script drops a heavily queried column | **Refine & Polish:** layout toggles, save / export migration scripts |
| **Final Review** | Complete DevOps tool: Docker orchestration + Kafka CDC + Java backend | Polished developer tool UI |

### Progress

- [ ] Testcontainers environment APIs
- [ ] Mock production DB + traffic generator
- [ ] Debezium → Kafka CDC pipeline
- [ ] Stream Replayer
- [ ] Apply-migration API
- [ ] Metrics + WebSocket
- [ ] Monaco SQL editor
- [ ] Metrics dashboard
- [ ] Live error console
- [ ] Save / export scripts, layout toggles
- [ ] JSqlParser analysis (bonus)

## 👥 Team

| Name | Role | Responsibilities | Branch |
|---|---|---|---|
| `<Your Name>` | **Team Lead + Frontend** | React UI, Monaco editor, dashboard, error console; repo, branch and merge management | `frontend-<name>` |
| `<Member A>` | Backend – Environments & APIs | Spring Boot REST APIs, Testcontainers shadow databases | `backend-<name>` |
| `<Member B>` | Backend – CDC & Infrastructure | Mock production DB, Debezium, Kafka, traffic generator | `cdc-<name>` |
| `<Member C>` | Backend – Stream Replayer | Kafka consumer, event-to-SQL replay, metrics, JSqlParser | `replayer-<name>` |

## 🤝 Contribution Rules

1. **Each member works on their own branch** — never push directly to `main`.
2. **Commit every day** with a clear message, e.g. `feat(ui): add error console filter`.
3. Open a **Pull Request** to `main`; the Team Lead reviews and merges it.
4. Branches are merged into `main` **every Friday** for progress tracking.
5. Pull the latest `main` into your branch before starting new work:
   ```bash
   git checkout <your-branch>
   git pull origin main
   ```
6. Respond to team messages within **24 hours**.

### Commit message format

| Prefix | Use for |
|---|---|
| `feat:` | A new feature |
| `fix:` | A bug fix |
| `docs:` | README or documentation changes |
| `test:` | Adding or updating tests |
| `refactor:` | Code cleanup without changing behaviour |

---

## 📄 License

This project was built as part of an internship program at **Infotact Solutions** for learning purposes.

---

<p align="center"><b>ShadowBase</b> — break your schema in the shadows, not in production.</p>
