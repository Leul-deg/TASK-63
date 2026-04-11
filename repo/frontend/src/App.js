import React from 'react';
import { BrowserRouter, Navigate, NavLink, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { canAccessAdmin, canViewResidentDirectory, isStudent, roleLabel } from './utils/roles';
import LoginPage                from './pages/LoginPage';
import ResidentsPage            from './pages/ResidentsPage';
import ResidentFormPage         from './pages/ResidentFormPage';
import ResidentAgreementsPage   from './pages/ResidentAgreementsPage';
import ResidentBookingsPage     from './pages/ResidentBookingsPage';
import ImportExportPage         from './pages/ImportExportPage';
import MessagesPage             from './pages/MessagesPage';
import NotificationsPage        from './pages/NotificationsPage';
import NotificationBell         from './components/NotificationBell';
import StudentSelfPage          from './pages/StudentSelfPage';
import BookingPolicyPage        from './pages/BookingPolicyPage';
import IntegrationKeysPage      from './pages/IntegrationKeysPage';
import CrawlerPage              from './pages/CrawlerPage';
import AnalyticsDashboard       from './pages/AnalyticsDashboard';
import UserManagementPage       from './pages/UserManagementPage';

// ── Route guard ───────────────────────────────────────────────────────────

/**
 * Redirects unauthenticated users to /login.
 * Renders a blank screen while the auth state is being resolved
 * (avoids a flash of the login page on refresh).
 */
function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (!user)   return <Navigate to="/login" replace />;
  return children;
}

/**
 * Redirects already-authenticated users away from /login.
 */
function RedirectIfAuthed({ children }) {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (user)    return <Navigate to="/" replace />;
  return children;
}

function RequireAdmin({ children }) {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (!canAccessAdmin(user)) return <Navigate to="/" replace />;
  return children;
}

/**
 * Redirects Students away from staff-only resident directory routes.
 * Students access their own record through /me instead.
 */
function RequireStaff({ children }) {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (!canViewResidentDirectory(user)) return <Navigate to="/me" replace />;
  return children;
}

function HomeRoute() {
  const { user, loading } = useAuth();
  if (loading) return null;
  return <Navigate to={isStudent(user) ? '/me' : '/residents'} replace />;
}

// ── Sidebar layout ────────────────────────────────────────────────────────

function Layout({ children }) {
  const { user, logout } = useAuth();

  const studentMode = isStudent(user);

  const navItems = [
    // Students see their own profile instead of the resident directory
    ...(studentMode
      ? [{ to: '/me', label: 'My Profile' }]
      : [
          { to: '/residents',              label: 'Residents'      },
          { to: '/residents/import-export', label: 'Import / Export' },
        ]),
    { to: '/messages',       label: 'Messages'      },
    { to: '/notifications',  label: 'Notifications' },
  ];

  const isAdmin = canAccessAdmin(user);
  const adminNavItems = [
    { to: '/admin/users',             label: 'User Management' },
    { to: '/admin/booking-policy',    label: 'Booking Policy' },
    { to: '/admin/integration-keys',  label: 'Integration Keys' },
    { to: '/admin/crawler',           label: 'Data Collector' },
    { to: '/admin/analytics',         label: 'Analytics' },
  ];

  const linkStyle = ({ isActive }) => ({
    display: 'block',
    padding: '0.5rem 1rem',
    textDecoration: 'none',
    color:      isActive ? '#0055cc' : '#333',
    fontWeight: isActive ? 'bold'   : 'normal',
    background: isActive ? '#e8f0fe': 'transparent',
    borderRadius: '4px',
    fontSize: '0.9rem',
  });

  return (
    <div style={{ display: 'flex', minHeight: '100vh', fontFamily: 'system-ui, sans-serif' }}>
      {/* Sidebar */}
      <nav style={styles.sidebar}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.2rem' }}>
          <div style={styles.sidebarTitle}>ResLife Portal</div>
          <NotificationBell />
        </div>

        {navItems.map(({ to, label }) => (
          <NavLink key={to} to={to} end={to === '/'} style={linkStyle}>
            {label}
          </NavLink>
        ))}

        {isAdmin && (
          <>
            <div style={styles.navSectionLabel}>Admin</div>
            {adminNavItems.map(({ to, label }) => (
              <NavLink key={to} to={to} style={linkStyle}>
                {label}
              </NavLink>
            ))}
          </>
        )}

        {/* User info + logout at the bottom */}
        {user && (
          <div style={styles.userArea}>
            <div style={styles.userName}>
              {user.firstName} {user.lastName}
            </div>
            {user.roles?.map(r => (
              <div key={r} style={styles.roleChip}>{roleLabel(r)}</div>
            ))}
            <button style={styles.logoutBtn} onClick={logout}>
              Sign out
            </button>
          </div>
        )}
      </nav>

      {/* Main content */}
      <div style={{ flex: 1, minWidth: 0 }}>{children}</div>
    </div>
  );
}

