import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';

const BASE = '/api/admin/crawl';

function fmt(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function statusColor(s) {
  const map = {
    RUNNING: '#0a6', COMPLETED: '#0a6',
    PENDING: '#888', PAUSED: '#c80',
    FAILED: '#c33',  CANCELLED: '#999',
  };
  return map[s] || '#555';
}

// ── Source form modal ─────────────────────────────────────────────────────

function SourceModal({ initial, onSaved, onClose }) {
  const editing = !!initial;
  const [form, setForm] = useState({
    name: initial?.name || '',
    baseUrl: initial?.baseUrl || '',
    siteType: initial?.siteType || 'HTML',
    description: initial?.description || '',
    city: initial?.city || '',
    keywords: initial?.keywords || '',
    crawlConfig: initial?.crawlConfig || '{}',
    scheduleCron: initial?.scheduleCron || '',
    scheduleIntervalSeconds: initial?.scheduleIntervalSeconds ?? '',
    delayMsBetweenRequests: initial?.delayMsBetweenRequests ?? 1000,
    maxDepth: initial?.maxDepth ?? 3,
    maxPages: initial?.maxPages ?? 100,
    active: initial?.active ?? true,
  });
  const [error, setError]   = useState('');
  const [saving, setSaving] = useState(false);

  function set(k, v) { setForm(f => ({ ...f, [k]: v })); }

  async function submit(e) {
    e.preventDefault();
    if (!form.name.trim()) { setError('Name is required'); return; }
    if (!form.baseUrl.trim()) { setError('Base URL is required'); return; }
    setSaving(true);
    try {
      const payload = {
        ...form,
        scheduleIntervalSeconds: form.scheduleIntervalSeconds !== '' ? Number(form.scheduleIntervalSeconds) : null,
        delayMsBetweenRequests:  Number(form.delayMsBetweenRequests),
        maxDepth:  Number(form.maxDepth),
        maxPages:  Number(form.maxPages),
        scheduleCron: form.scheduleCron.trim() || null,
        keywords:    form.keywords.trim() || null,
      };
      const url  = editing ? `${BASE}/sources/${initial.id}` : `${BASE}/sources`;
      const verb = editing ? 'PUT' : 'POST';
      const saved = editing
        ? await api.put(url, payload)
        : await api.post(url, payload);
      onSaved(saved);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div style={s.overlay}>
      <div style={{ ...s.modal, maxWidth: 560 }}>
        <h3 style={{ margin: '0 0 1rem' }}>{editing ? 'Edit Source' : 'New Crawl Source'}</h3>
        {error && <div style={s.errBanner}>{error}</div>}
        <form onSubmit={submit}>
          <label style={s.label}>Name *</label>
          <input style={s.input} value={form.name} onChange={e => set('name', e.target.value)} />

          <label style={s.label}>Base URL *</label>
          <input style={s.input} value={form.baseUrl} onChange={e => set('baseUrl', e.target.value)} placeholder="http://intranet.local/path" />

          <div style={{ display: 'flex', gap: 8 }}>
            <div style={{ flex: 1 }}>
              <label style={s.label}>Site type</label>
              <select style={s.input} value={form.siteType} onChange={e => set('siteType', e.target.value)}>
                {['HTML', 'JSON', 'RSS', 'XML'].map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div style={{ flex: 1 }}>
              <label style={s.label}>City</label>
              <input style={s.input} value={form.city} onChange={e => set('city', e.target.value)} />
            </div>
          </div>

          <label style={s.label}>Keywords (comma-separated)</label>
          <input style={s.input} value={form.keywords} onChange={e => set('keywords', e.target.value)} placeholder='vacancy, available' />

          <label style={s.label}>Description</label>
          <input style={s.input} value={form.description} onChange={e => set('description', e.target.value)} />

          <div style={{ display: 'flex', gap: 8 }}>
            <div style={{ flex: 1 }}>
              <label style={s.label}>Cron schedule</label>
              <input style={s.input} value={form.scheduleCron} onChange={e => set('scheduleCron', e.target.value)} placeholder="0 0 */6 * * *" />
            </div>
            <div style={{ flex: 1 }}>
              <label style={s.label}>Interval (seconds)</label>
              <input style={s.input} type="number" value={form.scheduleIntervalSeconds} onChange={e => set('scheduleIntervalSeconds', e.target.value)} placeholder="3600" />
            </div>
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <div style={{ flex: 1 }}>
              <label style={s.label}>Delay between requests (ms)</label>
              <input style={s.input} type="number" value={form.delayMsBetweenRequests} onChange={e => set('delayMsBetweenRequests', e.target.value)} />
            </div>
            <div style={{ flex: 1 }}>
              <label style={s.label}>Max depth</label>
              <input style={s.input} type="number" value={form.maxDepth} onChange={e => set('maxDepth', e.target.value)} />
            </div>
            <div style={{ flex: 1 }}>
              <label style={s.label}>Max pages</label>
              <input style={s.input} type="number" value={form.maxPages} onChange={e => set('maxPages', e.target.value)} />
            </div>
          </div>

          <label style={s.label}>Crawl config (JSON)</label>
          <textarea style={{ ...s.input, fontFamily: 'monospace', height: 80 }}
            value={form.crawlConfig} onChange={e => set('crawlConfig', e.target.value)} />

          <label style={{ ...s.label, display: 'flex', alignItems: 'center', gap: 6 }}>
            <input type="checkbox" checked={form.active} onChange={e => set('active', e.target.checked)} />
            Active
          </label>

          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <button style={s.btnPrimary} type="submit" disabled={saving}>
              {saving ? 'Saving…' : editing ? 'Save changes' : 'Create source'}
            </button>
            <button style={s.btnSecondary} type="button" onClick={onClose}>Cancel</button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Job pages panel ───────────────────────────────────────────────────────

function JobPagesPanel({ jobId, onClose }) {
  const [pages, setPages]   = useState([]);
  const [page,  setPage]    = useState(0);
  const [total, setTotal]   = useState(0);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (p = 0) => {
    setLoading(true);
    try {
      const data = await api.get(`${BASE}/jobs/${jobId}/pages?page=${p}&size=20`);
      setPages(data.content);
      setTotal(data.totalPages);
      setPage(p);
    } catch (e) { /* ignore */ }
    finally { setLoading(false); }
  }, [jobId]);

  useEffect(() => { load(0); }, [load]);

  return (
    <div style={s.overlay}>
      <div style={{ ...s.modal, maxWidth: 780, maxHeight: '80vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 style={{ margin: 0 }}>Pages for job</h3>
          <button style={s.btnSecondary} onClick={onClose}>Close</button>
        </div>
        {loading ? <div>Loading…</div> : (
          <>
            <table style={s.table}>
              <thead><tr>
                <th style={s.th}>URL</th>
                <th style={s.th}>Status</th>
                <th style={s.th}>HTTP</th>
                <th style={s.th}>Depth</th>
                <th style={s.th}>Fetched</th>
              </tr></thead>
              <tbody>
                {pages.map(p => (
                  <tr key={p.id} style={{ background: p.status === 'ERROR' ? '#fff0f0' : undefined }}>
                    <td style={{ ...s.td, maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                        title={p.url}>{p.url}</td>
                    <td style={{ ...s.td, color: p.status === 'ERROR' ? '#c33' : '#333', fontWeight: 600 }}>{p.status}</td>
                    <td style={s.td}>{p.httpStatus ?? '—'}</td>
                    <td style={s.td}>{p.depth}</td>
                    <td style={s.td}>{fmt(p.fetchedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {total > 1 && (
              <div style={{ display: 'flex', gap: 6, justifyContent: 'center', marginTop: 12 }}>
                <button style={s.btnSecondary} disabled={page === 0} onClick={() => load(page - 1)}>Prev</button>
                <span style={{ lineHeight: '2rem', fontSize: '0.85rem' }}>Page {page + 1} / {total}</span>
                <button style={s.btnSecondary} disabled={page >= total - 1} onClick={() => load(page + 1)}>Next</button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

// ── Jobs tab ──────────────────────────────────────────────────────────────

function JobsTab({ sourceId }) {
  const [jobs, setJobs]       = useState([]);
  const [page, setPage]       = useState(0);
  const [total, setTotal]     = useState(0);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState('');
  const [pagesJob, setPagesJob] = useState(null);

  const url = sourceId
    ? `${BASE}/sources/${sourceId}/jobs`
    : `${BASE}/jobs`;

  const load = useCallback(async (p = 0) => {
    setLoading(true);
    setError('');
    try {
      const data = await api.get(`${url}?page=${p}&size=20&sort=createdAt,desc`);
      setJobs(data.content);
      setTotal(data.totalPages);
      setPage(p);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }, [url]);

  useEffect(() => { load(0); }, [load]);

  async function control(jobId, action) {
    try {
      await api.post(`${BASE}/jobs/${jobId}/${action}`);
      load(page);
    } catch (e) { alert(e.message); }
  }

  return (
    <div>
      {pagesJob && <JobPagesPanel jobId={pagesJob} onClose={() => setPagesJob(null)} />}
      {error && <div style={s.errBanner}>{error}</div>}
      {loading ? <div style={{ padding: '1rem', color: '#888' }}>Loading…</div> : (
        <table style={s.table}>
          <thead><tr>
            {!sourceId && <th style={s.th}>Source</th>}
            <th style={s.th}>Status</th>
            <th style={s.th}>Trigger</th>
            <th style={s.th}>Crawled</th>
            <th style={s.th}>Skipped</th>
            <th style={s.th}>Failed</th>
            <th style={s.th}>Items</th>
            <th style={s.th}>Started</th>
            <th style={s.th}>Finished</th>
            <th style={s.th}>Actions</th>
          </tr></thead>
          <tbody>
            {jobs.map(j => (
              <tr key={j.id}>
                {!sourceId && <td style={s.td}>{j.sourceName}</td>}
                <td style={{ ...s.td, color: statusColor(j.status), fontWeight: 600 }}>{j.status}</td>
                <td style={s.td}>{j.triggerType}</td>
                <td style={s.td}>{j.pagesCrawled}</td>
                <td style={s.td}>{j.pagesSkipped}</td>
                <td style={{ ...s.td, color: j.pagesFailed > 0 ? '#c33' : undefined }}>{j.pagesFailed}</td>
                <td style={s.td}>{j.itemsFound}</td>
                <td style={s.td}>{fmt(j.startedAt)}</td>
                <td style={s.td}>{fmt(j.finishedAt)}</td>
                <td style={{ ...s.td, whiteSpace: 'nowrap' }}>
                  <button style={s.microBtn} onClick={() => setPagesJob(j.id)}>Pages</button>
                  {j.status === 'RUNNING'  && <button style={s.microBtn} onClick={() => control(j.id, 'pause')}>Pause</button>}
                  {j.status === 'PAUSED'   && <button style={s.microBtn} onClick={() => control(j.id, 'resume')}>Resume</button>}
                  {(j.status === 'RUNNING' || j.status === 'PAUSED' || j.status === 'PENDING') &&
                    <button style={{ ...s.microBtn, color: '#c33' }} onClick={() => control(j.id, 'cancel')}>Cancel</button>}
                </td>
              </tr>
            ))}
            {jobs.length === 0 && (
              <tr><td colSpan={sourceId ? 9 : 10} style={{ ...s.td, color: '#888', textAlign: 'center' }}>No jobs</td></tr>
            )}
          </tbody>
        </table>
      )}
      {total > 1 && (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'center', marginTop: 12 }}>
          <button style={s.btnSecondary} disabled={page === 0} onClick={() => load(page - 1)}>Prev</button>
          <span style={{ lineHeight: '2rem', fontSize: '0.85rem' }}>Page {page + 1} / {total}</span>
          <button style={s.btnSecondary} disabled={page >= total - 1} onClick={() => load(page + 1)}>Next</button>
        </div>
      )}
    </div>
  );
}

// ── Engine status banner ──────────────────────────────────────────────────

function EngineStatus() {
  const [status, setStatus] = useState(null);

  useEffect(() => {
    api.get(`${BASE}/engine/status`).then(setStatus).catch(() => {});
    const id = setInterval(() => {
      api.get(`${BASE}/engine/status`).then(setStatus).catch(() => {});
    }, 5000);
    return () => clearInterval(id);
  }, []);

  if (!status) return null;

  return (
    <div style={s.statusBanner}>
      Engine: <strong>{status.activeWorkers}</strong> / <strong>{status.maxConcurrent}</strong> active workers
      {status.runningJobIds?.length > 0 && (
        <span style={{ marginLeft: 12, color: '#555', fontSize: '0.8rem' }}>
          Running: {status.runningJobIds.length} job(s)
        </span>
      )}
    </div>
  );
}

// ── Sources tab ───────────────────────────────────────────────────────────

function SourcesTab() {
  const [sources, setSources] = useState([]);
  const [page,    setPage]    = useState(0);
  const [total,   setTotal]   = useState(0);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState('');
  const [modal,   setModal]   = useState(null); // null | 'create' | source object
  const [expanded, setExpanded] = useState(null);

  const load = useCallback(async (p = 0) => {
    setLoading(true);
    setError('');
    try {
      const data = await api.get(`${BASE}/sources?page=${p}&size=20`);
      setSources(data.content);
      setTotal(data.totalPages);
      setPage(p);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(0); }, [load]);

  async function triggerSource(id, name) {
    if (!window.confirm(`Trigger a manual crawl for "${name}"?`)) return;
    try {
      await api.post(`${BASE}/sources/${id}/trigger`);
      alert('Job created. Check the Jobs tab.');
    } catch (e) { alert(e.message); }
  }

  async function deleteSource(id, name) {
    if (!window.confirm(`Delete source "${name}"? This is a soft-delete and can be recovered from the database.`)) return;
    try {
      await api.delete(`${BASE}/sources/${id}`);
      load(page);
    } catch (e) { alert(e.message); }
  }

  function onSaved(src) {
    setModal(null);
    load(page);
  }

  return (
    <div>
      {modal && (
        <SourceModal
          initial={modal === 'create' ? null : modal}
          onSaved={onSaved}
          onClose={() => setModal(null)}
        />
      )}

      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <EngineStatus />
        <button style={s.btnPrimary} onClick={() => setModal('create')}>+ New source</button>
      </div>

      {error && <div style={s.errBanner}>{error}</div>}

      {loading ? <div style={{ padding: '1rem', color: '#888' }}>Loading…</div> : (
        <table style={s.table}>
          <thead><tr>
            <th style={s.th}>Name</th>
            <th style={s.th}>Base URL</th>
            <th style={s.th}>Type</th>
            <th style={s.th}>City</th>
            <th style={s.th}>Schedule</th>
            <th style={s.th}>Status</th>
            <th style={s.th}>Last crawled</th>
            <th style={s.th}>Actions</th>
          </tr></thead>
          <tbody>
            {sources.map(src => (
              <React.Fragment key={src.id}>
                <tr style={{ cursor: 'pointer' }} onClick={() => setExpanded(expanded === src.id ? null : src.id)}>
                  <td style={{ ...s.td, fontWeight: 600 }}>{src.name}</td>
                  <td style={{ ...s.td, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                      title={src.baseUrl}>{src.baseUrl}</td>
                  <td style={s.td}>{src.siteType}</td>
                  <td style={s.td}>{src.city || '—'}</td>
                  <td style={s.td}>{src.scheduleCron || (src.scheduleIntervalSeconds ? `${src.scheduleIntervalSeconds}s` : '—')}</td>
                  <td style={{ ...s.td, color: src.active ? '#0a6' : '#c33', fontWeight: 600 }}>{src.active ? 'Active' : 'Inactive'}</td>
                  <td style={s.td}>{fmt(src.lastCrawledAt)}</td>
                  <td style={{ ...s.td, whiteSpace: 'nowrap' }} onClick={e => e.stopPropagation()}>
                    <button style={s.microBtn} onClick={() => setModal(src)}>Edit</button>
                    <button style={s.microBtn} onClick={() => triggerSource(src.id, src.name)}>Run now</button>
                    <button style={{ ...s.microBtn, color: '#c33' }} onClick={() => deleteSource(src.id, src.name)}>Delete</button>
                  </td>
                </tr>
                {expanded === src.id && (
                  <tr>
                    <td colSpan={8} style={{ padding: '0 1rem 1rem', background: '#f9fafb', borderBottom: '1px solid #eee' }}>
                      <strong>Jobs for this source:</strong>
                      <JobsTab sourceId={src.id} />
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
            {sources.length === 0 && (
              <tr><td colSpan={8} style={{ ...s.td, color: '#888', textAlign: 'center' }}>No sources yet</td></tr>
            )}
          </tbody>
        </table>
      )}

      {total > 1 && (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'center', marginTop: 12 }}>
          <button style={s.btnSecondary} disabled={page === 0} onClick={() => load(page - 1)}>Prev</button>
          <span style={{ lineHeight: '2rem', fontSize: '0.85rem' }}>Page {page + 1} / {total}</span>
          <button style={s.btnSecondary} disabled={page >= total - 1} onClick={() => load(page + 1)}>Next</button>
        </div>
      )}
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────

export default function CrawlerPage() {
  const [tab, setTab] = useState('sources');

  return (
    <main style={{ padding: '2rem', maxWidth: 1100 }}>
      <h2 style={{ marginTop: 0 }}>Data Collector</h2>

      <div style={{ display: 'flex', gap: 4, marginBottom: 20, borderBottom: '2px solid #eee' }}>
        {[['sources', 'Sources'], ['jobs', 'All Jobs']].map(([k, label]) => (
          <button key={k} style={tab === k ? s.tabActive : s.tab} onClick={() => setTab(k)}>
            {label}
          </button>
        ))}
      </div>

      {tab === 'sources' && <SourcesTab />}
      {tab === 'jobs'    && <JobsTab />}
    </main>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────

const s = {
  overlay: {
    position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)',
    display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
  },
  modal: {
    background: '#fff', borderRadius: 8, padding: '1.5rem',
    width: '90%', boxShadow: '0 4px 24px rgba(0,0,0,0.18)', overflowY: 'auto',
  },
  label:  { display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#555', marginBottom: 3, marginTop: 10 },
  input:  { width: '100%', padding: '0.4rem 0.5rem', borderRadius: 4, border: '1px solid #ccc', fontSize: '0.9rem', boxSizing: 'border-box' },
  errBanner: { background: '#fff0f0', border: '1px solid #fcc', color: '#c33', borderRadius: 4, padding: '0.6rem 1rem', marginBottom: 12 },
  btnPrimary:   { padding: '0.45rem 1.1rem', background: '#0055cc', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer', fontWeight: 600, fontSize: '0.85rem' },
  btnSecondary: { padding: '0.45rem 1.1rem', background: '#fff', color: '#333', border: '1px solid #ccc', borderRadius: 4, cursor: 'pointer', fontWeight: 600, fontSize: '0.85rem' },
  microBtn: { padding: '2px 8px', marginRight: 4, background: '#f0f4ff', color: '#0055cc', border: '1px solid #c0d0f0', borderRadius: 3, cursor: 'pointer', fontSize: '0.78rem', fontWeight: 600 },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' },
  th: { textAlign: 'left', padding: '0.5rem 0.75rem', borderBottom: '2px solid #eee', fontWeight: 700, color: '#555', whiteSpace: 'nowrap' },
  td: { padding: '0.5rem 0.75rem', borderBottom: '1px solid #f0f0f0', verticalAlign: 'middle' },
  tab: { padding: '0.5rem 1.1rem', border: 'none', background: 'none', cursor: 'pointer', fontSize: '0.9rem', color: '#555', borderBottom: '2px solid transparent', marginBottom: -2 },
  tabActive: { padding: '0.5rem 1.1rem', border: 'none', background: 'none', cursor: 'pointer', fontSize: '0.9rem', color: '#0055cc', fontWeight: 700, borderBottom: '2px solid #0055cc', marginBottom: -2 },
  statusBanner: { padding: '0.4rem 0.8rem', background: '#f0f7ff', border: '1px solid #c0d8f8', borderRadius: 4, fontSize: '0.85rem', color: '#0044aa' },
};
