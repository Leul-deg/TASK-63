import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import ResidentAgreementsPage from './ResidentAgreementsPage';

jest.mock('react-router-dom', () => ({
  useParams: () => ({ id: 'r1' }),
  useNavigate: () => jest.fn(),
}));

jest.mock('../api/client', () => ({
  api: { get: jest.fn(), post: jest.fn() },
}));

const { api } = require('../api/client');

const STUB_RESIDENT   = { id: 'r1', firstName: 'Alice', lastName: 'Smith' };
const STUB_AGREEMENT  = {
  id: 'agr-1',
  agreementType: 'Standard Residential',
  status: 'PENDING',
  version: '2024-A',
  attachmentCount: 0,
};

describe('ResidentAgreementsPage', () => {
  beforeEach(() => jest.clearAllMocks());

  function mockEmpty() {
    api.get.mockImplementation(url => {
      if (url === `/api/residents/r1`)    return Promise.resolve(STUB_RESIDENT);
      if (url.endsWith('/agreements'))    return Promise.resolve([]);
      if (url.includes('/attachments'))   return Promise.resolve([]);
      return Promise.resolve({});
    });
  }

  function mockWithAgreement() {
    api.get.mockImplementation(url => {
      if (url === `/api/residents/r1`)    return Promise.resolve(STUB_RESIDENT);
      if (url.endsWith('/agreements'))    return Promise.resolve([STUB_AGREEMENT]);
      if (url.includes('/attachments'))   return Promise.resolve([]);
      return Promise.resolve({});
    });
  }

  test('renders "Housing Agreements" heading', async () => {
    mockEmpty();
    render(<ResidentAgreementsPage />);
    expect(await screen.findByText('Housing Agreements')).toBeInTheDocument();
  });

  test('shows resident name in subtitle after load', async () => {
    mockEmpty();
    render(<ResidentAgreementsPage />);
    expect(await screen.findByText('Alice Smith')).toBeInTheDocument();
  });

  test('shows empty state when resident has no agreements', async () => {
    mockEmpty();
    render(<ResidentAgreementsPage />);
    expect(await screen.findByText(/no housing agreements found/i)).toBeInTheDocument();
  });

  test('renders agreement card when agreements exist', async () => {
    mockWithAgreement();
    render(<ResidentAgreementsPage />);
    expect(await screen.findByText('Standard Residential')).toBeInTheDocument();
    expect(screen.getByText('PENDING')).toBeInTheDocument();
  });

  test('shows error message when API fails to load', async () => {
    api.get.mockRejectedValue(new Error('Network error'));
    render(<ResidentAgreementsPage />);
    expect(await screen.findByText(/network error/i)).toBeInTheDocument();
  });

  test('"+ New Agreement" button reveals the create form', async () => {
    mockEmpty();
    render(<ResidentAgreementsPage />);
    await screen.findByText('Housing Agreements');
    fireEvent.click(screen.getByRole('button', { name: /new agreement/i }));
    expect(screen.getByPlaceholderText(/standard residential/i)).toBeInTheDocument();
  });

  test('shows validation error when agreement type is blank on submit', async () => {
    mockEmpty();
    render(<ResidentAgreementsPage />);
    await screen.findByText('Housing Agreements');
    fireEvent.click(screen.getByRole('button', { name: /new agreement/i }));
    fireEvent.click(screen.getByRole('button', { name: /create agreement/i }));
    expect(screen.getByText(/agreement type is required/i)).toBeInTheDocument();
  });

  test('successful create adds the agreement card to the list', async () => {
    mockEmpty();
    api.post.mockResolvedValueOnce({ ...STUB_AGREEMENT, id: 'agr-new' });
    render(<ResidentAgreementsPage />);
    await screen.findByText('Housing Agreements');

    fireEvent.click(screen.getByRole('button', { name: /new agreement/i }));
    const typeInput = screen.getByPlaceholderText(/standard residential/i);
    fireEvent.change(typeInput, { target: { value: 'Standard Residential' } });
    fireEvent.click(screen.getByRole('button', { name: /create agreement/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith(
        `/api/residents/r1/agreements`,
        expect.objectContaining({ agreementType: 'Standard Residential' })
      );
    });
    expect(await screen.findByText('Standard Residential')).toBeInTheDocument();
  });
});
