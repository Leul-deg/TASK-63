import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';

// ── Constants ─────────────────────────────────────────────────────────────────

const ROW_STATUS_STYLES = {
  NEW:              { background: '#f0faf4', border: '#b7e4c7', badge: { bg: '#e6f4ea', color: '#1a7f37', label: 'New' } },
  MERGE_CANDIDATE:  { background: '#fffbeb', border: '#fcd34d', badge: { bg: '#fef3c7', color: '#92400e', label: 'Duplicate' } },
  INVALID:          { background: '#fff8f8', border: '#ffcccc', badge: { bg: '#fff0f0', color: '#c0392b', label: 'Invalid' } },
};

// Column order matches CSV_HEADERS on the backend
const COLUMNS = ['studentId','firstName','lastName','email','phone','dateOfBirth','enrollmentStatus','department','classYear','roomNumber','buildingName'];
const COL_LABELS = {
  studentId:'Student ID', firstName:'First Name', lastName:'Last Name', email:'Email',
  phone:'Phone', dateOfBirth:'Date of Birth', enrollmentStatus:'Enrollment',
  department:'Department', classYear:'Class Year', roomNumber:'Room', buildingName:'Building',
};

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ImportExportPage() {
  const navigate  = useNavigate();

  // Upload state
  const fileInputRef              = useRef(null);
  const [csvFile,   setCsvFile]   = useState(null);
  const [fileError, setFileError] = useState('');

  // Workflow state: idle | previewing | committing | done
  const [phase,   setPhase]   = useState('idle');
  const [preview, setPreview] = useState(null);   // ImportPreviewResponse
  const [result,  setResult]  = useState(null);   // ImportCommitResponse
  const [apiErr,  setApiErr]  = useState('');

  // Per-row decisions for MERGE_CANDIDATE rows: { rowNumber -> 'merge' | 'skip' }
  const [decisions, setDecisions] = useState({});

  // ── File selection ──────────────────────────────────────────────────────

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.csv')) {
      setFileError('Please select a .csv file.');
      setCsvFile(null);
      return;
    }
    setFileError('');
    setCsvFile(file);
  };

  // ── Preview ─────────────────────────────────────────────────────────────

  const handlePreview = async () => {
    if (!csvFile) { setFileError('Select a CSV file first.'); return; }
    setPhase('previewing');
    setApiErr('');
    try {
      const form = new FormData();
      form.append('file', csvFile);
      const data = await api.upload('/api/residents/import/preview', form);
      setPreview(data);
      // Default decisions: MERGE_CANDIDATE → 'skip', everything else N/A
      const d = {};
      data.rows.forEach(r => {
        if (r.status === 'MERGE_CANDIDATE') d[r.rowNumber] = 'skip';
      });
      setDecisions(d);
    } catch (err) {
      setApiErr(err.message || 'Preview failed.');
      setPhase('idle');
    }
  };

  // ── Bulk decision helpers ────────────────────────────────────────────────

  const setAllMergeCandidates = (action) => {
    const d = { ...decisions };
    (preview?.rows ?? [])
      .filter(r => r.status === 'MERGE_CANDIDATE')
      .forEach(r => { d[r.rowNumber] = action; });
    setDecisions(d);
  };

  // ── Commit ───────────────────────────────────────────────────────────────

  const handleCommit = async () => {
    setPhase('committing');
    setApiErr('');
    try {
      const rows = (preview?.rows ?? [])
        .filter(r => r.status !== 'INVALID')
        .map(r => {
          const action = r.status === 'NEW'
            ? 'CREATE'
            : (decisions[r.rowNumber] === 'merge' ? 'MERGE' : 'SKIP');
          return {
            rowNumber:     r.rowNumber,
            action,
            mergeTargetId: action === 'MERGE' ? (r.match?.sourceRowNumber ? null : r.match?.id) : null,
            mergeTargetRowNumber: action === 'MERGE' ? (r.match?.sourceRowNumber ?? null) : null,
            data:          r.data,
          };
        });

      const res = await api.post('/api/residents/import/commit', { rows });
      setResult(res);
      setPhase('done');
    } catch (err) {
      setApiErr(err.message || 'Commit failed.');
      setPhase('previewing');
    }
  };

  // ── Reset ────────────────────────────────────────────────────────────────

  const reset = () => {
    setCsvFile(null); setFileError(''); setPhase('idle');
    setPreview(null); setResult(null); setApiErr(''); setDecisions({});
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  // ── Render ───────────────────────────────────────────────────────────────

  return (
    <main style={s.main}>
      <div style={s.pageHeader}>
        <button style={s.backBtn} onClick={() => navigate('/residents')}>← Back</button>
        <h2 style={s.title}>Import / Export</h2>
      </div>

      {/* ── Export section ── */}
      <section style={s.card}>
        <h3 style={s.sectionTitle}>Export Residents</h3>
        <p style={s.sectionDesc}>
          Download all active resident records as a CSV file. Date of birth is included
          in plain text — treat the file as sensitive.
        </p>
        <a
          href="/api/residents/export.csv"
          download
          style={s.exportBtn}
        >
          ↓ Download CSV
        </a>
      </section>

      {/* ── Import section ── */}
      <section style={s.card}>
        <h3 style={s.sectionTitle}>Import Residents</h3>
        <p style={s.sectionDesc}>
          Upload a CSV with the following columns (header row required):
        </p>
        <pre style={s.headerPreview}>
          {COLUMNS.join(', ')}
        </pre>
        <p style={{ ...s.sectionDesc, marginTop: '0.25rem' }}>
          Dates must be <strong>YYYY-MM-DD</strong>. Phone must be <strong>555-123-4567</strong>.
          Max 5 000 rows per file.
        </p>

        {/* ── IDLE ── */}
        {phase === 'idle' && (
          <div style={s.uploadArea}>
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv,text/csv"
              style={{ display: 'none' }}
              onChange={handleFileChange}
            />
            <div
              style={s.dropZone}
              onClick={() => fileInputRef.current?.click()}
              role="button" tabIndex={0}
              onKeyDown={e => e.key === 'Enter' && fileInputRef.current?.click()}
            >
              {csvFile
                ? <span>📄 <strong>{csvFile.name}</strong> — ready to preview</span>
                : <span>Click to select a .csv file</span>}
            </div>
            {fileError && <div style={s.errorMsg}>{fileError}</div>}
            {apiErr   && <div style={s.errorMsg}>{apiErr}</div>}
            <button
              style={{ ...s.primaryBtn, marginTop: '0.75rem', opacity: csvFile ? 1 : 0.5 }}
              onClick={handlePreview}
              disabled={!csvFile}
            >
              Preview rows →
            </button>
          </div>
        )}

        {/* ── PREVIEWING ── */}
        {phase === 'previewing' && preview && (
          <PreviewPane
            preview={preview}
            decisions={decisions}
            setDecisions={setDecisions}
            onSetAll={setAllMergeCandidates}
            onCommit={handleCommit}
            onCancel={reset}
            apiErr={apiErr}
          />
        )}

        {/* ── COMMITTING ── */}
        {phase === 'committing' && (
          <div style={s.loadingMsg}>Importing records…</div>
        )}

        {/* ── DONE ── */}
        {phase === 'done' && result && (
          <ResultPane result={result} onReset={reset} onGoToResidents={() => navigate('/residents')} />
        )}
      </section>
    </main>
  );
}

