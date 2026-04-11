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
