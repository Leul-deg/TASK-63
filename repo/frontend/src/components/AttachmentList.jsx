import React, { useState } from 'react';
import { api } from '../api/client';

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatBytes(bytes) {
  if (bytes < 1024)         return `${bytes} B`;
  if (bytes < 1024 * 1024)  return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function typeIcon(contentType) {
  if (contentType?.includes('pdf'))   return '📄';
  if (contentType?.includes('image')) return '🖼';
  return '📎';
}

function formatDate(isoString) {
  if (!isoString) return '—';
  return new Date(isoString).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Displays a list of attachments for one agreement.
 *
 * Props:
 *   attachments    — array of AttachmentResponse objects
 *   downloadBase   — URL prefix for downloads,
 *                    e.g. `/api/residents/{r}/agreements/{a}/attachments`
 *   onDeleted      — called with the deleted attachment's id
 */
export default function AttachmentList({ attachments, downloadBase, onDeleted }) {
  if (!attachments || attachments.length === 0) {
    return <div style={styles.empty}>No attachments yet.</div>;
  }

  return (
    <ul style={styles.list}>
      {attachments.map(att => (
        <AttachmentRow
          key={att.id}
          attachment={att}
          downloadBase={downloadBase}
          onDeleted={onDeleted}
        />
      ))}
    </ul>
  );
}

// ── Row ───────────────────────────────────────────────────────────────────────

function AttachmentRow({ attachment: att, downloadBase, onDeleted }) {
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting,      setDeleting]      = useState(false);
  const [deleteErr,     setDeleteErr]     = useState('');

  const downloadUrl = `${downloadBase}/${att.id}/content`;

  const handleDelete = async () => {
    setDeleting(true);
    setDeleteErr('');
    try {
      await api.delete(`${downloadBase}/${att.id}`);
      onDeleted?.(att.id);
    } catch (err) {
      setDeleteErr(err.message || 'Delete failed.');
      setConfirmDelete(false);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <li style={styles.row}>
      {/* File icon + info */}
      <span style={styles.icon}>{typeIcon(att.contentType)}</span>
      <div style={styles.info}>
        <a
          href={downloadUrl}
          download={att.originalFilename}
          style={styles.filename}
          title="Download file"
        >
          {att.originalFilename}
        </a>
        <span style={styles.meta}>
          {formatBytes(att.fileSizeBytes)} · {att.contentType} · uploaded by {att.uploadedBy} · {formatDate(att.uploadedAt)}
        </span>
        {deleteErr && <span style={styles.deleteErr}>{deleteErr}</span>}
      </div>

      {/* Actions */}
      <div style={styles.actions}>
        <a href={downloadUrl} download={att.originalFilename} style={styles.downloadBtn}>
          ↓ Download
        </a>
        {!confirmDelete ? (
          <button style={styles.deleteBtn} onClick={() => setConfirmDelete(true)}>
            Delete
          </button>
        ) : (
          <span style={styles.confirmRow}>
            <span style={styles.confirmText}>Remove?</span>
            <button style={styles.confirmYes} onClick={handleDelete} disabled={deleting}>
              {deleting ? '…' : 'Yes'}
            </button>
            <button style={styles.confirmNo} onClick={() => setConfirmDelete(false)}>
              No
            </button>
          </span>
        )}
      </div>
    </li>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = {
  empty: {
    fontSize: '0.82rem',
    color: '#888',
    padding: '0.5rem 0',
  },
  list: {
    listStyle: 'none',
    margin: 0,
    padding: 0,
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  },
  row: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: '0.75rem',
    padding: '0.6rem 0.8rem',
    background: '#fff',
    border: '1px solid #e5e5e5',
    borderRadius: '6px',
  },
  icon: {
    fontSize: '1.3rem',
    lineHeight: 1,
    flexShrink: 0,
    paddingTop: '2px',
  },
  info: {
    flex: 1,
    minWidth: 0,
    display: 'flex',
    flexDirection: 'column',
    gap: '2px',
  },
  filename: {
    fontSize: '0.875rem',
    fontWeight: 600,
    color: '#0055cc',
    textDecoration: 'none',
    wordBreak: 'break-all',
  },
  meta: {
    fontSize: '0.72rem',
    color: '#888',
  },
  deleteErr: {
    fontSize: '0.72rem',
    color: '#c0392b',
    fontWeight: 600,
  },
  actions: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    flexShrink: 0,
  },
  downloadBtn: {
    fontSize: '0.78rem',
    color: '#0055cc',
    fontWeight: 600,
    textDecoration: 'none',
    border: '1px solid #d0e0f8',
    borderRadius: '4px',
    padding: '2px 8px',
    background: '#f0f6ff',
  },
  deleteBtn: {
    fontSize: '0.78rem',
    color: '#c0392b',
    background: 'none',
    border: '1px solid #f5c6cb',
    borderRadius: '4px',
    padding: '2px 8px',
    cursor: 'pointer',
    fontWeight: 600,
  },
  confirmRow: {
    display: 'flex',
    alignItems: 'center',
    gap: '4px',
  },
  confirmText: {
    fontSize: '0.75rem',
    color: '#c0392b',
    fontWeight: 600,
  },
  confirmYes: {
    fontSize: '0.75rem',
    background: '#c0392b',
    color: '#fff',
    border: 'none',
    borderRadius: '3px',
    padding: '2px 7px',
    cursor: 'pointer',
    fontWeight: 600,
  },
  confirmNo: {
    fontSize: '0.75rem',
    background: 'none',
    border: '1px solid #ccc',
    borderRadius: '3px',
    padding: '2px 7px',
    cursor: 'pointer',
    color: '#555',
  },
};
