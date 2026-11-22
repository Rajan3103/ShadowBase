// HeapProfilerPage.jsx — Heap Dump Diagnostics & Dominator Tree Memory Profiler.
// Uploads binary HPROF dumps, parses dominator trees, calculates retained sizes,
// and traces reference chains to GC Roots.
import { useState, useRef } from 'react'
import {
  uploadHeapDump,
  getHeapSummary,
  getRetainedSize,
  getReferenceChain,
  getErrorMessage,
  isMockMode,
} from '../services/api.js'

function formatBytes(bytes) {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`
}

function formatHexId(id) {
  if (id === null || id === undefined) return '0x0'
  return `0x${Number(id).toString(16).padStart(8, '0')}`
}

function HeapProfilerPage() {
  const [snapshot, setSnapshot] = useState(null)
  const [activeTab, setActiveTab] = useState('dominators') // 'dominators' | 'histogram'
  const [isUploading, setIsUploading] = useState(false)
  const [error, setError] = useState(null)
  const [filterText, setFilterText] = useState('')

  // Drilldown modal/drawer state
  const [selectedObjectId, setSelectedObjectId] = useState(null)
  const [inspectLoading, setInspectLoading] = useState(false)
  const [retainedData, setRetainedData] = useState(null)
  const [refChain, setRefChain] = useState(null)
  const [inspectError, setInspectError] = useState(null)

  const fileInputRef = useRef(null)

  async function handleFileSelected(event) {
    const file = event.target.files?.[0]
    if (!file) return
    setIsUploading(true)
    setError(null)
    try {
      const summary = await uploadHeapDump(file)
      setSnapshot(summary)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setIsUploading(false)
      event.target.value = ''
    }
  }

  async function handleLoadSample() {
    setIsUploading(true)
    setError(null)
    try {
      // In mock or dev mode, load default snapshot
      const summary = await getHeapSummary('00000000-0000-0000-0000-000000000001')
      setSnapshot(summary)
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setIsUploading(false)
    }
  }

  async function handleInspectObject(objectId) {
    setSelectedObjectId(objectId)
    setInspectLoading(true)
    setInspectError(null)
    try {
      const [retained, chain] = await Promise.all([
        getRetainedSize(snapshot.snapshotId, objectId),
        getReferenceChain(snapshot.snapshotId, objectId),
      ])
      setRetainedData(retained)
      setRefChain(chain)
    } catch (err) {
      setInspectError(getErrorMessage(err))
    } finally {
      setInspectLoading(false)
    }
  }

  function handleCloseModal() {
    setSelectedObjectId(null)
    setRetainedData(null)
    setRefChain(null)
    setInspectError(null)
  }

  const histogram = snapshot?.classHistogram || []
  const filteredHistogram = histogram.filter((item) =>
    item.className.toLowerCase().includes(filterText.toLowerCase())
  )

  const dominators = snapshot?.topDominators || []

  return (
    <div>
      <div className="toolbar">
        <div>
          <h2>Heap Dump Memory Profiler</h2>
          <p className="card-meta" style={{ margin: '4px 0 0' }}>
            Binary HPROF parser, Dominator Tree retained sizes, and GC root reference chain tracing.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            className="run-button"
            onClick={() => fileInputRef.current?.click()}
            disabled={isUploading}
          >
            {isUploading ? 'Parsing HPROF...' : 'Upload .hprof Dump'}
          </button>
          <button
            className="small-button"
            onClick={handleLoadSample}
            disabled={isUploading}
            title="Load demo heap snapshot"
          >
            {isMockMode() ? 'Load Demo Snapshot' : 'Reload Snapshot'}
          </button>
          <input
            type="file"
            ref={fileInputRef}
            style={{ display: 'none' }}
            accept=".hprof,.bin"
            onChange={handleFileSelected}
          />
        </div>
      </div>

      {error && <div className="card-error" style={{ marginBottom: '16px' }}>{error}</div>}

      {!snapshot && !isUploading && (
        <div
          className="sandbox-card"
          style={{
            padding: '48px 24px',
            textAlign: 'center',
            border: '2px dashed #334155',
            cursor: 'pointer',
          }}
          onClick={() => fileInputRef.current?.click()}
        >
          <h3 style={{ margin: '0 0 8px' }}>No Heap Dump Loaded</h3>
          <p className="card-meta" style={{ maxWidth: '540px', margin: '0 auto 16px' }}>
            Upload a Java binary heap dump (<code>.hprof</code>) generated via{' '}
            <code>jcmd &lt;pid&gt; GC.heap_dump</code> or test with the built-in demo snapshot to
            diagnose memory leaks, compute object retained sizes, and trace GC retention paths.
          </p>
          <button
            className="run-button"
            type="button"
            onClick={(e) => {
              e.stopPropagation()
              handleLoadSample()
            }}
          >
            Try Demo Heap Dump
          </button>
        </div>
      )}

      {isUploading && (
        <div className="sandbox-card" style={{ padding: '32px', textAlign: 'center' }}>
          <div className="status-dot green" style={{ display: 'inline-block', marginBottom: '8px' }} />
          <p>Parsing binary HPROF segments, building dominator tree & calculating retained sizes...</p>
        </div>
      )}

      {snapshot && (
        <>
          {/* ---- Summary Metrics Bar ---- */}
          <div className="metric-grid" style={{ marginTop: '16px', marginBottom: '20px' }}>
            <div className="metric-card">
              <div className="metric-label">Heap Size</div>
              <div className="metric-value">{formatBytes(snapshot.totalHeapSizeBytes)}</div>
              <div className="metric-sub">{snapshot.headerVersion}</div>
            </div>
            <div className="metric-card">
              <div className="metric-label">Total Objects</div>
              <div className="metric-value">{snapshot.totalObjectsCount?.toLocaleString()}</div>
              <div className="metric-sub">{snapshot.totalClassesCount?.toLocaleString()} classes</div>
            </div>
            <div className="metric-card">
              <div className="metric-label">GC Roots</div>
              <div className="metric-value">{snapshot.gcRootsCount}</div>
              <div className="metric-sub">Root anchors traced</div>
            </div>
            <div className="metric-card">
              <div className="metric-label">Snapshot ID</div>
              <div className="metric-value" style={{ fontSize: '15px' }}>
                {snapshot.snapshotId.slice(0, 13)}...
              </div>
              <div className="metric-sub">
                {new Date(snapshot.dumpTimestamp).toLocaleTimeString()}
              </div>
            </div>
          </div>

          {/* ---- Tabs Navigation ---- */}
          <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid #334155', marginBottom: '16px' }}>
            <button
              className={`small-button ${activeTab === 'dominators' ? 'active' : ''}`}
              style={{
                borderRadius: '6px 6px 0 0',
                background: activeTab === 'dominators' ? '#1e3a5f' : '#1f2937',
              }}
              onClick={() => setActiveTab('dominators')}
            >
              Dominator Tree & Leak Suspects ({dominators.length})
            </button>
            <button
              className={`small-button ${activeTab === 'histogram' ? 'active' : ''}`}
              style={{
                borderRadius: '6px 6px 0 0',
                background: activeTab === 'histogram' ? '#1e3a5f' : '#1f2937',
              }}
              onClick={() => setActiveTab('histogram')}
            >
              Class Histogram ({histogram.length})
            </button>
          </div>

          {/* ---- Tab 1: Top Dominators (Leak Suspects) ---- */}
          {activeTab === 'dominators' && (
            <div className="sandbox-card">
              <div className="card-header">
                <div>
                  <h3 style={{ margin: '0 0 4px' }}>Top Memory Dominators</h3>
                  <p className="card-meta">
                    Objects that hold the largest retained heap memory exclusively. Collecting these
                    objects immediately frees their dominated subtrees.
                  </p>
                </div>
              </div>

              <div className="results-scroll" style={{ marginTop: '12px' }}>
                <table className="results-table">
                  <thead>
                    <tr>
                      <th>Object ID</th>
                      <th>Class Name</th>
                      <th>Shallow Size</th>
                      <th>Retained Size</th>
                      <th>Heap %</th>
                      <th>Dominated Count</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dominators.map((dom) => (
                      <tr key={dom.objectId}>
                        <td>
                          <code>{formatHexId(dom.objectId)}</code>
                        </td>
                        <td style={{ fontWeight: 600 }}>{dom.className}</td>
                        <td>{formatBytes(dom.shallowSizeBytes)}</td>
                        <td style={{ color: '#38bdf8', fontWeight: 600 }}>
                          {formatBytes(dom.retainedSizeBytes)}
                        </td>
                        <td style={{ minWidth: '120px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <div
                              style={{
                                flex: 1,
                                height: '6px',
                                background: '#334155',
                                borderRadius: '3px',
                                overflow: 'hidden',
                              }}
                            >
                              <div
                                style={{
                                  width: `${Math.min(100, Math.max(2, dom.retainedPercentage || 0))}%`,
                                  height: '100%',
                                  background: dom.retainedPercentage > 15 ? '#ef4444' : '#38bdf8',
                                }}
                              />
                            </div>
                            <span style={{ fontSize: '12px', minWidth: '40px' }}>
                              {(dom.retainedPercentage || 0).toFixed(1)}%
                            </span>
                          </div>
                        </td>
                        <td>{dom.dominatedChildCount}</td>
                        <td>
                          <button
                            className="small-button"
                            onClick={() => handleInspectObject(dom.objectId)}
                          >
                            Trace GC Root
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* ---- Tab 2: Class Histogram ---- */}
          {activeTab === 'histogram' && (
            <div className="sandbox-card">
              <div className="toolbar" style={{ marginBottom: '12px' }}>
                <div>
                  <h3 style={{ margin: 0 }}>Class Histogram</h3>
                  <p className="card-meta">Aggregated instance count, shallow and retained bytes per class.</p>
                </div>
                <input
                  type="text"
                  className="text-input"
                  style={{ width: '280px' }}
                  placeholder="Filter classes by name..."
                  value={filterText}
                  onChange={(e) => setFilterText(e.target.value)}
                />
              </div>

              <div className="results-scroll">
                <table className="results-table">
                  <thead>
                    <tr>
                      <th>Class Name</th>
                      <th>Instances</th>
                      <th>Total Shallow Size</th>
                      <th>Total Retained Size</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredHistogram.map((item, idx) => (
                      <tr key={idx}>
                        <td style={{ fontWeight: 500 }}>{item.className}</td>
                        <td>{item.objectCount?.toLocaleString()}</td>
                        <td>{formatBytes(item.totalShallowSizeBytes)}</td>
                        <td style={{ color: '#38bdf8' }}>{formatBytes(item.totalRetainedSizeBytes)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      {/* ---- Retained Size & Reference Chain Inspection Modal ---- */}
      {selectedObjectId && (
        <div className="modal-backdrop" onClick={handleCloseModal}>
          <div
            className="modal-box"
            style={{ maxWidth: '780px', width: '90%' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="card-header" style={{ marginBottom: '12px' }}>
              <div>
                <h3 style={{ margin: 0 }}>Object Retention & Reference Path Analysis</h3>
                <p className="card-meta">Object ID: {formatHexId(selectedObjectId)}</p>
              </div>
              <button className="small-button" onClick={handleCloseModal}>
                ✕
              </button>
            </div>

            {inspectLoading && (
              <div style={{ padding: '24px', textAlign: 'center' }}>
                <p>Tracing dominator tree and shortest reference chain to GC root...</p>
              </div>
            )}

            {inspectError && <div className="card-error">{inspectError}</div>}

            {retainedData && !inspectLoading && (
              <div style={{ marginBottom: '20px' }}>
                <h4>Dominator & Retained Metrics</h4>
                <div className="metric-grid" style={{ marginTop: '8px' }}>
                  <div className="metric-card">
                    <div className="metric-label">Class</div>
                    <div className="metric-sub" style={{ wordBreak: 'break-all', fontSize: '13px' }}>
                      {retainedData.className}
                    </div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Shallow Size</div>
                    <div className="metric-value">{formatBytes(retainedData.shallowSizeBytes)}</div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Retained Size</div>
                    <div className="metric-value" style={{ color: '#38bdf8' }}>
                      {formatBytes(retainedData.retainedSizeBytes)}
                    </div>
                    <div className="metric-sub">{retainedData.percentageOfTotalHeap?.toFixed(2)}% of Heap</div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Dominated Subtree</div>
                    <div className="metric-value">{retainedData.dominatedObjectCount}</div>
                    <div className="metric-sub">Objects exclusive to this node</div>
                  </div>
                </div>
              </div>
            )}

            {refChain && !inspectLoading && (
              <div>
                <h4>Shortest Reference Chain from GC Root</h4>
                <div
                  style={{
                    background: '#111827',
                    border: '1px solid #334155',
                    borderRadius: '8px',
                    padding: '12px 16px',
                    marginTop: '8px',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
                    <span className="badge badge-green">{refChain.rootType || 'GC ROOT'}</span>
                    <strong style={{ fontSize: '14px' }}>
                      Root Object {formatHexId(refChain.rootObjectId)}
                    </strong>
                    <span className="card-meta">({refChain.rootContext || 'Anchor'})</span>
                  </div>

                  {refChain.hops && refChain.hops.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      {refChain.hops.map((hop, index) => (
                        <div
                          key={index}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            fontSize: '13px',
                            background: '#1f2937',
                            padding: '8px 12px',
                            borderRadius: '6px',
                          }}
                        >
                          <span style={{ color: '#94a3b8', minWidth: '24px' }}>#{index + 1}</span>
                          <span style={{ fontWeight: 600 }}>{hop.fromClassName}</span>
                          <code>{formatHexId(hop.fromObjectId)}</code>
                          <span style={{ color: '#f59e0b', fontWeight: 600 }}>
                            ──[{hop.fieldName || 'ref'}]──▶
                          </span>
                          <span style={{ fontWeight: 600, color: '#38bdf8' }}>{hop.toClassName}</span>
                          <code>{formatHexId(hop.toObjectId)}</code>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="card-meta">Object is an immediate GC Root or has no incoming references.</p>
                  )}
                </div>
              </div>
            )}

            <div style={{ marginTop: '20px', textAlign: 'right' }}>
              <button className="small-button" onClick={handleCloseModal}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default HeapProfilerPage