import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';

// ── Helpers ───────────────────────────────────────────────────────────────

function fmt(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function pct(v) {
  if (v == null) return '0%';
  return `${Number(v).toFixed(1)}%`;
}

// ── Reusable primitives ───────────────────────────────────────────────────

function StatCard({ label, value, sub, color = '#111', bg = '#fff' }) {
  return (
    <div style={{ padding: '1rem 1.2rem', border: '1px solid #eee', borderRadius: 8, background: bg, minWidth: 110 }}>
      <div style={{ fontSize: '1.8rem', fontWeight: 700, color, lineHeight: 1.1 }}>{value}</div>
      <div style={{ fontSize: '0.8rem', color: '#666', marginTop: 3 }}>{label}</div>
      {sub && <div style={{ fontSize: '0.72rem', color: '#aaa', marginTop: 2 }}>{sub}</div>}
    </div>
  );
}

function PercentBar({ value = 0, color = '#0055cc', bg = '#e8edf8' }) {
  const w = Math.min(Math.max(value, 0), 100);
  return (
    <div style={{ background: bg, borderRadius: 99, height: 10, overflow: 'hidden', flex: 1 }}>
      <div style={{ background: color, height: '100%', width: `${w}%`, borderRadius: 99, transition: 'width .4s ease' }} />
    </div>
  );
}

/** Simple SVG bar chart — no external library. */
function BarChart({ data = [], valueKey = 'cnt', labelKey = 'month', color = '#0055cc', height = 90 }) {
  if (!data.length) return <div style={{ color: '#bbb', fontSize: '0.8rem', padding: '1rem 0' }}>No data</div>;

  const W = 340, H = height, PAD = 20;
  const vals = data.map(d => Number(d[valueKey]) || 0);
  const max = Math.max(...vals, 1);
  const barW = Math.floor((W - PAD) / data.length) - 3;

  return (
    <svg viewBox={`0 0 ${W} ${H + 16}`} style={{ width: '100%', maxWidth: W, overflow: 'visible' }}>
      {data.map((d, i) => {
        const val  = Number(d[valueKey]) || 0;
        const barH = Math.max(((val / max) * (H - PAD)), 2);
        const x    = PAD / 2 + i * (barW + 3);
        const y    = H - barH;
        return (
          <g key={i}>
            <rect x={x} y={y} width={barW} height={barH} fill={color} rx={2} opacity={0.85} />
            {val > 0 && (
              <text x={x + barW / 2} y={y - 3} textAnchor="middle" fontSize="9" fill={color} fontWeight="600">
                {val}
              </text>
            )}
            <text x={x + barW / 2} y={H + 13} textAnchor="middle" fontSize="8" fill="#999">
              {String(d[labelKey]).slice(-5)}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

function SectionHeader({ title, computedAt }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '0.8rem' }}>
      <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>{title}</h3>
      {computedAt && <span style={{ fontSize: '0.72rem', color: '#aaa' }}>Updated {fmt(computedAt)}</span>}
    </div>
  );
}

// ── Metric panels ─────────────────────────────────────────────────────────

function BookingConversionPanel({ snapshot }) {
  if (!snapshot) return <EmptyPanel />;
  const { data, computedAt } = snapshot;
  const { byStatus = {}, conversionRate = 0, monthlyTrend = [] } = data;

  return (
    <div style={s.card}>
      <SectionHeader title="Booking Conversion" computedAt={computedAt} />
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: '1rem' }}>
        <StatCard label="Requested" value={byStatus.REQUESTED ?? 0} color="#555" />
        <StatCard label="Confirmed" value={byStatus.CONFIRMED ?? 0} color="#0a7" bg="#f0fdf4" />
        <StatCard label="Completed" value={byStatus.COMPLETED ?? 0} color="#0055cc" bg="#f0f4ff" />
        <StatCard label="Cancelled" value={byStatus.CANCELLED ?? 0} color="#c33" />
        <StatCard label="No-show" value={byStatus.NO_SHOW ?? 0} color="#c80" bg="#fff8e8" />
        <StatCard label="Conversion rate" value={pct(conversionRate)} color={conversionRate >= 60 ? '#0a7' : '#c80'} bg="#f8f9ff" />
      </div>
      <div style={{ fontSize: '0.8rem', color: '#666', marginBottom: 4 }}>Monthly converted bookings (last 6 months)</div>
      <BarChart data={monthlyTrend} valueKey="converted" labelKey="month" color="#0055cc" />
    </div>
  );
}

function NoShowRatePanel({ snapshot }) {
  if (!snapshot) return <EmptyPanel />;
  const { data, computedAt } = snapshot;
  const { byStatus = {}, noShowRate = 0, monthlyTrend = [] } = data;

  return (
    <div style={s.card}>
      <SectionHeader title="No-show Rate" computedAt={computedAt} />
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: '1rem' }}>
        <StatCard label="Checked in"  value={byStatus.CHECKED_IN  ?? 0} color="#0a7" bg="#f0fdf4" />
        <StatCard label="Checked out" value={byStatus.CHECKED_OUT ?? 0} color="#555" />
        <StatCard label="No-show"     value={byStatus.NO_SHOW     ?? 0} color="#c33" bg="#fff5f5" />
        <StatCard label="Cancelled"   value={byStatus.CANCELLED   ?? 0} color="#888" />
        <StatCard label="No-show rate" value={pct(noShowRate)} color={noShowRate <= 15 ? '#0a7' : noShowRate <= 25 ? '#c80' : '#c33'} bg="#f8f9ff" />
      </div>
      <div style={{ fontSize: '0.8rem', color: '#666', marginBottom: 4 }}>Monthly no-shows (last 6 months)</div>
      <BarChart data={monthlyTrend} valueKey="no_shows" labelKey="month" color="#e55" />
    </div>
  );
}

