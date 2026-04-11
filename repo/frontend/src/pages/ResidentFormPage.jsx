import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, HttpError } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { canRevealSensitive } from '../utils/roles';
import FormField from '../components/FormField';
import { useDebounce } from '../hooks/useDebounce';

// ── Constants ─────────────────────────────────────────────────────────────────

const STEPS = [
  { id: 'basics',    label: 'Student Basics'    },
  { id: 'enrollment',label: 'Enrollment'         },
  { id: 'contacts',  label: 'Emergency Contacts' },
  { id: 'movein',    label: 'Move-in Record'     },
];

const ENROLLMENT_STATUSES = ['ENROLLED', 'PART_TIME', 'WITHDRAWN', 'LEAVE_OF_ABSENCE', 'GRADUATED'];
const CHECK_IN_STATUSES   = ['PENDING', 'CHECKED_IN', 'CHECKED_OUT', 'NO_SHOW', 'CANCELLED'];
const PHONE_RE             = /^\d{3}-\d{3}-\d{4}$/;
const EMAIL_RE             = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// ── Blank state helpers ───────────────────────────────────────────────────────

const blankBasics = () => ({
  firstName: '', lastName: '', email: '', phone: '', studentId: '', dateOfBirth: '',
});

const blankEnrollment = () => ({
  enrollmentStatus: '', department: '', classYear: '', roomNumber: '', buildingName: '',
});

const blankContact = () => ({
  _key: crypto.randomUUID(), id: null,
  name: '', relationship: '', phone: '', email: '', primary: false, _delete: false,
});

const blankMoveIn = () => ({
  id: null,
  roomNumber: '', buildingName: '', moveInDate: '', moveOutDate: '',
  checkInStatus: 'PENDING', notes: '',
});

// ── Validation ────────────────────────────────────────────────────────────────

function validateBasics(f) {
  const e = {};
  if (!f.firstName.trim())                             e.firstName  = 'First name is required.';
  if (!f.lastName.trim())                              e.lastName   = 'Last name is required.';
  if (!f.email.trim())                                 e.email      = 'Email is required.';
  else if (!EMAIL_RE.test(f.email))                    e.email      = 'Enter a valid email address.';
  if (f.phone && !PHONE_RE.test(f.phone))              e.phone      = 'Use format 555-123-4567.';
  return e;
}

function validateEnrollment(f) {
  const e = {};
  if (f.classYear && (isNaN(f.classYear) || f.classYear < 1900 || f.classYear > 2100))
    e.classYear = 'Enter a valid 4-digit year.';
  return e;
}

function validateContact(c) {
  const e = {};
  if (!c.name.trim())                                  e.name         = 'Name is required.';
  if (!c.relationship.trim())                          e.relationship = 'Relationship is required.';
  if (!c.phone.trim())                                 e.phone        = 'Phone is required.';
  else if (!PHONE_RE.test(c.phone))                    e.phone        = 'Use format 555-123-4567.';
  if (c.email && !EMAIL_RE.test(c.email))              e.email        = 'Enter a valid email address.';
  return e;
}

function validateMoveIn(f) {
  const e = {};
  if (f.roomNumber && !f.buildingName.trim())          e.buildingName = 'Building is required when room is set.';
  if (f.buildingName && !f.roomNumber.trim())          e.roomNumber   = 'Room is required when building is set.';
  if ((f.roomNumber || f.buildingName) && !f.moveInDate) e.moveInDate = 'Move-in date is required.';
  return e;
}

// ── Phone auto-format ─────────────────────────────────────────────────────────

function formatPhone(raw) {
  const digits = raw.replace(/\D/g, '').slice(0, 10);
  if (digits.length <= 3) return digits;
  if (digits.length <= 6) return `${digits.slice(0,3)}-${digits.slice(3)}`;
  return `${digits.slice(0,3)}-${digits.slice(3,6)}-${digits.slice(6)}`;
}

// ── Main component ────────────────────────────────────────────────────────────

