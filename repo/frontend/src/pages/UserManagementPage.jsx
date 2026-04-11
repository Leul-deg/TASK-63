import React, { useState, useEffect, useCallback } from 'react';
import { api as client } from '../api/client';

const STATUSES = ['ACTIVE', 'DISABLED', 'FROZEN', 'BLACKLISTED'];

const STATUS_STYLE = {
  ACTIVE:      { background: '#e6f4ea', color: '#1e7e34' },
  DISABLED:    { background: '#f0f0f0', color: '#555' },
  FROZEN:      { background: '#e8f4ff', color: '#0055cc' },
  BLACKLISTED: { background: '#fff0f0', color: '#c00' },
};

function fmtDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function StatusBadge({ status }) {
  const s = STATUS_STYLE[status] || { background: '#f0f0f0', color: '#333' };
  return (
    <span style={{
      display: 'inline-block', padding: '2px 8px', borderRadius: 4,
      fontSize: '0.75rem', fontWeight: 700, ...s,
    }}>
      {status}
    </span>
  );
}

// ── Per-row component with inline status-change panel ─────────────────────

function UserRow({ user, onUpdated, onDeleted }) {
  const [expanded, setExpanded] = useState(false);
  const [newStatus, setNewStatus] = useState(user.accountStatus);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  // Keep local status selector in sync if parent updates the user
  useEffect(() => { setNewStatus(user.accountStatus); }, [user.accountStatus]);

  async function saveStatus() {
    setSaving(true);
    setError('');
    try {
      // PATCH returns LoginResponse (no status fields), so we update local state
      // directly from the values we sent rather than the server response.
      await client.patch(`/api/admin/users/${user.id}/status`, {
        status: newStatus,
        reason: reason.trim() || null,
      });
      onUpdated({ ...user, accountStatus: newStatus, statusReason: reason.trim() || null });
      setExpanded(false);
      setReason('');
    } catch (e) {
      setError(e.message);
    } finally {
      setSaving(false);
    }
  }

  async function doDelete() {
    if (!window.confirm(
      `Soft-delete "${user.username}"? The account will be disabled immediately and permanently removed after 30 days.`
    )) return;
    setSaving(true);
    try {
      await client.delete(`/api/admin/users/${user.id}`);
      onDeleted(user.id);
    } catch (e) {
      setError(e.message);
      setSaving(false);
    }
  }

  const pendingPurge = user.deleted && user.scheduledPurgeAt;

  return (
    <>
      <tr style={{ background: user.deleted ? '#fff8f8' : (expanded ? '#f5f7ff' : 'white') }}>
        <td style={m.td}>
          <div style={{ fontWeight: 500, fontSize: '0.88rem' }}>{user.username}</div>
          <div style={{ fontSize: '0.78rem', color: '#666' }}>{user.email}</div>
        </td>
        <td style={m.td}>
          {user.firstName || user.lastName
            ? `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim()
            : <span style={{ color: '#aaa' }}>—</span>}
        </td>
        <td style={m.td}>
          <StatusBadge status={user.accountStatus} />
          {pendingPurge && (
            <div style={{ fontSize: '0.72rem', color: '#a00', marginTop: 3 }}>
              Purge: {fmtDate(user.scheduledPurgeAt)}
            </div>
          )}
          {user.statusReason && (
            <div style={{ fontSize: '0.72rem', color: '#666', marginTop: 2, fontStyle: 'italic' }}>
              {user.statusReason.length > 60
                ? user.statusReason.slice(0, 57) + '…'
                : user.statusReason}
            </div>
          )}
        </td>
        <td style={{ ...m.td, fontSize: '0.78rem', color: '#444' }}>
          {user.roles && user.roles.length > 0
            ? [...user.roles].sort().join(', ')
            : <span style={{ color: '#aaa' }}>—</span>}
        </td>
        <td style={{ ...m.td, fontSize: '0.8rem', color: '#555', whiteSpace: 'nowrap' }}>
          {fmtDate(user.createdAt)}
        </td>
        <td style={{ ...m.td, whiteSpace: 'nowrap' }}>
          {!user.deleted ? (
            <>
              <button style={m.btnTiny}
                onClick={() => { setExpanded(e => !e); setError(''); }}>
                {expanded ? 'Cancel' : 'Change status'}
              </button>
              {' '}
              <button style={{ ...m.btnTiny, color: '#c00' }}
                disabled={saving}
                onClick={doDelete}>
                Delete
              </button>
            </>
          ) : (
            <span style={{ fontSize: '0.78rem', color: '#999', fontStyle: 'italic' }}>
              Pending purge
            </span>
          )}
        </td>
      </tr>

      {expanded && (
        <tr>
          <td colSpan={6} style={{
            padding: '0.6rem 1rem 0.8rem',
            background: '#f5f7ff',
            borderBottom: '1px solid #e0e4f0',
          }}>
            {error && <div style={{ ...m.errorBanner, marginBottom: 8 }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
              <div>
                <label style={m.label}>New status</label>
                <select style={{ ...m.input, width: 160, marginBottom: 0 }}
                  value={newStatus}
                  onChange={e => setNewStatus(e.target.value)}>
                  {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <div style={{ flex: 1, minWidth: 220 }}>
                <label style={m.label}>Reason <span style={{ fontWeight: 400, color: '#777' }}>(optional, max 500 chars)</span></label>
                <input
                  style={{ ...m.input, marginBottom: 0 }}
                  maxLength={500}
                  placeholder="Reason for this status change"
                  value={reason}
                  onChange={e => setReason(e.target.value)}
                />
              </div>
              <button style={m.btnPrimary} disabled={saving} onClick={saveStatus}>
                {saving ? 'Saving…' : 'Save'}
              </button>
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────

export default function UserManagementPage() {
  const [users, setUsers]             = useState(null);
  const [totalPages, setTotalPages]   = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError]             = useState('');

  // Filter + pagination state
  const [inputQ, setInputQ]           = useState('');
  const [q, setQ]                     = useState('');           // debounced
  const [statusFilter, setStatusFilter] = useState('');
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [page, setPage]               = useState(0);

  // Debounce search input — also resets to page 0
  useEffect(() => {
    const t = setTimeout(() => { setQ(inputQ); setPage(0); }, 300);
    return () => clearTimeout(t);
  }, [inputQ]);

  const load = useCallback((p, searchQ, status, inclDel) => {
    const params = new URLSearchParams({ page: p, size: 20 });
    if (searchQ) params.set('q', searchQ);
    if (status)  params.set('status', status);
    if (inclDel) params.set('includeDeleted', 'true');

    setError('');
    client.get(`/api/admin/users?${params}`)
      .then(data => {
        setUsers(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
      })
      .catch(e => setError(e.message));
  }, []);

  useEffect(() => {
    load(page, q, statusFilter, includeDeleted);
  }, [load, page, q, statusFilter, includeDeleted]);

  function handleStatusFilter(val) {
    setStatusFilter(val);
    setPage(0);
  }

  function handleIncludeDeleted(val) {
    setIncludeDeleted(val);
    setPage(0);
  }

  function handleUpdated(updated) {
    setUsers(us => us.map(u => u.id === updated.id ? updated : u));
  }

  function handleDeleted(id) {
    if (!includeDeleted) {
      // Optimistically remove from list when not showing deleted accounts
      setUsers(us => us.filter(u => u.id !== id));
      setTotalElements(t => t - 1);
    } else {
      // Show as pending-purge inline (server will purge in 30 days)
      const purgeAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
      setUsers(us => us.map(u => u.id === id
        ? { ...u, deleted: true, accountStatus: 'DISABLED', scheduledPurgeAt: purgeAt }
        : u));
    }
  }

  return (
    <main style={{ padding: '2rem', maxWidth: 1100 }}>
      {/* Header */}
      <div style={{ marginBottom: '1.5rem' }}>
        <h2 style={{ margin: 0 }}>User Management</h2>
        <p style={{ margin: '4px 0 0', color: '#666', fontSize: '0.9rem' }}>
          Search accounts, change status (ACTIVE / DISABLED / FROZEN / BLACKLISTED), and initiate soft-deletion.
        </p>
      </div>

      {/* Filters */}
      <div style={{ display: 'flex', gap: 10, marginBottom: '1rem', flexWrap: 'wrap', alignItems: 'center' }}>
        <input
          style={{ ...m.input, width: 280, marginBottom: 0 }}
          placeholder="Search by name, username or email…"
          value={inputQ}
          onChange={e => setInputQ(e.target.value)}
        />
        <select
          style={{ ...m.input, width: 170, marginBottom: 0 }}
          value={statusFilter}
          onChange={e => handleStatusFilter(e.target.value)}>
          <option value="">All statuses</option>
          {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
        <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.85rem', color: '#555', cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={includeDeleted}
            onChange={e => handleIncludeDeleted(e.target.checked)}
          />
          Show pending-deletion
        </label>
        {users !== null && (
          <span style={{ marginLeft: 'auto', fontSize: '0.82rem', color: '#666' }}>
            {totalElements} user{totalElements !== 1 ? 's' : ''}
          </span>
        )}
      </div>

      {error && <div style={m.errorBanner}>{error}</div>}

      {users === null ? (
        <p style={{ color: '#888' }}>Loading…</p>
      ) : users.length === 0 ? (
        <div style={{
          textAlign: 'center', padding: '3rem', color: '#888',
          border: '1px dashed #ccc', borderRadius: 6,
        }}>
          No users found.
        </div>
      ) : (
        <>
          <table style={m.table}>
            <thead>
              <tr>
                {['Username / Email', 'Name', 'Status', 'Roles', 'Created', 'Actions'].map(h => (
                  <th key={h} style={m.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {users.map(u => (
                <UserRow
                  key={u.id}
                  user={u}
                  onUpdated={handleUpdated}
                  onDeleted={handleDeleted}
                />
              ))}
            </tbody>
          </table>

          {totalPages > 1 && (
            <div style={{ display: 'flex', gap: 8, marginTop: 12, alignItems: 'center' }}>
              <button style={m.btnSecondary} disabled={page === 0}
                onClick={() => setPage(p => p - 1)}>Prev</button>
              <span style={{ fontSize: '0.85rem', color: '#555' }}>
                Page {page + 1} of {totalPages}
              </span>
              <button style={m.btnSecondary} disabled={page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}>Next</button>
            </div>
          )}
        </>
      )}
    </main>
  );
}

// ── Styles (same vocabulary as other admin pages) ─────────────────────────

const m = {
  errorBanner: {
    background: '#fff0f0', border: '1px solid #f0c0c0', color: '#c00',
    borderRadius: 4, padding: '0.5rem 0.75rem', marginBottom: 12, fontSize: '0.85rem',
  },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' },
  th: {
    textAlign: 'left', padding: '0.5rem 0.75rem',
    borderBottom: '2px solid #e0e4f0', fontWeight: 600, color: '#444', fontSize: '0.82rem',
    background: '#f5f7ff',
  },
  td: { padding: '0.5rem 0.75rem', borderBottom: '1px solid #eee', verticalAlign: 'middle' },
  label: { display: 'block', fontSize: '0.82rem', fontWeight: 600, color: '#333', marginBottom: 2 },
  input: {
    display: 'block', width: '100%', boxSizing: 'border-box',
    padding: '0.4rem 0.6rem', border: '1px solid #ccc', borderRadius: 4,
    fontSize: '0.9rem', marginBottom: 4,
  },
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
};
