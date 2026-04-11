import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import ResidentBookingsPage from './ResidentBookingsPage';
import { api } from '../api/client';

jest.mock('../api/client', () => ({
  api: {
    get: jest.fn(),
    post: jest.fn(),
    patch: jest.fn(),
  },
}));

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => jest.fn(),
  useParams: () => ({ id: 'resident-1' }),
}));

describe('ResidentBookingsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders existing bookings', async () => {
    api.get
      .mockResolvedValueOnce({ id: 'resident-1', firstName: 'Alex', lastName: 'Chen', buildingName: 'Maple Hall', roomNumber: '101' })
      .mockResolvedValueOnce([
        { id: 'booking-1', requestedDate: '2026-04-15', buildingName: 'Maple Hall', roomNumber: '101', purpose: 'Family visit', status: 'REQUESTED' },
      ]);

    render(<ResidentBookingsPage />);

    expect(await screen.findByText(/resident bookings/i)).toBeInTheDocument();
    expect(screen.getByText('Family visit')).toBeInTheDocument();
    expect(screen.getByText('REQUESTED')).toBeInTheDocument();
  });

  test('creates a booking', async () => {
    api.get
      .mockResolvedValueOnce({ id: 'resident-1', firstName: 'Alex', lastName: 'Chen', buildingName: 'Maple Hall', roomNumber: '101' })
      .mockResolvedValueOnce([]);
    api.post.mockResolvedValueOnce({
      id: 'booking-2',
      requestedDate: '2026-04-20',
      buildingName: 'Maple Hall',
      roomNumber: '101',
      purpose: 'Early arrival',
      status: 'REQUESTED',
    });

    render(<ResidentBookingsPage />);

    await screen.findByRole('button', { name: /create booking/i });

    fireEvent.change(screen.getByLabelText(/requested date/i), { target: { value: '2026-04-20' } });
    fireEvent.change(screen.getByLabelText(/purpose/i), { target: { value: 'Early arrival' } });
    fireEvent.click(screen.getByRole('button', { name: /create booking/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/residents/resident-1/bookings', expect.objectContaining({
        requestedDate: '2026-04-20',
        purpose: 'Early arrival',
      }));
    });
  });
});
