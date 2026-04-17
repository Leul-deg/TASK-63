import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import ResidentsPage from './ResidentsPage';

jest.mock('react-router-dom', () => ({
  useNavigate: () => jest.fn(),
}));

jest.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'staff-1', roles: ['RESIDENCE_STAFF'] },
  }),
}));

jest.mock('../api/client', () => ({
  api: {
    get: jest.fn(),
  },
}));

const { api } = require('../api/client');

describe('ResidentsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders page heading', async () => {
    // filter-options call
    api.get.mockResolvedValueOnce({ buildings: [], classYears: [] });
    // residents search call
    api.get.mockResolvedValueOnce({ content: [], totalPages: 0, totalElements: 0 });

    render(<ResidentsPage />);

    expect(await screen.findByText(/residents/i)).toBeInTheDocument();
  });

  test('shows resident names returned from the API', async () => {
    api.get.mockResolvedValueOnce({ buildings: ['North Hall'], classYears: [2026] });
    api.get.mockResolvedValueOnce({
      content: [
        { id: 'r1', firstName: 'Alice', lastName: 'Smith', email: 'alice@test.com',
          studentId: 'S001', roomNumber: '101', buildingName: 'North Hall',
          enrollmentStatus: 'ENROLLED', classYear: 2026, department: 'CS' },
      ],
      totalPages: 1,
      totalElements: 1,
    });

    render(<ResidentsPage />);

    const aliceElements = await screen.findAllByText(/alice/i);
    expect(aliceElements.length).toBeGreaterThan(0);
  });

  test('shows error message when API call fails', async () => {
    api.get.mockResolvedValueOnce({ buildings: [], classYears: [] });
    api.get.mockRejectedValueOnce(new Error('Server error'));

    render(<ResidentsPage />);

    expect(await screen.findByText(/server error/i)).toBeInTheDocument();
  });
});
