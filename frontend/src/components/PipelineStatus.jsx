// PipelineStatus.jsx — the CDC (change data capture) pipeline at the top of the Dashboard:
//   MySQL source (binlog) → Debezium connector → Kafka topic → Replayer → Shadow DB
// Each stage is a small card with a coloured dot (green = OK, amber = check it,
// red = broken, grey = unknown) and one line of detail.
// - The Debezium connector status comes from Kafka Connect (port 8083) every 10 seconds.
// - The Replayer and Shadow DB stages use the Dashboard's metrics (passed in as props).
import { useEffect, useState } from 'react'
import { getConnectorStatus, getErrorMessage } from '../services/api.js'

const CHECK_INTERVAL_MS = 10000 // ask Kafka Connect every 10 seconds
const CLOCK_INTERVAL_MS = 5000 // how often we re-check "was something replayed in the last 30 s"
const REPLAY_ACTIVE_MS = 30000 // "active" = replayed count went up in the last 30 seconds
const BREAKING_ERROR_RATE = 5 // error rate (%) from which the shadow DB is amber
const TOPIC_NAME = 'shadowbase_mysql.shadowbase_cdc.customers'
const REGISTER_HINT =
  "curl -X POST -H 'Content-Type: application/json' --data @docker/debezium/connector-config.json http://localhost:8083/connectors"

// Formats a timestamp as hh:mm:ss (24-hour), e.g. "14:05:09".
function formatTime(ts) {
  return new Date(ts).toLocaleTimeString('en-GB')
}

// Works out the Debezium stage from the Kafka Connect answer.
// check = { status: 'checking' } | { status: 'ok', registered, connectorState, tasks }
//       | { status: 'error', message }
// Returns { color, detail, trace?, hint? }.
function connectorStage(check) {
  if (check.status === 'checking') return { color: 'grey', detail: 'Checking...' }
  if (check.status === 'error') {
    return { color: 'grey', detail: 'Kafka Connect not reachable on :8083', title: check.message }
  }
  if (!check.registered) {
    return { color: 'grey', detail: 'Connector not registered', hint: REGISTER_HINT }
  }

  const failedTask = check.tasks.find((task) => task.state === 'FAILED')
  if (check.connectorState === 'FAILED' || failedTask) {
    return {
      color: 'red',
      detail: failedTask ? `FAILED (task ${failedTask.id} failed)` : 'FAILED',
      trace: failedTask?.trace || null, // shown under "Details"
    }
  }
  const running = check.tasks.filter((task) => task.state === 'RUNNING').length
  const tasksText = `${running}/${check.tasks.length} tasks running`
  if (check.connectorState === 'RUNNING') return { color: 'green', detail: `RUNNING · ${tasksText}` }
  // PAUSED or UNASSIGNED
  return { color: 'amber', detail: `${check.connectorState} · ${tasksText}` }
}

// One stage card: coloured dot, name, one line of detail (+ optional hint / details).
function Stage({ name, color, detail, title, children }) {
  return (
    <div className="pipeline-stage">
      <div className="pipeline-stage-head">
        <span className={`status-dot ${color}`} aria-label={color} />
        <span className="pipeline-stage-name">{name}</span>
      </div>
      <div className="pipeline-stage-detail" title={title || detail}>
        {detail}
      </div>
      {children}
    </div>
  )
}

// The arrow between two stages (drawn with CSS only; it points down on narrow screens).
function Arrow() {
  return <span className="pipeline-arrow" aria-hidden="true" />
}

// Props (from DashboardPage):
//   latest              — the latest metrics update { replayed, errorRate, ... } or null
//   lastReplayIncreaseTs — when "replayed" last went up (ms), or null
//   sandboxId           — the active sandbox id, or ''
function PipelineStatus({ latest, lastReplayIncreaseTs, sandboxId }) {
  const [check, setCheck] = useState({ status: 'checking' })
  const [lastChecked, setLastChecked] = useState(null) // time of the last answer
  const [checkKey, setCheckKey] = useState(0) // goes up by 1 on "Check now"
  // The current time, refreshed every 5 seconds (used for "in the last 30 s").
  const [now, setNow] = useState(() => Date.now())

  // Ask Kafka Connect now and then every 10 seconds. "Check now" restarts this.
  // The cleanup stops the timer when the Dashboard closes.
  useEffect(() => {
    let cancelled = false // stops old answers from changing the panel

    function runCheck() {
      getConnectorStatus()
        .then((result) => !cancelled && setCheck({ status: 'ok', ...result }))
        .catch((err) => !cancelled && setCheck({ status: 'error', message: getErrorMessage(err) }))
        .finally(() => !cancelled && setLastChecked(Date.now()))
    }

    runCheck()
    const timer = setInterval(runCheck, CHECK_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [checkKey])

  // Keep "now" up to date, so the Replayer turns amber 30 s after the last replay.
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), CLOCK_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [])

  // "Check now": show "Checking..." and ask again straight away.
  function handleCheckNow() {
    setCheck({ status: 'checking' })
    setCheckKey((key) => key + 1)
  }

  // ---- Work out each stage ----
  const debezium = connectorStage(check)
  const connectorRunning = debezium.color === 'green'

  const replayed = latest?.replayed ?? 0
  const replayActive = lastReplayIncreaseTs !== null && now - lastReplayIncreaseTs <= REPLAY_ACTIVE_MS

  const errorRate = latest?.errorRate ?? 0
  let shadowColor = 'grey'
  let shadowDetail = 'No sandbox selected'
  if (sandboxId) {
    shadowColor = errorRate >= BREAKING_ERROR_RATE ? 'amber' : 'green'
    shadowDetail = `Sandbox ${sandboxId.slice(0, 8)} · error rate ${errorRate.toFixed(1)}%`
  }

  return (
    <div className="pipeline-panel">
      <div className="pipeline-header">
        <p className="metric-label pipeline-title">CDC pipeline</p>
        <span className="pipeline-checked">
          Last checked {lastChecked ? formatTime(lastChecked) : '—'}
        </span>
        <button className="small-button" onClick={handleCheckNow} disabled={check.status === 'checking'}>
          {check.status === 'checking' ? 'Checking...' : 'Check now'}
        </button>
      </div>

      <div className="pipeline">
        <Stage
          name="MySQL source (binlog)"
          color={connectorRunning ? 'green' : 'grey'}
          detail={connectorRunning ? 'Binlog is being read' : 'Unknown (connector not running)'}
        />
        <Arrow />

        <Stage name="Debezium connector" color={debezium.color} detail={debezium.detail} title={debezium.title}>
          {/* Not registered: show how to register it. */}
          {debezium.hint && (
            <div className="pipeline-hint">
              Register it:
              <code>{debezium.hint}</code>
            </div>
          )}
          {/* Failed: the error trace, folded away until "Details" is clicked. */}
          {debezium.trace && (
            <details className="pipeline-details">
              <summary>Details</summary>
              <pre>{debezium.trace}</pre>
            </details>
          )}
        </Stage>
        <Arrow />

        <Stage name="Kafka topic" color={connectorRunning ? 'green' : 'grey'} detail={TOPIC_NAME} />
        <Arrow />

        <Stage
          name="Replayer"
          color={replayActive ? 'green' : 'amber'}
          detail={`${replayed.toLocaleString()} events replayed`}
          title={replayActive ? 'Replayed events in the last 30 seconds' : 'Nothing replayed in the last 30 seconds'}
        />
        <Arrow />

        <Stage name="Shadow DB" color={shadowColor} detail={shadowDetail} />
      </div>
    </div>
  )
}

export default PipelineStatus