export default function ResidentFormPage() {
  const { id }      = useParams();               // undefined → create mode
  const isEdit      = Boolean(id);
  const navigate    = useNavigate();
  const { user }    = useAuth();
  const canReveal   = canRevealSensitive(user);

  // Step state
  const [step, setStep] = useState(0);

  // Form sections
  const [basics,     setBasics]     = useState(blankBasics());
  const [enrollment, setEnrollment] = useState(blankEnrollment());
  const [contacts,   setContacts]   = useState([]);        // EC rows
  const [moveIn,     setMoveIn]     = useState(blankMoveIn());

  // Validation errors per section
  const [basicsErr,     setBasicsErr]     = useState({});
  const [enrollmentErr, setEnrollmentErr] = useState({});
  const [contactsErr,   setContactsErr]   = useState({});  // keyed by _key
  const [moveInErr,     setMoveInErr]     = useState({});

  // Filter options
  const [buildings, setBuildings] = useState([]);

  // Duplicate detection
  const [dupeCheck,   setDupeCheck]   = useState(null);  // DuplicateCheckResponse | null
  const [dupeLoading, setDupeLoading] = useState(false);
  const [forceCreate, setForceCreate] = useState(false);

  // Submit state
  const [saving,   setSaving]   = useState(false);
  const [saveErr,  setSaveErr]  = useState('');

  // Page load state
  const [loading, setLoading] = useState(isEdit);
  const [loadErr, setLoadErr] = useState('');

  // ── Load filter options ───────────────────────────────────────────────────

  useEffect(() => {
    api.get('/api/residents/filter-options')
      .then(d => setBuildings(d.buildings ?? []))
      .catch(() => {});
  }, []);

  // ── Load existing resident for edit ──────────────────────────────────────

  useEffect(() => {
    if (!isEdit) return;
    setLoading(true);
    Promise.all([
      api.get(`/api/residents/${id}`),
      api.get(`/api/residents/${id}/emergency-contacts`),
      api.get(`/api/residents/${id}/move-in-records`),
    ])
      .then(([r, ecs, mirs]) => {
        setBasics({
          firstName:   r.firstName   ?? '',
          lastName:    r.lastName    ?? '',
          email:       r.email       ?? '',
          phone:       r.phone       ?? '',
          studentId:   r.studentId   ?? '',
          dateOfBirth: r.dateOfBirth ?? '',   // ISO string or '' (restricted)
        });
        setEnrollment({
          enrollmentStatus: r.enrollmentStatus ?? '',
          department:       r.department       ?? '',
          classYear:        r.classYear        != null ? String(r.classYear) : '',
          roomNumber:       r.roomNumber       ?? '',
          buildingName:     r.buildingName     ?? '',
        });
        setContacts((ecs ?? []).map(c => ({
          _key:         c.id,
          id:           c.id,
          name:         c.name         ?? '',
          relationship: c.relationship ?? '',
          phone:        c.phone        ?? '',
          email:        c.email        ?? '',
          primary:      c.primary,
          _delete:      false,
        })));
        const latest = (mirs ?? []).sort(
          (a, b) => new Date(b.moveInDate) - new Date(a.moveInDate))[0];
        if (latest) {
          setMoveIn({
            id:            latest.id,
            roomNumber:    latest.roomNumber    ?? '',
            buildingName:  latest.buildingName  ?? '',
            moveInDate:    latest.moveInDate    ?? '',
            moveOutDate:   latest.moveOutDate   ?? '',
            checkInStatus: latest.checkInStatus ?? 'PENDING',
            notes:         latest.notes         ?? '',
          });
        }
      })
      .catch(e => setLoadErr(e.message))
      .finally(() => setLoading(false));
  }, [id, isEdit]);

  // ── Duplicate check (debounced, triggers when basics change) ─────────────

  const dupeKey = useDebounce(
    `${basics.firstName}|${basics.lastName}|${basics.email}|${basics.studentId}|${basics.dateOfBirth}`, 600);

  const prevDupeKey = useRef('');
  useEffect(() => {
    if (dupeKey === prevDupeKey.current) return;
    prevDupeKey.current = dupeKey;

    const { firstName, lastName, email, studentId, dateOfBirth } = basics;
    const hasEnoughToCheck = studentId || (firstName && lastName && dateOfBirth);
    if (!hasEnoughToCheck) { setDupeCheck(null); return; }

    const params = new URLSearchParams();
    if (firstName)  params.set('firstName', firstName);
    if (lastName)   params.set('lastName',  lastName);
    if (email)      params.set('email',     email);
    if (studentId)  params.set('studentId', studentId);
    if (dateOfBirth) params.set('dateOfBirth', dateOfBirth);
    if (isEdit && id) params.set('excludeId', id);

    setDupeLoading(true);
    api.get(`/api/residents/duplicate-check?${params}`)
      .then(r => setDupeCheck(r.candidates?.length ? r : null))
      .catch(() => setDupeCheck(null))
      .finally(() => setDupeLoading(false));
  }, [dupeKey, basics, isEdit, id]);

  // ── Step navigation ───────────────────────────────────────────────────────

  const goNext = () => {
    if (!validateStep(step)) return;
    setStep(s => Math.min(s + 1, STEPS.length - 1));
  };

  const goBack = () => setStep(s => Math.max(s - 1, 0));

  const validateStep = (s) => {
    switch (s) {
      case 0: { const e = validateBasics(basics);     setBasicsErr(e);     return !Object.keys(e).length; }
      case 1: { const e = validateEnrollment(enrollment); setEnrollmentErr(e); return !Object.keys(e).length; }
      case 2: {
        const newErrs = {};
        let ok = true;
        contacts.filter(c => !c._delete).forEach(c => {
          const e = validateContact(c);
          if (Object.keys(e).length) { newErrs[c._key] = e; ok = false; }
        });
        setContactsErr(newErrs);
        return ok;
      }
      case 3: { const e = validateMoveIn(moveIn); setMoveInErr(e); return !Object.keys(e).length; }
      default: return true;
    }
  };

  // ── Contact helpers ───────────────────────────────────────────────────────

  const addContact = () => setContacts(cs => [...cs, blankContact()]);

  const updateContact = (key, field, value) => setContacts(cs =>
    cs.map(c => c._key !== key ? c : { ...c, [field]: value }));

  const markPrimary = (key) => setContacts(cs =>
    cs.map(c => ({ ...c, primary: c._key === key })));

  const removeContact = (key) => setContacts(cs =>
    cs.map(c => c._key !== key ? c :
      c.id ? { ...c, _delete: true } : null).filter(Boolean));

  // ── Submit ────────────────────────────────────────────────────────────────

  const handleSubmit = useCallback(async () => {
    // Re-validate all steps
    const step0ok = (() => { const e = validateBasics(basics);          setBasicsErr(e);     return !Object.keys(e).length; })();
    const step1ok = (() => { const e = validateEnrollment(enrollment);  setEnrollmentErr(e); return !Object.keys(e).length; })();
    const step2ok = (() => {
      const newErrs = {};
      let ok = true;
      contacts.filter(c => !c._delete).forEach(c => {
        const e = validateContact(c);
        if (Object.keys(e).length) { newErrs[c._key] = e; ok = false; }
      });
      setContactsErr(newErrs);
      return ok;
    })();
    const step3ok = (() => { const e = validateMoveIn(moveIn); setMoveInErr(e); return !Object.keys(e).length; })();
    if (!step0ok || !step1ok || !step2ok || !step3ok) { setStep(!step0ok ? 0 : !step1ok ? 1 : !step2ok ? 2 : 3); return; }

    setSaving(true);
    setSaveErr('');
    try {
      const residentPayload = {
        ...basics,
        ...enrollment,
        classYear: enrollment.classYear ? parseInt(enrollment.classYear, 10) : null,
        dateOfBirth: basics.dateOfBirth || null,
        phone:       basics.phone || null,
      };

      let residentId = id;
      let savedResident;

      if (isEdit) {
        savedResident = await api.put(`/api/residents/${id}`, residentPayload);
      } else {
        try {
          savedResident = await api.post(
            `/api/residents${forceCreate ? '?force=true' : ''}`, residentPayload);
          residentId = savedResident.id;
        } catch (err) {
          if (err instanceof HttpError && err.status === 409) {
            setDupeCheck(err.body);
            setForceCreate(false);
            setStep(0);
            setSaveErr('Possible duplicates found. Review the matches below and click "Save Anyway" to proceed.');
            return;
          }
          throw err;
        }
      }

      // Emergency contacts
      const activeContacts = contacts.filter(c => !c._delete);
      for (const c of contacts.filter(c => c._delete && c.id)) {
        await api.delete(`/api/residents/${residentId}/emergency-contacts/${c.id}`);
      }
      for (const c of activeContacts) {
        const payload = { name: c.name, relationship: c.relationship, phone: c.phone, email: c.email || null, primary: c.primary };
        if (c.id) {
          await api.put(`/api/residents/${residentId}/emergency-contacts/${c.id}`, payload);
        } else {
          await api.post(`/api/residents/${residentId}/emergency-contacts`, payload);
        }
      }

      // Move-in record
      const hasMoveIn = moveIn.roomNumber || moveIn.buildingName || moveIn.moveInDate;
      if (hasMoveIn) {
        const mirPayload = {
          roomNumber:    moveIn.roomNumber,
          buildingName:  moveIn.buildingName,
          moveInDate:    moveIn.moveInDate || null,
          moveOutDate:   moveIn.moveOutDate || null,
          checkInStatus: moveIn.checkInStatus,
          notes:         moveIn.notes || null,
        };
        if (isEdit && moveIn.id) {
          await api.put(`/api/residents/${residentId}/move-in-records/${moveIn.id}`, mirPayload);
        } else {
          await api.post(`/api/residents/${residentId}/move-in-records`, mirPayload);
        }
      }

      navigate(`/residents`);
    } catch (err) {
      setSaveErr(err.message ?? 'Save failed. Please try again.');
    } finally {
      setSaving(false);
    }
  }, [basics, enrollment, contacts, moveIn, id, isEdit, forceCreate, navigate]);

  // ── Render ────────────────────────────────────────────────────────────────

  if (loading) return <div style={styles.loading}>Loading…</div>;
  if (loadErr) return (
    <div style={styles.loadErr}>
      Failed to load resident: {loadErr}
      <button onClick={() => navigate('/residents')} style={styles.backBtn}>Back to directory</button>
    </div>
  );

  const currentStepId = STEPS[step].id;

  return (
    <main style={styles.main}>
      {/* Header */}
      <div style={styles.header}>
        <button style={styles.backBtn} onClick={() => navigate('/residents')}>← Back</button>
        <h2 style={styles.title}>{isEdit ? 'Edit Resident' : 'New Resident'}</h2>
      </div>

      {/* Stepper */}
      <StepIndicator steps={STEPS} current={step} onClickStep={i => { if (i < step) setStep(i); }} />

      {/* Duplicate warning */}
      {dupeCheck && (
        <DuplicateWarning
          candidates={dupeCheck.candidates}
          onDismiss={() => setDupeCheck(null)}
          onForce={() => { setForceCreate(true); setDupeCheck(null); setSaveErr(''); }}
          isForced={forceCreate}
        />
      )}
      {dupeLoading && <div style={styles.dupeLoading}>Checking for duplicates…</div>}

      {/* Step panels */}
      <div style={styles.panel}>
        {currentStepId === 'basics'     && <BasicsSection     data={basics}      errors={basicsErr}      onChange={setBasics}     canReveal={canReveal} />}
        {currentStepId === 'enrollment' && <EnrollmentSection data={enrollment}  errors={enrollmentErr}  onChange={setEnrollment} buildings={buildings} />}
        {currentStepId === 'contacts'   && <ContactsSection   contacts={contacts} errors={contactsErr}  onAdd={addContact} onUpdate={updateContact} onRemove={removeContact} onPrimary={markPrimary} />}
        {currentStepId === 'movein'     && <MoveInSection     data={moveIn}      errors={moveInErr}      onChange={setMoveIn}     buildings={buildings} />}
      </div>

      {/* Save error */}
      {saveErr && (
        <div style={styles.saveErr}>
          {saveErr}
          {forceCreate && (
            <button style={styles.forceBtn} onClick={handleSubmit} disabled={saving}>
              Save Anyway
            </button>
          )}
        </div>
      )}

      {/* Nav buttons */}
      <div style={styles.nav}>
        {step > 0 && (
          <button style={styles.secondaryBtn} onClick={goBack} disabled={saving}>
            ← Back
          </button>
        )}
        <div style={{ flex: 1 }} />
        {step < STEPS.length - 1 ? (
          <button style={styles.primaryBtn} onClick={goNext}>
            Next →
          </button>
        ) : (
          <button style={styles.primaryBtn} onClick={handleSubmit} disabled={saving}>
            {saving ? 'Saving…' : isEdit ? 'Save Changes' : 'Create Resident'}
          </button>
        )}
      </div>
    </main>
  );
}