function SlotUtilizationPanel({ snapshot }) {
  if (!snapshot) return <EmptyPanel />;
  const { data, computedAt } = snapshot;
  const { occupiedSlots = 0, totalSlots = 0, utilizationRate = 0, byBuilding = [] } = data;

  return (
    <div style={s.card}>
      <SectionHeader title="Slot Utilization" computedAt={computedAt} />
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: '1rem' }}>
        <StatCard label="Occupied" value={occupiedSlots} color="#0055cc" bg="#f0f4ff" />
        <StatCard label="Total known" value={totalSlots} color="#555" />
        <StatCard label="Overall rate" value={pct(utilizationRate)}
          color={utilizationRate >= 70 ? '#0a7' : utilizationRate >= 50 ? '#c80' : '#c33'} bg="#f8f9ff" />
      </div>
      <div style={{ fontSize: '0.8rem', color: '#666', marginBottom: 8 }}>Utilization by building</div>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
        <thead>
          <tr>
            <th style={s.th}>Building</th>
            <th style={s.th}>Occupied</th>
            <th style={s.th}>Total</th>
            <th style={{ ...s.th, width: '40%' }}>Rate</th>
          </tr>
        </thead>
        <tbody>
          {byBuilding.map(b => (
            <tr key={b.building}>
              <td style={s.td}>{b.building}</td>
              <td style={s.td}>{b.occupied}</td>
              <td style={s.td}>{b.total}</td>
              <td style={s.td}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <PercentBar value={b.utilizationRate}
                    color={b.utilizationRate >= 70 ? '#0a7' : b.utilizationRate >= 50 ? '#c80' : '#c33'} />
                  <span style={{ fontSize: '0.78rem', color: '#555', minWidth: 40, textAlign: 'right' }}>
                    {pct(b.utilizationRate)}
                  </span>
                </div>
              </td>
            </tr>
          ))}
          {byBuilding.length === 0 && (
            <tr><td colSpan={4} style={{ ...s.td, color: '#bbb', textAlign: 'center' }}>No data</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function SettlementPanel({ snapshot }) {
  if (!snapshot) return <EmptyPanel />;
  const { data, computedAt } = snapshot;
  const { totalRequired = 0, totalCompleted = 0, totalPending = 0, completionRate = 0, byCategory = {} } = data;

  return (
    <div style={s.card}>
      <SectionHeader title="Settlement Outcome Completion" computedAt={computedAt} />
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: '1rem' }}>
        <StatCard label="Required"  value={totalRequired}  color="#555" />
        <StatCard label="Completed" value={totalCompleted} color="#0a7" bg="#f0fdf4" />
        <StatCard label="Pending"   value={totalPending}   color="#c33" bg="#fff5f5" />
        <StatCard label="Completion rate" value={pct(completionRate)}
          color={completionRate >= 80 ? '#0a7' : completionRate >= 50 ? '#c80' : '#c33'} bg="#f8f9ff" />
      </div>
      <div style={{ fontSize: '0.8rem', color: '#666', marginBottom: 8 }}>By category</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {Object.entries(byCategory).map(([cat, stats]) => {
          const r = stats.total > 0 ? (stats.completed / stats.total) * 100 : 0;
          return (
            <div key={cat}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem', marginBottom: 4 }}>
                <span style={{ fontWeight: 600 }}>{cat}</span>
                <span style={{ color: '#666' }}>{stats.completed} / {stats.total} ({pct(r)})</span>
              </div>
              <PercentBar value={r} color={r >= 80 ? '#0a7' : r >= 50 ? '#c80' : '#c33'} />
            </div>
          );
        })}
        {Object.keys(byCategory).length === 0 && (
          <div style={{ color: '#bbb', fontSize: '0.8rem' }}>No acknowledgment-required notifications</div>
        )}
      </div>
    </div>
  );
}

