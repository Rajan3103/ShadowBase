
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
2. The backend spins up a **temporary shadow database** using **Testcontainers**.
3. **Debezium (CDC)** captures every insert, update and delete happening in the MySQL source database and sends it through **Kafka**.
4. The **Stream Replayer** replays that traffic against the shadow clone.
5. The admin writes the merge script in the browser's **Monaco SQL editor** and applies it to the shadow.
6. The **React dashboard** instantly shows whether the shadowed traffic starts producing **SQL exceptions** on the new schema.
7. The admin fixes the script, re-tests, and only runs it on production once the error rate is **zero**.

## ⚙️ How It Works

```text
 ┌─────────────────────┐      MySQL Binlog       ┌──────────────────┐
 │ MySQL Source DB     │ ──────────────────────► │ Debezium MySQL   │
 │ (mock, MySQL 8.4)   │                         │ Connector        │
 └─────────────────────┘                         └────────┬─────────┘
                                                        │
                                                        ▼
                                               ┌──────────────────┐
                                               │      Kafka       │
                                               │     Topics       │
                                               └────────┬─────────┘
                                                        │
                                                        ▼
                                               ┌──────────────────┐
                                               │ Stream Replayer  │
                                               │ (Java consumer)  │
                                               └────────┬─────────┘
                                                        │ replays SQL
                                                        ▼
                                               ┌──────────────────┐
                                               │ Shadow Database  │
                                               │ (Testcontainers) │
                                               └──────────────────┘

 ┌─────────────────────┐     REST + WebSocket    ┌──────────────────────────┐
 │ React Developer UI  │ ◄─────────────────────► │ Spring Boot Backend      │
 │ Monaco SQL editor   │                         │ • Testcontainers         │
 │ Metrics dashboard   │                         │ • Applies migration      │
 │ Live error console  │                         │ • Tracks replay errors   │
 └─────────────────────┘                         └──────────────────────────┘
```

**Flow in short:**

`MySQL changes → Debezium → Kafka → Stream Replayer → Shadow DB (new schema) → errors & metrics → React dashboard`

> **Note:** Debezium captures **row-level changes** (INSERT / UPDATE / DELETE), not the original SQL text. The Stream Replayer rebuilds equivalent SQL statements from these change events and executes them on the shadow database.

The MySQL CDC pipeline has been configured using Docker Compose and verified with an initial snapshot and INSERT, UPDATE and DELETE operations. The corresponding change events were received in Kafka.

## 🧩 Key Modules

| Module | Technology | What it does |
|---|---|---|
| **Dynamic Environments** | Testcontainers (Java API) | Spins up, seeds and destroys Dockerized databases on demand through REST calls |
| **Change Data Capture** | Debezium + Kafka | Captures MySQL database changes through the binary log and streams them as events |
| **Stream Replayer** | Java Stream API + Kafka Consumer | Reads CDC events and replays them against the isolated shadow database, capturing every SQL exception |
| **Developer UI** | React + Monaco Editor | Browser-based, IDE-like interface to write migration scripts and watch live shadow-traffic error rates |
| **AST Analysis** *(bonus)* | JSqlParser | Warns the user if a migration script drops or changes a column that is heavily used by live traffic |

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend language | Java 21 |
| Backend framework | Spring Boot 3 (REST + WebSocket) |
| Build tool | Maven |
| Database | MySQL 8.4 |
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

```text
shadowbase/
├── backend/                     # Spring Boot application (Java, Maven)
│   ├── src/main/java/.../
│   └── pom.xml
├── frontend/                    # React + Vite application
│   ├── src/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── services/
│   │   └── App.jsx
│   └── package.json
├── replayer/                    # Java Kafka consumer / producer module
├── docker/
│   └── debezium/
│       └── docker-compose.yml   # MySQL CDC, Kafka and Debezium Connect
├── .env.example                 # Example environment variables
├── .gitignore
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
git clone https://github.com/Rajan3103/ShadowBase.git
cd ShadowBase
```

### 2. Configure and start the CDC infrastructure

Create a local `.env` file in the project root using `.env.example` as a reference.

Set your local MySQL password:

```env
MYSQL_ROOT_PASSWORD=your_local_mysql_password
```

Start the infrastructure from the repository root:

```powershell
docker compose --env-file .\.env -f .\docker\debezium\docker-compose.yml up -d
```

This starts:

- MySQL 8.4 CDC source database
- Apache Kafka
- Debezium Connect

