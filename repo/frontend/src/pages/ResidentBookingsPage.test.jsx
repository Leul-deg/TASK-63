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

  test('shows error when the initial load fails', async () => {
    api.get.mockRejectedValue(new Error('Server error'));
    render(<ResidentBookingsPage />);
    expect(await screen.findByText(/server error/i)).toBeInTheDocument();
  });

  test('shows empty booking history message when no bookings exist', async () => {
    api.get
      .mockResolvedValueOnce({ id: 'resident-1', firstName: 'Alex', lastName: 'Chen', buildingName: '', roomNumber: '' })
      .mockResolvedValueOnce([]);
    render(<ResidentBookingsPage />);
    expect(await screen.findByText(/no bookings yet/i)).toBeInTheDocument();
  });

  test('shows validation error when required fields are missing on submit', async () => {
    api.get
      .mockResolvedValueOnce({ id: 'resident-1', firstName: 'Alex', lastName: 'Chen', buildingName: '', roomNumber: '' })
      .mockResolvedValueOnce([]);
    render(<ResidentBookingsPage />);
    await screen.findByRole('button', { name: /create booking/i });

    fireEvent.click(screen.getByRole('button', { name: /create booking/i }));

    expect(await screen.findByText(/requested date and building are required/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  test('updating a booking status calls api.patch with the new status', async () => {
    const booking = {
      id: 'booking-1', requestedDate: '2026-04-15',
      buildingName: 'Maple Hall', roomNumber: '101',
      purpose: 'Visit', status: 'REQUESTED',
    };
    api.get
      .mockResolvedValueOnce({ id: 'resident-1', firstName: 'Alex', lastName: 'Chen', buildingName: 'Maple Hall', roomNumber: '101' })
      .mockResolvedValueOnce([booking]);
    api.patch.mockResolvedValueOnce({ ...booking, status: 'CONFIRMED' });
    window.prompt = jest.fn().mockReturnValue('');

    render(<ResidentBookingsPage />);
    await screen.findByText('REQUESTED');
    fireEvent.click(screen.getByRole('button', { name: /^confirm$/i }));

    await waitFor(() => {
      expect(api.patch).toHaveBeenCalledWith(
        '/api/residents/resident-1/bookings/booking-1/status',
        expect.objectContaining({ status: 'CONFIRMED' }),
      );
    });
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
