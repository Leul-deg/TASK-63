import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import MaskedField from './MaskedField';

describe('MaskedField', () => {
  // ── Case 1: null / undefined → Restricted badge ───────────────────────────

  test('shows Restricted badge when value is null', () => {
    render(<MaskedField value={null} canReveal={true} label="DOB" />);
    expect(screen.getByText('Restricted')).toBeInTheDocument();
  });

  test('shows Restricted badge when value is undefined', () => {
    render(<MaskedField value={undefined} canReveal={true} label="DOB" />);
    expect(screen.getByText('Restricted')).toBeInTheDocument();
  });

  // ── Case 2: empty string → emptyText ──────────────────────────────────────

  test('shows default emptyText (—) for empty string', () => {
    render(<MaskedField value="" canReveal={true} label="DOB" />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  test('shows custom emptyText for empty string', () => {
    render(<MaskedField value="" canReveal={true} label="DOB" emptyText="N/A" />);
    expect(screen.getByText('N/A')).toBeInTheDocument();
  });

  // ── Case 3: canReveal=false → permanent mask, no button ───────────────────

  test('shows bullet mask with no Reveal button when canReveal is false', () => {
    render(<MaskedField value="secret" canReveal={false} label="DOB" />);
    expect(screen.getByText('••••••••')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  // ── Case 4: canReveal=true, not yet revealed → mask + Reveal button ───────

  test('shows mask and Reveal button when canReveal is true and not yet revealed', () => {
    render(<MaskedField value="1990-01-15" canReveal={true} label="date of birth" />);
    expect(screen.getByText('••••••••')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reveal date of birth/i })).toBeInTheDocument();
    expect(screen.queryByText('1990-01-15')).not.toBeInTheDocument();
  });

  test('clicking Reveal shows the value', () => {
    render(<MaskedField value="1990-01-15" canReveal={true} label="date of birth" />);
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    expect(screen.getByText('1990-01-15')).toBeInTheDocument();
  });

  // ── Case 5: revealed → value + Hide button ────────────────────────────────

  test('clicking Reveal then Hide re-masks the value', () => {
    render(<MaskedField value="1990-01-15" canReveal={true} label="date of birth" />);
    fireEvent.click(screen.getByRole('button', { name: /reveal/i }));
    expect(screen.getByText('1990-01-15')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /hide date of birth/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /hide/i }));
    expect(screen.queryByText('1990-01-15')).not.toBeInTheDocument();
    expect(screen.getByText('••••••••')).toBeInTheDocument();
  });
});
