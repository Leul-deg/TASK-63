import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import AttachmentUploader from '../components/AttachmentUploader';
import AttachmentList from '../components/AttachmentList';

// ── Constants ─────────────────────────────────────────────────────────────────

const AGREEMENT_STATUSES = ['PENDING', 'SIGNED', 'EXPIRED', 'CANCELLED'];

const STATUS_STYLES = {
  PENDING:   { background: '#fef3c7', color: '#92400e' },
  SIGNED:    { background: '#e6f4ea', color: '#1a7f37' },
  EXPIRED:   { background: '#f0f0f0', color: '#555'    },
  CANCELLED: { background: '#fff0f0', color: '#c0392b' },
};

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ResidentAgreementsPage() {
  const { id: residentId } = useParams();
  const navigate = useNavigate();

  // Resident info (for header)
  const [resident,    setResident]    = useState(null);
  // Agreements with their attachment lists
  const [agreements,  setAgreements]  = useState([]);
  // Per-agreement attachment maps: { [agreementId]: AttachmentResponse[] }
  const [attachments, setAttachments] = useState({});

  const [loading, setLoading]   = useState(true);
  const [loadErr, setLoadErr]   = useState('');

  // New agreement form state
  const [showNewForm,    setShowNewForm]    = useState(false);
  const [newAgreement,   setNewAgreement]   = useState({ agreementType: '', status: 'PENDING', signedDate: '', expiresDate: '', version: '', notes: '' });
  const [newAgreErr,     setNewAgreErr]     = useState('');
  const [savingAgreement, setSavingAgreement] = useState(false);

  // ── Load data ───────────────────────────────────────────────────────────

  const loadAll = useCallback(() => {
    setLoading(true);
    setLoadErr('');
    Promise.all([
      api.get(`/api/residents/${residentId}`),
      api.get(`/api/residents/${residentId}/agreements`),
    ])
      .then(async ([r, agrs]) => {
        setResident(r);
        setAgreements(agrs ?? []);
        // Load attachments for each agreement in parallel
        const attResults = await Promise.all(
          (agrs ?? []).map(a =>
            api.get(`/api/residents/${residentId}/agreements/${a.id}/attachments`)
              .then(list => [a.id, list])
              .catch(() => [a.id, []])
          )
        );
        const attMap = Object.fromEntries(attResults);
        setAttachments(attMap);
      })
      .catch(e => setLoadErr(e.message))
      .finally(() => setLoading(false));
  }, [residentId]);

  useEffect(() => { loadAll(); }, [loadAll]);

  // ── Create agreement ────────────────────────────────────────────────────

  const handleCreateAgreement = async () => {
    if (!newAgreement.agreementType.trim()) {
      setNewAgreErr('Agreement type is required.');
      return;
    }
    setSavingAgreement(true);
    setNewAgreErr('');
    try {
      const created = await api.post(`/api/residents/${residentId}/agreements`, {
        agreementType: newAgreement.agreementType,
        status:        newAgreement.status || 'PENDING',
        signedDate:    newAgreement.signedDate  || null,
        expiresDate:   newAgreement.expiresDate || null,
        version:       newAgreement.version     || null,
        notes:         newAgreement.notes       || null,
      });
      setAgreements(prev => [...prev, created]);
      setAttachments(prev => ({ ...prev, [created.id]: [] }));
      setNewAgreement({ agreementType: '', status: 'PENDING', signedDate: '', expiresDate: '', version: '', notes: '' });
      setShowNewForm(false);
    } catch (err) {
      setNewAgreErr(err.message || 'Failed to create agreement.');
    } finally {
      setSavingAgreement(false);
    }
  };

  // ── Attachment callbacks ─────────────────────────────────────────────────

  const handleUploaded = (agreementId, newAtt) => {
    setAttachments(prev => ({
      ...prev,
      [agreementId]: [...(prev[agreementId] ?? []), newAtt],
    }));
    // Bump attachment count on the agreement card
    setAgreements(prev =>
      prev.map(a => a.id === agreementId
        ? { ...a, attachmentCount: (a.attachmentCount ?? 0) + 1 }
        : a)
    );
  };

  const handleDeleted = (agreementId, attachmentId) => {
    setAttachments(prev => ({
      ...prev,
      [agreementId]: (prev[agreementId] ?? []).filter(a => a.id !== attachmentId),
    }));
    setAgreements(prev =>
      prev.map(a => a.id === agreementId
        ? { ...a, attachmentCount: Math.max(0, (a.attachmentCount ?? 1) - 1) }
        : a)
    );
  };

  // ── Render ───────────────────────────────────────────────────────────────

  if (loading) return <div style={page.loading}>Loading…</div>;
  if (loadErr) return (
    <div style={page.err}>
      {loadErr}
      <button style={page.backBtn} onClick={() => navigate('/residents')}>Back</button>
    </div>
  );

  const name = resident ? `${resident.firstName} ${resident.lastName}` : residentId;

  return (
    <main style={page.main}>
      {/* Header */}
      <div style={page.header}>
        <button style={page.backBtn} onClick={() => navigate('/residents')}>← Back</button>
        <div>
          <h2 style={page.title}>Housing Agreements</h2>
          <div style={page.subtitle}>{name}</div>
        </div>
        <button style={page.newAgreBtn} onClick={() => setShowNewForm(f => !f)}>
          {showNewForm ? 'Cancel' : '+ New Agreement'}
        </button>
      </div>

      {/* New agreement form */}
      {showNewForm && (
        <div style={page.newAgreForm}>
          <h4 style={{ margin: '0 0 0.75rem', fontSize: '0.95rem' }}>New Agreement</h4>
          <div style={page.grid2}>
            <div style={page.field}>
              <label style={page.label}>Agreement Type <span style={page.required}>*</span></label>
              <input
                style={page.input}
                value={newAgreement.agreementType}
                onChange={e => setNewAgreement(f => ({ ...f, agreementType: e.target.value }))}
                placeholder="e.g. Standard Residential"
              />
            </div>
            <div style={page.field}>
              <label style={page.label}>Status</label>
              <select
                style={page.input}
                value={newAgreement.status}
                onChange={e => setNewAgreement(f => ({ ...f, status: e.target.value }))}
              >
                {AGREEMENT_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
          </div>
          <div style={page.grid2}>
            <div style={page.field}>
              <label style={page.label}>Signed Date</label>
              <input type="date" style={page.input}
                value={newAgreement.signedDate}
                onChange={e => setNewAgreement(f => ({ ...f, signedDate: e.target.value }))} />
            </div>
            <div style={page.field}>
              <label style={page.label}>Expires Date</label>
              <input type="date" style={page.input}
                value={newAgreement.expiresDate}
                onChange={e => setNewAgreement(f => ({ ...f, expiresDate: e.target.value }))} />
            </div>
          </div>
          <div style={page.field}>
            <label style={page.label}>Version</label>
            <input style={page.input} value={newAgreement.version}
              onChange={e => setNewAgreement(f => ({ ...f, version: e.target.value }))}
              placeholder="e.g. 2024-A" />
          </div>
          <div style={page.field}>
            <label style={page.label}>Notes</label>
            <textarea style={{ ...page.input, resize: 'vertical' }} rows={2}
              value={newAgreement.notes}
              onChange={e => setNewAgreement(f => ({ ...f, notes: e.target.value }))} />
          </div>
          {newAgreErr && <div style={page.fieldErr}>{newAgreErr}</div>}
          <button style={page.saveAgreBtn} onClick={handleCreateAgreement} disabled={savingAgreement}>
            {savingAgreement ? 'Saving…' : 'Create Agreement'}
          </button>
        </div>
      )}

      {/* Empty state */}
      {agreements.length === 0 && !showNewForm && (
        <div style={page.empty}>
          <p>No housing agreements found for this resident.</p>
          <button style={page.newAgreBtn} onClick={() => setShowNewForm(true)}>
            + Create First Agreement
          </button>
        </div>
      )}

      {/* Agreement cards */}
      {agreements.map(agr => {
        const attList     = attachments[agr.id] ?? [];
        const downloadBase = `/api/residents/${residentId}/agreements/${agr.id}/attachments`;
        const uploadUrl    = downloadBase;

        return (
          <AgreementCard
            key={agr.id}
            agreement={agr}
            attachments={attList}
            uploadUrl={uploadUrl}
            downloadBase={downloadBase}
            onUploaded={att => handleUploaded(agr.id, att)}
            onDeleted={attId => handleDeleted(agr.id, attId)}
          />
        );
      })}
    </main>
  );
}

// ── Agreement card ────────────────────────────────────────────────────────────

function AgreementCard({ agreement: agr, attachments, uploadUrl, downloadBase, onUploaded, onDeleted }) {
  const [expanded, setExpanded] = useState(true);
  const statusStyle = STATUS_STYLES[agr.status] ?? {};

  return (
    <div style={card.wrapper}>
      {/* Card header */}
      <div style={card.header} onClick={() => setExpanded(e => !e)} role="button" tabIndex={0}
        onKeyDown={e => e.key === 'Enter' && setExpanded(v => !v)}>
        <div style={card.headerLeft}>
          <span style={card.type}>{agr.agreementType}</span>
          <span style={{ ...card.statusBadge, ...statusStyle }}>{agr.status}</span>
          {agr.version && <span style={card.version}>v{agr.version}</span>}
        </div>
        <div style={card.headerRight}>
          <span style={card.attCount}>
            {agr.attachmentCount ?? attachments.length} attachment{(agr.attachmentCount ?? attachments.length) !== 1 ? 's' : ''}
          </span>
          <span style={card.chevron}>{expanded ? '▲' : '▼'}</span>
        </div>
      </div>

      {/* Card body */}
      {expanded && (
        <div style={card.body}>
          {/* Agreement meta */}
          <div style={card.meta}>
            {agr.signedDate  && <span>Signed: <strong>{agr.signedDate}</strong></span>}
            {agr.expiresDate && <span>Expires: <strong>{agr.expiresDate}</strong></span>}
            {agr.notes       && <span style={card.notes}>{agr.notes}</span>}
          </div>

          {/* Attachment list */}
          <div style={card.section}>
            <div style={card.sectionTitle}>Attachments</div>
            <AttachmentList
              attachments={attachments}
              downloadBase={downloadBase}
              onDeleted={onDeleted}
            />
          </div>

          {/* Upload zone */}
          <div style={card.section}>
            <div style={card.sectionTitle}>Upload new attachment</div>
            <AttachmentUploader uploadUrl={uploadUrl} onUploaded={onUploaded} />
          </div>
        </div>
      )}
    </div>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const page = {
  main:      { padding: '2rem', maxWidth: '800px', fontFamily: 'system-ui, sans-serif' },
  header:    { display: 'flex', alignItems: 'flex-start', gap: '1rem', marginBottom: '1.5rem', flexWrap: 'wrap' },
  title:     { margin: 0, fontSize: '1.4rem', fontWeight: 600 },
  subtitle:  { color: '#666', fontSize: '0.9rem', marginTop: '2px' },
  loading:   { padding: '3rem', color: '#888', fontFamily: 'system-ui, sans-serif' },
  err:       { padding: '2rem', color: '#c0392b', fontFamily: 'system-ui, sans-serif', display: 'flex', gap: '1rem' },
  empty:     { padding: '2.5rem 1rem', textAlign: 'center', color: '#666', background: '#fafafa', borderRadius: '8px', border: '1px dashed #ccc' },
  newAgreBtn: { marginLeft: 'auto', padding: '0.4rem 1rem', background: '#0055cc', color: '#fff', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 600, fontSize: '0.85rem' },
  backBtn:   { padding: '0.35rem 0.75rem', background: 'none', border: '1px solid #ccc', borderRadius: '5px', cursor: 'pointer', fontSize: '0.85rem', color: '#555' },
  newAgreForm: { background: '#fff', border: '1px solid #e5e5e5', borderRadius: '8px', padding: '1.25rem', marginBottom: '1.5rem' },
  grid2:     { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', marginBottom: '0.75rem' },
  field:     { display: 'flex', flexDirection: 'column', gap: '3px', marginBottom: '0.5rem' },
  label:     { fontSize: '0.82rem', fontWeight: 600, color: '#333' },
  required:  { color: '#c0392b' },
  input:     { padding: '0.4rem 0.6rem', fontSize: '0.875rem', border: '1px solid #ccc', borderRadius: '5px', outline: 'none', width: '100%', boxSizing: 'border-box' },
  fieldErr:  { color: '#c0392b', fontSize: '0.78rem', fontWeight: 600, marginBottom: '0.5rem' },
  saveAgreBtn: { padding: '0.45rem 1.25rem', background: '#0055cc', color: '#fff', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 600, fontSize: '0.875rem' },
};

const card = {
  wrapper:     { background: '#fff', border: '1px solid #e5e5e5', borderRadius: '8px', marginBottom: '1rem', overflow: 'hidden' },
  header:      { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.85rem 1rem', cursor: 'pointer', userSelect: 'none', gap: '0.5rem' },
  headerLeft:  { display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' },
  headerRight: { display: 'flex', alignItems: 'center', gap: '0.75rem', flexShrink: 0 },
  type:        { fontWeight: 600, fontSize: '0.95rem', color: '#111' },
  statusBadge: { fontSize: '0.72rem', fontWeight: 700, padding: '2px 8px', borderRadius: '4px', textTransform: 'uppercase' },
  version:     { fontSize: '0.72rem', color: '#888', fontStyle: 'italic' },
  attCount:    { fontSize: '0.75rem', color: '#888' },
  chevron:     { fontSize: '0.7rem', color: '#888' },
  body:        { borderTop: '1px solid #eee', padding: '1rem' },
  meta:        { display: 'flex', flexWrap: 'wrap', gap: '1rem', marginBottom: '1rem', fontSize: '0.82rem', color: '#555' },
  notes:       { fontStyle: 'italic', color: '#888', flexBasis: '100%' },
  section:     { marginTop: '1rem' },
  sectionTitle: { fontSize: '0.8rem', fontWeight: 700, color: '#444', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: '0.5rem' },
};