// ── Root app ──────────────────────────────────────────────────────────────

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public */}
          <Route
            path="/login"
            element={
              <RedirectIfAuthed>
                <LoginPage />
              </RedirectIfAuthed>
            }
          />

          {/* Protected — all inside the sidebar layout */}
          <Route
            path="/*"
            element={
              <RequireAuth>
                <Layout>
                  <Routes>
                    <Route path="/"                       element={<HomeRoute />} />
                    <Route path="/me"                         element={<StudentSelfPage />} />
                    <Route path="/residents"              element={<RequireStaff><ResidentsPage /></RequireStaff>} />
                    <Route path="/residents/new"              element={<RequireStaff><ResidentFormPage /></RequireStaff>} />
                    <Route path="/residents/:id/edit"         element={<RequireStaff><ResidentFormPage /></RequireStaff>} />
                    <Route path="/residents/:id/agreements"   element={<RequireStaff><ResidentAgreementsPage /></RequireStaff>} />
                    <Route path="/residents/:id/bookings"     element={<RequireStaff><ResidentBookingsPage /></RequireStaff>} />
                    <Route path="/residents/import-export"   element={<RequireStaff><ImportExportPage /></RequireStaff>} />
                    <Route path="/messages"                  element={<MessagesPage />} />
                    <Route path="/notifications"             element={<NotificationsPage />} />
                    <Route path="/admin/users"            element={<RequireAdmin><UserManagementPage /></RequireAdmin>} />
                    <Route path="/admin/booking-policy"   element={<RequireAdmin><BookingPolicyPage /></RequireAdmin>} />
                    <Route path="/admin/integration-keys" element={<RequireAdmin><IntegrationKeysPage /></RequireAdmin>} />
                    <Route path="/admin/crawler"          element={<RequireAdmin><CrawlerPage /></RequireAdmin>} />
                    <Route path="/admin/analytics"        element={<RequireAdmin><AnalyticsDashboard /></RequireAdmin>} />
                  </Routes>
                </Layout>
              </RequireAuth>
            }
          />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────

const styles = {
  sidebar: {
    width: '220px',
    minWidth: '220px',
    borderRight: '1px solid #ddd',
    padding: '1rem',
    background: '#fafafa',
    display: 'flex',
    flexDirection: 'column',
    gap: '2px',
  },
  sidebarTitle: {
    fontWeight: 700,
    fontSize: '1rem',
    color: '#111',
  },
  userArea: {
    marginTop: 'auto',
    paddingTop: '1.5rem',
    borderTop: '1px solid #eee',
  },
  userName: {
    fontWeight: 600,
    fontSize: '0.85rem',
    color: '#111',
    marginBottom: '4px',
  },
  roleChip: {
    display: 'inline-block',
    fontSize: '0.7rem',
    fontWeight: 600,
    padding: '2px 6px',
    borderRadius: '4px',
    background: '#e8f0fe',
    color: '#0055cc',
    marginBottom: '8px',
  },
  navSectionLabel: {
    fontSize: '0.68rem',
    fontWeight: 700,
    letterSpacing: '0.06em',
    textTransform: 'uppercase',
    color: '#999',
    padding: '0.8rem 1rem 0.2rem',
  },
  logoutBtn: {
    width: '100%',
    padding: '0.4rem',
    fontSize: '0.8rem',
    fontWeight: 600,
    background: 'none',
    border: '1px solid #ddd',
    borderRadius: '4px',
    cursor: 'pointer',
    color: '#555',
    marginTop: '4px',
  },
};
