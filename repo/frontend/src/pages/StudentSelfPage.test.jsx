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
