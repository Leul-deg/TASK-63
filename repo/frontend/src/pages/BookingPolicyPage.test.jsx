import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import BookingPolicyPage from './BookingPolicyPage';

jest.mock('../context/AuthContext', () => ({ useAuth: jest.fn() }));
jest.mock('../utils/roles', () => ({ canAccessAdmin: jest.fn() }));
jest.mock('../api/client', () => ({
  api: { get: jest.fn(), put: jest.fn(), post: jest.fn() },
}));

const { useAuth }       = require('../context/AuthContext');
const { canAccessAdmin } = require('../utils/roles');
const { api }            = require('../api/client');

const STUB_POLICY = {
  version: 3,
  createdByUsername: 'admin',
  createdAt: '2024-01-01T12:00:00Z',
  policy: {
    windowDays: 14,
    sameDayCutoffHour: 17,
    sameDayCutoffMinute: 0,
    noShowThreshold: 2,
    noShowWindowDays: 30,
    canaryEnabled: false,
    canaryRolloutPercent: 10,
    canaryBuildingIds: [],
    holidayBlackoutDates: [],
  },
};

function setupAdmin() {
  useAuth.mockReturnValue({ user: { username: 'admin' } });
  canAccessAdmin.mockReturnValue(true);
  api.get.mockImplementation(url => {
    if (url === '/api/admin/booking-policy')          return Promise.resolve(STUB_POLICY);
    if (url.includes('/booking-policy/history'))      return Promise.resolve([]);
    return Promise.resolve({});
  });
}

describe('BookingPolicyPage', () => {
  beforeEach(() => jest.clearAllMocks());

  test('renders "Booking & Visit Policy" heading for admin', async () => {
    setupAdmin();
    render(<BookingPolicyPage />);
    expect(await screen.findByText(/booking & visit policy/i)).toBeInTheDocument();
  });

  test('shows current version number after loading', async () => {
    setupAdmin();
    render(<BookingPolicyPage />);
    expect(await screen.findByText(/version 3/i)).toBeInTheDocument();
  });

  test('non-admin sees "Admin access required."', () => {
    useAuth.mockReturnValue({ user: { username: 'guest' } });
    canAccessAdmin.mockReturnValue(false);
    render(<BookingPolicyPage />);
    expect(screen.getByText(/admin access required/i)).toBeInTheDocument();
  });

  test('shows error message when API fails to load', async () => {
    useAuth.mockReturnValue({ user: { username: 'admin' } });
    canAccessAdmin.mockReturnValue(true);
    api.get.mockRejectedValue(new Error('Network failure'));
    render(<BookingPolicyPage />);
    expect(await screen.findByText(/failed to load policy/i)).toBeInTheDocument();
  });

  test('"Save New Version" button calls api.put with the current policy', async () => {
    setupAdmin();
    api.put.mockResolvedValueOnce({ ...STUB_POLICY, version: 4 });
    render(<BookingPolicyPage />);
    await screen.findByText(/booking & visit policy/i);

    fireEvent.click(screen.getByRole('button', { name: /save new version/i }));

    await waitFor(() => {
      expect(api.put).toHaveBeenCalledWith(
        '/api/admin/booking-policy',
        expect.objectContaining({ policy: expect.any(Object) })
      );
    });
  });

  test('save success shows confirmation with new version number', async () => {
    setupAdmin();
    api.put.mockResolvedValueOnce({ ...STUB_POLICY, version: 4 });
    render(<BookingPolicyPage />);
    await screen.findByText(/booking & visit policy/i);

    fireEvent.click(screen.getByRole('button', { name: /save new version/i }));

    expect(await screen.findByText(/saved as version 4/i)).toBeInTheDocument();
  });

  test('"View History" button reveals the Version History section', async () => {
    setupAdmin();
    render(<BookingPolicyPage />);
    await screen.findByText(/booking & visit policy/i);

    fireEvent.click(screen.getByRole('button', { name: /view history/i }));

    expect(await screen.findByRole('heading', { name: /version history/i })).toBeInTheDocument();
  });
});
