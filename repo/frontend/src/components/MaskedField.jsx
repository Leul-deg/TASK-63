import React, { useState } from 'react';

/**
 * Displays a sensitive value with two levels of protection:
 *
 *  1. **Null / restricted** — the backend returned `null` because the caller
 *     lacks sufficient role. Shows a grey "Restricted" badge.
 *
 *  2. **Masked (default)** — the backend returned a real value but the UI
 *     shows bullets by default. An authorized user can click "Reveal" to
 *     see the plaintext. A second click re-masks it.
 *
 * Props:
 *  - `value`      — the decrypted string from the API, or `null` if restricted.
 *  - `canReveal`  — boolean; true when the current user may reveal the value.
 *  - `label`      — accessible label used for the reveal/hide button aria-label.
 *  - `emptyText`  — text shown when value is an empty string (default "—").
 */
export default function MaskedField({ value, canReveal, label = 'field', emptyText = '—' }) {
  const [revealed, setRevealed] = useState(false);

  // ── Case 1: backend withheld the value (null) ──────────────────────────
  if (value === null || value === undefined) {
    return (
      <span style={styles.badge}>
        Restricted
      </span>
    );
  }

  // ── Case 2: empty string ────────────────────────────────────────────────
  if (value === '') {
    return <span style={{ color: '#888' }}>{emptyText}</span>;
  }

  // ── Case 3: user cannot reveal — show permanent mask ───────────────────
  if (!canReveal) {
    return <span style={styles.masked}>••••••••</span>;
  }

  // ── Case 4: user can reveal but hasn't yet ──────────────────────────────
  if (!revealed) {
    return (
      <span style={styles.row}>
        <span style={styles.masked}>••••••••</span>
        <button
          style={styles.revealBtn}
          onClick={() => setRevealed(true)}
          aria-label={`Reveal ${label}`}
        >
          Reveal
        </button>
      </span>
    );
  }

  // ── Case 5: revealed ────────────────────────────────────────────────────
  return (
    <span style={styles.row}>
      <span>{value}</span>
      <button
        style={{ ...styles.revealBtn, color: '#c0392b' }}
        onClick={() => setRevealed(false)}
        aria-label={`Hide ${label}`}
      >
        Hide
      </button>
    </span>
  );
}

const styles = {
  row: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.5rem',
  },
  masked: {
    letterSpacing: '0.15em',
    color: '#555',
  },
  badge: {
    display: 'inline-block',
    fontSize: '0.7rem',
    fontWeight: 600,
    padding: '2px 6px',
    borderRadius: '4px',
    background: '#f0f0f0',
    color: '#888',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  revealBtn: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    fontSize: '0.75rem',
    fontWeight: 600,
    color: '#0055cc',
    padding: '0',
    textDecoration: 'underline',
  },
};
