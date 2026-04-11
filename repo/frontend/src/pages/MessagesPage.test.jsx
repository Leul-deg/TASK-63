import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import MessagesPage from './MessagesPage';

jest.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'student-1', roles: ['STUDENT'] },
  }),
}));

jest.mock('../api/client', () => ({
  api: {
    get: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
    upload: jest.fn(),
  },
}));

const { api } = require('../api/client');

describe('MessagesPage', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  test('student can block a staff member from the block management panel', async () => {
    api.get.mockImplementation((path) => {
      if (path === '/api/messages/threads') return Promise.resolve([]);
      if (path === '/api/messages/blocks') return Promise.resolve([]);
      if (path === '/api/messages/users?q=ra') {
        return Promise.resolve([
          { id: 'staff-1', displayName: 'Residence Assistant', username: 'ra1' },
        ]);
      }
      return Promise.resolve([]);
    });
    api.post.mockResolvedValueOnce(null);

    await act(async () => {
      render(<MessagesPage />);
    });

    await act(async () => {
      fireEvent.click(await screen.findByTitle(/manage blocks/i));
    });
    expect(await screen.findByText(/blocked staff/i)).toBeInTheDocument();

    fireEvent.change(
      screen.getByPlaceholderText(/search staff by name or username/i),
      { target: { value: 'ra' } }
    );

    await act(async () => {
      jest.advanceTimersByTime(350);
    });

    expect(await screen.findByText(/Residence Assistant/i)).toBeInTheDocument();
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /block/i }));
    });

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/api/messages/blocks/staff-1', {});
    });
  });
});
