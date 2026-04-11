import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { canRevealSensitive, hasAnyRole } from '../utils/roles';
import MaskedField from '../components/MaskedField';
import { useDebounce } from '../hooks/useDebounce';

const CAN_EDIT_ROLES = ['ADMIN','HOUSING_ADMINISTRATOR','DIRECTOR','RESIDENT_DIRECTOR','RESIDENT_ASSISTANT','RESIDENCE_STAFF','STAFF'];

// Move-in status options (mirrors CheckInStatus enum in the backend)
const MOVE_IN_STATUS_OPTIONS = [
  { value: 'PENDING',     label: 'Pending'     },
  { value: 'CHECKED_IN',  label: 'Checked in'  },
  { value: 'CHECKED_OUT', label: 'Checked out' },
  { value: 'NO_SHOW',     label: 'No show'     },
  { value: 'CANCELLED',   label: 'Cancelled'   },
];

const PAGE_SIZE = 20;

export default function ResidentsPage() {
  const { user }    = useAuth();
  const canReveal   = canRevealSensitive(user);
  const canEdit     = hasAnyRole(user, CAN_EDIT_ROLES);
  const navigate    = useNavigate();

  // ── Filter state ─────────────────────────────────────────────────────────
  const [searchInput, setSearchInput]         = useState('');
  const [building,    setBuilding]            = useState('');
  const [classYear,   setClassYear]           = useState('');
  const [moveInStatus, setMoveInStatus]       = useState('');
  const debouncedSearch = useDebounce(searchInput, 350);

  // ── Filter options (from API) ─────────────────────────────────────────────
  const [buildingOptions,  setBuildingOptions]  = useState([]);
  const [classYearOptions, setClassYearOptions] = useState([]);

  // ── Results ───────────────────────────────────────────────────────────────
  const [residents,   setResidents]   = useState([]);
  const [page,        setPage]        = useState(0);
  const [totalPages,  setTotalPages]  = useState(0);
  const [totalItems,  setTotalItems]  = useState(0);
  const [loading,     setLoading]     = useState(true);
  const [error,       setError]       = useState('');

  // ── Load filter options once ──────────────────────────────────────────────
  useEffect(() => {
    api.get('/api/residents/filter-options')
      .then(data => {
        setBuildingOptions(data.buildings  ?? []);
        setClassYearOptions(data.classYears ?? []);
      })
      .catch(() => {}); // non-fatal
  }, []);

  // ── Reset to page 0 when filters change ──────────────────────────────────
  useEffect(() => { setPage(0); }, [debouncedSearch, building, classYear, moveInStatus]);

  // ── Fetch results ─────────────────────────────────────────────────────────
  const fetchResidents = useCallback(() => {
    setLoading(true);
    setError('');

    const params = new URLSearchParams({
      page,
      size: PAGE_SIZE,
      sort: 'lastName,asc',
    });
    if (debouncedSearch) params.set('q',            debouncedSearch);
    if (building)        params.set('building',     building);
    if (classYear)       params.set('classYear',    classYear);
    if (moveInStatus)    params.set('moveInStatus', moveInStatus);

    api.get(`/api/residents?${params}`)
      .then(data => {
        setResidents(data.content    ?? []);
        setTotalPages(data.totalPages ?? 0);
        setTotalItems(data.totalElements ?? 0);
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, [page, debouncedSearch, building, classYear, moveInStatus]);

  useEffect(() => { fetchResidents(); }, [fetchResidents]);

  // ── Active filter chips ───────────────────────────────────────────────────
  const activeFilters = [
    debouncedSearch && { key: 'q',            label: `"${debouncedSearch}"`,                     clear: () => setSearchInput('') },
    building        && { key: 'building',     label: `Building: ${building}`,                   clear: () => setBuilding('')     },
    classYear       && { key: 'classYear',    label: `Class of ${classYear}`,                   clear: () => setClassYear('')    },
    moveInStatus    && { key: 'moveInStatus', label: MOVE_IN_STATUS_OPTIONS.find(o => o.value === moveInStatus)?.label ?? moveInStatus, clear: () => setMoveInStatus('') },
  ].filter(Boolean);

  const clearAllFilters = () => {
    setSearchInput('');
    setBuilding('');
    setClassYear('');
    setMoveInStatus('');
  };

  return (
    <main style={styles.main}>
      {/* Header */}
      <div style={styles.header}>
        <h2 style={styles.title}>Residents</h2>
        <span style={styles.roleTag}>{canReveal ? 'Staff view' : 'Restricted view'}</span>
        {canEdit && (
          <button style={styles.newBtn} onClick={() => navigate('/residents/new')}>
            + New Resident
          </button>
        )}
      </div>

      {/* Search + filters */}
      <div style={styles.toolbar}>
        <input
          type="search"
          placeholder="Search by name, email, or student ID…"
          style={styles.searchInput}
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
          aria-label="Search residents"
        />

        <Select
          value={building}
          onChange={e => setBuilding(e.target.value)}
          aria-label="Filter by building"
        >
          <option value="">All buildings</option>
          {buildingOptions.map(b => (
            <option key={b} value={b}>{b}</option>
          ))}
        </Select>

        <Select
          value={classYear}
          onChange={e => setClassYear(e.target.value)}
          aria-label="Filter by class year"
        >
          <option value="">All years</option>
          {classYearOptions.map(y => (
            <option key={y} value={y}>{y}</option>
          ))}
        </Select>

        <Select
          value={moveInStatus}
          onChange={e => setMoveInStatus(e.target.value)}
          aria-label="Filter by move-in status"
        >
          <option value="">All statuses</option>
          {MOVE_IN_STATUS_OPTIONS.map(o => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </Select>
      </div>

      {/* Active filter chips */}
      {activeFilters.length > 0 && (
        <div style={styles.chips}>
          {activeFilters.map(f => (
            <span key={f.key} style={styles.chip}>
              {f.label}
              <button style={styles.chipClose} onClick={f.clear} aria-label={`Remove filter ${f.label}`}>×</button>
            </span>
          ))}
          <button style={styles.clearAll} onClick={clearAllFilters}>Clear all</button>
        </div>
      )}

      {/* Result count */}
      {!loading && !error && (
        <div style={styles.resultCount}>
          {totalItems === 0
            ? 'No residents found.'
            : `${totalItems.toLocaleString()} resident${totalItems === 1 ? '' : 's'}`}
        </div>
      )}

      {/* Error state */}
      {error && (
        <div style={styles.errorBox}>
          {error}
          <button style={styles.retryBtn} onClick={fetchResidents}>Retry</button>
        </div>
      )}

      {/* Loading state */}
      {loading && <LoadingSkeleton />}

      {/* Results table */}
      {!loading && !error && residents.length > 0 && (
        <>
          <div style={styles.tableWrapper}>
            <table style={styles.table}>
              <thead>
                <tr>
                  <Th>Student ID</Th>
                  <Th>Name</Th>
                  <Th>Email</Th>
                  <Th>Date of Birth</Th>
                  <Th>Class Year</Th>
                  <Th>Room</Th>
                  <Th>Building</Th>
                  <Th>Status</Th>
                  {canEdit && <Th></Th>}
                </tr>
              </thead>
              <tbody>
                {residents.map(r => (
                  <ResidentRow key={r.id} resident={r} canReveal={canReveal} canEdit={canEdit} />
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div style={styles.pagination}>
              <button
                style={styles.pageBtn}
                onClick={() => setPage(p => p - 1)}
                disabled={page === 0}
              >
                ← Previous
              </button>
              <span style={styles.pageInfo}>
                Page {page + 1} of {totalPages}
              </span>
              <button
                style={styles.pageBtn}
                onClick={() => setPage(p => p + 1)}
                disabled={page >= totalPages - 1}
              >
                Next →
              </button>
            </div>
          )}
        </>
      )}
    </main>
  );
}

// ── Sub-components ────────────────────────────────────────────────────────

function ResidentRow({ resident: r, canReveal, canEdit }) {
  const navigate = useNavigate();
  return (
    <tr style={styles.tr}>
      <Td>{r.studentId ?? '—'}</Td>
      <Td>{r.firstName} {r.lastName}</Td>
      <Td>{r.email}</Td>
      <Td>
        <MaskedField value={r.dateOfBirth} canReveal={canReveal} label="date of birth" />
      </Td>
      <Td>{r.classYear ?? '—'}</Td>
      <Td>{r.roomNumber ?? '—'}</Td>
      <Td>{r.buildingName ?? '—'}</Td>
      <Td>
        <span style={{
          ...styles.statusBadge,
          background: r.enrollmentStatus ? '#e6f4ea' : '#f0f0f0',
          color:      r.enrollmentStatus ? '#1a7f37' : '#888',
        }}>
          {r.enrollmentStatus ?? 'Unknown'}
        </span>
      </Td>
      {canEdit && (
        <Td>
          <div style={{ display: 'flex', gap: '4px' }}>
            <button style={styles.editBtn} onClick={() => navigate(`/residents/${r.id}/edit`)}>
              Edit
            </button>
            <button style={styles.editBtn} onClick={() => navigate(`/residents/${r.id}/agreements`)}>
              Agreements
            </button>
            <button style={styles.editBtn} onClick={() => navigate(`/residents/${r.id}/bookings`)}>
              Bookings
            </button>
          </div>
        </Td>
      )}
    </tr>
  );
}

function Select({ children, ...props }) {
  return (
    <select style={styles.select} {...props}>
      {children}
    </select>
  );
}

function Th({ children }) {
  return <th style={styles.th}>{children}</th>;
}

function Td({ children }) {
  return <td style={styles.td}>{children}</td>;
}

function LoadingSkeleton() {
  return (
    <div style={styles.skeleton}>
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} style={{ ...styles.skeletonRow, opacity: 1 - i * 0.08 }} />
      ))}
    </div>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────

const styles = {
  main: {
    padding: '2rem',
    fontFamily: 'system-ui, sans-serif',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
    marginBottom: '1.25rem',
  },
  title: {
    margin: 0,
    fontSize: '1.4rem',
    fontWeight: 600,
  },
  roleTag: {
    fontSize: '0.75rem',
    fontWeight: 600,
    padding: '3px 8px',
    borderRadius: '4px',
    background: '#e8f0fe',
    color: '#0055cc',
  },
  toolbar: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '0.5rem',
    marginBottom: '0.75rem',
  },
  searchInput: {
    flex: '1 1 240px',
    padding: '0.45rem 0.75rem',
    fontSize: '0.9rem',
    border: '1px solid #ccc',
    borderRadius: '5px',
    outline: 'none',
  },
  select: {
    padding: '0.45rem 0.6rem',
    fontSize: '0.875rem',
    border: '1px solid #ccc',
    borderRadius: '5px',
    background: '#fff',
    cursor: 'pointer',
    outline: 'none',
  },
  chips: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '0.4rem',
    marginBottom: '0.75rem',
    alignItems: 'center',
  },
  chip: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '4px',
    fontSize: '0.78rem',
    fontWeight: 600,
    padding: '3px 8px',
    background: '#e8f0fe',
    color: '#0055cc',
    borderRadius: '4px',
  },
  chipClose: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    color: '#0055cc',
    fontSize: '1rem',
    lineHeight: 1,
    padding: '0 2px',
    fontWeight: 700,
  },
  clearAll: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    fontSize: '0.78rem',
    color: '#888',
    textDecoration: 'underline',
    padding: 0,
  },
  resultCount: {
    fontSize: '0.82rem',
    color: '#666',
    marginBottom: '0.75rem',
  },
  errorBox: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
    background: '#fff0f0',
    border: '1px solid #ffcccc',
    borderRadius: '5px',
    padding: '0.6rem 0.8rem',
    color: '#c0392b',
    marginBottom: '1rem',
    fontSize: '0.875rem',
  },
  retryBtn: {
    background: 'none',
    border: '1px solid #c0392b',
    borderRadius: '4px',
    color: '#c0392b',
    cursor: 'pointer',
    fontSize: '0.8rem',
    padding: '2px 8px',
    fontWeight: 600,
  },
  skeleton: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
    marginTop: '0.5rem',
  },
  skeletonRow: {
    height: '38px',
    background: '#eee',
    borderRadius: '4px',
    animation: 'pulse 1.4s ease-in-out infinite',
  },
  tableWrapper: {
    overflowX: 'auto',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    fontSize: '0.9rem',
  },
  th: {
    textAlign: 'left',
    padding: '0.6rem 0.75rem',
    borderBottom: '2px solid #ddd',
    fontWeight: 600,
    whiteSpace: 'nowrap',
    color: '#444',
    background: '#fafafa',
  },
  td: {
    padding: '0.6rem 0.75rem',
    borderBottom: '1px solid #eee',
    verticalAlign: 'middle',
  },
  tr: {
    transition: 'background 0.1s',
  },
  statusBadge: {
    display: 'inline-block',
    fontSize: '0.75rem',
    fontWeight: 600,
    padding: '2px 7px',
    borderRadius: '4px',
  },
  pagination: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
    marginTop: '1.5rem',
  },
  pageInfo: {
    fontSize: '0.875rem',
    color: '#555',
  },
  pageBtn: {
    padding: '0.4rem 0.9rem',
    fontSize: '0.875rem',
    border: '1px solid #ccc',
    borderRadius: '4px',
    background: '#fff',
    cursor: 'pointer',
  },
  newBtn: {
    marginLeft: 'auto',
    padding: '0.45rem 1rem',
    background: '#0055cc',
    color: '#fff',
    border: 'none',
    borderRadius: '5px',
    cursor: 'pointer',
    fontWeight: 600,
    fontSize: '0.875rem',
  },
  editBtn: {
    background: 'none',
    border: '1px solid #ccc',
    borderRadius: '4px',
    padding: '2px 8px',
    cursor: 'pointer',
    fontSize: '0.78rem',
    color: '#0055cc',
    fontWeight: 600,
  },
};
