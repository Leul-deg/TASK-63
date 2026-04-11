import React, { useRef, useState } from 'react';
import { api } from '../api/client';

// ── Constants ─────────────────────────────────────────────────────────────────

const MAX_SIZE_BYTES = 15 * 1024 * 1024; // 15 MB

const ALLOWED_TYPES = new Set([
  'application/pdf',
  'image/jpeg',
  'image/jpg',   // non-standard alias some OS report
  'image/png',
]);

const ALLOWED_EXTENSIONS = new Set(['pdf', 'jpg', 'jpeg', 'png']);

const TYPE_LABELS = {
  'application/pdf': 'PDF',
  'image/jpeg': 'JPEG',
  'image/jpg':  'JPEG',
  'image/png':  'PNG',
};

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatBytes(bytes) {
  if (bytes < 1024)        return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function getExtension(filename) {
  const dot = filename.lastIndexOf('.');
  return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : '';
}

function typeIcon(contentType) {
  if (contentType?.includes('pdf'))   return '📄';
  if (contentType?.includes('image')) return '🖼';
  return '📎';
}

/**
 * Client-side file validation.
 * Returns an error string, or null if the file is acceptable.
 */
function validateFile(file) {
  const ext = getExtension(file.name);
  const typeOk  = ALLOWED_TYPES.has(file.type) || ALLOWED_EXTENSIONS.has(ext);
  const extOk   = ALLOWED_EXTENSIONS.has(ext);

  if (!typeOk || !extOk) {
    return 'Only PDF, JPEG, and PNG files are accepted.';
  }
  if (file.size > MAX_SIZE_BYTES) {
    return `File is too large (${formatBytes(file.size)}). Maximum size is 15 MB.`;
  }
  if (file.size === 0) {
    return 'File is empty.';
  }
  return null;
}

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * Drag-and-drop / click-to-upload component for agreement attachments.
 *
 * Props:
 *   uploadUrl    — POST endpoint (e.g. /api/residents/{r}/agreements/{a}/attachments)
 *   onUploaded   — called with the new AttachmentResponse on success
 */
export default function AttachmentUploader({ uploadUrl, onUploaded }) {
  const inputRef          = useRef(null);
  const [dragging, setDragging]   = useState(false);
  const [staged,   setStaged]     = useState(null);    // File object
  const [error,    setError]      = useState('');
  const [uploading, setUploading] = useState(false);
  const [success,  setSuccess]    = useState(false);

  // ── File selection ──────────────────────────────────────────────────────

  const handleFiles = (files) => {
    setSuccess(false);
    const file = files[0];
    if (!file) return;
    const err = validateFile(file);
    if (err) { setError(err); setStaged(null); return; }
    setError('');
    setStaged(file);
  };

  const onInputChange = (e) => handleFiles(e.target.files);

  const onDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    handleFiles(e.dataTransfer.files);
  };

  const onDragOver = (e) => { e.preventDefault(); setDragging(true);  };
  const onDragLeave = ()  => setDragging(false);

  const onZoneClick = () => {
    if (!uploading) inputRef.current?.click();
  };

  // ── Upload ──────────────────────────────────────────────────────────────

  const handleUpload = async () => {
    if (!staged || uploading) return;
    setUploading(true);
    setError('');
    try {
      const form = new FormData();
      form.append('file', staged);
      const result = await api.upload(uploadUrl, form);
      setStaged(null);
      setSuccess(true);
      onUploaded?.(result);
    } catch (err) {
      setError(err.message || 'Upload failed. Please try again.');
    } finally {
      setUploading(false);
    }
  };

  const handleCancel = () => {
    setStaged(null);
    setError('');
    setSuccess(false);
    if (inputRef.current) inputRef.current.value = '';
  };

  // ── Render ──────────────────────────────────────────────────────────────

  return (
    <div style={styles.wrapper}>
      {/* Drop zone */}
      <div
        style={{
          ...styles.dropZone,
          ...(dragging    ? styles.dropZoneActive : {}),
          ...(staged      ? styles.dropZoneStaged : {}),
          ...(uploading   ? styles.dropZoneUploading : {}),
        }}
        onClick={onZoneClick}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        role="button"
        tabIndex={0}
        aria-label="Upload file"
        onKeyDown={e => e.key === 'Enter' && onZoneClick()}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
          style={{ display: 'none' }}
          onChange={onInputChange}
        />

        {!staged && !uploading && (
          <div style={styles.prompt}>
            <span style={styles.icon}>⬆</span>
            <span>Drag a file here or <u>click to browse</u></span>
            <span style={styles.hint}>PDF, JPEG, or PNG · max 15 MB</span>
          </div>
        )}

        {staged && !uploading && (
          <div style={styles.staged}>
            <span style={styles.fileIcon}>{typeIcon(staged.type)}</span>
            <div style={styles.fileInfo}>
              <span style={styles.fileName}>{staged.name}</span>
              <span style={styles.fileMeta}>
                {TYPE_LABELS[staged.type] ?? getExtension(staged.name).toUpperCase()}
                &nbsp;·&nbsp;{formatBytes(staged.size)}
              </span>
            </div>
          </div>
        )}

        {uploading && (
          <div style={styles.prompt}>
            <span style={{ fontSize: '1.4rem' }}>⏳</span>
            <span>Uploading…</span>
          </div>
        )}
      </div>

      {/* Validation / error message */}
      {error && (
        <div style={styles.error} role="alert">
          ⚠ {error}
        </div>
      )}

      {/* Success message */}
      {success && !staged && (
        <div style={styles.successMsg}>✓ File uploaded successfully.</div>
      )}

      {/* Action buttons */}
      {staged && !uploading && (
        <div style={styles.actions}>
          <button style={styles.uploadBtn} onClick={handleUpload}>Upload file</button>
          <button style={styles.cancelBtn} onClick={handleCancel}>Cancel</button>
        </div>
      )}
    </div>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = {
  wrapper: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
  },
  dropZone: {
    border: '2px dashed #ccc',
    borderRadius: '8px',
    padding: '1.25rem 1rem',
    textAlign: 'center',
    cursor: 'pointer',
    transition: 'border-color 0.15s, background 0.15s',
    background: '#fafafa',
    userSelect: 'none',
  },
  dropZoneActive: {
    borderColor: '#0055cc',
    background: '#e8f0fe',
  },
  dropZoneStaged: {
    borderColor: '#1a7f37',
    background: '#f0faf4',
    cursor: 'default',
  },
  dropZoneUploading: {
    borderColor: '#aaa',
    background: '#f5f5f5',
    cursor: 'not-allowed',
  },
  prompt: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '4px',
    fontSize: '0.875rem',
    color: '#555',
  },
  icon: {
    fontSize: '1.5rem',
    marginBottom: '2px',
  },
  hint: {
    fontSize: '0.72rem',
    color: '#999',
    marginTop: '2px',
  },
  staged: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    justifyContent: 'center',
  },
  fileIcon: {
    fontSize: '1.8rem',
    lineHeight: 1,
  },
  fileInfo: {
    display: 'flex',
    flexDirection: 'column',
    gap: '2px',
    textAlign: 'left',
  },
  fileName: {
    fontSize: '0.875rem',
    fontWeight: 600,
    color: '#222',
    wordBreak: 'break-all',
    maxWidth: '280px',
  },
  fileMeta: {
    fontSize: '0.75rem',
    color: '#666',
  },
  error: {
    fontSize: '0.8rem',
    color: '#c0392b',
    fontWeight: 600,
    padding: '0.4rem 0.6rem',
    background: '#fff0f0',
    borderRadius: '5px',
    border: '1px solid #ffcccc',
  },
  successMsg: {
    fontSize: '0.8rem',
    color: '#1a7f37',
    fontWeight: 600,
    padding: '0.4rem 0.6rem',
    background: '#f0faf4',
    borderRadius: '5px',
    border: '1px solid #b7e4c7',
  },
  actions: {
    display: 'flex',
    gap: '0.5rem',
  },
  uploadBtn: {
    padding: '0.4rem 1rem',
    background: '#0055cc',
    color: '#fff',
    border: 'none',
    borderRadius: '5px',
    cursor: 'pointer',
    fontWeight: 600,
    fontSize: '0.85rem',
  },
  cancelBtn: {
    padding: '0.4rem 0.75rem',
    background: 'none',
    color: '#666',
    border: '1px solid #ccc',
    borderRadius: '5px',
    cursor: 'pointer',
    fontSize: '0.85rem',
  },
};
