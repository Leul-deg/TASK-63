import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';

// ── Priority config ────────────────────────────────────────────────────────────
const PRIORITY_META = {
  CRITICAL: { label: 'Critical', bg: '#fce8e6', text: '#c00',   border: '#f5c6cb' },
  HIGH:     { label: 'High',     bg: '#fef7e0', text: '#856404', border: '#ffd97d' },
  NORMAL:   { label: 'Normal',   bg: '#e8f0fe', text: '#1a73e8', border: '#c5d9fb' },
  LOW:      { label: 'Low',      bg: '#f1f3f4', text: '#555',   border: '#ddd'    },
};

const CATEGORY_LABELS = {
  GENERAL:     'General',
  ONBOARDING:  'Onboarding',
  APPOINTMENT: 'Appointment',
  SETTLEMENT:  'Settlement',
  ARBITRATION: 'Arbitration',
};

// ── Acknowledgment Modal ───────────────────────────────────────────────────────
function AcknowledgeModal({ notification, onAcknowledged, onClose }) {
  const [busy, setBusy]   = useState(false);
  const [error, setError] = useState('');

  const confirm = async () => {
    setBusy(true); setError('');
    try {
      const updated = await api.post(`/api/notifications/${notification.id}/acknowledge`, {});
      onAcknowledged(updated);
    } catch (e) {
      setError(e.body?.message || e.message || 'Failed to record acknowledgment.');
    } finally { setBusy(false); }
  };

  const meta = PRIORITY_META[notification.priority] || PRIORITY_META.NORMAL;

  return (
    <div style={overlayStyle}>
      <div style={{ ...modalStyle, maxWidth: 520 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
          <span style={{ fontSize: '1.5rem' }}>⚠️</span>
          <div>
            <div style={{ fontWeight: 700, fontSize: '1rem' }}>Acknowledgment Required</div>
            <span style={priorityBadge(meta)}>
              {meta.label} — {CATEGORY_LABELS[notification.category] || notification.category}
            </span>
          </div>
        </div>

        <h3 style={{ margin: '0 0 8px', fontSize: '1.05rem' }}>{notification.title}</h3>
        <p style={{ color: '#444', fontSize: '0.9rem', lineHeight: 1.6,
                    whiteSpace: 'pre-wrap', margin: '0 0 16px' }}>
          {notification.body}
        </p>

        <div style={{ background: '#fef7e0', border: '1px solid #ffd97d', borderRadius: 6,
                      padding: '10px 14px', marginBottom: 16, fontSize: '0.85rem', color: '#6d4c00' }}>
          By clicking <strong>"I Acknowledge Receipt"</strong> below, you confirm that you have
          read and understood the contents of this notification. This action will be recorded
          for audit purposes.
        </div>

        {error && <p style={{ color: '#c00', fontSize: '0.85rem', marginBottom: 8 }}>{error}</p>}

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button style={btnSecondary} onClick={onClose} disabled={busy}>
            Read Later
          </button>
          <button style={{ ...btnPrimary, background: '#c00' }} onClick={confirm} disabled={busy}>
            {busy ? 'Recording…' : 'I Acknowledge Receipt'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Notification Card ──────────────────────────────────────────────────────────
function NotificationCard({ notif, onMarkRead, onAcknowledge }) {
  const meta = PRIORITY_META[notif.priority] || PRIORITY_META.NORMAL;
  const needsAck = notif.requiresAcknowledgment && !notif.acknowledged;

  return (
    <div style={{
      border: `1px solid ${notif.read ? '#eee' : meta.border}`,
      borderLeft: `4px solid ${meta.text}`,
      borderRadius: 8,
      padding: '14px 16px',
      background: notif.read ? '#fff' : meta.bg,
      marginBottom: 10,
      transition: 'background 0.2s',
    }}>
      {/* Header row */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginBottom: 4 }}>
            {!notif.read && <span style={unreadDot} title="Unread" />}
            <span style={priorityBadge(meta)}>{meta.label}</span>
            <span style={categoryBadge}>
              {CATEGORY_LABELS[notif.category] || notif.category}
            </span>
            {needsAck && <span style={ackRequiredBadge}>Action Required</span>}
            {notif.acknowledged && <span style={ackedBadge}>✓ Acknowledged</span>}
          </div>
          <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: notif.read ? 500 : 700,
                       color: '#111', wordBreak: 'break-word' }}>
            {notif.title}
          </h4>
        </div>
        <time style={{ fontSize: '0.72rem', color: '#888', whiteSpace: 'nowrap', marginTop: 2 }}>
          {new Date(notif.createdAt).toLocaleString([], {
            month: 'short', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
          })}
        </time>
      </div>

      {/* Body */}
      {notif.body && (
        <p style={{ margin: '8px 0 0', fontSize: '0.88rem', color: '#333',
                    lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
          {notif.body}
        </p>
      )}

      {/* Actions */}
      <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
        {!notif.read && (
          <button style={btnSmall} onClick={() => onMarkRead(notif.id)}>
            Mark as read
          </button>
        )}
        {needsAck && (
          <button style={{ ...btnSmall, background: '#c00', color: '#fff',
                           border: '1px solid #c00', fontWeight: 700 }}
                  onClick={() => onAcknowledge(notif)}>
            Acknowledge ↗
          </button>
        )}
        {notif.acknowledged && notif.acknowledgedAt && (
          <span style={{ fontSize: '0.75rem', color: '#888', alignSelf: 'center' }}>
            Acknowledged {new Date(notif.acknowledgedAt).toLocaleDateString()}
          </span>
        )}
      </div>
    </div>
  );
}

// ── Send Template Panel (staff only) ──────────────────────────────────────────
function SendPanel({ templates, onSent }) {
  const [key, setKey]         = useState('');
  const [vars, setVars]       = useState({});
  const [recipients, setRec]  = useState('');
  const [query, setQuery]     = useState('');
  const [userResults, setUR]  = useState([]);
  const [selected, setSel]    = useState([]);
  const [error, setError]     = useState('');
  const [busy, setBusy]       = useState(false);

  const tpl = templates.find(t => t.templateKey === key);

  // Detect placeholder keys from the chosen template
  const placeholders = tpl
    ? [...new Set([
        ...[...tpl.titlePattern.matchAll(/\{\{(\w+)\}\}/g)].map(m => m[1]),
        ...[...tpl.bodyPattern.matchAll(/\{\{(\w+)\}\}/g)].map(m => m[1]),
      ])]
    : [];

  useEffect(() => {
    if (query.length < 2) { setUR([]); return; }
    const t = setTimeout(() => {
      api.get(`/api/messages/users?q=${encodeURIComponent(query)}`).then(setUR).catch(() => {});
    }, 300);
    return () => clearTimeout(t);
  }, [query]);

  const toggleUser = (u) =>
    setSel(s => s.find(x => x.id === u.id) ? s.filter(x => x.id !== u.id) : [...s, u]);

  const submit = async () => {
    if (!key) return setError('Choose a template.');
    if (!selected.length) return setError('Select at least one recipient.');
    setBusy(true); setError('');
    try {
      const res = await api.post('/api/notifications/send', {
        templateKey: key,
        recipientIds: selected.map(u => u.id),
        variables: vars,
      });
      setSel([]); setKey(''); setVars({}); setQuery(''); setUR([]);
      onSent(res.sent);
    } catch (e) {
      setError(e.body?.message || e.message || 'Failed to send.');
    } finally { setBusy(false); }
  };

  return (
    <div style={{ background: '#fafafa', border: '1px solid #eee', borderRadius: 8,
                  padding: '1rem', marginBottom: '1.5rem' }}>
      <h4 style={{ margin: '0 0 12px', fontSize: '0.95rem' }}>Send Notification</h4>

      <label style={lbl}>Template</label>
      <select style={inp} value={key} onChange={e => { setKey(e.target.value); setVars({}); }}>
        <option value="">— Choose a template —</option>
        {templates.map(t => (
          <option key={t.templateKey} value={t.templateKey}>
            [{t.category}] {t.templateKey}
            {t.requiresAcknowledgment ? ' ⚠ ack required' : ''}
          </option>
        ))}
      </select>

      {tpl && (
        <div style={{ fontSize: '0.8rem', color: '#666', marginBottom: 8, marginTop: 4 }}>
          Priority: <strong>{tpl.defaultPriority}</strong>
          {tpl.description && ` — ${tpl.description}`}
        </div>
      )}

      {placeholders.length > 0 && (
        <>
          <label style={lbl}>Template Variables</label>
          {placeholders.map(p => (
            <div key={p} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
              <span style={{ width: 160, fontSize: '0.82rem', color: '#555',
                             fontFamily: 'monospace' }}>
                {`{{${p}}}`}
              </span>
              <input style={{ ...inp, flex: 1, padding: '5px 8px' }}
                     placeholder={`Value for ${p}`}
                     value={vars[p] || ''}
                     onChange={e => setVars(v => ({ ...v, [p]: e.target.value }))} />
            </div>
          ))}
        </>
      )}

      <label style={lbl}>Recipients</label>
      <input style={inp} value={query} onChange={e => setQuery(e.target.value)}
             placeholder="Search by name or username…" />
      {userResults.length > 0 && (
        <ul style={sugList}>
          {userResults.map(u => (
            <li key={u.id} style={sugItem(selected.some(x => x.id === u.id))}
                onClick={() => toggleUser(u)}>
              {u.displayName} <span style={{ color: '#888' }}>@{u.username}</span>
            </li>
          ))}
        </ul>
      )}
      {selected.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 4 }}>
          {selected.map(u => (
            <span key={u.id} style={chip}>
              {u.displayName}
              <button style={chipX} onClick={() => toggleUser(u)}>×</button>
            </span>
          ))}
        </div>
      )}

      {error && <p style={{ color: '#c00', fontSize: '0.82rem', marginTop: 6 }}>{error}</p>}
      <button style={{ ...btnPrimary, marginTop: 12 }} onClick={submit} disabled={busy}>
        {busy ? 'Sending…' : `Send to ${selected.length || '?'} recipient(s)`}
      </button>
    </div>
  );
}

// ── Main Page ──────────────────────────────────────────────────────────────────
export default function NotificationsPage() {
  const { user } = useAuth();
  const isStaff = user?.roles?.some(r =>
    ['ADMIN','HOUSING_ADMINISTRATOR','DIRECTOR','RESIDENT_DIRECTOR',
     'RESIDENT_ASSISTANT','RESIDENCE_STAFF','STAFF'].includes(r));

  const [notifications, setNotifications] = useState([]);
  const [totalPages, setTotalPages]       = useState(0);
  const [page, setPage]                   = useState(0);
  const [unreadOnly, setUnreadOnly]       = useState(false);
  const [category, setCategory]           = useState('');
  const [loading, setLoading]             = useState(true);
  const [error, setError]                 = useState('');
  const [ackTarget, setAckTarget]         = useState(null);  // notification to acknowledge
  const [templates, setTemplates]         = useState([]);
  const [showSend, setShowSend]           = useState(false);
  const [sentToast, setSentToast]         = useState('');

  const loadInbox = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const params = new URLSearchParams({
        unreadOnly: unreadOnly,
        page,
        size: 20,
        ...(category ? { category } : {}),
      });
      const data = await api.get(`/api/notifications?${params}`);
      setNotifications(data.content || []);
      setTotalPages(data.totalPages || 0);
    } catch (e) {
      setError('Failed to load notifications.');
    } finally { setLoading(false); }
  }, [unreadOnly, category, page]);

  useEffect(() => { loadInbox(); }, [loadInbox]);

  useEffect(() => {
    if (isStaff) {
      api.get('/api/notifications/templates').then(setTemplates).catch(() => {});
    }
  }, [isStaff]);

  // Auto-open acknowledgment modal for the first pending critical item
  useEffect(() => {
    if (!ackTarget) {
      const pending = notifications.find(n => n.requiresAcknowledgment && !n.acknowledged);
      if (pending && pending.priority === 'CRITICAL') {
        setAckTarget(pending);
      }
    }
  }, [notifications, ackTarget]);

  const markRead = async (id) => {
    try {
      const updated = await api.post(`/api/notifications/${id}/read`, {});
      setNotifications(ns => ns.map(n => n.id === id ? updated : n));
    } catch (_) {}
  };

  const markAllRead = async () => {
    try {
      await api.post('/api/notifications/read-all', {});
      setNotifications(ns => ns.map(n => ({ ...n, read: true })));
    } catch (_) {}
  };

  const onAcknowledged = (updated) => {
    setNotifications(ns => ns.map(n => n.id === updated.id ? updated : n));
    setAckTarget(null);
  };

  const unreadCount = notifications.filter(n => !n.read).length;
  const pendingCount = notifications.filter(n => n.requiresAcknowledgment && !n.acknowledged).length;

  return (
    <main style={{ padding: '1.5rem', maxWidth: 780, margin: '0 auto' }}>
      {/* Page header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
                    marginBottom: '1rem', flexWrap: 'wrap', gap: 8 }}>
        <div>
          <h2 style={{ margin: 0 }}>Notifications</h2>
          {(unreadCount > 0 || pendingCount > 0) && (
            <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: '#555' }}>
              {unreadCount > 0 && <span>{unreadCount} unread</span>}
              {unreadCount > 0 && pendingCount > 0 && ' · '}
              {pendingCount > 0 && (
                <span style={{ color: '#c00', fontWeight: 600 }}>
                  {pendingCount} require acknowledgment
                </span>
              )}
            </p>
          )}
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          {unreadCount > 0 && (
            <button style={btnSecondary} onClick={markAllRead}>Mark all as read</button>
          )}
          {isStaff && (
            <button style={btnPrimary} onClick={() => setShowSend(v => !v)}>
              {showSend ? 'Hide' : '+ Send Notification'}
            </button>
          )}
        </div>
      </div>

      {/* Send panel (staff) */}
      {isStaff && showSend && (
        <SendPanel
          templates={templates}
          onSent={(n) => {
            setSentToast(`Sent to ${n} recipient(s).`);
            setTimeout(() => setSentToast(''), 4000);
            loadInbox();
          }}
        />
      )}

      {sentToast && (
        <div style={{ background: '#d4edda', border: '1px solid #c3e6cb', borderRadius: 6,
                      padding: '8px 14px', marginBottom: 12, fontSize: '0.88rem', color: '#155724' }}>
          {sentToast}
        </div>
      )}

      {/* Filters */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 14, flexWrap: 'wrap', alignItems: 'center' }}>
        <label style={{ display: 'flex', alignItems: 'center', gap: 5,
                        fontSize: '0.85rem', cursor: 'pointer' }}>
          <input type="checkbox" checked={unreadOnly}
                 onChange={e => { setUnreadOnly(e.target.checked); setPage(0); }} />
          Unread only
        </label>

        <select style={{ ...inp, width: 'auto', padding: '5px 8px', fontSize: '0.85rem' }}
                value={category}
                onChange={e => { setCategory(e.target.value); setPage(0); }}>
          <option value="">All categories</option>
          {Object.entries(CATEGORY_LABELS).map(([k, v]) => (
            <option key={k} value={k}>{v}</option>
          ))}
        </select>

        <button style={btnSmall} onClick={() => loadInbox()}>Refresh</button>
      </div>

      {/* Pending acknowledgment alert */}
      {pendingCount > 0 && (
        <div style={{ background: '#fce8e6', border: '1px solid #f5c6cb', borderRadius: 8,
                      padding: '10px 14px', marginBottom: 14, display: 'flex',
                      alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: '1.2rem' }}>⚠️</span>
          <div style={{ flex: 1 }}>
            <strong style={{ color: '#c00' }}>
              {pendingCount} notification{pendingCount > 1 ? 's' : ''} require{pendingCount === 1 ? 's' : ''} your acknowledgment.
            </strong>
            <p style={{ margin: '2px 0 0', fontSize: '0.82rem', color: '#6d2323' }}>
              Review each item marked "Action Required" and click "Acknowledge" to confirm receipt.
            </p>
          </div>
        </div>
      )}

      {/* Notification list */}
      {loading ? (
        <p style={{ color: '#888' }}>Loading…</p>
      ) : error ? (
        <p style={{ color: '#c00' }}>{error}</p>
      ) : notifications.length === 0 ? (
        <div style={{ textAlign: 'center', color: '#888', padding: '3rem 0' }}>
          <p style={{ fontSize: '2rem', margin: 0 }}>🔔</p>
          <p>{unreadOnly ? 'No unread notifications.' : 'No notifications yet.'}</p>
        </div>
      ) : (
        notifications.map(n => (
          <NotificationCard
            key={n.id}
            notif={n}
            onMarkRead={markRead}
            onAcknowledge={setAckTarget}
          />
        ))
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', gap: 6, marginTop: 16 }}>
          <button style={btnSmall} disabled={page === 0} onClick={() => setPage(p => p - 1)}>
            ← Prev
          </button>
          <span style={{ fontSize: '0.85rem', color: '#555', alignSelf: 'center' }}>
            Page {page + 1} of {totalPages}
          </span>
          <button style={btnSmall} disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
            Next →
          </button>
        </div>
      )}

      {/* Acknowledgment modal */}
      {ackTarget && (
        <AcknowledgeModal
          notification={ackTarget}
          onAcknowledged={onAcknowledged}
          onClose={() => setAckTarget(null)}
        />
      )}
    </main>
  );
}