function EmptyPanel() {
  return (
    <div style={{ ...s.card, color: '#bbb', display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 140 }}>
      Computing…
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────

export default function AnalyticsDashboard() {
  const [metrics,   setMetrics]   = useState(null);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState('');
  const [refreshing, setRefreshing] = useState(false);

  const fetchMetrics = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setMetrics(await api.get('/api/admin/analytics'));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMetrics();
    const id = setInterval(fetchMetrics, 15 * 60 * 1000);
    return () => clearInterval(id);
  }, [fetchMetrics]);

  async function triggerRefresh() {
    setRefreshing(true);
    try {
      await api.post('/api/admin/analytics/refresh');
      await fetchMetrics();
    } catch (e) {
      setError(e.message);
    } finally {
      setRefreshing(false);
    }
  }

  return (
    <main style={{ padding: '2rem', maxWidth: 1100 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <h2 style={{ margin: 0 }}>Analytics</h2>
        <button
          style={refreshing ? s.btnDisabled : s.btnSecondary}
          onClick={triggerRefresh}
          disabled={refreshing}
        >
          {refreshing ? 'Refreshing…' : 'Refresh now'}
        </button>
      </div>

      {error && <div style={s.errBanner}>{error}</div>}

      {loading ? (
        <div style={{ color: '#888', padding: '2rem' }}>Loading…</div>
      ) : (
        <div style={s.grid}>
          <BookingConversionPanel snapshot={metrics?.booking_conversion} />
          <NoShowRatePanel        snapshot={metrics?.no_show_rate} />
          <SlotUtilizationPanel   snapshot={metrics?.slot_utilization} />
          <SettlementPanel        snapshot={metrics?.settlement_completion} />
        </div>
      )}
    </main>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────

const s = {
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(460px, 1fr))',
    gap: '1.2rem',
  },
  card: {
    border: '1px solid #e8eaed',
    borderRadius: 10,
    padding: '1.2rem 1.4rem',
    background: '#fff',
    boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
  },
  th: {
    textAlign: 'left',
    padding: '0.4rem 0.5rem',
    borderBottom: '2px solid #eee',
    fontWeight: 700,
    color: '#666',
    fontSize: '0.78rem',
  },
  td: {
    padding: '0.45rem 0.5rem',
    borderBottom: '1px solid #f3f3f3',
    fontSize: '0.85rem',
    verticalAlign: 'middle',
  },
  errBanner: {
    background: '#fff0f0', border: '1px solid #fcc', color: '#c33',
    borderRadius: 4, padding: '0.7rem 1rem', marginBottom: '1rem',
  },
  btnSecondary: {
    padding: '0.45rem 1.1rem', background: '#fff', color: '#333',
    border: '1px solid #ccc', borderRadius: 4, cursor: 'pointer',
    fontWeight: 600, fontSize: '0.85rem',
  },
  btnDisabled: {
    padding: '0.45rem 1.1rem', background: '#f5f5f5', color: '#aaa',
    border: '1px solid #ddd', borderRadius: 4, cursor: 'not-allowed',
    fontWeight: 600, fontSize: '0.85rem',
  },
};
