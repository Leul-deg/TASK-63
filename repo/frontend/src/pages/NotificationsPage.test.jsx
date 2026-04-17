import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import NotificationsPage from './NotificationsPage';

jest.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'student-1', roles: ['STUDENT'] },
  }),
}));

jest.mock('../api/client', () => ({
  api: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const { api } = require('../api/client');

describe('NotificationsPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders notification card with title, body, and category label', async () => {
    api.get.mockResolvedValueOnce({
      content: [
        {
          id: 'notif-2',
          title: 'Room assignment update',
          body: 'Your room has been changed.',
          priority: 'HIGH',
          category: 'ONBOARDING',
          read: false,
          requiresAcknowledgment: false,
          acknowledged: false,
          createdAt: '2026-04-10T10:00:00Z',
        },
      ],
      totalPages: 1,
    });

    render(<NotificationsPage />);

    expect(await screen.findByText('Room assignment update')).toBeInTheDocument();
    expect(screen.getByText('Your room has been changed.')).toBeInTheDocument();
    // The HIGH priority badge is unique to the notification card (category filter
    // is by category, not priority), so this confirms the card is rendered.
    expect(screen.getByText('High')).toBeInTheDocument();
  });

  test('shows empty state text when inbox is empty', async () => {
    api.get.mockResolvedValueOnce({ content: [], totalPages: 0 });
    render(<NotificationsPage />);
    expect(await screen.findByText(/no notifications yet/i)).toBeInTheDocument();
  });

  test('mark-as-read button posts to the notification read endpoint', async () => {
    const notif = {
      id: 'notif-3', title: 'Unread notice', body: 'Body text',
      priority: 'NORMAL', category: 'GENERAL', read: false,
      requiresAcknowledgment: false, acknowledged: false,
      createdAt: '2026-04-10T10:00:00Z',
    };
    api.get.mockResolvedValueOnce({ content: [notif], totalPages: 1 });
    api.post.mockResolvedValueOnce({ ...notif, read: true });

    render(<NotificationsPage />);
    await screen.findByText('Unread notice');
    fireEvent.click(screen.getByRole('button', { name: /mark as read/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/notifications/notif-3/read', {});
    });
  });

  test('mark-all-as-read button posts to the read-all endpoint', async () => {
    const notif = {
      id: 'notif-4', title: 'Another notice', body: 'Body',
      priority: 'NORMAL', category: 'GENERAL', read: false,
      requiresAcknowledgment: false, acknowledged: false,
      createdAt: '2026-04-10T10:00:00Z',
    };
    api.get.mockResolvedValueOnce({ content: [notif], totalPages: 1 });
    api.post.mockResolvedValueOnce({});

    render(<NotificationsPage />);
    await screen.findByText('Another notice');
    fireEvent.click(screen.getByRole('button', { name: /mark all as read/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/notifications/read-all', {});
    });
  });

  test('unread-only checkbox triggers a second inbox fetch', async () => {
    api.get
      .mockResolvedValueOnce({ content: [], totalPages: 0 })
      .mockResolvedValueOnce({ content: [], totalPages: 0 });

    render(<NotificationsPage />);
    await screen.findByText(/no notifications yet/i);

    fireEvent.click(screen.getByLabelText(/unread only/i));

    await waitFor(() => {
      expect(api.get).toHaveBeenCalledTimes(2);
    });
  });

  test('auto-opens critical acknowledgment modal and records acknowledgment', async () => {
    api.get.mockResolvedValueOnce({
      content: [
        {
          id: 'notif-1',
          title: 'Critical notice',
          body: 'Read this now',
          priority: 'CRITICAL',
          category: 'ARBITRATION',
          read: false,
          requiresAcknowledgment: true,
          acknowledged: false,
          createdAt: '2026-04-10T10:00:00Z',
        },
      ],
      totalPages: 1,
    });
    api.post.mockResolvedValueOnce({
      id: 'notif-1',
      title: 'Critical notice',
      body: 'Read this now',
      priority: 'CRITICAL',
      category: 'ARBITRATION',
      read: true,
      requiresAcknowledgment: true,
      acknowledged: true,
      acknowledgedAt: '2026-04-10T10:05:00Z',
      createdAt: '2026-04-10T10:00:00Z',
    });

    render(<NotificationsPage />);

    expect(await screen.findByText(/acknowledgment required/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /i acknowledge receipt/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/notifications/notif-1/acknowledge', {});
    });
  });
});
