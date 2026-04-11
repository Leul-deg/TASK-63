import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { canAccessAdmin } from '../utils/roles';

// ── Default policy shape (mirrors BookingPolicy.java defaults) ────────────────
const DEFAULT_POLICY = {
  windowDays: 14,
  sameDayCutoffHour: 17,
  sameDayCutoffMinute: 0,
  noShowThreshold: 2,
  noShowWindowDays: 30,
  canaryEnabled: false,
  canaryRolloutPercent: 10,
  canaryBuildingIds: [],
  holidayBlackoutDates: [],
};

function pad2(n) { return String(n).padStart(2, '0'); }
function fmtTime(h, m) { return `${pad2(h)}:${pad2(m)}`; }

function fmtDate(iso) {
  return new Date(iso).toLocaleString([], {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

// ── Validation ────────────────────────────────────────────────────────────────
function validate(p) {
  const errs = {};
  if (!Number.isInteger(p.windowDays) || p.windowDays < 1 || p.windowDays > 365)
    errs.windowDays = 'Must be 1–365 days';
  if (!Number.isInteger(p.sameDayCutoffHour) || p.sameDayCutoffHour < 0 || p.sameDayCutoffHour > 23)
    errs.sameDayCutoffHour = 'Must be 0–23';
  if (!Number.isInteger(p.sameDayCutoffMinute) || p.sameDayCutoffMinute < 0 || p.sameDayCutoffMinute > 59)
    errs.sameDayCutoffMinute = 'Must be 0–59';
  if (!Number.isInteger(p.noShowThreshold) || p.noShowThreshold < 1 || p.noShowThreshold > 20)
    errs.noShowThreshold = 'Must be 1–20';
  if (!Number.isInteger(p.noShowWindowDays) || p.noShowWindowDays < 1 || p.noShowWindowDays > 365)
    errs.noShowWindowDays = 'Must be 1–365 days';
  if (!Number.isInteger(p.canaryRolloutPercent) || p.canaryRolloutPercent < 0 || p.canaryRolloutPercent > 100)
    errs.canaryRolloutPercent = 'Must be 0–100';
  const datePattern = /^\d{4}-\d{2}-\d{2}$/;
  const seen = new Set();
  for (const b of (p.holidayBlackoutDates || [])) {
    if (!datePattern.test(b.date)) { errs.holidayDates = `Invalid date format: "${b.date}" — use YYYY-MM-DD`; break; }
    if (seen.has(b.date))          { errs.holidayDates = `Duplicate blackout date: ${b.date}`; break; }
    seen.add(b.date);
  }
  return errs;
}

// ── Section wrapper ────────────────────────────────────────────────────────────
function Section({ title, children }) {
  return (
    <fieldset style={sectionStyle}>
      <legend style={legendStyle}>{title}</legend>
      {children}
    </fieldset>
  );
}

// ── Field row ──────────────────────────────────────────────────────────────────
function Field({ label, hint, error, children }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label style={lblStyle}>{label}</label>
      {hint && <span style={{ fontSize: '0.78rem', color: '#888', marginLeft: 6 }}>{hint}</span>}
      {children}
      {error && <div style={{ color: '#c00', fontSize: '0.78rem', marginTop: 2 }}>{error}</div>}
    </div>
  );
}

// ── Holiday Blackout Editor ───────────────────────────────────────────────────
function BlackoutEditor({ dates, onChange, error }) {
  const [newDate, setNewDate]   = useState('');
  const [newLabel, setNewLabel] = useState('');
  const [addErr, setAddErr]     = useState('');

  const add = () => {
    setAddErr('');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(newDate)) {
      return setAddErr('Date must be YYYY-MM-DD');
    }
    if (!newLabel.trim()) return setAddErr('Label is required');
    if (dates.find(d => d.date === newDate)) return setAddErr('Date already added');
    onChange([...dates, { date: newDate, label: newLabel.trim() }]);
    setNewDate(''); setNewLabel('');
  };

  const remove = (d) => onChange(dates.filter(x => x.date !== d));

  const sorted = [...dates].sort((a, b) => a.date.localeCompare(b.date));

  return (
    <div>
      {/* Add row */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 6, flexWrap: 'wrap' }}>
        <input
          type="date"
          style={{ ...inpStyle, width: 150 }}
          value={newDate}
          onChange={e => setNewDate(e.target.value)}
        />
        <input
          style={{ ...inpStyle, flex: 1, minWidth: 150 }}
          value={newLabel}
          onChange={e => setNewLabel(e.target.value)}
          placeholder="Label (e.g. Christmas Day)"
          maxLength={100}
        />
        <button style={btnAdd} onClick={add} type="button">+ Add</button>
      </div>
      {addErr && <div style={{ color: '#c00', fontSize: '0.78rem', marginBottom: 4 }}>{addErr}</div>}
      {error  && <div style={{ color: '#c00', fontSize: '0.78rem', marginBottom: 4 }}>{error}</div>}

      {/* Date list */}
      {sorted.length > 0 && (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
          <thead>
            <tr style={{ background: '#f5f5f5' }}>
              <th style={thStyle}>Date</th>
              <th style={thStyle}>Label</th>
              <th style={{ ...thStyle, width: 60 }}></th>
            </tr>
          </thead>
          <tbody>
            {sorted.map(b => (
              <tr key={b.date} style={{ borderBottom: '1px solid #eee' }}>
                <td style={tdStyle}>{b.date}</td>
                <td style={tdStyle}>{b.label}</td>
                <td style={{ ...tdStyle, textAlign: 'center' }}>
                  <button style={btnRemove} onClick={() => remove(b.date)} type="button">
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {sorted.length === 0 && (
        <p style={{ color: '#aaa', fontSize: '0.82rem', margin: '4px 0' }}>
          No blackout dates configured.
        </p>
      )}
    </div>
  );
}

// ── Building ID Tag Editor ────────────────────────────────────────────────────
function BuildingTagEditor({ ids, onChange }) {
  const [input, setInput] = useState('');

  const add = () => {
    const id = input.trim();
    if (!id || ids.includes(id)) { setInput(''); return; }
    onChange([...ids, id]);
    setInput('');
  };

  const handleKey = (e) => {
    if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); add(); }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
        <input
          style={{ ...inpStyle, flex: 1 }}
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKey}
          placeholder="Building ID (Enter or comma to add)"
          maxLength={100}
        />
        <button style={btnAdd} onClick={add} type="button">Add</button>
      </div>
      {ids.length > 0 ? (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
          {ids.map(id => (
            <span key={id} style={tagStyle}>
              {id}
              <button style={tagX} onClick={() => onChange(ids.filter(x => x !== id))} type="button">
                ×
              </button>
            </span>
          ))}
        </div>
      ) : (
        <p style={{ color: '#aaa', fontSize: '0.82rem', margin: 0 }}>
          No explicit buildings — canary uses hash-based selection only.
        </p>
      )}
    </div>
  );
}

// ── Policy Editor ──────────────────────────────────────────────────────────────
function PolicyEditor({ policy, onChange, errors }) {
  const set = (field, val) => onChange({ ...policy, [field]: val });

  const timeStr = fmtTime(policy.sameDayCutoffHour, policy.sameDayCutoffMinute);
  const setTime = (str) => {
    const [h, m] = str.split(':').map(Number);
    onChange({ ...policy, sameDayCutoffHour: h || 0, sameDayCutoffMinute: m || 0 });
  };

  return (
    <div>
      {/* General */}
      <Section title="Booking Window">
        <Field label="Rolling window (days)"
               hint="How many days ahead a booking may be made"
               error={errors.windowDays}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="range" min={1} max={90} value={policy.windowDays}
                   onChange={e => set('windowDays', parseInt(e.target.value))}
                   style={{ flex: 1 }} />
            <input type="number" min={1} max={365} value={policy.windowDays}
                   onChange={e => set('windowDays', parseInt(e.target.value) || 1)}
                   style={{ ...inpStyle, width: 70 }} />
            <span style={unitLabel}>days</span>
          </div>
        </Field>

        <Field label="Same-day booking cutoff"
               hint="Bookings for today are blocked after this time"
               error={errors.sameDayCutoffHour || errors.sameDayCutoffMinute}>
          <input type="time" value={timeStr} onChange={e => setTime(e.target.value)}
                 style={{ ...inpStyle, width: 130 }} />
          <span style={{ ...unitLabel, marginLeft: 8 }}>
            (currently {fmtTime(policy.sameDayCutoffHour, policy.sameDayCutoffMinute)})
          </span>
        </Field>
      </Section>

      {/* No-show */}
      <Section title="No-Show Policy">
        <p style={sectionNote}>
          Visitors who reach the threshold within the window will be unable to make new bookings.
        </p>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <Field label="No-show threshold"
                 hint="Number of no-shows that triggers a restriction"
                 error={errors.noShowThreshold}>
            <input type="number" min={1} max={20} value={policy.noShowThreshold}
                   onChange={e => set('noShowThreshold', parseInt(e.target.value) || 1)}
                   style={{ ...inpStyle, width: '100%' }} />
          </Field>
          <Field label="Rolling window"
                 hint="Days over which no-shows are counted"
                 error={errors.noShowWindowDays}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <input type="number" min={1} max={365} value={policy.noShowWindowDays}
                     onChange={e => set('noShowWindowDays', parseInt(e.target.value) || 1)}
                     style={{ ...inpStyle, flex: 1 }} />
              <span style={unitLabel}>days</span>
            </div>
          </Field>
        </div>
        <div style={ruleBox}>
          After <strong>{policy.noShowThreshold} no-show{policy.noShowThreshold !== 1 ? 's' : ''}</strong> within{' '}
          <strong>{policy.noShowWindowDays} days</strong>, the visitor cannot make new bookings.
        </div>
      </Section>

      {/* Canary */}
      <Section title="Canary Rollout">
        <p style={sectionNote}>
          Enable canary mode to test this policy on a subset of buildings before full rollout.
        </p>
        <Field label="Enable canary rollout">
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
            <input type="checkbox" checked={policy.canaryEnabled}
                   onChange={e => set('canaryEnabled', e.target.checked)} />
            <span style={{ fontSize: '0.9rem' }}>
              {policy.canaryEnabled ? 'Active — policy only applies to the canary cohort' : 'Disabled — policy applies to all buildings'}
            </span>
          </label>
        </Field>

        {policy.canaryEnabled && (
          <>
            <Field label="Rollout percentage"
                   hint="Percentage of buildings selected by stable hash"
                   error={errors.canaryRolloutPercent}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <input type="range" min={0} max={100} value={policy.canaryRolloutPercent}
                       onChange={e => set('canaryRolloutPercent', parseInt(e.target.value))}
                       style={{ flex: 1 }} />
                <input type="number" min={0} max={100} value={policy.canaryRolloutPercent}
                       onChange={e => set('canaryRolloutPercent', Math.min(100, Math.max(0, parseInt(e.target.value) || 0)))}
                       style={{ ...inpStyle, width: 65 }} />
                <span style={unitLabel}>%</span>
              </div>
            </Field>

            <Field label="Always-included buildings"
                   hint="These building IDs are always in the canary cohort regardless of the percentage">
              <BuildingTagEditor
                ids={policy.canaryBuildingIds}
                onChange={ids => set('canaryBuildingIds', ids)}
              />
            </Field>

            <div style={ruleBox}>
              Policy applies to <strong>{policy.canaryRolloutPercent}%</strong> of buildings
              (by hash) plus <strong>{policy.canaryBuildingIds.length}</strong> explicit building
              {policy.canaryBuildingIds.length !== 1 ? 's' : ''}.
            </div>
          </>
        )}
      </Section>

      {/* Holiday blackouts */}
      <Section title="Holiday Blackout Dates">
        <p style={sectionNote}>
          Bookings are not accepted on blackout dates regardless of other policy settings.
        </p>
        <BlackoutEditor
          dates={policy.holidayBlackoutDates}
          onChange={dates => set('holidayBlackoutDates', dates)}
          error={errors.holidayDates}
        />
      </Section>
    </div>
  );
}

// ── Version History Table ──────────────────────────────────────────────────────
function VersionHistory({ history, currentVersion, onActivate, loading }) {
  const [activating, setActivating] = useState(null);
  const [confirm, setConfirm]       = useState(null);

  const doActivate = async (version) => {
    setActivating(version); setConfirm(null);
    try { await onActivate(version); }
    finally { setActivating(null); }
  };

  if (loading) return <p style={{ color: '#888', padding: '0.5rem' }}>Loading history…</p>;
  if (!history.length) return <p style={{ color: '#888' }}>No history yet.</p>;

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem' }}>
        <thead>
          <tr style={{ background: '#f5f5f5', textAlign: 'left' }}>
            <th style={thStyle}>Ver.</th>
            <th style={thStyle}>Status</th>
            <th style={thStyle}>Created</th>
            <th style={thStyle}>By</th>
            <th style={thStyle}>Description</th>
            <th style={thStyle}>Key settings</th>
            <th style={thStyle}></th>
          </tr>
        </thead>
        <tbody>
          {history.map(v => {
            const isActive = v.version === currentVersion;
            return (
              <tr key={v.id}
                  style={{ borderBottom: '1px solid #eee',
                           background: isActive ? '#f0f7ff' : 'transparent' }}>
                <td style={{ ...tdStyle, fontWeight: 700 }}>v{v.version}</td>
                <td style={tdStyle}>
                  {isActive
                    ? <span style={activeBadge}>Active</span>
                    : <span style={inactiveBadge}>Inactive</span>}
                </td>
                <td style={tdStyle}>{v.createdAt ? fmtDate(v.createdAt) : '—'}</td>
                <td style={tdStyle}>{v.createdByUsername || '—'}</td>
                <td style={{ ...tdStyle, maxWidth: 220, color: '#555' }}>
                  {v.description || <em style={{ color: '#aaa' }}>No description</em>}
                </td>
                <td style={{ ...tdStyle, fontSize: '0.78rem', color: '#666' }}>
                  {v.policy
                    ? `${v.policy.windowDays}d window · ${fmtTime(v.policy.sameDayCutoffHour, v.policy.sameDayCutoffMinute)} cutoff · ` +
                      `${v.policy.noShowThreshold} no-shows/${v.policy.noShowWindowDays}d` +
                      (v.policy.canaryEnabled ? ` · Canary ${v.policy.canaryRolloutPercent}%` : '')
                    : '—'}
                </td>
                <td style={{ ...tdStyle, textAlign: 'right' }}>
                  {!isActive && (
                    confirm === v.version ? (
                      <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                        <button style={btnDanger} disabled={activating === v.version}
                                onClick={() => doActivate(v.version)}>
                          {activating === v.version ? 'Activating…' : 'Confirm'}
                        </button>
                        <button style={btnSmall} onClick={() => setConfirm(null)}>Cancel</button>
                      </span>
                    ) : (
                      <button style={btnSmall} onClick={() => setConfirm(v.version)}>
                        Activate
                      </button>
                    )
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// ── Main Page ──────────────────────────────────────────────────────────────────
export default function BookingPolicyPage() {
  const { user } = useAuth();
  const isAdmin = canAccessAdmin(user);

  const [current, setCurrent]     = useState(null);
  const [history, setHistory]     = useState([]);
  const [draft, setDraft]         = useState(DEFAULT_POLICY);
  const [description, setDesc]    = useState('');
  const [errors, setErrors]       = useState({});
  const [saving, setSaving]       = useState(false);
  const [saveError, setSaveError] = useState('');
  const [saveSuccess, setSaveSuccess] = useState('');
  const [histLoading, setHistLoading] = useState(true);
  const [showHistory, setShowHistory] = useState(false);
  const [loadError, setLoadError] = useState('');

  const loadCurrent = useCallback(async () => {
    try {
      const data = await api.get('/api/admin/booking-policy');
      setCurrent(data);
      setDraft({ ...DEFAULT_POLICY, ...data.policy });
    } catch (e) {
      setLoadError(e.body?.message || 'Failed to load policy.');
    }
  }, []);

  const loadHistory = useCallback(async () => {
    setHistLoading(true);
    try {
      const data = await api.get('/api/admin/booking-policy/history');
      setHistory(data);
    } catch (_) {}
    finally { setHistLoading(false); }
  }, []);

  useEffect(() => { loadCurrent(); }, [loadCurrent]);
  useEffect(() => {
    if (showHistory) loadHistory();
  }, [showHistory, loadHistory]);

  const save = async () => {
    const errs = validate(draft);
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setErrors({}); setSaving(true); setSaveError(''); setSaveSuccess('');
    try {
      const result = await api.put('/api/admin/booking-policy', {
        policy: draft,
        description: description.trim() || null,
      });
      setCurrent(result);
      setDraft({ ...DEFAULT_POLICY, ...result.policy });
      setDesc('');
      setSaveSuccess(`Saved as version ${result.version}.`);
      if (showHistory) await loadHistory();
      setTimeout(() => setSaveSuccess(''), 5000);
    } catch (e) {
      setSaveError(e.body?.message || e.message || 'Failed to save policy.');
    } finally { setSaving(false); }
  };

  const activate = async (version) => {
    try {
      const result = await api.post(`/api/admin/booking-policy/activate/${version}`, {});
      setCurrent(result);
      setDraft({ ...DEFAULT_POLICY, ...result.policy });
      await loadHistory();
    } catch (e) {
      setSaveError(e.body?.message || 'Failed to activate version.');
    }
  };

  if (!isAdmin) {
    return (
      <main style={{ padding: '2rem' }}>
        <h2>Booking Policy</h2>
        <p style={{ color: '#888' }}>Admin access required.</p>
      </main>
    );
  }

  return (
    <main style={{ padding: '1.5rem 2rem', maxWidth: 860, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
                    marginBottom: '1.2rem', flexWrap: 'wrap', gap: 8 }}>
        <div>
          <h2 style={{ margin: 0 }}>Booking &amp; Visit Policy</h2>
          {current && (
            <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: '#555' }}>
              Current: <strong>version {current.version}</strong>
              {current.createdByUsername && ` · by ${current.createdByUsername}`}
              {current.createdAt && ` · ${fmtDate(current.createdAt)}`}
            </p>
          )}
        </div>
        <button style={btnSecondary} onClick={() => setShowHistory(v => !v)}>
          {showHistory ? 'Hide History' : 'View History'}
        </button>
      </div>

      {loadError && (
        <div style={alertDanger}>{loadError}</div>
      )}

      {/* Policy editor */}
      <PolicyEditor policy={draft} onChange={setDraft} errors={errors} />

      {/* Save controls */}
      <div style={{ marginTop: '1.5rem', padding: '1rem', background: '#fafafa',
                    border: '1px solid #eee', borderRadius: 8 }}>
        <label style={lblStyle}>Change description <span style={{ color: '#888', fontWeight: 400 }}>(recommended)</span></label>
        <textarea
          style={{ ...inpStyle, width: '100%', height: 64, resize: 'vertical', marginTop: 4 }}
          value={description}
          onChange={e => setDesc(e.target.value)}
          placeholder="Briefly explain what changed and why, e.g. 'Extended window to 30 days for summer semester'"
          maxLength={500}
        />
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                      marginTop: 10, flexWrap: 'wrap', gap: 8 }}>
          <div>
            {saveError   && <span style={{ color: '#c00', fontSize: '0.85rem' }}>{saveError}</span>}
            {saveSuccess && <span style={{ color: '#155724', fontSize: '0.85rem' }}>✓ {saveSuccess}</span>}
          </div>
          <button style={btnPrimary} onClick={save} disabled={saving}>
            {saving ? 'Saving…' : 'Save New Version'}
          </button>
        </div>
        <p style={{ margin: '8px 0 0', fontSize: '0.78rem', color: '#888' }}>
          Saving creates an immutable new version. The previous version is preserved in history.
        </p>
      </div>

      {/* Version history */}
      {showHistory && (
        <div style={{ marginTop: '1.5rem' }}>
          <h3 style={{ margin: '0 0 12px', fontSize: '1rem' }}>Version History</h3>
          <VersionHistory
            history={history}
            currentVersion={current?.version}
            onActivate={activate}
            loading={histLoading}
          />
        </div>
      )}
    </main>
  );
}

// ── Styles ─────────────────────────────────────────────────────────────────────
const sectionStyle  = { border: '1px solid #e0e0e0', borderRadius: 8, padding: '1rem', marginBottom: 16, fontFamily: 'system-ui, sans-serif' };
const legendStyle   = { fontWeight: 700, fontSize: '0.9rem', padding: '0 8px', color: '#333' };
const sectionNote   = { margin: '0 0 12px', fontSize: '0.83rem', color: '#666', lineHeight: 1.5 };
const ruleBox       = { background: '#f0f7ff', border: '1px solid #c5d9fb', borderRadius: 6, padding: '8px 12px', fontSize: '0.85rem', color: '#1a3a6e', marginTop: 8 };
const lblStyle      = { display: 'block', fontSize: '0.82rem', fontWeight: 600, color: '#333', marginBottom: 3 };
const unitLabel     = { fontSize: '0.82rem', color: '#666' };
const inpStyle      = { boxSizing: 'border-box', padding: '6px 10px', border: '1px solid #ccc', borderRadius: 6, fontSize: '0.88rem', fontFamily: 'inherit' };
const thStyle       = { padding: '6px 10px', fontWeight: 600, fontSize: '0.8rem', borderBottom: '2px solid #ddd', whiteSpace: 'nowrap' };
const tdStyle       = { padding: '7px 10px', verticalAlign: 'middle', fontSize: '0.83rem' };
const tagStyle      = { display: 'inline-flex', alignItems: 'center', gap: 4, background: '#e8f0fe', color: '#1a73e8', borderRadius: 12, padding: '2px 8px', fontSize: '0.8rem' };
const tagX          = { background: 'none', border: 'none', cursor: 'pointer', color: '#1a73e8', fontSize: '1rem', lineHeight: 1, padding: 0 };
const btnPrimary    = { background: '#1a73e8', color: '#fff', border: 'none', borderRadius: 6, padding: '8px 18px', cursor: 'pointer', fontSize: '0.88rem', fontWeight: 600 };
const btnSecondary  = { background: 'none', color: '#555', border: '1px solid #ccc', borderRadius: 6, padding: '6px 12px', cursor: 'pointer', fontSize: '0.85rem' };
const btnSmall      = { background: '#f1f3f4', color: '#333', border: '1px solid #ddd', borderRadius: 4, padding: '3px 8px', cursor: 'pointer', fontSize: '0.78rem' };
const btnDanger     = { background: '#c00', color: '#fff', border: 'none', borderRadius: 4, padding: '3px 8px', cursor: 'pointer', fontSize: '0.78rem', fontWeight: 600 };
const btnAdd        = { background: '#1a73e8', color: '#fff', border: 'none', borderRadius: 6, padding: '6px 12px', cursor: 'pointer', fontSize: '0.85rem', whiteSpace: 'nowrap' };
const btnRemove     = { background: 'none', color: '#c00', border: '1px solid #f5c6cb', borderRadius: 4, padding: '2px 6px', cursor: 'pointer', fontSize: '0.75rem' };
const activeBadge   = { background: '#d4edda', color: '#155724', borderRadius: 4, padding: '2px 7px', fontSize: '0.75rem', fontWeight: 700 };
const inactiveBadge = { background: '#f1f3f4', color: '#666',    borderRadius: 4, padding: '2px 7px', fontSize: '0.75rem' };
const alertDanger   = { background: '#fce8e6', border: '1px solid #f5c6cb', borderRadius: 6, padding: '10px 14px', color: '#c00', marginBottom: 12, fontSize: '0.88rem' };
