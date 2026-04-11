import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';

// ── Constants ──────────────────────────────────────────────────────────────────
const INBOX_POLL_MS   = 10_000;
const THREAD_POLL_MS  =  3_000;
const MAX_IMG_BYTES   = 15 * 1024 * 1024;

// ── Helpers ────────────────────────────────────────────────────────────────────
function fmt(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

function threadLabel(thread, currentUserId) {
  if (thread.subject) return thread.subject;
  const others = (thread.participants || []).filter(p => p.userId !== currentUserId);
  return others.map(p => p.displayName).join(', ') || 'Conversation';
}

function StatusDot({ status }) {
  if (!status) return null;
  const map = {
    READ:      { color: '#1a73e8', title: 'Read',      marks: '✓✓' },
    DELIVERED: { color: '#555',    title: 'Delivered',  marks: '✓✓' },
    SENT:      { color: '#aaa',    title: 'Sent',       marks: '✓'  },
  };
  const { color, title, marks } = map[status] ?? map['SENT'];
  return (
    <span title={title} style={{ color, fontSize: '0.65rem', marginLeft: 4 }}>
      {marks}
    </span>
  );
}

// ── New Thread Modal ───────────────────────────────────────────────────────────
function NewThreadModal({ onClose, onCreate }) {
  const [query, setQuery]       = useState('');
  const [results, setResults]   = useState([]);
  const [selected, setSelected] = useState([]);
  const [subject, setSubject]   = useState('');
  const [body, setBody]         = useState('');
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);

  useEffect(() => {
    if (query.length < 2) { setResults([]); return; }
    const t = setTimeout(() => {
      api.get(`/api/messages/users?q=${encodeURIComponent(query)}`)
        .then(setResults).catch(() => {});
    }, 300);
    return () => clearTimeout(t);
  }, [query]);

  const toggle = (u) =>
    setSelected(s => s.find(x => x.id === u.id)
      ? s.filter(x => x.id !== u.id)
      : [...s, u]);

  const submit = async () => {
    if (!selected.length) return setError('Select at least one recipient.');
    if (!body.trim())     return setError('Message body is required.');
    setLoading(true); setError('');
    try {
      const t = await api.post('/api/messages/threads', {
        subject: subject.trim() || null,
        recipientIds: selected.map(u => u.id),
        body: body.trim(),
      });
      onCreate(t);
    } catch (e) {
      setError(e.body?.message || e.message || 'Failed to create thread.');
    } finally { setLoading(false); }
  };

  return (
    <div style={overlay}>
      <div style={modal}>
        <h3 style={{ margin: '0 0 1rem' }}>New Conversation</h3>

        <label style={lbl}>To</label>
        <input style={inp} value={query} onChange={e => setQuery(e.target.value)}
          placeholder="Search by name or username…" />

        {results.length > 0 && (
          <ul style={sugList}>
            {results.map(u => (
              <li key={u.id} style={sugItem(selected.some(x => x.id === u.id))}
                  onClick={() => toggle(u)}>
                {u.displayName} <span style={{ color:'#888', fontSize:'0.8rem' }}>@{u.username}</span>
              </li>
            ))}
          </ul>
        )}

        {selected.length > 0 && (
          <div style={{ display:'flex', flexWrap:'wrap', gap:4, marginBottom:8 }}>
            {selected.map(u => (
              <span key={u.id} style={chip}>
                {u.displayName}
                <button style={chipX} onClick={() => toggle(u)}>×</button>
              </span>
            ))}
          </div>
        )}

        <label style={lbl}>Subject <span style={{ color:'#888', fontSize:'0.8rem' }}>(optional)</span></label>
        <input style={inp} value={subject} onChange={e => setSubject(e.target.value)}
          placeholder="e.g. Maintenance follow-up" maxLength={255} />

        <label style={lbl}>Message</label>
        <textarea style={{ ...inp, height: 90, resize:'vertical' }}
          value={body} onChange={e => setBody(e.target.value)}
          placeholder="Write your message…" maxLength={5000} />

        {error && <p style={{ color:'#c00', margin:'4px 0', fontSize:'0.85rem' }}>{error}</p>}

        <div style={{ display:'flex', gap:8, justifyContent:'flex-end', marginTop:12 }}>
          <button style={btnSecondary} onClick={onClose}>Cancel</button>
          <button style={btnPrimary} onClick={submit} disabled={loading}>
            {loading ? 'Sending…' : 'Send'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Quick Replies Picker ───────────────────────────────────────────────────────
function QuickReplyPicker({ replies, onPick }) {
  if (!replies.length) return null;
  return (
    <div style={{ display:'flex', flexWrap:'wrap', gap:4, padding:'6px 0' }}>
      {replies.map(r => (
        <button key={r.replyKey} style={qrBtn} onClick={() => onPick(r)}>
          {r.label}
        </button>
      ))}
    </div>
  );
}

// ── Message Bubble ─────────────────────────────────────────────────────────────
function MessageBubble({ msg, currentUserId, onDelete }) {
  const mine = msg.senderId === currentUserId;
  const [imgErr, setImgErr] = useState(false);

  return (
    <div style={{ display:'flex', flexDirection:'column',
                  alignItems: mine ? 'flex-end' : 'flex-start',
                  marginBottom: 8 }}>
      {!mine && (
        <span style={{ fontSize:'0.72rem', color:'#888', marginBottom:2 }}>
          {msg.senderDisplayName}
        </span>
      )}
      <div style={bubble(mine, msg.messageType)}>
        {msg.deleted ? (
          <em style={{ color: mine ? 'rgba(255,255,255,0.7)' : '#aaa' }}>Message deleted</em>
        ) : (
          <>
            {msg.messageType === 'IMAGE' && msg.imageUrl && !imgErr ? (
              <img src={msg.imageUrl} alt="attachment"
                   style={{ maxWidth: 240, maxHeight: 200, borderRadius:6, display:'block' }}
                   onError={() => setImgErr(true)} />
            ) : msg.messageType === 'IMAGE' && imgErr ? (
              <span style={{ fontSize:'0.8rem', opacity:0.7 }}>[Image unavailable]</span>
            ) : null}
            {msg.body && <p style={{ margin:0, whiteSpace:'pre-wrap' }}>{msg.body}</p>}
          </>
        )}
      </div>
      <div style={{ display:'flex', alignItems:'center', gap:6,
                    fontSize:'0.7rem', color:'#999', marginTop:2 }}>
        <span>{fmt(msg.createdAt)}</span>
        {mine && <StatusDot status={msg.status} />}
        {mine && !msg.deleted && (
          <button style={deleteBtn} onClick={() => onDelete(msg.id)}>Delete</button>
        )}
      </div>
    </div>
  );
}

// ── Thread View ────────────────────────────────────────────────────────────────
function ThreadView({ threadId, currentUserId, isStaff, onBack }) {
  const [detail, setDetail]       = useState(null);
  const [messages, setMessages]   = useState([]);
  const [quickReplies, setQRs]    = useState([]);
  const [body, setBody]           = useState('');
  const [showQR, setShowQR]       = useState(false);
  const [imgFile, setImgFile]     = useState(null);
  const [imgPreview, setImgPreview] = useState(null);
  const [sending, setSending]     = useState(false);
  const [error, setError]         = useState('');
  const bottomRef = useRef(null);
  const lastMsgTimeRef = useRef(null);
  const fileRef = useRef(null);

  const scrollBottom = () => bottomRef.current?.scrollIntoView({ behavior: 'smooth' });

  const loadThread = useCallback(async () => {
    try {
      const d = await api.get(`/api/messages/threads/${threadId}`);
      setDetail(d);
      setMessages(d.messages || []);
      if (d.messages?.length) {
        lastMsgTimeRef.current = d.messages[d.messages.length - 1].createdAt;
      }
    } catch (e) { setError('Failed to load thread.'); }
  }, [threadId]);

  useEffect(() => {
    setDetail(null); setMessages([]); setError('');
    loadThread().then(scrollBottom);
    if (isStaff) {
      api.get('/api/messages/quick-replies').then(setQRs).catch(() => {});
    }
  }, [threadId, isStaff, loadThread]);

  // Refresh thread every 3 s so existing bubbles can pick up
  // cross-device DELIVERED/READ status changes, not just new messages.
  useEffect(() => {
    const id = setInterval(async () => {
      try {
        const latest = await api.get(`/api/messages/threads/${threadId}`);
        setDetail(latest);
        setMessages(latest.messages || []);
        if (latest.messages?.length) {
          lastMsgTimeRef.current = latest.messages[latest.messages.length - 1].createdAt;
        } else {
          lastMsgTimeRef.current = null;
        }
        if (latest.messages?.length !== messages.length) {
          setTimeout(scrollBottom, 50);
        }
      } catch (_) {}
    }, THREAD_POLL_MS);
    return () => clearInterval(id);
  }, [threadId, messages.length]);

  useEffect(() => { setTimeout(scrollBottom, 100); }, [messages.length]);

  const pickImage = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    if (file.size > MAX_IMG_BYTES) { setError('Image must be under 15 MB.'); return; }
    setImgFile(file);
    setImgPreview(URL.createObjectURL(file));
    setError('');
  };

  const clearImage = () => {
    setImgFile(null);
    if (imgPreview) URL.revokeObjectURL(imgPreview);
    setImgPreview(null);
  };

  const send = async () => {
    if (sending) return;
    if (!body.trim() && !imgFile) return;
    setSending(true); setError('');
    try {
      let newMsg;
      if (imgFile) {
        const form = new FormData();
        form.append('file', imgFile);
        newMsg = await api.upload(`/api/messages/threads/${threadId}/messages/image`, form);
        clearImage();
      } else {
        newMsg = await api.post(`/api/messages/threads/${threadId}/messages`, { body: body.trim() });
      }
      setBody('');
      setMessages(prev => {
        if (prev.find(m => m.id === newMsg.id)) return prev;
        return [...prev, newMsg];
      });
      lastMsgTimeRef.current = newMsg.createdAt;
      setTimeout(scrollBottom, 50);
    } catch (e) {
      setError(e.body?.message || e.message || 'Failed to send.');
    } finally { setSending(false); }
  };

  const sendQuickReply = async (r) => {
    setSending(true); setError('');
    try {
      const newMsg = await api.post(`/api/messages/threads/${threadId}/messages`,
        { quickReplyKey: r.replyKey });
      setMessages(prev => prev.find(m => m.id === newMsg.id) ? prev : [...prev, newMsg]);
      lastMsgTimeRef.current = newMsg.createdAt;
      setShowQR(false);
      setTimeout(scrollBottom, 50);
    } catch (e) {
      setError(e.body?.message || e.message || 'Failed to send quick reply.');
    } finally { setSending(false); }
  };

  const deleteMsg = async (msgId) => {
    try {
      await api.delete(`/api/messages/threads/${threadId}/messages/${msgId}`);
      setMessages(prev => prev.map(m => m.id === msgId ? { ...m, deleted: true } : m));
    } catch (e) { setError('Could not delete message.'); }
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
  };

  if (!detail && !error) return <div style={loadingPane}>Loading…</div>;
  if (error && !detail) return <div style={loadingPane}>{error}</div>;

  const others = (detail.participants || []).filter(p => p.userId !== currentUserId);
  const title = detail.subject || others.map(p => p.displayName).join(', ') || 'Conversation';

  return (
    <div style={threadPane}>
      {/* Header */}
      <div style={threadHeader}>
        <button style={backBtn} onClick={onBack}>←</button>
        <div>
          <div style={{ fontWeight:600 }}>{title}</div>
          {detail.threadType === 'SYSTEM_NOTICE' && (
            <span style={noticeBadge}>System Notice</span>
          )}
        </div>
      </div>

      {/* Messages */}
      <div style={msgList}>
        {messages.map(m => (
          <MessageBubble key={m.id} msg={m} currentUserId={currentUserId} onDelete={deleteMsg} />
        ))}
        <div ref={bottomRef} />
      </div>

      {/* Compose */}
      <div style={compose}>
        {error && <p style={{ color:'#c00', fontSize:'0.8rem', margin:'0 0 4px' }}>{error}</p>}

        {isStaff && quickReplies.length > 0 && (
          <div>
            <button style={qrToggle} onClick={() => setShowQR(v => !v)}>
              {showQR ? 'Hide quick replies' : 'Quick replies ▾'}
            </button>
            {showQR && <QuickReplyPicker replies={quickReplies} onPick={sendQuickReply} />}
          </div>
        )}

        {imgPreview && (
          <div style={{ position:'relative', display:'inline-block', marginBottom:4 }}>
            <img src={imgPreview} alt="preview"
                 style={{ maxHeight:80, borderRadius:4, border:'1px solid #ddd' }} />
            <button style={removeImgBtn} onClick={clearImage}>×</button>
          </div>
        )}

        <div style={{ display:'flex', gap:6 }}>
          <textarea
            style={composeInput}
            value={body}
            onChange={e => setBody(e.target.value)}
            onKeyDown={handleKey}
            placeholder="Write a message… (Enter to send, Shift+Enter for newline)"
            rows={2}
            maxLength={5000}
            disabled={sending}
          />
          <div style={{ display:'flex', flexDirection:'column', gap:4 }}>
            <button style={iconBtn} title="Attach image" onClick={() => fileRef.current?.click()}>
              📎
            </button>
            <input ref={fileRef} type="file" accept="image/jpeg,image/png" style={{ display:'none' }}
                   onChange={pickImage} />
            <button style={btnPrimary} onClick={send} disabled={sending || (!body.trim() && !imgFile)}>
              {sending ? '…' : 'Send'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Inbox List ─────────────────────────────────────────────────────────────────
function InboxList({ threads, selectedId, currentUserId, onSelect, loading }) {
  if (loading && !threads.length) {
    return <div style={emptyMsg}>Loading inbox…</div>;
  }
  if (!threads.length) {
    return <div style={emptyMsg}>No conversations yet.</div>;
  }
  return (
    <ul style={{ listStyle:'none', margin:0, padding:0 }}>
      {threads.map(t => {
        const label = threadLabel(t, currentUserId);
        const last  = t.lastMessage;
        const unread = t.unreadCount > 0;
        return (
          <li key={t.id} style={threadItem(t.id === selectedId)} onClick={() => onSelect(t.id)}>
            <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center' }}>
              <span style={{ fontWeight: unread ? 700 : 500, fontSize:'0.9rem',
                             overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap',
                             maxWidth: 150 }}>
                {label}
              </span>
              <div style={{ display:'flex', flexDirection:'column', alignItems:'flex-end', gap:2 }}>
                <span style={{ fontSize:'0.7rem', color:'#888' }}>
                  {fmt(t.updatedAt)}
                </span>
                {t.unreadCount > 0 && (
                  <span style={unreadBadge}>{t.unreadCount > 99 ? '99+' : t.unreadCount}</span>
                )}
              </div>
            </div>
            {last && (
              <div style={{ fontSize:'0.78rem', color:'#666', marginTop:2,
                            overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
                {last.messageType === 'IMAGE' ? '📷 Image' : (last.body || '')}
              </div>
            )}
            {t.threadType === 'SYSTEM_NOTICE' && (
              <span style={{ ...noticeBadge, fontSize:'0.65rem' }}>Notice</span>
            )}
          </li>
        );
      })}
    </ul>
  );
}

// ── System Notice Modal ────────────────────────────────────────────────────────
function NoticeModal({ onClose, onSent }) {
  const [query, setQuery]       = useState('');
  const [results, setResults]   = useState([]);
  const [selected, setSelected] = useState([]);
  const [subject, setSubject]   = useState('');
  const [body, setBody]         = useState('');
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);

  useEffect(() => {
    if (query.length < 2) { setResults([]); return; }
    const t = setTimeout(() => {
      api.get(`/api/messages/users?q=${encodeURIComponent(query)}`).then(setResults).catch(() => {});
    }, 300);
    return () => clearTimeout(t);
  }, [query]);

  const toggle = (u) =>
    setSelected(s => s.find(x => x.id === u.id) ? s.filter(x => x.id !== u.id) : [...s, u]);

  const submit = async () => {
    if (!selected.length) return setError('Select at least one recipient.');
    if (!subject.trim())  return setError('Subject is required for notices.');
    if (!body.trim())     return setError('Body is required.');
    setLoading(true); setError('');
    try {
      await api.post('/api/messages/notices', {
        subject: subject.trim(),
        body: body.trim(),
        recipientIds: selected.map(u => u.id),
      });
      onSent();
    } catch (e) {
      setError(e.body?.message || e.message || 'Failed to send notice.');
    } finally { setLoading(false); }
  };

  return (
    <div style={overlay}>
      <div style={modal}>
        <h3 style={{ margin:'0 0 1rem' }}>Send System Notice</h3>
        <p style={{ color:'#666', fontSize:'0.85rem', marginTop:0 }}>
          System notices bypass recipient blocks and are marked as official notices.
        </p>

        <label style={lbl}>To</label>
        <input style={inp} value={query} onChange={e => setQuery(e.target.value)}
          placeholder="Search recipients…" />
        {results.length > 0 && (
          <ul style={sugList}>
            {results.map(u => (
              <li key={u.id} style={sugItem(selected.some(x => x.id === u.id))}
                  onClick={() => toggle(u)}>
                {u.displayName} <span style={{ color:'#888' }}>@{u.username}</span>
              </li>
            ))}
          </ul>
        )}
        {selected.length > 0 && (
          <div style={{ display:'flex', flexWrap:'wrap', gap:4, marginBottom:8 }}>
            {selected.map(u => (
              <span key={u.id} style={chip}>
                {u.displayName}<button style={chipX} onClick={() => toggle(u)}>×</button>
              </span>
            ))}
          </div>
        )}

        <label style={lbl}>Subject</label>
        <input style={inp} value={subject} onChange={e => setSubject(e.target.value)} maxLength={255} />

        <label style={lbl}>Body</label>
        <textarea style={{ ...inp, height:90, resize:'vertical' }}
          value={body} onChange={e => setBody(e.target.value)} maxLength={5000} />

        {error && <p style={{ color:'#c00', fontSize:'0.85rem' }}>{error}</p>}
        <div style={{ display:'flex', gap:8, justifyContent:'flex-end', marginTop:12 }}>
          <button style={btnSecondary} onClick={onClose}>Cancel</button>
          <button style={{ ...btnPrimary, background:'#c55' }} onClick={submit} disabled={loading}>
            {loading ? 'Sending…' : 'Send Notice'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Blocked Staff Panel ────────────────────────────────────────────────────────
function BlocksPanel({ onClose }) {
  const [blocks, setBlocks] = useState([]);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/api/messages/blocks').then(setBlocks).catch(() => {});
  }, []);

  useEffect(() => {
    if (query.length < 2) {
      setResults([]);
      return;
    }
    const t = setTimeout(() => {
      api.get(`/api/messages/users?q=${encodeURIComponent(query)}`)
        .then(users => {
          const blocked = new Set(blocks.map(b => b.staffUserId));
          setResults(users.filter(u => !blocked.has(u.id)));
        })
        .catch(() => setResults([]));
    }, 300);
    return () => clearTimeout(t);
  }, [query, blocks]);

  const block = async (staff) => {
    setBusyId(staff.id);
    setError('');
    try {
      await api.post(`/api/messages/blocks/${staff.id}`, {});
      setBlocks(prev => [
        ...prev,
        { staffUserId: staff.id, displayName: staff.displayName, blockedAt: new Date().toISOString() },
      ]);
      setResults(prev => prev.filter(u => u.id !== staff.id));
      setQuery('');
    } catch (e) {
      setError(e.body?.message || e.message || 'Could not block staff member.');
    } finally {
      setBusyId(null);
    }
  };

  const unblock = async (staffId) => {
    try {
      await api.delete(`/api/messages/blocks/${staffId}`);
      setBlocks(b => b.filter(x => x.staffUserId !== staffId));
    } catch (e) {
      setError(e.body?.message || e.message || 'Could not unblock staff member.');
    }
  };

  return (
    <div style={overlay}>
      <div style={modal}>
        <h3 style={{ margin:'0 0 1rem' }}>Blocked Staff</h3>
        <p style={{ color:'#666', fontSize:'0.85rem', marginTop:0 }}>
          Block a staff member to prevent them from initiating new direct conversations with you.
        </p>
        <label style={lbl}>Block a staff member</label>
        <input
          style={inp}
          value={query}
          onChange={e => setQuery(e.target.value)}
          placeholder="Search staff by name or username…"
        />
        {results.length > 0 && (
          <ul style={sugList}>
            {results.map(u => (
              <li key={u.id} style={sugItem(false)}>
                <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', gap:8 }}>
                  <span>
                    {u.displayName} <span style={{ color:'#888', fontSize:'0.8rem' }}>@{u.username}</span>
                  </span>
                  <button style={btnSecondary} onClick={() => block(u)} disabled={busyId === u.id}>
                    {busyId === u.id ? 'Blocking…' : 'Block'}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
        {error && <p style={{ color:'#c00', fontSize:'0.85rem' }}>{error}</p>}
        {blocks.length === 0
          ? <p style={{ color:'#888' }}>You have not blocked any staff members.</p>
          : (
            <ul style={{ listStyle:'none', padding:0, margin:0 }}>
              {blocks.map(b => (
                <li key={b.staffUserId} style={{ display:'flex', justifyContent:'space-between',
                    alignItems:'center', padding:'6px 0', borderBottom:'1px solid #eee' }}>
                  <span>{b.displayName}</span>
                  <button style={btnSecondary} onClick={() => unblock(b.staffUserId)}>Unblock</button>
                </li>
              ))}
            </ul>
          )}
        <div style={{ textAlign:'right', marginTop:16 }}>
          <button style={btnSecondary} onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}

// ── Main Page ──────────────────────────────────────────────────────────────────
export default function MessagesPage() {
  const { user } = useAuth();
  const currentUserId = user?.id;
  const isStaff = user?.roles?.some(r =>
    ['ADMIN','HOUSING_ADMINISTRATOR','DIRECTOR','RESIDENT_DIRECTOR',
     'RESIDENT_ASSISTANT','RESIDENCE_STAFF','STAFF'].includes(r));

  const [threads, setThreads]         = useState([]);
  const [inboxLoading, setInboxLoading] = useState(true);
  const [selectedId, setSelectedId]   = useState(null);
  const [showNew, setShowNew]         = useState(false);
  const [showNotice, setShowNotice]   = useState(false);
  const [showBlocks, setShowBlocks]   = useState(false);

  const loadInbox = useCallback(async () => {
    try {
      const ts = await api.get('/api/messages/threads');
      setThreads(ts);
    } catch (_) {}
    setInboxLoading(false);
  }, []);

  useEffect(() => {
    loadInbox();
    const id = setInterval(loadInbox, INBOX_POLL_MS);
    return () => clearInterval(id);
  }, [loadInbox]);

  const onThreadCreated = (t) => {
    setShowNew(false);
    setShowNotice(false);
    setThreads(prev => [t, ...prev.filter(x => x.id !== t.id)]);
    setSelectedId(t.id);
  };

  return (
    <div style={page}>
      {/* Sidebar */}
      <div style={sidebar}>
        <div style={sidebarHeader}>
          <span style={{ fontWeight:700 }}>Messages</span>
          <div style={{ display:'flex', gap:4 }}>
            {isStaff && (
              <button style={iconBtn} title="Send system notice" onClick={() => setShowNotice(true)}>
                📢
              </button>
            )}
            {!isStaff && (
              <button style={iconBtn} title="Manage blocks" onClick={() => setShowBlocks(true)}>
                🚫
              </button>
            )}
            <button style={btnPrimary} onClick={() => setShowNew(true)}>+ New</button>
          </div>
        </div>

        <InboxList
          threads={threads}
          selectedId={selectedId}
          currentUserId={currentUserId}
          onSelect={setSelectedId}
          loading={inboxLoading}
        />
      </div>

      {/* Thread area */}
      <div style={{ flex:1, display:'flex', flexDirection:'column', minWidth:0 }}>
        {selectedId ? (
          <ThreadView
            key={selectedId}
            threadId={selectedId}
            currentUserId={currentUserId}
            isStaff={isStaff}
            onBack={() => setSelectedId(null)}
          />
        ) : (
          <div style={emptyThread}>
            <p>Select a conversation or start a new one.</p>
            <button style={btnPrimary} onClick={() => setShowNew(true)}>Start new conversation</button>
          </div>
        )}
      </div>

      {showNew     && <NewThreadModal onClose={() => setShowNew(false)}    onCreate={onThreadCreated} />}
      {showNotice  && <NoticeModal   onClose={() => setShowNotice(false)}  onSent={() => { setShowNotice(false); loadInbox(); }} />}
      {showBlocks  && <BlocksPanel   onClose={() => setShowBlocks(false)} />}
    </div>
  );
}

// ── Styles ─────────────────────────────────────────────────────────────────────
const page         = { display:'flex', height:'calc(100vh - 0px)', overflow:'hidden', fontFamily:'system-ui, sans-serif' };
const sidebar      = { width:280, minWidth:280, borderRight:'1px solid #ddd', display:'flex', flexDirection:'column', background:'#fafafa' };
const sidebarHeader= { display:'flex', justifyContent:'space-between', alignItems:'center', padding:'12px 12px 8px', borderBottom:'1px solid #eee' };
const threadPane   = { display:'flex', flexDirection:'column', height:'100%' };
const threadHeader = { display:'flex', alignItems:'center', gap:8, padding:'10px 16px', borderBottom:'1px solid #eee', background:'#fff' };
const backBtn      = { background:'none', border:'none', cursor:'pointer', fontSize:'1.1rem', padding:4, color:'#555' };
const msgList      = { flex:1, overflowY:'auto', padding:'12px 16px' };
const compose      = { borderTop:'1px solid #eee', padding:'10px 16px', background:'#fff' };
const composeInput = { flex:1, padding:'8px 10px', border:'1px solid #ddd', borderRadius:6, fontSize:'0.9rem', resize:'none', fontFamily:'inherit' };
const emptyThread  = { flex:1, display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center', color:'#888', gap:12 };
const emptyMsg     = { padding:'1rem', color:'#888', fontSize:'0.9rem' };
const loadingPane  = { flex:1, display:'flex', alignItems:'center', justifyContent:'center', color:'#888' };
const threadItem   = (active) => ({ padding:'10px 12px', cursor:'pointer', borderBottom:'1px solid #f0f0f0', background: active ? '#e8f0fe' : 'transparent', ':hover':{ background:'#f5f5f5' } });
const unreadBadge  = { background:'#1a73e8', color:'#fff', borderRadius:10, padding:'1px 6px', fontSize:'0.7rem', fontWeight:700 };
const noticeBadge  = { display:'inline-block', background:'#fff3cd', color:'#856404', borderRadius:4, padding:'1px 5px', fontSize:'0.7rem', fontWeight:600, marginTop:2 };
const bubble       = (mine, type) => ({
  maxWidth: 340, padding:'8px 12px', borderRadius: mine ? '16px 16px 4px 16px' : '16px 16px 16px 4px',
  background: type === 'SYSTEM_NOTICE' ? '#fff3cd' : mine ? '#1a73e8' : '#f1f3f4',
  color: mine && type !== 'SYSTEM_NOTICE' ? '#fff' : '#111',
  fontSize:'0.88rem', wordBreak:'break-word',
});
const deleteBtn    = { background:'none', border:'none', cursor:'pointer', color:'#c00', fontSize:'0.7rem', padding:0 };
const overlay      = { position:'fixed', inset:0, background:'rgba(0,0,0,0.4)', display:'flex', alignItems:'center', justifyContent:'center', zIndex:1000 };
const modal        = { background:'#fff', borderRadius:10, padding:'1.5rem', width:460, maxWidth:'95vw', maxHeight:'80vh', overflowY:'auto', boxShadow:'0 4px 20px rgba(0,0,0,0.2)' };
const lbl          = { display:'block', fontSize:'0.82rem', fontWeight:600, color:'#444', marginBottom:4, marginTop:10 };
const inp          = { width:'100%', boxSizing:'border-box', padding:'8px 10px', border:'1px solid #ccc', borderRadius:6, fontSize:'0.9rem', fontFamily:'inherit' };
const sugList      = { listStyle:'none', margin:'2px 0 6px', padding:0, border:'1px solid #ddd', borderRadius:6, maxHeight:140, overflowY:'auto' };
const sugItem      = (sel) => ({ padding:'7px 10px', cursor:'pointer', background: sel ? '#e8f0fe' : '#fff', borderBottom:'1px solid #f0f0f0' });
const chip         = { display:'inline-flex', alignItems:'center', gap:4, background:'#e8f0fe', color:'#1a73e8', borderRadius:12, padding:'2px 8px', fontSize:'0.8rem' };
const chipX        = { background:'none', border:'none', cursor:'pointer', color:'#1a73e8', fontSize:'1rem', lineHeight:1, padding:0 };
const btnPrimary   = { background:'#1a73e8', color:'#fff', border:'none', borderRadius:6, padding:'7px 14px', cursor:'pointer', fontSize:'0.85rem', fontWeight:600 };
const btnSecondary = { background:'none', color:'#555', border:'1px solid #ddd', borderRadius:6, padding:'6px 12px', cursor:'pointer', fontSize:'0.85rem' };
const iconBtn      = { background:'none', border:'1px solid #ddd', borderRadius:6, padding:'4px 8px', cursor:'pointer', fontSize:'1rem' };
const qrBtn        = { background:'#f1f3f4', border:'1px solid #ddd', borderRadius:12, padding:'3px 10px', cursor:'pointer', fontSize:'0.78rem', whiteSpace:'nowrap' };
const qrToggle     = { background:'none', border:'none', color:'#1a73e8', cursor:'pointer', fontSize:'0.8rem', padding:0, marginBottom:4 };
const removeImgBtn = { position:'absolute', top:-6, right:-6, background:'#555', color:'#fff', border:'none', borderRadius:'50%', width:18, height:18, cursor:'pointer', fontSize:'0.8rem', lineHeight:'18px', textAlign:'center', padding:0 };
