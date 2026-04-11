import React, { useEffect, useState } from 'react';
import { api } from '../api/client';

export default function StudentSelfPage() {
  const [resident, setResident] = useState(null);
  const [bookings, setBookings] = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState('');

  useEffect(() => {
    Promise.all([
      api.get('/api/students/me'),
      api.get('/api/students/me/bookings').catch(() => []),
    ])
      .then(([residentData, bookingData]) => {
        setResident(residentData);
        setBookings(bookingData ?? []);
      })
      .catch(err  => setError(err.message ?? 'Failed to load your profile.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <main style={styles.main}><p style={styles.muted}>Loading…</p></main>;

  if (error) {
    return (
      <main style={styles.main}>
        <h2 style={styles.title}>My Profile</h2>
        <div style={styles.errorBox}>{error}</div>
      </main>
    );
  }

  if (!resident) return null;

  return (
    <main style={styles.main}>
      <h2 style={styles.title}>My Profile</h2>
      <p style={styles.subtitle}>Your residential record — read-only view.</p>

      <div style={styles.card}>
        <Section label="Name"              value={`${resident.firstName ?? ''} ${resident.lastName ?? ''}`.trim() || '—'} />
        <Section label="Student ID"        value={resident.studentId      ?? '—'} />
        <Section label="Email"             value={resident.email          ?? '—'} />
        <Section label="Phone"             value={resident.phone          ?? '—'} />
        <Section label="Date of Birth"     value="Restricted" />
        <Section label="Enrollment Status" value={resident.enrollmentStatus ?? '—'} />
        <Section label="Department"        value={resident.department     ?? '—'} />
        <Section label="Class Year"        value={resident.classYear      ?? '—'} />
        <Section label="Room"              value={resident.roomNumber     ?? '—'} />
        <Section label="Building"          value={resident.buildingName   ?? '—'} />
      </div>

      <div style={{ ...styles.card, marginTop: '1rem' }}>
        <h3 style={{ margin: '0 0 0.75rem', fontSize: '1rem' }}>My Bookings</h3>
        {bookings.length === 0 ? (
          <p style={styles.muted}>No bookings recorded yet.</p>
        ) : (
          <table style={styles.table}>
            <thead>
              <tr>
                <th style={styles.th}>Date</th>
                <th style={styles.th}>Building</th>
                <th style={styles.th}>Room</th>
                <th style={styles.th}>Status</th>
              </tr>
            </thead>
            <tbody>
              {bookings.map(booking => (
                <tr key={booking.id}>
                  <td style={styles.td}>{booking.requestedDate}</td>
                  <td style={styles.td}>{booking.buildingName}</td>
                  <td style={styles.td}>{booking.roomNumber ?? '—'}</td>
                  <td style={styles.td}>{booking.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <p style={styles.note}>
        To update your information, contact your Residence Life office.
      </p>
    </main>
  );
}

function Section({ label, value }) {
  return (
    <div style={styles.row}>
      <span style={styles.label}>{label}</span>
      <span style={styles.value}>{value}</span>
    </div>
  );
}

const styles = {
  main: {
    padding: '2rem',
    fontFamily: 'system-ui, sans-serif',
    maxWidth: '600px',
  },
  title: {
    margin: '0 0 0.25rem',
    fontSize: '1.4rem',
    fontWeight: 600,
  },
  subtitle: {
    margin: '0 0 1.5rem',
    color: '#666',
    fontSize: '0.875rem',
  },
  card: {
    border: '1px solid #ddd',
    borderRadius: '6px',
    overflow: 'hidden',
  },
  row: {
    display: 'flex',
    padding: '0.65rem 1rem',
    borderBottom: '1px solid #f0f0f0',
    fontSize: '0.9rem',
  },
  label: {
    width: '160px',
    minWidth: '160px',
    fontWeight: 600,
    color: '#444',
  },
  value: {
    color: '#111',
  },
  muted: {
    color: '#888',
  },
  errorBox: {
    background: '#fff0f0',
    border: '1px solid #ffcccc',
    borderRadius: '5px',
    padding: '0.75rem 1rem',
    color: '#c0392b',
    fontSize: '0.875rem',
  },
  note: {
    marginTop: '1.25rem',
    fontSize: '0.8rem',
    color: '#888',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    fontSize: '0.86rem',
  },
  th: {
    textAlign: 'left',
    padding: '0.45rem 0.55rem',
    borderBottom: '2px solid #eee',
    color: '#555',
    fontWeight: 600,
  },
  td: {
    padding: '0.5rem 0.55rem',
    borderBottom: '1px solid #f0f0f0',
  },
};