// ── Styles ─────────────────────────────────────────────────────────────────────
const lbl        = { display: 'block', fontSize: '0.82rem', fontWeight: 600, color: '#444', marginBottom: 3, marginTop: 10 };
const inp        = { width: '100%', boxSizing: 'border-box', padding: '7px 10px', border: '1px solid #ccc', borderRadius: 6, fontSize: '0.88rem', fontFamily: 'inherit' };
const btnPrimary = { background: '#1a73e8', color: '#fff', border: 'none', borderRadius: 6, padding: '7px 14px', cursor: 'pointer', fontSize: '0.85rem', fontWeight: 600 };
const btnSecondary={ background: 'none', color: '#555', border: '1px solid #ddd', borderRadius: 6, padding: '6px 12px', cursor: 'pointer', fontSize: '0.85rem' };
const btnSmall   = { background: '#f1f3f4', color: '#333', border: '1px solid #ddd', borderRadius: 4, padding: '4px 10px', cursor: 'pointer', fontSize: '0.8rem' };
const overlayStyle= { position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 };
const modalStyle = { background: '#fff', borderRadius: 10, padding: '1.5rem', width: 460, maxWidth: '95vw', maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 4px 24px rgba(0,0,0,0.2)' };
const sugList    = { listStyle: 'none', margin: '3px 0 6px', padding: 0, border: '1px solid #ddd', borderRadius: 6, maxHeight: 140, overflowY: 'auto' };
const sugItem    = (sel) => ({ padding: '7px 10px', cursor: 'pointer', background: sel ? '#e8f0fe' : '#fff', borderBottom: '1px solid #f0f0f0' });
const chip       = { display: 'inline-flex', alignItems: 'center', gap: 4, background: '#e8f0fe', color: '#1a73e8', borderRadius: 12, padding: '2px 8px', fontSize: '0.8rem' };
const chipX      = { background: 'none', border: 'none', cursor: 'pointer', color: '#1a73e8', fontSize: '1rem', lineHeight: 1, padding: 0 };
const unreadDot  = { width: 8, height: 8, borderRadius: '50%', background: '#1a73e8', display: 'inline-block', flexShrink: 0 };
const ackRequiredBadge= { background: '#fce8e6', color: '#c00', border: '1px solid #f5c6cb', borderRadius: 4, padding: '1px 6px', fontSize: '0.7rem', fontWeight: 700 };
const ackedBadge = { background: '#d4edda', color: '#155724', border: '1px solid #c3e6cb', borderRadius: 4, padding: '1px 6px', fontSize: '0.7rem', fontWeight: 600 };
const categoryBadge={ background: '#f1f3f4', color: '#555', borderRadius: 4, padding: '1px 6px', fontSize: '0.7rem', fontWeight: 600 };
const priorityBadge = (meta) => ({
  display: 'inline-block', background: meta.bg, color: meta.text,
  border: `1px solid ${meta.border}`, borderRadius: 4,
  padding: '1px 6px', fontSize: '0.7rem', fontWeight: 700,
});
