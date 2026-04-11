import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from '../api/client';

const AuthContext = createContext(null);

/**
 * Provides the current authenticated user and auth actions (login / logout)
 * to the entire component tree.
 *
 * On mount it calls GET /api/auth/csrf (forces the XSRF-TOKEN cookie to be
 * set) then GET /api/auth/me (restores an existing session if present).
 */
export function AuthProvider({ children }) {
  const [user, setUser]       = useState(null);   // LoginResponse | null
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  // -----------------------------------------------------------------------
  // Bootstrap: restore session on page load
  // -----------------------------------------------------------------------
  useEffect(() => {
    (async () => {
      try {
        // Prime the CSRF cookie before any mutating request
        await api.get('/api/auth/csrf');
        // Restore existing session (returns 401 if none — caught below)
        const me = await api.get('/api/auth/me');
        setUser(me);
      } catch {
        // Not logged in — this is the normal unauthenticated state
        setUser(null);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // -----------------------------------------------------------------------
  // Login
  // -----------------------------------------------------------------------
  const login = useCallback(async (identifier, password) => {
    setError(null);
    const me = await api.post('/api/auth/login', { identifier, password });
    setUser(me);
    return me;
  }, []);

  // -----------------------------------------------------------------------
  // Logout
  // -----------------------------------------------------------------------
  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout');
    } finally {
      setUser(null);
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, error, setError, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

/** Hook — throws if used outside <AuthProvider>. */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
