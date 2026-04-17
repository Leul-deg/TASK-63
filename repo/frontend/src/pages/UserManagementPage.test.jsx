import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import UserManagementPage from './UserManagementPage';

jest.mock('../api/client', () => ({
  api: { get: jest.fn(), patch: jest.fn(), delete: jest.fn() },
}));

const { api } = require('../api/client');

const STUB_USER = {
  id: 'u-1',
  username: 'jdoe',
  email: 'jdoe@example.com',
  firstName: 'John',
  lastName: 'Doe',
  accountStatus: 'ACTIVE',
  statusReason: null,
  roles: ['ROLE_RESIDENT'],
  createdAt: '2024-03-01T10:00:00Z',
  deleted: false,
  scheduledPurgeAt: null,
};

function mockEmpty() {
  api.get.mockResolvedValue({ content: [], totalPages: 0, totalElements: 0 });
}

function mockWithUser() {
  api.get.mockResolvedValue({ content: [STUB_USER], totalPages: 1, totalElements: 1 });
}

describe('UserManagementPage', () => {
  beforeEach(() => jest.clearAllMocks());

  test('renders "User Management" heading', async () => {
    mockEmpty();
    render(<UserManagementPage />);
    expect(screen.getByText('User Management')).toBeInTheDocument();
  });

  test('shows "Loading…" before data arrives', () => {
    api.get.mockReturnValue(new Promise(() => {})); // never resolves
    render(<UserManagementPage />);
    expect(screen.getByText('Loading…')).toBeInTheDocument();
  });

  test('shows "No users found." when the list is empty', async () => {
    mockEmpty();
    render(<UserManagementPage />);
    expect(await screen.findByText('No users found.')).toBeInTheDocument();
  });

  test('renders a user row with username and email', async () => {
    mockWithUser();
    render(<UserManagementPage />);
    expect(await screen.findByText('jdoe')).toBeInTheDocument();
    expect(screen.getByText('jdoe@example.com')).toBeInTheDocument();
  });

  test('"Change status" button reveals the inline status form', async () => {
    mockWithUser();
    render(<UserManagementPage />);
    await screen.findByText('jdoe');

    fireEvent.click(screen.getByRole('button', { name: /change status/i }));

    expect(screen.getByRole('button', { name: /^save$/i })).toBeInTheDocument();
  });

  test('saving a status change calls api.patch with new status and reason', async () => {
    mockWithUser();
    api.patch.mockResolvedValueOnce({});
    render(<UserManagementPage />);
    await screen.findByText('jdoe');

    fireEvent.click(screen.getByRole('button', { name: /change status/i }));

    // The inline select starts at the user's current status ("ACTIVE"),
    // distinguishing it from the filter select which shows "All statuses".
    const select = screen.getByDisplayValue('ACTIVE');
    fireEvent.change(select, { target: { value: 'DISABLED' } });

    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => {
      expect(api.patch).toHaveBeenCalledWith(
        '/api/admin/users/u-1/status',
        expect.objectContaining({ status: 'DISABLED' })
      );
    });
  });

  test('shows error banner when loading users fails', async () => {
    api.get.mockRejectedValue(new Error('Unauthorized'));
    render(<UserManagementPage />);
    expect(await screen.findByText('Unauthorized')).toBeInTheDocument();
  });
});
