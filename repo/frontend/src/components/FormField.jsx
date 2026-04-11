import React from 'react';

/**
 * Labelled form field with inline error display and required marker.
 *
 * Props:
 *   label      — visible label text
 *   required   — shows a red asterisk when true
 *   error      — error string; renders below the input when set
 *   hint       — helper text (e.g. format description)
 *   children   — the actual <input>, <select>, or <textarea> element
 */
export default function FormField({ label, required, error, hint, children, style }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '3px', ...style }}>
      {label && (
        <label style={styles.label}>
          {label}
          {required && <span style={styles.required} aria-hidden="true"> *</span>}
        </label>
      )}
      {React.cloneElement(children, {
        style: {
          ...inputBaseStyle,
          borderColor: error ? '#c0392b' : '#ccc',
          ...(children.props.style ?? {}),
        },
        'aria-invalid': error ? 'true' : undefined,
      })}
      {hint && !error && <span style={styles.hint}>{hint}</span>}
      {error && <span style={styles.error} role="alert">{error}</span>}
    </div>
  );
}

const inputBaseStyle = {
  padding: '0.45rem 0.65rem',
  fontSize: '0.9rem',
  border: '1px solid #ccc',
  borderRadius: '5px',
  outline: 'none',
  width: '100%',
  boxSizing: 'border-box',
};

const styles = {
  label: {
    fontSize: '0.82rem',
    fontWeight: 600,
    color: '#333',
  },
  required: {
    color: '#c0392b',
    fontWeight: 700,
  },
  hint: {
    fontSize: '0.75rem',
    color: '#888',
  },
  error: {
    fontSize: '0.75rem',
    color: '#c0392b',
    fontWeight: 600,
  },
};