// ── Step indicator ────────────────────────────────────────────────────────────

function StepIndicator({ steps, current, onClickStep }) {
  return (
    <div style={indicator.wrapper}>
      {steps.map((s, i) => {
        const done = i < current;
        const active = i === current;
        return (
          <React.Fragment key={s.id}>
            <button
              style={{ ...indicator.step, ...(active ? indicator.active : done ? indicator.done : {}) }}
              onClick={() => onClickStep(i)}
              disabled={i > current}
            >
              <span style={indicator.num}>{done ? '✓' : i + 1}</span>
              <span style={indicator.label}>{s.label}</span>
            </button>
            {i < steps.length - 1 && (
              <div style={{ ...indicator.line, background: done ? '#0055cc' : '#ddd' }} />
            )}
          </React.Fragment>
        );
      })}
    </div>
  );
}

// ── Section: Student basics ───────────────────────────────────────────────────

function BasicsSection({ data, errors, onChange, canReveal }) {
  const set = (field) => (e) => onChange(d => ({ ...d, [field]: e.target.value }));
  const setPhone = (e) => onChange(d => ({ ...d, phone: formatPhone(e.target.value) }));

  return (
    <div style={styles.section}>
      <h3 style={styles.sectionTitle}>Student Basics</h3>
      <div style={styles.grid2}>
        <FormField label="First Name" required error={errors.firstName}>
          <input value={data.firstName} onChange={set('firstName')} placeholder="Jane" />
        </FormField>
        <FormField label="Last Name" required error={errors.lastName}>
          <input value={data.lastName} onChange={set('lastName')} placeholder="Smith" />
        </FormField>
      </div>
      <FormField label="Email Address" required error={errors.email}>
        <input type="email" value={data.email} onChange={set('email')} placeholder="jane.smith@example.edu" />
      </FormField>
      <div style={styles.grid2}>
        <FormField label="Phone" hint="Format: 555-123-4567" error={errors.phone}>
          <input value={data.phone} onChange={setPhone} placeholder="555-123-4567" inputMode="tel" />
        </FormField>
        <FormField label="Student ID" error={errors.studentId}>
          <input value={data.studentId} onChange={set('studentId')} placeholder="S1234567" />
        </FormField>
      </div>
      {canReveal && (
        <FormField label="Date of Birth" hint="YYYY-MM-DD" error={errors.dateOfBirth}>
          <input type="date" value={data.dateOfBirth} onChange={set('dateOfBirth')} />
        </FormField>
      )}
      {!canReveal && (
        <div style={styles.restrictedField}>
          Date of Birth — <em>restricted field, only visible to staff</em>
        </div>
      )}
    </div>
  );
}