Check running containers:

```powershell
docker ps
```

Check Kafka Connect:

```powershell
Invoke-RestMethod http://localhost:8083/
```

### 3. Register and verify the MySQL Debezium connector

Register the MySQL connector using the Kafka Connect REST API and the MySQL connector configuration.

List registered connectors:

```powershell
Invoke-RestMethod http://localhost:8083/connectors
```

Check connector status:

```powershell
Invoke-RestMethod http://localhost:8083/connectors/shadowbase-mysql-connector/status
```

The connector and its task should report `RUNNING`.

### 4. Verify CDC events in Kafka

The MySQL connector publishes database changes to the Kafka topic:

```text
shadowbase_mysql.shadowbase_cdc.customers
```

The following operations were used to verify CDC:

```sql
INSERT INTO customers (name, email)
VALUES ('CDC Test', 'cdc@example.com');

UPDATE customers
SET name = 'CDC Updated'
WHERE id = 2;

DELETE FROM customers
WHERE id = 2;
```

The initial snapshot and the corresponding INSERT, UPDATE and DELETE events were verified in Kafka.

### 5. Run the backend

```bash
cd backend
mvn spring-boot:run
```

Backend runs at **http://localhost:8080**

### 6. Run the frontend

```bash
cd ../frontend
npm install
npm run dev
```

Frontend runs at **http://localhost:5173**

### 7. Start live traffic (optional, for the demo)

```bash
cd ../traffic-generator
mvn exec:java
```

### Default ports

| Service | Port |
|---|---|
| React frontend | 5173 |
| Spring Boot backend | 8080 |
| MySQL CDC source | 3309 |
| Kafka | 9092 |
| Kafka Connect (Debezium) | 8083 |
| Shadow databases | random (assigned by Testcontainers) |

## 📡 API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/sandboxes` | Create a sandbox and its initial containers |
| `GET` | `/api/sandboxes/{id}` | Retrieve a sandbox and its containers |
| `GET` | `/api/sandboxes/user/{userId}` | List sandboxes belonging to a user |
| `POST` | `/api/sandboxes/{id}/start` | Start all containers in a sandbox |
| `POST` | `/api/sandboxes/{id}/stop` | Stop all containers in a sandbox |
| `DELETE` | `/api/sandboxes/{id}` | Delete a sandbox and its containers |
| `POST` | `/api/sandboxes/{id}/containers` | Add a MySQL or PostgreSQL container |
| `GET` | `/api/sandboxes/{id}/containers` | List all containers in a sandbox |

**WebSocket** — connect to `/ws`, then subscribe to:
- `/topic/metrics` — live counters (queries replayed, error rate)
- `/topic/errors` — live SQL exceptions from shadow traffic

## 🗓️ Development Roadmap

| Week | Backend (Java, Testcontainers, Debezium) | Frontend (React, Monaco Editor) |
|---|---|---|
| **Week 1** | **Container Engine:** Testcontainers integration; REST endpoints to spin up, seed and destroy databases | **IDE Scaffolding:** Monaco Editor in React with SQL syntax highlighting |
| **Week 2** | **CDC Setup:** Debezium on mock MySQL database, streaming transaction logs to Kafka | **Metrics Dashboard:** "Queries Replayed" and "Error Rate" components |
| **Mid Review** | **Cloning Audit:** fresh DB spin-up, base schema applied, production logs intercepted | **UI Synchronization:** Monaco editor executes queries against backend container APIs |
| **Week 3** | **Traffic Replay:** Java consumer reads the Kafka CDC stream and replays it on the shadow container | **Error Logging:** live console showing SQL exceptions from shadow traffic |
| **Week 4** | **AST Analysis (Bonus):** JSqlParser warns when a script drops a heavily queried column | **Refine & Polish:** layout toggles, save / export migration scripts |
| **Final Review** | Complete DevOps tool: Docker orchestration + Kafka CDC + Java backend | Polished developer tool UI |

### Progress

- [ ] Testcontainers environment APIs
- [ ] Mock production DB + traffic generator
- [x] MySQL CDC infrastructure using Docker Compose
- [x] Debezium MySQL connector registration and status verification
- [x] Initial snapshot verification in Kafka
- [x] INSERT, UPDATE and DELETE CDC event verification
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
| `<Member B>` | Backend – CDC & Infrastructure | MySQL source DB, Debezium, Kafka, traffic generator | `cdc-<name>` |
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