// ── Preview pane ──────────────────────────────────────────────────────────────

function PreviewPane({ preview, decisions, setDecisions, onSetAll, onCommit, onCancel, apiErr }) {
  const { totalRows, newCount, mergeCount, invalidCount, rows } = preview;
  const actionableCount = newCount + mergeCount;
  const commitLabel = `Commit ${actionableCount} row${actionableCount !== 1 ? 's' : ''}`;

  return (
    <div>
      {/* Stats bar */}
      <div style={s.statsBar}>
        <Stat label="Total"     value={totalRows}    />
        <Stat label="New"       value={newCount}     color="#1a7f37" />
        <Stat label="Duplicate" value={mergeCount}   color="#92400e" />
        <Stat label="Invalid"   value={invalidCount} color="#c0392b" />
      </div>

      {/* Bulk actions for merge candidates */}
      {mergeCount > 0 && (
        <div style={s.bulkActions}>
          <span style={s.bulkLabel}>Duplicates ({mergeCount}):</span>
          <button style={s.bulkBtn} onClick={() => onSetAll('merge')}>Merge all</button>
          <button style={s.bulkBtn} onClick={() => onSetAll('skip')}>Skip all</button>
        </div>
      )}

      {/* Table */}
      <div style={s.tableScroll}>
        <table style={s.table}>
          <thead>
            <tr>
              <Th>#</Th>
              <Th>Status</Th>
              {COLUMNS.map(c => <Th key={c}>{COL_LABELS[c]}</Th>)}
              <Th>Action</Th>
            </tr>
          </thead>
          <tbody>
            {rows.map(row => (
              <PreviewRow
                key={row.rowNumber}
                row={row}
                decision={decisions[row.rowNumber]}
                onDecision={val => setDecisions(d => ({ ...d, [row.rowNumber]: val }))}
              />
            ))}
          </tbody>
        </table>
      </div>

      {apiErr && <div style={{ ...s.errorMsg, marginTop: '0.75rem' }}>{apiErr}</div>}

      {invalidCount > 0 && (
        <div style={s.warnNote}>
          ⚠ {invalidCount} invalid row{invalidCount !== 1 ? 's' : ''} will be skipped.
          Fix the source file and re-upload to import them.
        </div>
      )}

      {/* Footer actions */}
      <div style={s.footerBtns}>
        <button style={s.secondaryBtn} onClick={onCancel}>Cancel</button>
        <button
          style={s.primaryBtn}
          onClick={onCommit}
          disabled={actionableCount === 0}
        >
          {commitLabel}
        </button>
      </div>
    </div>
  );
}

