import React, { useState, useEffect, useCallback } from 'react';
import { api as client } from '../api/client';

const BASE = '/api/admin';

// ── Helpers ───────────────────────────────────────────────────────────────

async function api(method, path, body) {
  try {
    switch (method) {
      case 'GET':
        return await client.get(path);
      case 'POST':
        return await client.post(path, body);
      case 'PUT':
        return await client.put(path, body);
      case 'DELETE':
        return await client.delete(path);
      default:
        throw new Error(`Unsupported method: ${method}`);
    }
  } catch (err) {
    if (err?.message) {
      throw err;
    }
    throw new Error('Request failed');
  }
}

function fmtDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

// ── Once-shown secret modal ───────────────────────────────────────────────

function SecretRevealModal({ title, label, secret, onClose }) {
  const [copied, setCopied] = useState(false);

  function copy() {
    navigator.clipboard.writeText(secret).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  return (
    <div style={m.overlay}>
      <div style={{ ...m.modal, maxWidth: 520 }}>
        <h3 style={{ margin: '0 0 0.5rem' }}>{title}</h3>
        <div style={m.warningBanner}>
          This {label} will not be shown again. Copy it now and store it securely.
        </div>
        <div style={m.secretBox}>
          <code style={{ wordBreak: 'break-all', fontSize: '0.85rem' }}>{secret}</code>
        </div>
        <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
          <button style={m.btnPrimary} onClick={copy}>
            {copied ? 'Copied!' : 'Copy to clipboard'}
          </button>
          <button style={m.btnSecondary} onClick={onClose}>Done</button>
        </div>
      </div>
    </div>
  );
}

// ── Create key form ───────────────────────────────────────────────────────

function CreateKeyModal({ onCreated, onClose }) {
  const [form, setForm]   = useState({ name: '', description: '', allowedEvents: '' });
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  async function submit(e) {
    e.preventDefault();
    if (!form.name.trim()) { setError('Name is required'); return; }
    setSaving(true);
    try {
      const key = await api('POST', `${BASE}/integration-keys`, {
        name: form.name.trim(),
        description: form.description.trim() || null,
        allowedEvents: form.allowedEvents.trim() || null,
      });
      onCreated(key);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div style={m.overlay}>
      <div style={{ ...m.modal, maxWidth: 480 }}>
        <h3 style={{ margin: '0 0 1rem' }}>New Integration Key</h3>
        {error && <div style={m.errorBanner}>{error}</div>}
        <form onSubmit={submit}>
          <label style={m.label}>Name *</label>
          <input style={m.input} value={form.name}
            onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />

          <label style={m.label}>Description</label>
          <textarea style={{ ...m.input, height: 60, resize: 'vertical' }}
            value={form.description}
            onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />

          <label style={m.label}>
            Allowed events
            <span style={m.hint}> (JSON array, e.g. ["resident.created"] — blank = unrestricted)</span>
          </label>
          <input style={m.input} placeholder='["event.type"]'
            value={form.allowedEvents}
            onChange={e => setForm(f => ({ ...f, allowedEvents: e.target.value }))} />

          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <button type="submit" style={m.btnPrimary} disabled={saving}>
              {saving ? 'Creating…' : 'Create key'}
            </button>
            <button type="button" style={m.btnSecondary} onClick={onClose}>Cancel</button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Add webhook form ──────────────────────────────────────────────────────

function AddWebhookModal({ keyId, onCreated, onClose }) {
  const [form, setForm]   = useState({ name: '', targetUrl: '', eventTypes: '' });
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  async function submit(e) {
    e.preventDefault();
    if (!form.name.trim() || !form.targetUrl.trim() || !form.eventTypes.trim()) {
      setError('All fields are required');
      return;
    }
    setSaving(true);
    try {
      const wh = await api('POST', `${BASE}/integration-keys/${keyId}/webhooks`, {
        name: form.name.trim(),
        targetUrl: form.targetUrl.trim(),
        eventTypes: form.eventTypes.trim(),
      });
      onCreated(wh);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div style={m.overlay}>
      <div style={{ ...m.modal, maxWidth: 480 }}>
        <h3 style={{ margin: '0 0 1rem' }}>Add Webhook Endpoint</h3>
        {error && <div style={m.errorBanner}>{error}</div>}
        <form onSubmit={submit}>
          <label style={m.label}>Name *</label>
          <input style={m.input} value={form.name}
            onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />

          <label style={m.label}>Target URL *
            <span style={m.hint}> (must resolve to a private/local IP)</span>
          </label>
          <input style={m.input} placeholder="http://192.168.1.50:8080/webhook"
            value={form.targetUrl}
            onChange={e => setForm(f => ({ ...f, targetUrl: e.target.value }))} />

          <label style={m.label}>Event types * (JSON array)</label>
          <input style={m.input} placeholder='["resident.updated","booking.cancelled"]'
            value={form.eventTypes}
            onChange={e => setForm(f => ({ ...f, eventTypes: e.target.value }))} />

          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <button type="submit" style={m.btnPrimary} disabled={saving}>
              {saving ? 'Adding…' : 'Add webhook'}
            </button>
            <button type="button" style={m.btnSecondary} onClick={onClose}>Cancel</button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Webhook list ──────────────────────────────────────────────────────────

function WebhookPanel({ keyId }) {
  const [webhooks, setWebhooks]         = useState(null);
  const [addOpen, setAddOpen]           = useState(false);
  const [newSecret, setNewSecret]       = useState(null);
  const [error, setError]               = useState('');

  const load = useCallback(() => {
    api('GET', `${BASE}/integration-keys/${keyId}/webhooks`)
      .then(setWebhooks).catch(e => setError(e.message));
  }, [keyId]);

  useEffect(() => { load(); }, [load]);

  async function toggle(wid, active) {
    try {
      const updated = await api('POST',
        `${BASE}/integration-keys/${keyId}/webhooks/${wid}/toggle?active=${active}`);
      setWebhooks(ws => ws.map(w => w.id === wid ? updated : w));
    } catch (e) { setError(e.message); }
  }

  async function del(wid) {
    if (!window.confirm('Delete this webhook endpoint?')) return;
    try {
      await api('DELETE', `${BASE}/integration-keys/${keyId}/webhooks/${wid}`);
      setWebhooks(ws => ws.filter(w => w.id !== wid));
    } catch (e) { setError(e.message); }
  }

  if (!webhooks) return <div style={{ padding: '0.5rem', color: '#888', fontSize: '0.85rem' }}>Loading…</div>;

  return (
    <div style={{ marginTop: 8 }}>
      {error && <div style={{ ...m.errorBanner, marginBottom: 8 }}>{error}</div>}

      {newSecret && (
        <SecretRevealModal
          title="Webhook signing secret"
          label="signing secret"
          secret={newSecret}
          onClose={() => setNewSecret(null)}
        />
      )}
      {addOpen && (
        <AddWebhookModal
          keyId={keyId}
          onCreated={wh => { setAddOpen(false); setWebhooks(ws => [...ws, wh]); setNewSecret(wh.signingSecret); }}
          onClose={() => setAddOpen(false)}
        />
      )}

      {webhooks.length === 0 ? (
        <p style={{ color: '#888', fontSize: '0.85rem', margin: '0 0 8px' }}>No webhook endpoints yet.</p>
      ) : (
        <table style={m.table}>
          <thead>
            <tr>
              {['Name', 'Target URL', 'Events', 'Secret prefix', 'Status', ''].map(h => (
                <th key={h} style={m.th}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {webhooks.map(w => (
              <tr key={w.id}>
                <td style={m.td}>{w.name}</td>
                <td style={{ ...m.td, fontFamily: 'monospace', fontSize: '0.8rem' }}>{w.targetUrl}</td>
                <td style={{ ...m.td, fontFamily: 'monospace', fontSize: '0.78rem', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {w.eventTypes}
                </td>
                <td style={{ ...m.td, fontFamily: 'monospace' }}>{w.signingSecretPrefix}…</td>
                <td style={m.td}>
                  <span style={{ color: w.active ? '#177a17' : '#888', fontWeight: 600, fontSize: '0.8rem' }}>
                    {w.active ? 'Active' : 'Disabled'}
                  </span>
                </td>
                <td style={{ ...m.td, whiteSpace: 'nowrap' }}>
                  <button style={m.btnTiny} onClick={() => toggle(w.id, !w.active)}>
                    {w.active ? 'Disable' : 'Enable'}
                  </button>
                  {' '}
                  <button style={{ ...m.btnTiny, color: '#c00' }} onClick={() => del(w.id)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <button style={{ ...m.btnSecondary, marginTop: 8, fontSize: '0.82rem' }}
        onClick={() => setAddOpen(true)}>
        + Add webhook
      </button>
    </div>
  );
}

// ── Audit log panel ───────────────────────────────────────────────────────

function AuditPanel({ keyId }) {
  const [logs, setLogs]   = useState(null);
  const [page, setPage]   = useState(0);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState('');

  const load = useCallback((p) => {
    const url = keyId
      ? `${BASE}/integration-keys/${keyId}/audit?page=${p}&size=20&sort=createdAt,desc`
      : `${BASE}/integrations/audit?page=${p}&size=20&sort=createdAt,desc`;
    api('GET', url)
      .then(d => { setLogs(d.content); setTotal(d.totalPages); })
      .catch(e => setError(e.message));
  }, [keyId]);

  useEffect(() => { load(0); }, [load]);

  if (!logs) return <div style={{ padding: '0.5rem', color: '#888', fontSize: '0.85rem' }}>Loading…</div>;

  return (
    <div style={{ marginTop: 8 }}>
      {error && <div style={m.errorBanner}>{error}</div>}
      {logs.length === 0 ? (
        <p style={{ color: '#888', fontSize: '0.85rem' }}>No activity yet.</p>
      ) : (
        <table style={m.table}>
          <thead>
            <tr>
              {['Time', 'Dir', 'Event', 'IP / URL', 'Status', 'Result'].map(h => (
                <th key={h} style={m.th}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {logs.map(l => (
              <tr key={l.id} style={{ background: l.success ? 'transparent' : '#fff5f5' }}>
                <td style={{ ...m.td, whiteSpace: 'nowrap', fontSize: '0.78rem' }}>{fmtDate(l.createdAt)}</td>
                <td style={m.td}>
                  <span style={{
                    display: 'inline-block', padding: '1px 6px', borderRadius: 3, fontSize: '0.72rem',
                    fontWeight: 700, background: l.direction === 'INBOUND' ? '#e8f0fe' : '#fff3cd',
                    color: l.direction === 'INBOUND' ? '#0055cc' : '#856404',
                  }}>{l.direction}</span>
                </td>
                <td style={{ ...m.td, fontFamily: 'monospace', fontSize: '0.78rem' }}>{l.eventType || '—'}</td>
                <td style={{ ...m.td, fontFamily: 'monospace', fontSize: '0.78rem', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {l.sourceIp || l.targetUrl || '—'}
                </td>
                <td style={m.td}>{l.httpStatus ?? '—'}</td>
                <td style={{ ...m.td, fontSize: '0.78rem', color: l.success ? '#177a17' : '#c00' }}>
                  {l.success ? 'OK' : (l.errorMessage || 'Failed')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {total > 1 && (
        <div style={{ display: 'flex', gap: 8, marginTop: 8, alignItems: 'center' }}>
          <button style={m.btnTiny} disabled={page === 0} onClick={() => { setPage(p => p - 1); load(page - 1); }}>
            Prev
          </button>
          <span style={{ fontSize: '0.82rem', color: '#555' }}>Page {page + 1} of {total}</span>
          <button style={m.btnTiny} disabled={page >= total - 1} onClick={() => { setPage(p => p + 1); load(page + 1); }}>
            Next
          </button>
        </div>
      )}
    </div>
  );
}

// ── Key row ───────────────────────────────────────────────────────────────

function KeyRow({ keyData, onRevoked }) {
  const [expanded, setExpanded] = useState(false);
  const [tab, setTab]           = useState('webhooks');
  const [revoking, setRevoking] = useState(false);
  const [reason, setReason]     = useState('');
  const [error, setError]       = useState('');

  async function revoke() {
    if (!window.confirm(`Revoke key "${keyData.name}"? This cannot be undone.`)) return;
    setRevoking(false);
    try {
      const updated = await api('POST',
        `${BASE}/integration-keys/${keyData.id}/revoke`, { reason: reason.trim() || null });
      onRevoked(updated);
    } catch (e) { setError(e.message); }
  }

  const status = !keyData.active
    ? <span style={{ color: '#888', fontSize: '0.8rem', fontWeight: 600 }}>Revoked</span>
    : <span style={{ color: '#177a17', fontSize: '0.8rem', fontWeight: 600 }}>Active</span>;

  return (
    <>
      <tr style={{ background: expanded ? '#f5f7ff' : 'white' }}>
        <td style={m.td}>
          <button style={m.expandBtn} onClick={() => setExpanded(e => !e)}>
            {expanded ? '▾' : '▸'}
          </button>
          {keyData.name}
        </td>
        <td style={{ ...m.td, fontFamily: 'monospace', fontSize: '0.82rem' }}>{keyData.keyId}</td>
        <td style={{ ...m.td, fontFamily: 'monospace' }}>{keyData.secretPrefix}…</td>
        <td style={m.td}>{status}</td>
        <td style={{ ...m.td, fontSize: '0.82rem' }}>{keyData.createdByUsername || '—'}</td>
        <td style={{ ...m.td, fontSize: '0.8rem' }}>{fmtDate(keyData.lastUsedAt)}</td>
        <td style={m.td}>
          {keyData.active && (
            <button style={{ ...m.btnTiny, color: '#c00' }} onClick={() => setRevoking(true)}>
              Revoke
            </button>
          )}
        </td>
      </tr>

      {revoking && (
        <tr>
          <td colSpan={7} style={{ padding: '0.5rem 1rem', background: '#fff5f5', borderBottom: '1px solid #f0c0c0' }}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input style={{ ...m.input, flex: 1, marginBottom: 0 }}
                placeholder="Reason for revocation (optional)"
                value={reason} onChange={e => setReason(e.target.value)} />
              <button style={{ ...m.btnPrimary, background: '#c00', borderColor: '#c00' }} onClick={revoke}>
                Confirm revoke
              </button>
              <button style={m.btnSecondary} onClick={() => setRevoking(false)}>Cancel</button>
            </div>
            {error && <div style={{ color: '#c00', fontSize: '0.82rem', marginTop: 4 }}>{error}</div>}
          </td>
        </tr>
      )}

      {expanded && (
        <tr>
          <td colSpan={7} style={{ padding: '0.75rem 1.5rem 1rem', background: '#f8f9ff', borderBottom: '1px solid #e0e4f0' }}>
            {keyData.description && (
              <p style={{ margin: '0 0 0.5rem', fontSize: '0.85rem', color: '#555' }}>{keyData.description}</p>
            )}
            {keyData.allowedEvents && (
              <p style={{ margin: '0 0 0.75rem', fontSize: '0.82rem', color: '#555' }}>
                <strong>Allowed events:</strong> <code style={{ fontSize: '0.8rem' }}>{keyData.allowedEvents}</code>
              </p>
            )}
            <div style={{ display: 'flex', gap: 0, marginBottom: 12, borderBottom: '1px solid #dde' }}>
              {['webhooks', 'audit'].map(t => (
                <button key={t} style={{ ...m.tabBtn, ...(tab === t ? m.tabBtnActive : {}) }}
                  onClick={() => setTab(t)}>
                  {t === 'webhooks' ? 'Webhook Endpoints' : 'Audit Log'}
                </button>
              ))}
            </div>
            {tab === 'webhooks' && <WebhookPanel keyId={keyData.id} />}
            {tab === 'audit'    && <AuditPanel   keyId={keyData.id} />}
          </td>
        </tr>
      )}
    </>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────

export default function IntegrationKeysPage() {
  const [keys, setKeys]         = useState(null);
  const [page, setPage]         = useState(0);
  const [total, setTotal]       = useState(0);
  const [error, setError]       = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [newSecret, setNewSecret]   = useState(null); // { name, secret }
  const [allAuditOpen, setAllAuditOpen] = useState(false);

  const load = useCallback((p) => {
    api('GET', `${BASE}/integration-keys?page=${p}&size=20&sort=createdAt,desc`)
      .then(d => { setKeys(d.content); setTotal(d.totalPages); })
      .catch(e => setError(e.message));
  }, []);

  useEffect(() => { load(0); }, [load]);

  function handleCreated(key) {
    setCreateOpen(false);
    setNewSecret({ name: key.name, secret: key.secret });
    setKeys(prev => [key, ...(prev || [])]);
  }

  function handleRevoked(updated) {
    setKeys(prev => prev.map(k => k.id === updated.id ? updated : k));
  }

  return (
    <main style={{ padding: '2rem', maxWidth: 1100 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
        <div>
          <h2 style={{ margin: 0 }}>Integration Keys</h2>
          <p style={{ margin: '4px 0 0', color: '#666', fontSize: '0.9rem' }}>
            Manage HMAC-authenticated API keys for local on-prem device integrations.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button style={m.btnSecondary} onClick={() => setAllAuditOpen(a => !a)}>
            {allAuditOpen ? 'Hide global audit' : 'Global audit log'}
          </button>
          <button style={m.btnPrimary} onClick={() => setCreateOpen(true)}>
            + New key
          </button>
        </div>
      </div>

      {error && <div style={m.errorBanner}>{error}</div>}

      {newSecret && (
        <SecretRevealModal
          title={`Key created: ${newSecret.name}`}
          label="integration key secret"
          secret={newSecret.secret}
          onClose={() => setNewSecret(null)}
        />
      )}
      {createOpen && (
        <CreateKeyModal onCreated={handleCreated} onClose={() => setCreateOpen(false)} />
      )}

      {allAuditOpen && (
        <div style={{ marginBottom: '1.5rem', padding: '1rem', border: '1px solid #dde', borderRadius: 6, background: '#fafbff' }}>
          <h3 style={{ margin: '0 0 0.75rem', fontSize: '1rem' }}>Global Audit Log</h3>
          <AuditPanel keyId={null} />
        </div>
      )}

      {!keys ? (
        <p style={{ color: '#888' }}>Loading…</p>
      ) : keys.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#888', border: '1px dashed #ccc', borderRadius: 6 }}>
          No integration keys yet. Create one to get started.
        </div>
      ) : (
        <>
          <table style={m.table}>
            <thead>
              <tr>
                {['Name / Key ID', 'Key ID', 'Secret prefix', 'Status', 'Created by', 'Last used', ''].map(h => (
                  <th key={h} style={m.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {keys.map(k => (
                <KeyRow key={k.id} keyData={k} onRevoked={handleRevoked} />
              ))}
            </tbody>
          </table>

          {total > 1 && (
            <div style={{ display: 'flex', gap: 8, marginTop: 12, alignItems: 'center' }}>
              <button style={m.btnSecondary} disabled={page === 0}
                onClick={() => { const p = page - 1; setPage(p); load(p); }}>Prev</button>
              <span style={{ fontSize: '0.85rem', color: '#555' }}>Page {page + 1} of {total}</span>
              <button style={m.btnSecondary} disabled={page >= total - 1}
                onClick={() => { const p = page + 1; setPage(p); load(p); }}>Next</button>
            </div>
          )}
        </>
      )}
    </main>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────

const m = {
  overlay: {
    position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)',
    display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
  },
  modal: {
    background: 'white', borderRadius: 8, padding: '1.5rem',
    width: '95%', boxShadow: '0 8px 32px rgba(0,0,0,0.18)',
  },
  label: { display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#333', marginBottom: 4, marginTop: 12 },
  hint:  { fontWeight: 400, color: '#777', fontSize: '0.8rem' },
  input: {
    display: 'block', width: '100%', boxSizing: 'border-box',
    padding: '0.4rem 0.6rem', border: '1px solid #ccc', borderRadius: 4,
    fontSize: '0.9rem', marginBottom: 4,
  },
  errorBanner: {
    background: '#fff0f0', border: '1px solid #f0c0c0', color: '#c00',
    borderRadius: 4, padding: '0.5rem 0.75rem', marginBottom: 12, fontSize: '0.85rem',
  },
  warningBanner: {
    background: '#fff8e1', border: '1px solid #ffe082', borderRadius: 4,
    padding: '0.5rem 0.75rem', marginBottom: 12, fontSize: '0.85rem', color: '#5d4037',
  },
  secretBox: {
    background: '#f5f5f5', border: '1px solid #ddd', borderRadius: 4,
    padding: '0.75rem', fontFamily: 'monospace', fontSize: '0.85rem',
  },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' },
  th: {
    textAlign: 'left', padding: '0.5rem 0.75rem',
    borderBottom: '2px solid #e0e4f0', fontWeight: 600, color: '#444', fontSize: '0.82rem',
    background: '#f5f7ff',
  },
  td: { padding: '0.5rem 0.75rem', borderBottom: '1px solid #eee', verticalAlign: 'middle' },
  btnPrimary: {
    padding: '0.45rem 1rem', background: '#0055cc', color: 'white',
    border: '1px solid #0055cc', borderRadius: 4, cursor: 'pointer',
    fontWeight: 600, fontSize: '0.88rem',
  },
  btnSecondary: {
    padding: '0.45rem 1rem', background: 'white', color: '#333',
    border: '1px solid #ccc', borderRadius: 4, cursor: 'pointer', fontSize: '0.88rem',
  },
  btnTiny: {
    padding: '2px 8px', background: 'white', color: '#333',
    border: '1px solid #ccc', borderRadius: 3, cursor: 'pointer', fontSize: '0.78rem',
  },
  expandBtn: {
    background: 'none', border: 'none', cursor: 'pointer',
    fontSize: '0.9rem', marginRight: 6, color: '#555', padding: 0,
  },
  tabBtn: {
    background: 'none', border: 'none', borderBottom: '2px solid transparent',
    padding: '6px 14px', cursor: 'pointer', fontSize: '0.85rem', color: '#555',
  },
  tabBtnActive: {
    borderBottom: '2px solid #0055cc', color: '#0055cc', fontWeight: 600,
  },
};