// ── Building selector (shared by Enrollment and Move-in) ─────────────────────

/**
 * Dropdown of known buildings with an "Other / unlisted" escape hatch.
 *
 * When the user picks "Other / unlisted", a text input appears so they can
 * type the real building name.  The sentinel "__other__" is used only as the
 * <select> option value internally and is never propagated to the parent via
 * onChange.
 *
 * FormField injects `style` and `aria-invalid` via React.cloneElement; both
 * are forwarded to the active control so border-color and focus styling remain
 * consistent with every other field on the form.
 */
function BuildingSelector({ value, onChange, buildings, style, 'aria-invalid': ariaInvalid }) {
  // In edit mode a persisted building might not be in the current list (e.g.
  // renamed or removed from filter-options).  Treat it as "Other" so the name
  // is shown in the text input rather than silently lost.
  const [isOther, setIsOther] = useState(value !== '' && !buildings.includes(value));

  function handleSelectChange(e) {
    if (e.target.value === '__other__') {
      setIsOther(true);
      onChange('');   // clear until the user types the real name
    } else {
      setIsOther(false);
      onChange(e.target.value);
    }
  }

  return (
    <>
      <select
        value={isOther ? '__other__' : value}
        onChange={handleSelectChange}
        style={style}
        aria-invalid={ariaInvalid}
      >
        <option value="">— Select —</option>
        {buildings.map(b => <option key={b} value={b}>{b}</option>)}
        <option value="__other__">Other / unlisted</option>
      </select>
      {isOther && (
        <input
          value={value}
          onChange={e => onChange(e.target.value)}
          placeholder="Enter building name"
          autoFocus
          style={{ ...style, marginTop: 6 }}
          aria-invalid={ariaInvalid}
        />
      )}
    </>
  );
}

