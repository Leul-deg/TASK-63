import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { fireEvent } from '@testing-library/react';
import NotificationBell from './NotificationBell';

const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

jest.mock('../api/client', () => ({
  api: { get: jest.fn() },
}));

const { api } = require('../api/client');

describe('NotificationBell', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders the bell button', async () => {
    api.get.mockResolvedValueOnce({ unread: 0, pendingAcknowledgment: 0 });
    render(<NotificationBell />);
    expect(await screen.findByRole('button')).toBeInTheDocument();
  });

  test('shows no badge when counts are zero', async () => {
    api.get.mockResolvedValueOnce({ unread: 0, pendingAcknowledgment: 0 });
    render(<NotificationBell />);
    await waitFor(() => expect(api.get).toHaveBeenCalled());
    // badge element should not appear
    expect(screen.queryByText(/^\d+$|^!$|^99\+$/)).not.toBeInTheDocument();
  });

  test('shows unread count badge when there are unread notifications', async () => {
    api.get.mockResolvedValueOnce({ unread: 5, pendingAcknowledgment: 0 });
    render(<NotificationBell />);
    expect(await screen.findByText('5')).toBeInTheDocument();
  });

  test('shows "!" badge (red) when there are pending acknowledgments', async () => {
    api.get.mockResolvedValueOnce({ unread: 2, pendingAcknowledgment: 1 });
    render(<NotificationBell />);
    expect(await screen.findByText('!')).toBeInTheDocument();
  });

  test('shows 99+ when unread count exceeds 99', async () => {
    api.get.mockResolvedValueOnce({ unread: 150, pendingAcknowledgment: 0 });
    render(<NotificationBell />);
    expect(await screen.findByText('99+')).toBeInTheDocument();
  });

  test('navigates to /notifications when clicked', async () => {
    api.get.mockResolvedValueOnce({ unread: 0, pendingAcknowledgment: 0 });
    render(<NotificationBell />);
    const btn = await screen.findByRole('button');
    fireEvent.click(btn);
    expect(mockNavigate).toHaveBeenCalledWith('/notifications');
  });
});
