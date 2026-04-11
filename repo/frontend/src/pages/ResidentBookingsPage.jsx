import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';

const STATUS_META = {
  REQUESTED: { color: '#555', bg: '#f0f0f0' },
  CONFIRMED: { color: '#0a7', bg: '#f0fdf4' },
  COMPLETED: { color: '#0055cc', bg: '#f0f4ff' },
  CANCELLED: { color: '#c33', bg: '#fff0f0' },
  NO_SHOW: { color: '#c80', bg: '#fff8e8' },
};

const NEXT_ACTIONS = [
  { value: 'CONFIRMED', label: 'Confirm' },
  { value: 'COMPLETED', label: 'Complete' },
  { value: 'NO_SHOW', label: 'Mark no-show' },
  { value: 'CANCELLED', label: 'Cancel' },
];

function statusBadge(status) {
  const meta = STATUS_META[status] || STATUS_META.REQUESTED;
  return {
    display: 'inline-block',
    padding: '2px 8px',
    borderRadius: 4,
    fontSize: '0.72rem',
    fontWeight: 700,
    color: meta.color,
    background: meta.bg,
  };
}

export default function ResidentBookingsPage() {
  const { id: residentId } = useParams();
  const navigate = useNavigate();

  const [resident, setResident] = useState(null);
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    requestedDate: '',
    buildingName: '',
    roomNumber: '',
    purpose: '',
    notes: '',
  });

  const load = useCallback(() => {
    setLoading(true);
    setLoadError('');
    Promise.all([
      api.get(`/api/residents/${residentId}`),
      api.get(`/api/residents/${residentId}/bookings`),
    ])
      .then(([residentData, bookingData]) => {
        setResident(residentData);
        setBookings(bookingData ?? []);
        setForm(f => ({
          ...f,
          buildingName: f.buildingName || residentData.buildingName || '',
          roomNumber: f.roomNumber || residentData.roomNumber || '',
        }));
      })
      .catch(err => setLoadError(err.message || 'Failed to load bookings.'))
      .finally(() => setLoading(false));
  }, [residentId]);

  useEffect(() => {
    load();
  }, [load]);

  const residentName = useMemo(() => {
    if (!resident) return residentId;
    return `${resident.firstName ?? ''} ${resident.lastName ?? ''}`.trim() || residentId;
  }, [resident, residentId]);

  async function createBooking() {
    if (!form.requestedDate || !form.buildingName.trim()) {
      setSubmitError('Requested date and building are required.');
      return;
    }
    setSaving(true);
    setSubmitError('');
    try {
      const created = await api.post(`/api/residents/${residentId}/bookings`, {
        requestedDate: form.requestedDate,
        buildingName: form.buildingName.trim(),
        roomNumber: form.roomNumber.trim() || null,
        purpose: form.purpose.trim() || null,
        notes: form.notes.trim() || null,
      });
      setBookings(prev => [created, ...prev]);
      setForm(f => ({ ...f, requestedDate: '', purpose: '', notes: '' }));
    } catch (err) {
      setSubmitError(err.message || 'Failed to create booking.');
    } finally {
      setSaving(false);
    }
  }

  async function updateStatus(bookingId, status) {
    const reason = window.prompt(`Optional reason for ${status.toLowerCase()}:`) || '';
    try {
      const updated = await api.patch(`/api/residents/${residentId}/bookings/${bookingId}/status`, {
        status,
        reason: reason.trim() || null,
      });
      setBookings(prev => prev.map(b => (b.id === bookingId ? updated : b)));
    } catch (err) {
      setSubmitError(err.message || 'Failed to update booking status.');
    }
  }

  if (loading) return <main style={s.main}><p style={s.muted}>Loading…</p></main>;
  if (loadError) return <main style={s.main}><div style={s.error}>{loadError}</div></main>;

  return (
    <main style={s.main}>
      <div style={s.header}>
        <button style={s.backBtn} onClick={() => navigate('/residents')}>← Back</button>
        <div>
          <h2 style={s.title}>Resident Bookings</h2>
          <div style={s.subtitle}>{residentName}</div>
        </div>
      </div>

      <section style={s.card}>
        <h3 style={s.sectionTitle}>Create Booking</h3>
        <div style={s.grid2}>
          <Field label="Requested date" required>
            <input
              type="date"
              aria-label="Requested date"
              value={form.requestedDate}
              onChange={e => setForm(f => ({ ...f, requestedDate: e.target.value }))}
            />
          </Field>
          <Field label="Building" required>
            <input
              aria-label="Building"
              value={form.buildingName}
              onChange={e => setForm(f => ({ ...f, buildingName: e.target.value }))}
              placeholder="Residence hall"
            />
          </Field>
        </div>
        <div style={s.grid2}>
          <Field label="Room number">
            <input
              aria-label="Room number"
              value={form.roomNumber}
              onChange={e => setForm(f => ({ ...f, roomNumber: e.target.value }))}
              placeholder="Optional room"
            />
          </Field>
          <Field label="Purpose">
            <input
              aria-label="Purpose"
              value={form.purpose}
              onChange={e => setForm(f => ({ ...f, purpose: e.target.value }))}
              placeholder="Family visit, early arrival, etc."
            />
          </Field>
        </div>
        <Field label="Notes">
          <textarea
            aria-label="Notes"
            rows={3}
            value={form.notes}
            onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
          />
        </Field>
        {submitError && <div style={s.error}>{submitError}</div>}
        <button style={s.primaryBtn} onClick={createBooking} disabled={saving}>
          {saving ? 'Saving…' : 'Create booking'}
        </button>
      </section>

      <section style={s.card}>
        <h3 style={s.sectionTitle}>Booking History</h3>
        {bookings.length === 0 ? (
          <p style={s.muted}>No bookings yet.</p>
        ) : (
          <table style={s.table}>
            <thead>
              <tr>
                <th style={s.th}>Date</th>
                <th style={s.th}>Building</th>
                <th style={s.th}>Room</th>
                <th style={s.th}>Purpose</th>
                <th style={s.th}>Status</th>
                <th style={s.th}>Reason</th>
                <th style={s.th}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {bookings.map(booking => (
                <tr key={booking.id}>
                  <td style={s.td}>{booking.requestedDate}</td>
                  <td style={s.td}>{booking.buildingName}</td>
                  <td style={s.td}>{booking.roomNumber || '—'}</td>
                  <td style={s.td}>{booking.purpose || '—'}</td>
                  <td style={s.td}><span style={statusBadge(booking.status)}>{booking.status}</span></td>
                  <td style={s.td}>{booking.decisionReason || '—'}</td>
                  <td style={s.td}>
                    <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                      {NEXT_ACTIONS.filter(action => action.value !== booking.status).map(action => (
                        <button
                          key={action.value}
                          style={s.smallBtn}
                          onClick={() => updateStatus(booking.id, action.value)}
                        >
                          {action.label}
                        </button>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}

function Field({ label, required, children }) {
  return (
    <div style={s.field}>
      <label style={s.label}>
        {label} {required ? <span style={{ color: '#c33' }}>*</span> : null}
      </label>
      {children}
    </div>
  );
}

const s = {
  main: { padding: '2rem', maxWidth: 960, fontFamily: 'system-ui, sans-serif' },
  header: { display: 'flex', alignItems: 'flex-start', gap: '1rem', marginBottom: '1.5rem' },
  title: { margin: 0, fontSize: '1.4rem', fontWeight: 600 },
  subtitle: { color: '#666', fontSize: '0.9rem', marginTop: '2px' },
  card: { border: '1px solid #e5e5e5', borderRadius: 8, background: '#fff', padding: '1.25rem', marginBottom: '1rem' },
  sectionTitle: { margin: '0 0 0.75rem', fontSize: '1rem', fontWeight: 600 },
  grid2: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' },
  field: { display: 'flex', flexDirection: 'column', gap: 4, marginBottom: '0.75rem' },
  label: { fontSize: '0.82rem', fontWeight: 600, color: '#333' },
  primaryBtn: { padding: '0.5rem 1.25rem', background: '#0055cc', color: '#fff', border: 'none', borderRadius: 5, cursor: 'pointer', fontWeight: 600 },
  smallBtn: { padding: '2px 8px', background: '#fff', color: '#0055cc', border: '1px solid #cdd8ee', borderRadius: 4, cursor: 'pointer', fontSize: '0.76rem', fontWeight: 600 },
  backBtn: { padding: '0.35rem 0.75rem', background: 'none', border: '1px solid #ccc', borderRadius: 5, cursor: 'pointer', fontSize: '0.85rem', color: '#555' },
  muted: { color: '#888' },
  error: { background: '#fff0f0', border: '1px solid #ffcccc', borderRadius: 5, padding: '0.6rem 0.8rem', color: '#c0392b', fontSize: '0.85rem', marginBottom: '0.75rem' },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '0.86rem' },
  th: { textAlign: 'left', padding: '0.5rem 0.6rem', borderBottom: '2px solid #eee', color: '#555', fontWeight: 600 },
  td: { padding: '0.55rem 0.6rem', borderBottom: '1px solid #f0f0f0', verticalAlign: 'top' },
};
