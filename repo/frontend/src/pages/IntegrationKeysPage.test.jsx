import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import IntegrationKeysPage from './IntegrationKeysPage';

jest.mock('../api/client', () => ({
  api: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const { api } = require('../api/client');

const STUB_KEY = {
  id: 'key-1',
  name: 'Test Key',
  description: 'A test integration key',
  secretPrefix: 'rk_ab',
  active: true,
  createdAt: '2026-04-01T00:00:00Z',
};

describe('IntegrationKeysPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders page heading', async () => {
    api.get.mockResolvedValueOnce({ content: [], totalPages: 0 });

    render(<IntegrationKeysPage />);

    expect(await screen.findByText(/integration keys/i)).toBeInTheDocument();
  });

  test('displays keys returned from the API', async () => {
    api.get.mockResolvedValueOnce({ content: [STUB_KEY], totalPages: 1 });

    render(<IntegrationKeysPage />);

    expect(await screen.findByText('Test Key')).toBeInTheDocument();
  });

  test('shows error message when API call fails', async () => {
    api.get.mockRejectedValueOnce(new Error('Unauthorized'));

    render(<IntegrationKeysPage />);

    expect(await screen.findByText(/unauthorized/i)).toBeInTheDocument();
  });
});
