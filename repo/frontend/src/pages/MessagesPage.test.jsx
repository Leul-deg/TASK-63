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
    // jsdom does not implement scrollIntoView; silence the error so timer-driven
    // scroll calls do not crash tests that exercise the message pane.
    window.HTMLElement.prototype.scrollIntoView = jest.fn();
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

  test('empty inbox shows placeholder text', async () => {
    api.get.mockImplementation((path) => {
      if (path === '/api/messages/threads') return Promise.resolve([]);
      if (path === '/api/messages/blocks')  return Promise.resolve([]);
      return Promise.resolve([]);
    });

    await act(async () => { render(<MessagesPage />); });

    expect(await screen.findByText(/no conversations yet/i)).toBeInTheDocument();
  });

  test('thread list renders subject and sender for each thread', async () => {
    api.get.mockImplementation((path) => {
      if (path === '/api/messages/threads') return Promise.resolve([
        { id: 'thread-1', subject: 'Move-in question', lastMessage: 'Hi', participants: [], updatedAt: null },
      ]);
      if (path === '/api/messages/blocks') return Promise.resolve([]);
      return Promise.resolve([]);
    });

    await act(async () => { render(<MessagesPage />); });

    expect(await screen.findByText('Move-in question')).toBeInTheDocument();
  });

  test('clicking a thread loads its messages', async () => {
    const thread = { id: 'thread-1', subject: 'Hello', participants: [], updatedAt: null };
    api.get.mockImplementation((path) => {
      if (path === '/api/messages/threads')          return Promise.resolve([thread]);
      if (path === '/api/messages/threads/thread-1') return Promise.resolve({
        ...thread,
        messages: [{ id: 'msg-1', senderId: 'staff-1', body: 'Welcome!', sentAt: null, status: 'DELIVERED' }],
      });
      if (path === '/api/messages/blocks') return Promise.resolve([]);
      return Promise.resolve([]);
    });

    await act(async () => { render(<MessagesPage />); });
    await act(async () => {
      fireEvent.click(await screen.findByText('Hello'));
    });

    expect(await screen.findByText('Welcome!')).toBeInTheDocument();
  });

  test('sending a text message calls api.post with the message body', async () => {
    const thread = { id: 'thread-1', subject: 'Chat', participants: [], updatedAt: null };
    api.get.mockImplementation((path) => {
      if (path === '/api/messages/threads')          return Promise.resolve([thread]);
      if (path === '/api/messages/threads/thread-1') return Promise.resolve({
        ...thread, messages: [],
      });
      if (path === '/api/messages/blocks') return Promise.resolve([]);
      return Promise.resolve([]);
    });
    api.post.mockResolvedValueOnce({ id: 'msg-new', body: 'Hey', sentAt: null, status: 'SENT' });

    await act(async () => { render(<MessagesPage />); });
    await act(async () => {
      fireEvent.click(await screen.findByText('Chat'));
    });

    const input = await screen.findByPlaceholderText(/write a message/i);
    fireEvent.change(input, { target: { value: 'Hey' } });

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /send/i }));
    });

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        '/api/messages/threads/thread-1/messages',
        expect.objectContaining({ body: 'Hey' })
      );
    });
  });

  test('student role shows Manage Blocks icon and no Send Notice icon', async () => {
    // isStaff is computed synchronously from user.roles; no API response needed
    // to verify which toolbar buttons are rendered.
    api.get.mockImplementation((path) => {
      if (path === '/api/messages/threads') return Promise.resolve([]);
      if (path === '/api/messages/blocks')  return Promise.resolve([]);
      return Promise.resolve([]);
    });

    await act(async () => { render(<MessagesPage />); });

    // Staff-only icon is absent for STUDENT.
    expect(screen.queryByTitle(/send system notice/i)).not.toBeInTheDocument();
    // Student-only icon is present.
    expect(screen.getByTitle(/manage blocks/i)).toBeInTheDocument();
  });
});