// ── Section: Enrollment ───────────────────────────────────────────────────────

function EnrollmentSection({ data, errors, onChange, buildings }) {
  const set = (field) => (e) => onChange(d => ({ ...d, [field]: e.target.value }));

  return (
    <div style={styles.section}>
      <h3 style={styles.sectionTitle}>Enrollment & Classification</h3>
      <div style={styles.grid2}>
        <FormField label="Enrollment Status" error={errors.enrollmentStatus}>
          <select value={data.enrollmentStatus} onChange={set('enrollmentStatus')}>
            <option value="">— Select —</option>
            {ENROLLMENT_STATUSES.map(s => (
              <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </FormField>
        <FormField label="Class Year" hint="e.g. 2027" error={errors.classYear}>
          <input type="number" value={data.classYear} onChange={set('classYear')}
            placeholder="2027" min="1900" max="2100" />
        </FormField>
      </div>
      <FormField label="Department" error={errors.department}>
        <input value={data.department} onChange={set('department')} placeholder="e.g. Computer Science" />
      </FormField>
      <div style={styles.grid2}>
        <FormField label="Building" error={errors.buildingName}>
          {buildings.length > 0 ? (
            <BuildingSelector
              value={data.buildingName}
              onChange={v => onChange(d => ({ ...d, buildingName: v }))}
              buildings={buildings}
            />
          ) : (
            <input value={data.buildingName} onChange={set('buildingName')} placeholder="Building name" />
          )}
        </FormField>
        <FormField label="Room Number" error={errors.roomNumber}>
          <input value={data.roomNumber} onChange={set('roomNumber')} placeholder="e.g. 204A" />
        </FormField>
      </div>
    </div>
  );
}

// ── Section: Emergency contacts ───────────────────────────────────────────────

function ContactsSection({ contacts, errors, onAdd, onUpdate, onRemove, onPrimary }) {
  const visible = contacts.filter(c => !c._delete);
  return (
    <div style={styles.section}>
      <h3 style={styles.sectionTitle}>Emergency Contacts</h3>
      {visible.length === 0 && (
        <div style={styles.emptyContacts}>No emergency contacts added yet.</div>
      )}
      {visible.map((c, idx) => (
        <ContactRow
          key={c._key}
          contact={c}
          index={idx}
          errors={errors[c._key] ?? {}}
          onChange={(field, val) => onUpdate(c._key, field, val)}
          onRemove={() => onRemove(c._key)}
          onMarkPrimary={() => onPrimary(c._key)}
        />
      ))}
      <button style={styles.addBtn} onClick={onAdd}>+ Add contact</button>
    </div>
  );
}

function ContactRow({ contact: c, index, errors, onChange, onRemove, onMarkPrimary }) {
  const set = (field) => (e) => onChange(field, e.target.value);
  const setPhone = (e) => onChange('phone', formatPhone(e.target.value));
  return (
    <div style={styles.contactCard}>
      <div style={styles.contactHeader}>
        <span style={styles.contactTitle}>Contact {index + 1}</span>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          {c.primary
            ? <span style={styles.primaryBadge}>Primary</span>
            : <button style={styles.linkBtn} onClick={onMarkPrimary}>Set as primary</button>}
          <button style={styles.removeBtn} onClick={onRemove}>Remove</button>
        </div>
      </div>
      <div style={styles.grid2}>
        <FormField label="Name" required error={errors.name}>
          <input value={c.name} onChange={set('name')} placeholder="Full name" />
        </FormField>
        <FormField label="Relationship" required error={errors.relationship}>
          <input value={c.relationship} onChange={set('relationship')} placeholder="e.g. Parent" />
        </FormField>
      </div>
      <div style={styles.grid2}>
        <FormField label="Phone" required hint="555-123-4567" error={errors.phone}>
          <input value={c.phone} onChange={setPhone} placeholder="555-123-4567" inputMode="tel" />
        </FormField>
        <FormField label="Email" error={errors.email}>
          <input type="email" value={c.email} onChange={set('email')} placeholder="optional" />
        </FormField>
      </div>
    </div>
  );
}

// ── Section: Move-in record ───────────────────────────────────────────────────

function MoveInSection({ data, errors, onChange, buildings }) {
  const set = (field) => (e) => onChange(d => ({ ...d, [field]: e.target.value }));
  const hasRecord = data.roomNumber || data.buildingName || data.moveInDate;

  return (
    <div style={styles.section}>
      <h3 style={styles.sectionTitle}>Move-in Record</h3>
      <p style={styles.sectionHint}>
        Optionally record the initial move-in details. Leave blank to skip.
      </p>
      <div style={styles.grid2}>
        <FormField label="Building" required={!!hasRecord} error={errors.buildingName}>
          {buildings.length > 0 ? (
            <BuildingSelector
              value={data.buildingName}
              onChange={v => onChange(d => ({ ...d, buildingName: v }))}
              buildings={buildings}
            />
          ) : (
            <input value={data.buildingName} onChange={set('buildingName')} placeholder="Building name" />
          )}
        </FormField>
        <FormField label="Room Number" required={!!hasRecord} error={errors.roomNumber}>
          <input value={data.roomNumber} onChange={set('roomNumber')} placeholder="e.g. 204A" />
        </FormField>
      </div>
      <div style={styles.grid2}>
        <FormField label="Move-in Date" required={!!hasRecord} error={errors.moveInDate}>
          <input type="date" value={data.moveInDate} onChange={set('moveInDate')} />
        </FormField>
        <FormField label="Move-out Date (optional)" error={errors.moveOutDate}>
          <input type="date" value={data.moveOutDate} onChange={set('moveOutDate')} />
        </FormField>
      </div>
      <FormField label="Check-in Status">
        <select value={data.checkInStatus} onChange={set('checkInStatus')}>
          {CHECK_IN_STATUSES.map(s => <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>)}
        </select>
      </FormField>
      <FormField label="Notes (optional)">
        <textarea value={data.notes} onChange={set('notes')} rows={3} placeholder="Any notes about move-in…" />
      </FormField>
    </div>
  );
}

// ── Duplicate warning ─────────────────────────────────────────────────────────

function DuplicateWarning({ candidates, onDismiss, onForce, isForced }) {
  return (
    <div style={dupe.box}>
      <div style={dupe.header}>
        <strong>Possible duplicates detected</strong>
        <button style={dupe.dismiss} onClick={onDismiss}>×</button>
      </div>
      <p style={dupe.sub}>
        The following residents may already exist. Review before saving.
      </p>
      <table style={dupe.table}>
        <thead>
          <tr>
            <th style={dupe.th}>Student ID</th>
            <th style={dupe.th}>Name</th>
            <th style={dupe.th}>Email</th>
            <th style={dupe.th}>Matched on</th>
          </tr>
        </thead>
        <tbody>
          {candidates.map(c => (
            <tr key={c.id}>
              <td style={dupe.td}>{c.studentId ?? '—'}</td>
              <td style={dupe.td}>{c.firstName} {c.lastName}</td>
              <td style={dupe.td}>{c.email}</td>
              <td style={dupe.td}><span style={dupe.reason}>{c.matchReason}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
      {!isForced && (
        <button style={dupe.forceBtn} onClick={onForce}>
          Save anyway (not a duplicate)
        </button>
      )}
    </div>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = {
  main: { padding: '2rem', maxWidth: '720px', fontFamily: 'system-ui, sans-serif' },
  header: { display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1.5rem' },
  title: { margin: 0, fontSize: '1.4rem', fontWeight: 600 },
  loading: { padding: '3rem', color: '#888', fontFamily: 'system-ui, sans-serif' },
  loadErr: { padding: '2rem', color: '#c0392b', fontFamily: 'system-ui, sans-serif', display: 'flex', gap: '1rem', alignItems: 'center' },
  panel: { background: '#fff', border: '1px solid #e5e5e5', borderRadius: '8px', padding: '1.5rem', marginTop: '1.5rem' },
  section: { display: 'flex', flexDirection: 'column', gap: '1rem' },
  sectionTitle: { margin: '0 0 0.25rem', fontSize: '1.1rem', fontWeight: 600 },
  sectionHint: { margin: 0, fontSize: '0.82rem', color: '#666' },
  grid2: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' },
  restrictedField: { fontSize: '0.85rem', color: '#888', padding: '0.4rem 0.65rem', background: '#f8f8f8', borderRadius: '5px', border: '1px solid #e0e0e0' },
  emptyContacts: { fontSize: '0.875rem', color: '#888', padding: '1rem', background: '#fafafa', borderRadius: '5px', textAlign: 'center' },
  contactCard: { border: '1px solid #e0e0e0', borderRadius: '7px', padding: '1rem', display: 'flex', flexDirection: 'column', gap: '0.75rem', background: '#fafafa' },
  contactHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  contactTitle: { fontWeight: 600, fontSize: '0.875rem' },
  primaryBadge: { fontSize: '0.72rem', fontWeight: 700, padding: '2px 7px', background: '#e6f4ea', color: '#1a7f37', borderRadius: '4px' },
  addBtn: { alignSelf: 'flex-start', background: 'none', border: '1px dashed #aaa', borderRadius: '5px', padding: '0.35rem 0.75rem', cursor: 'pointer', fontSize: '0.85rem', color: '#555' },
  nav: { display: 'flex', alignItems: 'center', marginTop: '1.5rem', gap: '0.75rem' },
  primaryBtn: { padding: '0.55rem 1.5rem', background: '#0055cc', color: '#fff', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem' },
  secondaryBtn: { padding: '0.55rem 1rem', background: '#fff', color: '#333', border: '1px solid #ccc', borderRadius: '5px', cursor: 'pointer', fontSize: '0.9rem' },
  backBtn: { padding: '0.35rem 0.75rem', background: 'none', border: '1px solid #ccc', borderRadius: '5px', cursor: 'pointer', fontSize: '0.85rem', color: '#555' },
  saveErr: { marginTop: '0.75rem', padding: '0.6rem 0.8rem', background: '#fff0f0', border: '1px solid #ffcccc', borderRadius: '5px', color: '#c0392b', fontSize: '0.875rem', display: 'flex', alignItems: 'center', gap: '1rem' },
  forceBtn: { padding: '0.3rem 0.75rem', background: '#c0392b', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 600, fontSize: '0.8rem' },
  dupeLoading: { marginTop: '0.5rem', fontSize: '0.8rem', color: '#888' },
  linkBtn: { background: 'none', border: 'none', cursor: 'pointer', color: '#0055cc', fontSize: '0.8rem', textDecoration: 'underline', padding: 0 },
  removeBtn: { background: 'none', border: 'none', cursor: 'pointer', color: '#c0392b', fontSize: '0.8rem', textDecoration: 'underline', padding: 0 },
};

const indicator = {
  wrapper: { display: 'flex', alignItems: 'center', gap: 0 },
  step: { display: 'flex', alignItems: 'center', gap: '6px', padding: '0.5rem 0.75rem', background: 'none', border: 'none', cursor: 'pointer', borderRadius: '6px', fontSize: '0.82rem', color: '#888', fontWeight: 600 },
  active: { color: '#0055cc', background: '#e8f0fe' },
  done:   { color: '#1a7f37' },
  num:    { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: '20px', height: '20px', borderRadius: '50%', background: '#ddd', fontSize: '0.72rem', fontWeight: 700 },
  label:  { },
  line:   { flex: 1, height: '2px', minWidth: '24px' },
};

const dupe = {
  box: { marginTop: '1rem', background: '#fffbeb', border: '1px solid #f0c040', borderRadius: '7px', padding: '1rem' },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.25rem' },
  sub: { margin: '0 0 0.75rem', fontSize: '0.82rem', color: '#555' },
  dismiss: { background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.1rem', color: '#888' },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem', marginBottom: '0.75rem' },
  th: { textAlign: 'left', padding: '4px 8px', borderBottom: '1px solid #e0c060', fontWeight: 600, color: '#555' },
  td: { padding: '4px 8px', borderBottom: '1px solid #f0e0a0' },
  reason: { display: 'inline-block', padding: '1px 6px', background: '#fef3c7', color: '#92400e', borderRadius: '4px', fontSize: '0.72rem', fontWeight: 700 },
  forceBtn: { background: 'none', border: '1px solid #b7791f', color: '#b7791f', borderRadius: '4px', padding: '0.3rem 0.75rem', cursor: 'pointer', fontSize: '0.8rem', fontWeight: 600 },
};