// ── Preview row ───────────────────────────────────────────────────────────────

function PreviewRow({ row, decision, onDecision }) {
  const [expanded, setExpanded] = useState(row.status === 'INVALID');
  const st = ROW_STATUS_STYLES[row.status] ?? ROW_STATUS_STYLES.INVALID;

  return (
    <>
      <tr style={{ background: st.background, borderLeft: `3px solid ${st.border}` }}>
        <Td>{row.rowNumber}</Td>
        <Td>
          <span style={{ ...s.badge, background: st.badge.bg, color: st.badge.color }}>
            {st.badge.label}
          </span>
        </Td>
        {COLUMNS.map(col => (
          <Td key={col} style={{ maxWidth: '140px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {row.data[col] || <span style={{ color: '#bbb' }}>—</span>}
          </Td>
        ))}
        <Td>
          {row.status === 'MERGE_CANDIDATE' && (
            <MergeDecision rowNumber={row.rowNumber} decision={decision} onDecision={onDecision} />
          )}
          {row.status === 'NEW'     && <span style={{ color: '#1a7f37', fontSize: '0.78rem', fontWeight: 600 }}>Will create</span>}
          {row.status === 'INVALID' && (
            <button style={s.linkBtn} onClick={() => setExpanded(e => !e)}>
              {row.errors.length} error{row.errors.length !== 1 ? 's' : ''} {expanded ? '▲' : '▼'}
            </button>
          )}
        </Td>
      </tr>

      {/* Expanded details */}
      {expanded && row.status === 'INVALID' && (
        <tr>
          <td colSpan={COLUMNS.length + 3} style={s.errorDetail}>
            <ul style={{ margin: 0, paddingLeft: '1.25rem' }}>
              {row.errors.map((e, i) => <li key={i}>{e}</li>)}
            </ul>
          </td>
        </tr>
      )}

      {/* Merge details */}
      {row.status === 'MERGE_CANDIDATE' && row.match && (
        <tr>
          <td colSpan={COLUMNS.length + 3} style={s.matchDetail}>
            <span style={s.matchLabel}>
              {row.match.sourceRowNumber ? 'Matches row in this file:' : 'Matches existing:'}
            </span>
            <span style={s.matchReason}>{row.match.matchReason}</span>
            {' '}
            <strong>{row.match.firstName} {row.match.lastName}</strong>
            {row.match.studentId && ` · ${row.match.studentId}`}
            {' '}· {row.match.email}
            {row.match.sourceRowNumber && ` · row ${row.match.sourceRowNumber}`}
          </td>
        </tr>
      )}
    </>
  );
}

function MergeDecision({ rowNumber, decision, onDecision }) {
  const groupName = `merge-decision-${rowNumber}`;
  return (
    <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
      <label style={s.radioLabel}>
        <input type="radio" name={groupName} value="merge"
          checked={decision === 'merge'}
          onChange={() => onDecision('merge')} />
        {' '}Merge
      </label>
      <label style={s.radioLabel}>
        <input type="radio" name={groupName} value="skip"
          checked={decision !== 'merge'}
          onChange={() => onDecision('skip')} />
        {' '}Skip
      </label>
    </div>
  );
}

// ── Result pane ───────────────────────────────────────────────────────────────

function ResultPane({ result, onReset, onGoToResidents }) {
  return (
    <div style={s.resultPane}>
      <div style={s.resultTitle}>Import complete</div>
      <div style={s.resultStats}>
        <ResultStat value={result.created} label="Created"  color="#1a7f37" />
        <ResultStat value={result.merged}  label="Merged"   color="#0055cc" />
        <ResultStat value={result.skipped} label="Skipped"  color="#888"    />
        {result.failed > 0 &&
          <ResultStat value={result.failed}  label="Failed"   color="#c0392b" />}
      </div>

      {result.failures?.length > 0 && (
        <div style={s.failureList}>
          <div style={{ fontWeight: 600, marginBottom: '0.4rem', fontSize: '0.85rem' }}>
            Rows that failed:
          </div>
          {result.failures.map(f => (
            <div key={f.rowNumber} style={s.failureRow}>
              Row {f.rowNumber}: {f.reason}
            </div>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem', flexWrap: 'wrap' }}>
        <button style={s.primaryBtn} onClick={onGoToResidents}>View residents</button>
        <button style={s.secondaryBtn} onClick={onReset}>Import another file</button>
      </div>
    </div>
  );
}

// ── Small helpers ─────────────────────────────────────────────────────────────

function Stat({ label, value, color }) {
  return (
    <div style={s.stat}>
      <span style={{ ...s.statValue, color: color ?? '#111' }}>{value}</span>
      <span style={s.statLabel}>{label}</span>
    </div>
  );
}

function ResultStat({ label, value, color }) {
  return (
    <div style={{ textAlign: 'center', minWidth: '70px' }}>
      <div style={{ fontSize: '2rem', fontWeight: 700, color }}>{value}</div>
      <div style={{ fontSize: '0.78rem', color: '#666' }}>{label}</div>
    </div>
  );
}

function Th({ children }) {
  return <th style={s.th}>{children}</th>;
}
function Td({ children, style }) {
  return <td style={{ ...s.td, ...style }}>{children}</td>;
}

// ── Styles ────────────────────────────────────────────────────────────────────

const s = {
  main:         { padding: '2rem', fontFamily: 'system-ui, sans-serif', maxWidth: '1100px' },
  pageHeader:   { display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1.5rem' },
  title:        { margin: 0, fontSize: '1.4rem', fontWeight: 600 },
  backBtn:      { padding: '0.35rem 0.75rem', background: 'none', border: '1px solid #ccc', borderRadius: '5px', cursor: 'pointer', fontSize: '0.85rem', color: '#555' },
  card:         { background: '#fff', border: '1px solid #e5e5e5', borderRadius: '8px', padding: '1.5rem', marginBottom: '1.5rem' },
  sectionTitle: { margin: '0 0 0.5rem', fontSize: '1.05rem', fontWeight: 600 },
  sectionDesc:  { margin: '0 0 0.75rem', fontSize: '0.875rem', color: '#555' },
  headerPreview:{ background: '#f5f5f5', border: '1px solid #e0e0e0', borderRadius: '5px', padding: '0.5rem 0.75rem', fontSize: '0.8rem', color: '#333', margin: '0 0 0.5rem', whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontFamily: 'monospace' },
  exportBtn:    { display: 'inline-block', padding: '0.45rem 1rem', background: '#0055cc', color: '#fff', borderRadius: '5px', textDecoration: 'none', fontWeight: 600, fontSize: '0.875rem' },
  uploadArea:   { display: 'flex', flexDirection: 'column', gap: '0.25rem' },
  dropZone:     { border: '2px dashed #ccc', borderRadius: '8px', padding: '1.25rem', textAlign: 'center', cursor: 'pointer', fontSize: '0.9rem', color: '#555', background: '#fafafa', userSelect: 'none' },
  errorMsg:     { fontSize: '0.8rem', color: '#c0392b', fontWeight: 600, padding: '0.4rem 0.6rem', background: '#fff0f0', border: '1px solid #ffcccc', borderRadius: '5px' },
  primaryBtn:   { padding: '0.5rem 1.25rem', background: '#0055cc', color: '#fff', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 600, fontSize: '0.875rem' },
  secondaryBtn: { padding: '0.5rem 1rem', background: '#fff', color: '#333', border: '1px solid #ccc', borderRadius: '5px', cursor: 'pointer', fontSize: '0.875rem' },
  statsBar:     { display: 'flex', gap: '1.5rem', padding: '0.75rem 0', borderBottom: '1px solid #eee', marginBottom: '0.75rem' },
  stat:         { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1px' },
  statValue:    { fontSize: '1.4rem', fontWeight: 700 },
  statLabel:    { fontSize: '0.72rem', color: '#888', textTransform: 'uppercase', letterSpacing: '0.04em' },
  bulkActions:  { display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem', fontSize: '0.82rem' },
  bulkLabel:    { fontWeight: 600, color: '#555' },
  bulkBtn:      { padding: '2px 10px', background: 'none', border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer', fontSize: '0.78rem', fontWeight: 600, color: '#333' },
  tableScroll:  { overflowX: 'auto', marginBottom: '0.75rem' },
  table:        { width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem' },
  th:           { padding: '0.5rem 0.6rem', background: '#f5f5f5', borderBottom: '2px solid #ddd', textAlign: 'left', fontWeight: 600, whiteSpace: 'nowrap', color: '#444' },
  td:           { padding: '0.4rem 0.6rem', borderBottom: '1px solid #eee', verticalAlign: 'middle' },
  badge:        { display: 'inline-block', fontSize: '0.7rem', fontWeight: 700, padding: '2px 7px', borderRadius: '4px', textTransform: 'uppercase', letterSpacing: '0.03em' },
  errorDetail:  { padding: '0.4rem 1rem 0.6rem 2rem', background: '#fff0f0', fontSize: '0.78rem', color: '#c0392b', borderBottom: '1px solid #ffcccc' },
  matchDetail:  { padding: '0.35rem 1rem 0.45rem 1.5rem', background: '#fffbeb', fontSize: '0.78rem', color: '#555', borderBottom: '1px solid #fcd34d' },
  matchLabel:   { fontWeight: 600, marginRight: '0.4rem' },
  matchReason:  { display: 'inline-block', padding: '1px 6px', background: '#fef3c7', color: '#92400e', borderRadius: '3px', fontSize: '0.7rem', fontWeight: 700, marginRight: '0.4rem' },
  radioLabel:   { fontSize: '0.78rem', display: 'inline-flex', alignItems: 'center', gap: '2px', cursor: 'pointer' },
  linkBtn:      { background: 'none', border: 'none', cursor: 'pointer', color: '#c0392b', fontSize: '0.78rem', fontWeight: 600, textDecoration: 'underline', padding: 0 },
  warnNote:     { fontSize: '0.82rem', color: '#92400e', background: '#fffbeb', border: '1px solid #fcd34d', borderRadius: '5px', padding: '0.5rem 0.75rem', marginBottom: '0.75rem' },
  footerBtns:   { display: 'flex', gap: '0.75rem', justifyContent: 'flex-end', marginTop: '0.75rem' },
  loadingMsg:   { color: '#666', padding: '2rem', textAlign: 'center', fontSize: '0.9rem' },
  resultPane:   { padding: '1rem 0' },
  resultTitle:  { fontSize: '1.1rem', fontWeight: 600, marginBottom: '1rem', color: '#1a7f37' },
  resultStats:  { display: 'flex', gap: '2rem', marginBottom: '1rem' },
  failureList:  { background: '#fff0f0', border: '1px solid #ffcccc', borderRadius: '6px', padding: '0.75rem 1rem', fontSize: '0.82rem' },
  failureRow:   { color: '#c0392b', padding: '2px 0' },
};
