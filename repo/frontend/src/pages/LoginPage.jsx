import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const { login, error, setError } = useAuth();
  const navigate = useNavigate();

  const [identifier, setIdentifier] = useState('');
  const [password,   setPassword]   = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [localError, setLocalError] = useState('');

  async function handleSubmit(e) {
    e.preventDefault();
    setLocalError('');
    setError(null);
    setSubmitting(true);
    try {
      await login(identifier.trim(), password);
      navigate('/', { replace: true });
    } catch (err) {
      setLocalError(err.message || 'Login failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  const displayError = localError || error;

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <div style={styles.logo}>ResLife Portal</div>
        <h1 style={styles.heading}>Sign in</h1>

        {displayError && (
          <div style={styles.errorBox} role="alert">
            {displayError}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <label style={styles.label} htmlFor="identifier">
            Username or email
          </label>
          <input
            id="identifier"
            style={styles.input}
            type="text"
            autoComplete="username"
            autoFocus
            required
            value={identifier}
            onChange={e => setIdentifier(e.target.value)}
          />

          <label style={styles.label} htmlFor="password">
            Password
          </label>
          <input
            id="password"
            style={styles.input}
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={e => setPassword(e.target.value)}
          />

          <button
            type="submit"
            style={{
              ...styles.button,
              opacity: submitting ? 0.6 : 1,
              cursor:  submitting ? 'not-allowed' : 'pointer',
            }}
            disabled={submitting}
          >
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  );
}

const styles = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: '#f4f6f9',
    fontFamily: 'system-ui, sans-serif',
  },
  card: {
    background: '#fff',
    borderRadius: '8px',
    boxShadow: '0 2px 12px rgba(0,0,0,0.1)',
    padding: '2.5rem 2rem',
    width: '100%',
    maxWidth: '380px',
  },
  logo: {
    fontWeight: 700,
    fontSize: '1rem',
    color: '#0055cc',
    marginBottom: '1rem',
    letterSpacing: '0.02em',
  },
  heading: {
    margin: '0 0 1.5rem',
    fontSize: '1.4rem',
    fontWeight: 600,
    color: '#111',
  },
  label: {
    display: 'block',
    fontSize: '0.875rem',
    fontWeight: 500,
    color: '#333',
    marginBottom: '4px',
  },
  input: {
    display: 'block',
    width: '100%',
    padding: '0.55rem 0.75rem',
    fontSize: '0.95rem',
    border: '1px solid #ccc',
    borderRadius: '5px',
    marginBottom: '1rem',
    boxSizing: 'border-box',
    outline: 'none',
  },
  button: {
    display: 'block',
    width: '100%',
    padding: '0.65rem',
    fontSize: '0.95rem',
    fontWeight: 600,
    background: '#0055cc',
    color: '#fff',
    border: 'none',
    borderRadius: '5px',
    marginTop: '0.5rem',
  },
  errorBox: {
    background: '#fff0f0',
    border: '1px solid #ffcccc',
    borderRadius: '5px',
    padding: '0.6rem 0.8rem',
    color: '#c0392b',
    fontSize: '0.875rem',
    marginBottom: '1.2rem',
  },
};
