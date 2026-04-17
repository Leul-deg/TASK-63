import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import StudentSelfPage from './StudentSelfPage';

jest.mock('../api/client', () => ({
  api: {
    get: jest.fn(),
  },
}));

const { api } = require('../api/client');

describe('StudentSelfPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('shows loading state while profile data is being fetched', () => {
    api.get.mockReturnValue(new Promise(() => {})); // never resolves
    render(<StudentSelfPage />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  test('shows error message when the profile API call fails', async () => {
    api.get.mockRejectedValue(new Error('Network error'));
    render(<StudentSelfPage />);
    expect(await screen.findByText(/my profile/i)).toBeInTheDocument();
    expect(screen.getByText(/network error/i)).toBeInTheDocument();
  });

  test('renders bookings table rows when the student has bookings', async () => {
    api.get
      .mockResolvedValueOnce({
        firstName: 'Alex', lastName: 'Chen', studentId: 'S-100',
        email: 'alex@example.edu', phone: '555-123-4567',
        dateOfBirth: null, enrollmentStatus: 'ENROLLED', department: 'CS',
        classYear: 2027, roomNumber: '101', buildingName: 'Maple Hall',
      })
      .mockResolvedValueOnce([
        { id: 'booking-1', requestedDate: '2026-04-15', buildingName: 'Maple Hall', roomNumber: '101', status: 'CONFIRMED' },
      ]);

    render(<StudentSelfPage />);

    expect(await screen.findByText('CONFIRMED')).toBeInTheDocument();
    expect(screen.getByText('2026-04-15')).toBeInTheDocument();
  });

  test('does not render raw date of birth in self-service view', async () => {
    api.get
      .mockResolvedValueOnce({
        firstName: 'Alex',
        lastName: 'Chen',
        studentId: 'S-100',
        email: 'alex@example.edu',
        phone: '555-123-4567',
        dateOfBirth: null,
        enrollmentStatus: 'ENROLLED',
        department: 'CS',
        classYear: 2027,
        roomNumber: '101',
        buildingName: 'Maple Hall',
      })
      .mockResolvedValueOnce([]);

    render(<StudentSelfPage />);

    expect(await screen.findByText(/my profile/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText('Restricted')).toBeInTheDocument();
    });
    expect(screen.getByText('Alex Chen')).toBeInTheDocument();
    expect(screen.getByText('S-100')).toBeInTheDocument();
    expect(screen.getByText('alex@example.edu')).toBeInTheDocument();
    expect(screen.getByText('555-123-4567')).toBeInTheDocument();
    expect(screen.getByText('ENROLLED')).toBeInTheDocument();
    expect(screen.getByText('CS')).toBeInTheDocument();
    expect(screen.getByText('2027')).toBeInTheDocument();
    expect(screen.getByText('Maple Hall')).toBeInTheDocument();
    expect(screen.queryByText('2005-01-01')).not.toBeInTheDocument();
  });
});
